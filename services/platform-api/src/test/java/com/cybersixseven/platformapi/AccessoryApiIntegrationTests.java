package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.controller.SubmissionController;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.service.AccessoryKeys;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessoryApiIntegrationTests extends PostgresIntegrationTest {

    private static final String VALID_REQUEST =
            """
            {
              "answers": [
                {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
                {"questionId":"00000000-0000-0000-0000-000000000002","answer":27},
                {"questionId":"00000000-0000-0000-0000-000000000003","answer":0}
              ]
            }
            """;

    @LocalServerPort
    private int port;

    private final SubmissionRepository submissionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    AccessoryApiIntegrationTests(
            SubmissionRepository submissionRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @BeforeEach
    void resetRows() {
        outboxEventRepository.deleteAll();
        submissionRepository.deleteAll();
    }

    @Test
    void capabilityPollingReturnsReadinessAndNeverLeaksTheObjectKey() throws Exception {
        JsonNode created = objectMapper.readTree(post(VALID_REQUEST).body());
        UUID id = UUID.fromString(created.get("id").asText());
        String secret = created.get("submissionSecret").asText();

        HttpResponse<String> pending = getSubmission(id, secret, false);
        assertEquals(200, pending.statusCode());
        JsonNode pendingBody = objectMapper.readTree(pending.body());
        assertEquals("PENDING", pendingBody.get("accessoryStatus").asText());
        assertEquals(2, pendingBody.get("score").asInt());
        assertFalse(leaksObjectLocation(pending.body()));

        String key = AccessoryKeys.forSubmission(id);
        HttpResponse<String> patched = patchAccessory(id, key, "test-internal-api-key");
        assertEquals(200, patched.statusCode());
        JsonNode patchBody = objectMapper.readTree(patched.body());
        assertEquals("READY", patchBody.get("accessoryStatus").asText());
        assertFalse(leaksObjectLocation(patched.body()));

        HttpResponse<String> ready = getSubmission(id, secret, false);
        JsonNode readyBody = objectMapper.readTree(ready.body());
        assertEquals("READY", readyBody.get("accessoryStatus").asText());
        assertFalse(leaksObjectLocation(ready.body()));

        Submission stored = submissionRepository.findById(id).orElseThrow();
        assertEquals(key, stored.getAccessoryKey());
        assertNull(readyBody.get("accessoryKey"));
        assertNull(readyBody.get("accessoryUrl"));
        assertNull(readyBody.get("url"));
    }

    @Test
    void missingWrongOrQueryStringCapabilityCannotPoll() throws Exception {
        JsonNode created = objectMapper.readTree(post(VALID_REQUEST).body());
        UUID id = UUID.fromString(created.get("id").asText());
        String secret = created.get("submissionSecret").asText();

        assertEquals(404, getSubmission(id, null, false).statusCode());
        assertEquals(404, getSubmission(id, "not-the-secret", false).statusCode());
        assertEquals(404, getSubmission(id, secret, true).statusCode());
        assertEquals(404, get(path("/api/submissions/" + UUID.randomUUID()), secret).statusCode());
    }

    @Test
    void internalAccessoryPatchIsIdempotentAndConflictsOnADifferentKey() throws Exception {
        JsonNode created = objectMapper.readTree(post(VALID_REQUEST).body());
        UUID id = UUID.fromString(created.get("id").asText());
        String key = AccessoryKeys.forSubmission(id);

        assertEquals(401, patchAccessory(id, key, null).statusCode());
        assertEquals(401, patchAccessory(id, key, "wrong").statusCode());
        assertEquals(409, patchAccessory(id, "accessories/" + id + ".stl", "test-internal-api-key").statusCode());
        assertEquals(409, patchAccessory(id, "https://example.invalid/" + key, "test-internal-api-key").statusCode());
        assertEquals(200, patchAccessory(id, key, "test-internal-api-key").statusCode());
        assertEquals(200, patchAccessory(id, key, "test-internal-api-key").statusCode());

        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertEquals(
                409,
                patchAccessory(id, AccessoryKeys.forSubmission(other), "test-internal-api-key")
                        .statusCode());
        assertEquals(key, submissionRepository.findById(id).orElseThrow().getAccessoryKey());
    }

    @Test
    void unknownSubmissionPatchIsNotFoundAndDownloadRouteDoesNotExist() throws Exception {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");
        assertEquals(
                404,
                patchAccessory(missing, AccessoryKeys.forSubmission(missing), "test-internal-api-key")
                        .statusCode());

        JsonNode created = objectMapper.readTree(post(VALID_REQUEST).body());
        UUID id = UUID.fromString(created.get("id").asText());
        String secret = created.get("submissionSecret").asText();
        HttpResponse<String> download = get(path("/api/submissions/" + id + "/accessory/download"), secret);
        assertTrue(download.statusCode() == 404 || download.statusCode() == 403, download.body());
        assertFalse(leaksObjectLocation(download.body()));
    }

    private static boolean leaksObjectLocation(String body) {
        String lower = body.toLowerCase();
        return lower.contains("accessorykey")
                || lower.contains("accessoryurl")
                || lower.contains("reward.stl")
                || lower.contains("cybersixseven-accessories")
                || lower.contains("s3.amazonaws.com")
                || lower.contains("x-amz-");
    }

    private HttpResponse<String> post(String body) throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(path("/api/submissions")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getSubmission(UUID id, String secret, boolean secretInQuery)
            throws IOException, InterruptedException {
        String url = path("/api/submissions/" + id);
        if (secretInQuery && secret != null) {
            url += "?secret=" + secret;
            return get(url, null);
        }
        return get(url, secret);
    }

    private HttpResponse<String> get(String url, String secret) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (secret != null) {
            builder.header(SubmissionController.SECRET_HEADER, secret);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patchAccessory(UUID id, String accessoryKey, String internalKey)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(path("/internal/submissions/" + id + "/accessory")))
                .header("Content-Type", "application/json")
                .method(
                        "PATCH",
                        HttpRequest.BodyPublishers.ofString(
                                "{\"accessoryKey\":\"" + accessoryKey + "\"}"));
        if (internalKey != null) {
            builder.header("X-Internal-Api-Key", internalKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String path(String suffix) {
        return "http://localhost:" + port + suffix;
    }
}
