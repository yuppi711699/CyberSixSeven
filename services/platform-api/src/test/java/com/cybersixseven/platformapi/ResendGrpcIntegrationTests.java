package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.contracts.device.DeviceCommandGrpc;
import com.cybersixseven.contracts.device.DeviceCommandRequest;
import com.cybersixseven.contracts.device.DeviceCommandResponse;
import com.cybersixseven.platformapi.entity.CommandResendAudit;
import com.cybersixseven.platformapi.repository.CommandResendAuditRepository;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.service.CommandResendAuditService;
import com.cybersixseven.platformapi.service.ResendRateLimiter;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResendGrpcIntegrationTests extends PostgresIntegrationTest {

    enum Mode {
        OK,
        SLOW,
        DOWN
    }

    private static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.OK);
    private static Server server;
    private static final int grpcPort;

    static {
        try {
            Server started = NettyServerBuilder.forPort(0)
                    .addService(new DeviceCommandGrpc.DeviceCommandImplBase() {
                        @Override
                        public void sendCommand(
                                DeviceCommandRequest request, StreamObserver<DeviceCommandResponse> observer) {
                            try {
                                if (MODE.get() == Mode.SLOW) {
                                    Thread.sleep(1000);
                                }
                                if (MODE.get() == Mode.DOWN) {
                                    observer.onError(Status.UNAVAILABLE
                                            .withDescription("broker socket /tmp/secret")
                                            .asRuntimeException());
                                    return;
                                }
                                observer.onNext(DeviceCommandResponse.newBuilder()
                                        .setCommandId(request.getCommandId())
                                        .setAccepted(true)
                                        .setMessage("republished")
                                        .build());
                                observer.onCompleted();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                observer.onError(Status.UNAVAILABLE.asRuntimeException());
                            }
                        }
                    })
                    .build()
                    .start();
            server = started;
            grpcPort = started.getPort();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void grpc(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.grpc.client.channel.device-command.target", () -> "static://127.0.0.1:" + grpcPort);
        registry.add("app.grpc.device-command.deadline", () -> "300ms");
        registry.add("app.grpc.device-command.connect-timeout", () -> "500ms");
    }

    @LocalServerPort
    private int port;

    private final SubmissionRepository submissionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CommandResendAuditRepository auditRepository;
    private final ResendRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private AuthSupport auth;

    @Autowired
    ResendGrpcIntegrationTests(
            SubmissionRepository submissionRepository,
            OutboxEventRepository outboxEventRepository,
            CommandResendAuditRepository auditRepository,
            ResendRateLimiter rateLimiter,
            ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditRepository = auditRepository;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void reset() throws IOException, InterruptedException {
        MODE.set(Mode.OK);
        rateLimiter.reset();
        auditRepository.deleteAll();
        outboxEventRepository.deleteAll();
        submissionRepository.deleteAll();
        auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
    }

    @Test
    void resendKeepsCommandIdAndWritesAudit() throws IOException, InterruptedException {
        AuthSupport.Session teacher = auth.login("teacher@example.test", "teacher-pass-1");
        AuthSupport.Session student = auth.registerStudent();
        HttpResponse<String> submitted = send("POST", "/api/submissions", answers(), student.accessToken());
        assertEquals(201, submitted.statusCode(), submitted.body());
        UUID commandId = outboxEventRepository.findAll().get(0).getId();
        UUID deviceId = deviceId(teacher.accessToken());

        HttpResponse<String> response =
                send("POST", "/api/admin/devices/" + deviceId + "/resend-command", "", teacher.accessToken());
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(commandId.toString(), body.get("commandId").asText());
        assertTrue(body.get("accepted").asBoolean());
        assertEquals("command republished", body.get("message").asText());

        CommandResendAudit audit = auditRepository.findAll().get(0);
        assertEquals(commandId, audit.getCommandId());
        assertEquals(deviceId, audit.getDeviceId());
        assertEquals(UUID.fromString(teacher.userId()), audit.getStaffUserId());
        assertEquals(CommandResendAuditService.ACCEPTED, audit.getOutcome());
    }

    @Test
    void deadlineAndOutageDoNotLeakInternals() throws IOException, InterruptedException {
        AuthSupport.Session teacher = auth.login("teacher@example.test", "teacher-pass-1");
        AuthSupport.Session student = auth.registerStudent();
        assertEquals(201, send("POST", "/api/submissions", answers(), student.accessToken()).statusCode());
        UUID deviceId = deviceId(teacher.accessToken());

        MODE.set(Mode.SLOW);
        HttpResponse<String> timeout =
                send("POST", "/api/admin/devices/" + deviceId + "/resend-command", "", teacher.accessToken());
        assertEquals(504, timeout.statusCode(), timeout.body());
        assertFalse(timeout.body().contains("DEADLINE"));
        assertTrue(auditRepository.findAll().stream()
                .anyMatch(row -> CommandResendAuditService.TIMEOUT.equals(row.getOutcome())));

        rateLimiter.reset();
        MODE.set(Mode.DOWN);
        HttpResponse<String> down =
                send("POST", "/api/admin/devices/" + deviceId + "/resend-command", "", teacher.accessToken());
        assertEquals(503, down.statusCode(), down.body());
        assertEquals("command service unavailable", objectMapper.readTree(down.body()).get("message").asText());
        assertFalse(down.body().contains("secret"));
        assertFalse(down.body().contains("broker"));
    }

    @Test
    void sixthResendInAMinuteIsRejected() throws IOException, InterruptedException {
        AuthSupport.Session teacher = auth.login("teacher@example.test", "teacher-pass-1");
        AuthSupport.Session student = auth.registerStudent();
        assertEquals(201, send("POST", "/api/submissions", answers(), student.accessToken()).statusCode());
        UUID deviceId = deviceId(teacher.accessToken());
        String path = "/api/admin/devices/" + deviceId + "/resend-command";
        for (int attempt = 1; attempt <= 5; attempt++) {
            HttpResponse<String> response = send("POST", path, "", teacher.accessToken());
            assertEquals(200, response.statusCode(), response.body());
        }
        HttpResponse<String> limited = send("POST", path, "", teacher.accessToken());
        assertEquals(429, limited.statusCode(), limited.body());
        assertEquals(
                1,
                auditRepository.findAll().stream()
                        .filter(row -> CommandResendAuditService.RATE_LIMITED.equals(row.getOutcome()))
                        .count());
    }

    private UUID deviceId(String accessToken) throws IOException, InterruptedException {
        HttpResponse<String> devices = send("GET", "/api/admin/devices?size=20", null, accessToken);
        assertEquals(200, devices.statusCode(), devices.body());
        for (JsonNode device : objectMapper.readTree(devices.body()).get("content")) {
            if ("esp32-dev-001".equals(device.get("hardwareId").asText())) {
                return UUID.fromString(device.get("id").asText());
            }
        }
        throw new IllegalStateException("demo device missing");
    }

    private HttpResponse<String> send(String method, String path, String body, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }
        return auth.send(builder.build());
    }

    private static String answers() {
        return """
                {"answers":[
                  {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
                  {"questionId":"00000000-0000-0000-0000-000000000002","answer":27},
                  {"questionId":"00000000-0000-0000-0000-000000000003","answer":12}
                ]}
                """;
    }
}
