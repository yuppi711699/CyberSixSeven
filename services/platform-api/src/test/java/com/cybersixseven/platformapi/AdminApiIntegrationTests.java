package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.cybersixseven.platformapi.service.LeaderboardService;
import com.cybersixseven.platformapi.service.SubmissionCapabilityGenerator;
import com.cybersixseven.platformapi.service.SubmissionCapabilityGenerator.GeneratedCapability;
import com.cybersixseven.platformapi.service.SubmissionService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminApiIntegrationTests extends PostgresIntegrationTest {

    private static final String ANSWERS =
            """
            {"answers":[
              {"questionId":"00000000-0000-0000-0000-000000000001","answer":12},
              {"questionId":"00000000-0000-0000-0000-000000000002","answer":27},
              {"questionId":"00000000-0000-0000-0000-000000000003","answer":0}
            ]}
            """;

    @LocalServerPort
    private int port;

    private final SubmissionRepository submissionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final LeaderboardService leaderboardService;
    private final SubmissionService submissionService;
    private final SubmissionCapabilityGenerator capabilityGenerator;
    private AuthSupport auth;

    @Autowired
    AdminApiIntegrationTests(
            SubmissionRepository submissionRepository,
            OutboxEventRepository outboxEventRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            StringRedisTemplate redis,
            LeaderboardService leaderboardService,
            SubmissionService submissionService,
            SubmissionCapabilityGenerator capabilityGenerator) {
        this.submissionRepository = submissionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.leaderboardService = leaderboardService;
        this.submissionService = submissionService;
        this.capabilityGenerator = capabilityGenerator;
    }

    @BeforeEach
    void reset() throws IOException, InterruptedException {
        jdbcTemplate.update("DELETE FROM command_resend_audits");
        outboxEventRepository.deleteAll();
        submissionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM questions WHERE display_order > 3");
        Set<String> markers = redis.keys(LeaderboardService.APPLIED_PREFIX + "*");
        if (markers != null && !markers.isEmpty()) {
            redis.delete(markers);
        }
        redis.delete(LeaderboardService.SCORES_KEY);
        auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
    }

    @Test
    void studentIsForbiddenOnStaffCollections() throws IOException, InterruptedException {
        AuthSupport.Session student = auth.registerStudent();
        for (String path : List.of(
                "/api/admin/questions",
                "/api/admin/submissions",
                "/api/admin/devices",
                "/api/admin/leaderboard")) {
            HttpResponse<String> response = send("GET", path, null, student.accessToken());
            assertEquals(403, response.statusCode(), path);
        }
        HttpResponse<String> resend = send(
                "POST",
                "/api/admin/devices/00000000-0000-0000-0000-000000000099/resend-command",
                "",
                student.accessToken());
        assertEquals(403, resend.statusCode());
    }

    @Test
    void teacherQuestionAppearsForStudentsWithoutTheAnswer() throws IOException, InterruptedException {
        AuthSupport.Session teacher = auth.login("teacher@example.test", "teacher-pass-1");
        HttpResponse<String> created = send(
                "POST",
                "/api/admin/questions",
                """
                {"prompt":"What is 1 + 1?","options":[1,2],"correctAnswer":2,"maxPoints":1,"displayOrder":4}
                """,
                teacher.accessToken());
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(objectMapper.readTree(created.body()).has("correctAnswer"));

        AuthSupport.Session student = auth.registerStudent();
        HttpResponse<String> questions = send("GET", "/api/questions", null, student.accessToken());
        assertEquals(200, questions.statusCode());
        assertFalse(questions.body().contains("correctAnswer"));
        assertFalse(questions.body().contains("teacher@example.test"));
        boolean found = false;
        for (JsonNode question : objectMapper.readTree(questions.body())) {
            if ("What is 1 + 1?".equals(question.get("prompt").asText())) {
                found = true;
            }
        }
        assertTrue(found);

        HttpResponse<String> invalid = send(
                "POST",
                "/api/admin/questions",
                """
                {"prompt":"","options":[],"correctAnswer":1,"maxPoints":0,"displayOrder":5}
                """,
                teacher.accessToken());
        assertEquals(400, invalid.statusCode());
    }

    @Test
    void submissionsPageUsesNicknameAndNotEmail() throws IOException, InterruptedException {
        AuthSupport.Session student = auth.registerStudent();
        assertEquals(201, send("POST", "/api/submissions", ANSWERS, student.accessToken()).statusCode());
        AuthSupport.Session teacher = auth.login("teacher@example.test", "teacher-pass-1");
        HttpResponse<String> page =
                send("GET", "/api/admin/submissions?page=0&size=5&sort=score,desc", null, teacher.accessToken());
        assertEquals(200, page.statusCode(), page.body());
        JsonNode body = objectMapper.readTree(page.body());
        JsonNode row = body.get("content").get(0);
        assertEquals(student.userId(), row.get("studentId").asText());
        assertEquals("Pat", row.get("nickname").asText());
        assertEquals(2, row.get("score").asInt());
        assertFalse(page.body().contains(student.email()));
        assertFalse(row.has("email"));
    }

    @Test
    void duplicateLeaderboardApplyIncrementsOnce() throws IOException, InterruptedException {
        AuthSupport.Session student = auth.registerStudent();
        HttpResponse<String> submitted = send("POST", "/api/submissions", ANSWERS, student.accessToken());
        assertEquals(201, submitted.statusCode(), submitted.body());
        UUID submissionId = UUID.fromString(objectMapper.readTree(submitted.body()).get("id").asText());
        UUID studentId = UUID.fromString(student.userId());

        assertEquals(2.0, redis.opsForZSet().score(LeaderboardService.SCORES_KEY, student.userId()));
        assertFalse(leaderboardService.apply(submissionId, studentId, 2));
        assertEquals(2.0, redis.opsForZSet().score(LeaderboardService.SCORES_KEY, student.userId()));

        AuthSupport.Session teacher = auth.login("teacher@example.test", "teacher-pass-1");
        HttpResponse<String> board = send("GET", "/api/admin/leaderboard", null, teacher.accessToken());
        JsonNode entry = objectMapper.readTree(board.body()).get("content").get(0);
        assertEquals(student.userId(), entry.get("userId").asText());
        assertEquals("Pat", entry.get("nickname").asText());
        assertEquals(2.0, entry.get("score").asDouble());
        assertFalse(board.body().contains(student.email()));
    }

    @Test
    void claimAppliesAwardedPointsOnce() throws IOException, InterruptedException {
        AuthSupport.Session student = auth.registerStudent();
        GeneratedCapability capability = capabilityGenerator.generate();
        UUID submissionId = UUID.randomUUID();
        submissionRepository.save(new com.cybersixseven.platformapi.entity.Submission(
                submissionId, List.of(), 4, 4, capability.hash(), Instant.now()));
        UUID studentId = UUID.fromString(student.userId());

        submissionService.claim(submissionId, studentId, capability.plaintext());
        assertEquals(4.0, redis.opsForZSet().score(LeaderboardService.SCORES_KEY, student.userId()));
        submissionService.claim(submissionId, studentId, capability.plaintext());
        assertEquals(4.0, redis.opsForZSet().score(LeaderboardService.SCORES_KEY, student.userId()));
    }

    private HttpResponse<String> send(String method, String path, String body, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        if ("POST".equals(method) || "PUT".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return auth.send(builder.build());
    }
}
