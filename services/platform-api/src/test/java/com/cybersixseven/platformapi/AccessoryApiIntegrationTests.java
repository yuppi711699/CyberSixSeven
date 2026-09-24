package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cybersixseven.platformapi.controller.SubmissionController;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.service.AccessoryKeys;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
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
    private AuthSupport auth;
    private AuthSupport.Session student;

    @Autowired
    AccessoryApiIntegrationTests(
            SubmissionRepository submissionRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void resetRows() throws Exception {
        outboxEventRepository.deleteAll();
        submissionRepository.deleteAll();
        auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
        student = auth.registerStudent();
    }

    @Test
    void jwtPollingReturnsReadinessAndNeverLeaksTheObjectKey() throws Exception {
        JsonNode created = objectMapper.readTree(post(VALID_REQUEST, student.accessToken()).body());
        UUID id = UUID.fromString(created.get("id").asText());
        String secret = created.get("submissionSecret").asText();

        HttpResponse<String> pending = getSubmission(id, student.accessToken(), null);
        assertEquals(200, pending.statusCode());
        JsonNode pendingBody = objectMapper.readTree(pending.body());
        assertEquals("PENDING", pendingBody.get("accessoryStatus").asText());
        assertFalse(leaksObjectLocation(pending.body()));

        String key = AccessoryKeys.forSubmission(id);
        HttpResponse<String> patched = patchAccessory(id, key, "test-internal-api-key");
        assertEquals(200, patched.statusCode());

        HttpResponse<String> ready = getSubmission(id, student.accessToken(), null);
        JsonNode readyBody = objectMapper.readTree(ready.body());
        assertEquals("READY", readyBody.get("accessoryStatus").asText());
        assertFalse(leaksObjectLocation(ready.body()));
        assertNull(readyBody.get("accessoryKey"));

        assertEquals(404, getSubmission(id, null, secret).statusCode());
    }

    @Test
    void unclaimedCapabilityStillWorksAndWrongProofIs404() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest("memory-only".getBytes(StandardCharsets.UTF_8));
        submissionRepository.save(new Submission(id, List.of(), 1, 1, hash, Instant.parse("2026-09-16T00:00:00Z")));

        assertEquals(200, getSubmission(id, null, "memory-only").statusCode());
        assertEquals(404, getSubmission(id, null, null).statusCode());
        assertEquals(404, getSubmission(id, null, "not-the-secret").statusCode());
        assertEquals(404, get(path("/api/submissions/" + UUID.randomUUID()), null, "memory-only").statusCode());
    }

    @Test
    void internalAccessoryPatchIsIdempotentAndConflictsOnADifferentKey() throws Exception {
        JsonNode created = objectMapper.readTree(post(VALID_REQUEST, student.accessToken()).body());
        UUID id = UUID.fromString(created.get("id").asText());
        String key = AccessoryKeys.forSubmission(id);

        assertEquals(401, patchAccessory(id, key, null).statusCode());
        assertEquals(401, patchAccessory(id, key, "wrong").statusCode());
        assertEquals(409, patchAccessory(id, "accessories/" + id + ".stl", "test-internal-api-key").statusCode());
        assertEquals(200, patchAccessory(id, key, "test-internal-api-key").statusCode());
        assertEquals(200, patchAccessory(id, key, "test-internal-api-key").statusCode());
    }

    @Test
    void downloadRequiresOwnerJwtAndRedirectsToAShortLivedUrl() throws Exception {
        JsonNode created = objectMapper.readTree(post(VALID_REQUEST, student.accessToken()).body());
        UUID id = UUID.fromString(created.get("id").asText());
        patchAccessory(id, AccessoryKeys.forSubmission(id), "test-internal-api-key");

        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://s3.example/object?X-Amz-Expires=300"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        HttpResponse<String> unauthenticated = get(path("/api/submissions/" + id + "/accessory/download"), null, null);
        assertEquals(401, unauthenticated.statusCode());

        HttpResponse<String> owner = get(
                path("/api/submissions/" + id + "/accessory/download"), student.accessToken(), null);
        assertEquals(302, owner.statusCode());
        assertTrue(owner.headers().firstValue("location").orElseThrow().startsWith("https://s3.example/"));

        AuthSupport.Session other = auth.registerStudent();
        assertEquals(
                404,
                get(path("/api/submissions/" + id + "/accessory/download"), other.accessToken(), null)
                        .statusCode());
        assertEquals(404, getSubmission(id, other.accessToken(), null).statusCode());
    }

    private static boolean leaksObjectLocation(String body) {
        String lower = body.toLowerCase();
        return lower.contains("accessorykey")
                || lower.contains("accessoryurl")
                || lower.contains("reward.stl")
                || lower.contains("cybersixseven-accessories")
                || lower.contains("s3.amazonaws.com");
    }

    private HttpResponse<String> post(String body, String accessToken) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(path("/api/submissions")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return auth.send(builder.build());
    }

    private HttpResponse<String> getSubmission(UUID id, String accessToken, String secret)
            throws IOException, InterruptedException {
        return get(path("/api/submissions/" + id), accessToken, secret);
    }

    private HttpResponse<String> get(String url, String accessToken, String secret)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET();
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if (secret != null) {
            builder.header(SubmissionController.SECRET_HEADER, secret);
        }
        return auth.send(builder.build());
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
        return auth.send(builder.build());
    }

    private String path(String suffix) {
        return "http://localhost:" + port + suffix;
    }
}
