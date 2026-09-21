package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HeartbeatAuthorizationTests extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    HeartbeatAuthorizationTests(DeviceRepository deviceRepository, ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Test
    void heartbeatWithoutKeyIsUnauthorized() throws Exception {
        HttpResponse<String> response = patch(
                "/internal/devices/esp32-dev-001/heartbeat",
                """
                {"deviceId":"esp32-dev-001","submissionId":"s","commandId":"c","status":"handled"}
                """,
                null);
        assertEquals(401, response.statusCode());
    }

    @Test
    void heartbeatWithWrongKeyIsUnauthorized() throws Exception {
        HttpResponse<String> response = patch(
                "/internal/devices/esp32-dev-001/heartbeat",
                """
                {"deviceId":"esp32-dev-001","submissionId":"s","commandId":"c","status":"handled"}
                """,
                "wrong");
        assertEquals(401, response.statusCode());
    }

    @Test
    void heartbeatWithConfiguredKeyUpdatesLastSeen() throws Exception {
        HttpResponse<String> response = patch(
                "/internal/devices/esp32-dev-001/heartbeat",
                """
                {"deviceId":"esp32-dev-001","submissionId":"00000000-0000-0000-0000-000000000099","commandId":"00000000-0000-0000-0000-000000000098","status":"handled"}
                """,
                "test-internal-api-key");
        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("esp32-dev-001", body.get("deviceId").asText());
        assertNotNull(body.get("lastSeenAt"));

        Device device = deviceRepository.findByHardwareId("esp32-dev-001").orElseThrow();
        assertNotNull(device.getLastSeenAt());
    }

    @Test
    void incompleteAcknowledgementIsRejected() throws Exception {
        HttpResponse<String> response = patch(
                "/internal/devices/esp32-dev-001/heartbeat",
                """
                {"deviceId":"esp32-dev-001","commandId":"c","status":"handled"}
                """,
                "test-internal-api-key");
        assertEquals(400, response.statusCode());
    }

    @Test
    void healthRemainsPublicAndUnknownPathsAreDenied() throws Exception {
        assertEquals(200, get("/actuator/health").statusCode());
        assertEquals("{\"status\":\"UP\"}", get("/actuator/health").body());
        int denied = get("/nope").statusCode();
        assertTrue(denied == 401 || denied == 403, "fallback must not be public: " + denied);
        assertEquals(401, get("/api/questions").statusCode());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String body, String key)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (key != null) {
            builder.header("X-Internal-Api-Key", key);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
