package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.entity.ScoredAnswerSnapshot;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionApiIntegrationTests extends PostgresIntegrationTest {

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
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private AuthSupport auth;

    @Autowired
    SubmissionApiIntegrationTests(
            SubmissionRepository submissionRepository,
            OutboxEventRepository outboxEventRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void resetDatabase() throws IOException, InterruptedException {
        outboxEventRepository.deleteAll();
        submissionRepository.deleteAll();
        jdbcTemplate.update(
                "UPDATE questions SET prompt = ?, correct_answer = ? WHERE id = ?::uuid",
                "What is 7 + 5?",
                12,
                "00000000-0000-0000-0000-000000000001");
        auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
    }

    @Test
    void questionsNeverExposeCorrectAnswers() throws IOException, InterruptedException {
        AuthSupport.Session session = auth.registerStudent();
        HttpResponse<String> response = get("/api/questions", session.accessToken());
        JsonNode questions = objectMapper.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals(3, questions.size());
        assertEquals("What is 7 + 5?", questions.get(0).get("prompt").asText());
        questions.forEach(question -> {
            assertFalse(question.has("correctAnswer"));
            assertFalse(question.has("maxPoints"));
        });
    }

    @Test
    void validSubmissionReturnsServerScoreAndStoresOnlyCapabilityHash()
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        AuthSupport.Session session = auth.registerStudent();
        HttpResponse<String> response = post(VALID_REQUEST, session.accessToken());
        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(201, response.statusCode());
        assertEquals(2, body.get("score").asInt());
        assertEquals(3, body.get("maxScore").asInt());
        assertEquals(3, body.get("answers").size());
        assertTrue(body.get("answers").get(0).get("correct").asBoolean());
        assertFalse(body.get("answers").get(2).get("correct").asBoolean());

        Submission stored = submissionRepository.findById(
                        java.util.UUID.fromString(body.get("id").asText()))
                .orElseThrow();
        String capability = body.get("submissionSecret").asText();
        byte[] expectedHash = MessageDigest.getInstance("SHA-256")
                .digest(capability.getBytes(StandardCharsets.UTF_8));

        assertEquals(java.util.UUID.fromString(session.userId()), stored.getStudentId());
        assertArrayEquals(expectedHash, stored.getSubmissionSecretHash());
        assertEquals(2, stored.getScore());

        List<OutboxEvent> outbox = outboxEventRepository.findAll();
        assertEquals(1, outbox.size());
        assertEquals(stored.getId(), outbox.get(0).getPayload().submissionId());
        assertEquals(outbox.get(0).getId(), outbox.get(0).getPayload().commandId());
        assertEquals("esp32-dev-001", outbox.get(0).getPayload().deviceId());
        assertEquals(3, outbox.get(0).getPayload().intensity());
    }

    @Test
    void unauthenticatedSubmissionIsUnauthorized() throws IOException, InterruptedException {
        assertEquals(401, post(VALID_REQUEST, null).statusCode());
        assertEquals(0, submissionRepository.count());
    }

    @Test
    void invalidRequestsCreateNoPartialRows() throws IOException, InterruptedException {
        AuthSupport.Session session = auth.registerStudent();
        List<String> invalidRequests = List.of(
                """
                {"answers":[
                  {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
                  {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
                  {"questionId":"00000000-0000-0000-0000-000000000003","answer":12}
                ]}
                """,
                """
                {"answers":[
                  {"questionId":"00000000-0000-0000-0000-000000000001","answer":12}
                ]}
                """,
                """
                {"answers":[
                  {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
                  {"questionId":"00000000-0000-0000-0000-000000000002","answer":27},
                  {"questionId":"ffffffff-ffff-ffff-ffff-ffffffffffff","answer":12}
                ]}
                """,
                """
                {"answers":[
                  {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
                  {"questionId":"00000000-0000-0000-0000-000000000002","answer":"not-a-number"},
                  {"questionId":"00000000-0000-0000-0000-000000000003","answer":12}
                ]}
                """,
                VALID_REQUEST.substring(0, VALID_REQUEST.lastIndexOf('}'))
                        + ",\"score\":999}");

        for (String request : invalidRequests) {
            assertEquals(400, post(request, session.accessToken()).statusCode());
        }
        assertEquals(0, submissionRepository.count());
        assertEquals(0, outboxEventRepository.count());
    }

    @Test
    void laterQuestionEditCannotChangeStoredHistory()
            throws IOException, InterruptedException {
        JsonNode body = objectMapper.readTree(post(VALID_REQUEST, auth.registerStudent().accessToken()).body());
        Submission before = submissionRepository.findById(
                        java.util.UUID.fromString(body.get("id").asText()))
                .orElseThrow();
        List<ScoredAnswerSnapshot> originalSnapshots = before.getAnswers();

        jdbcTemplate.update(
                "UPDATE questions SET prompt = ?, correct_answer = ? WHERE id = ?::uuid",
                "Edited prompt",
                999,
                "00000000-0000-0000-0000-000000000001");

        Submission after = submissionRepository.findById(before.getId()).orElseThrow();
        assertEquals(originalSnapshots, after.getAnswers());
        assertEquals("What is 7 + 5?", after.getAnswers().get(0).prompt());
        assertEquals(12, after.getAnswers().get(0).correctAnswer());
    }

    private HttpResponse<String> get(String path, String accessToken) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return auth.send(builder.build());
    }

    private HttpResponse<String> post(String body, String accessToken) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/submissions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return auth.send(builder.build());
    }
}
