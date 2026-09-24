package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.dto.AnswerSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionRequest;
import com.cybersixseven.platformapi.entity.DeviceCommandEvent;
import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import com.cybersixseven.platformapi.service.OutboxClaimService;
import com.cybersixseven.platformapi.service.OutboxPublisher;
import com.cybersixseven.platformapi.service.SubmissionService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxIntegrationTests extends PostgresIntegrationTest {

    private static final String VALID_REQUEST =
            """
            {
              "answers": [
                {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
                {"questionId":"00000000-0000-0000-0000-000000000002","answer":27},
                {"questionId":"00000000-0000-0000-0000-000000000003","answer":12}
              ]
            }
            """;

    @LocalServerPort
    private int port;

    private final SubmissionRepository submissionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxPublisher outboxPublisher;
    private final OutboxClaimService outboxClaimService;
    private final SubmissionService submissionService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private AuthSupport auth;
    private UUID studentId;

    @Autowired
    OutboxIntegrationTests(
            SubmissionRepository submissionRepository,
            OutboxEventRepository outboxEventRepository,
            OutboxPublisher outboxPublisher,
            OutboxClaimService outboxClaimService,
            SubmissionService submissionService,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxPublisher = outboxPublisher;
        this.outboxClaimService = outboxClaimService;
        this.submissionService = submissionService;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionManager = transactionManager;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void resetRows() throws Exception {
        outboxEventRepository.deleteAll();
        submissionRepository.deleteAll();
        reset(snsClient);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("test-sns").build());
        UserAccount student = userAccountRepository.save(new UserAccount(
                UUID.randomUUID(),
                "outbox-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("password1"),
                "Outbox",
                UserRole.STUDENT,
                Instant.now()));
        studentId = student.getId();
        auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
    }

    @Test
    void submissionCommitWritesOutboxAndPublishesRawJsonWithSameCommandId()
            throws IOException, InterruptedException {
        HttpResponse<String> response = post(VALID_REQUEST);
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(201, response.statusCode());

        outboxPublisher.publishDue();

        List<OutboxEvent> rows = outboxEventRepository.findAll();
        assertEquals(1, rows.size());
        OutboxEvent row = rows.get(0);
        assertEquals(UUID.fromString(body.get("id").asText()), row.getPayload().submissionId());
        assertEquals(row.getId(), row.getPayload().commandId());
        assertNotNull(row.getPublishedAt());

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, atLeastOnce()).publish(captor.capture());
        JsonNode published = objectMapper.readTree(captor.getValue().message());
        assertEquals(row.getId().toString(), published.get("commandId").asText());
        assertEquals(row.getPayload().submissionId().toString(), published.get("submissionId").asText());
        assertEquals("esp32-dev-001", published.get("deviceId").asText());
        assertEquals("correct", published.get("event").asText());
        assertEquals(3, published.get("intensity").asInt());
        assertEquals("application/json", guessNotSnsEnvelope(captor.getValue().message()));
    }

    @Test
    void rollbackEmitsNeitherSubmissionNorOutboxNorSns() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        try {
            template.executeWithoutResult(status -> {
                submissionService.submit(validRequest(), studentId);
                throw new IllegalStateException("force-rollback");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        assertEquals(0, submissionRepository.count());
        assertEquals(0, outboxEventRepository.count());
        verify(snsClient, never()).publish(any(PublishRequest.class));
    }

    @Test
    void publisherRecoversUnpublishedRowsAfterRestart() {
        UUID commandId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        outboxEventRepository.save(new OutboxEvent(
                commandId,
                new DeviceCommandEvent(commandId, submissionId, "esp32-dev-001", "correct", 3),
                Instant.now()));

        outboxPublisher.publishDue();

        OutboxEvent published = outboxEventRepository.findById(commandId).orElseThrow();
        assertNotNull(published.getPublishedAt());
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        assertTrue(captor.getValue().message().contains(commandId.toString()));
    }

    @Test
    void publisherRetriesAfterSnsFailureLeavesRowUnpublishedThenRecovers() {
        UUID commandId = UUID.randomUUID();
        outboxEventRepository.save(new OutboxEvent(
                commandId,
                new DeviceCommandEvent(commandId, UUID.randomUUID(), "esp32-dev-001", "incorrect", 3),
                Instant.now()));

        when(snsClient.publish(any(PublishRequest.class)))
                .thenThrow(SnsException.builder().message("boom").build())
                .thenReturn(PublishResponse.builder().messageId("retry").build());

        outboxPublisher.publishDue();
        assertNull(outboxEventRepository.findById(commandId).orElseThrow().getPublishedAt());

        outboxPublisher.publishDue();
        assertNotNull(outboxEventRepository.findById(commandId).orElseThrow().getPublishedAt());
        verify(snsClient, times(2)).publish(any(PublishRequest.class));
    }

    @Test
    void concurrentClaimsDoNotPublishTheSameRowTwice() throws Exception {
        UUID commandId = UUID.randomUUID();
        outboxEventRepository.save(new OutboxEvent(
                commandId,
                new DeviceCommandEvent(commandId, UUID.randomUUID(), "esp32-dev-001", "correct", 3),
                Instant.now()));

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger claimed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<OutboxEvent>> first = pool.submit(() -> {
                start.await();
                List<OutboxEvent> rows =
                        outboxClaimService.claim("a", Instant.now(), Duration.ofSeconds(30), 10);
                claimed.addAndGet(rows.size());
                return rows;
            });
            Future<List<OutboxEvent>> second = pool.submit(() -> {
                start.await();
                List<OutboxEvent> rows =
                        outboxClaimService.claim("b", Instant.now(), Duration.ofSeconds(30), 10);
                claimed.addAndGet(rows.size());
                return rows;
            });
            start.countDown();
            List<OutboxEvent> a = first.get(5, TimeUnit.SECONDS);
            List<OutboxEvent> b = second.get(5, TimeUnit.SECONDS);
            assertEquals(1, a.size() + b.size());
            assertEquals(1, claimed.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void claimRejectsOutOfRangeBatchSize() {
        Instant now = Instant.now();
        assertThrows(
                IllegalArgumentException.class,
                () -> outboxClaimService.claim("a", now, Duration.ofSeconds(30), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> outboxClaimService.claim("a", now, Duration.ofSeconds(30), 101));
    }

    private static String guessNotSnsEnvelope(String message) {
        assertTrue(!message.contains("\"Type\":\"Notification\""), message);
        return "application/json";
    }

    private CreateSubmissionRequest validRequest() {
        return new CreateSubmissionRequest(List.of(
                new AnswerSubmissionRequest(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"), 12),
                new AnswerSubmissionRequest(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"), 27),
                new AnswerSubmissionRequest(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"), 12)));
    }

    private HttpResponse<String> post(String body) throws IOException, InterruptedException {
        AuthSupport.Session session = auth.registerStudent();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/submissions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + session.accessToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return auth.send(request);
    }
}
