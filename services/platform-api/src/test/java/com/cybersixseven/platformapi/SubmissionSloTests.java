package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionSloTests extends PostgresIntegrationTest {

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

    private static final int SAMPLES = 20;

    @LocalServerPort
    private int port;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private AuthSupport auth;
    private AuthSupport.Session session;

    @Autowired
    SubmissionSloTests(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void student() throws IOException, InterruptedException {
        jdbcTemplate.update(
                "UPDATE questions SET prompt = ?, correct_answer = ? WHERE id = ?::uuid",
                "What is 7 + 5?",
                12,
                "00000000-0000-0000-0000-000000000001");
        auth = new AuthSupport(port, objectMapper);
        session = auth.registerStudent();
    }

    @Test
    void postSubmissionsP95StaysUnder300Milliseconds() throws IOException, InterruptedException {
        long[] nanos = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long started = System.nanoTime();
            HttpResponse<String> response = auth.send(auth.bearer(
                            HttpRequest.newBuilder(auth.uri("/api/submissions"))
                                    .header("Content-Type", "application/json")
                                    .header("Origin", AuthSupport.ORIGIN),
                            session.accessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(VALID_REQUEST))
                    .build());
            nanos[i] = System.nanoTime() - started;
            assertEquals(201, response.statusCode(), response.body());
        }
        Arrays.sort(nanos);
        int index = (int) Math.ceil(SAMPLES * 0.95) - 1;
        long p95Ms = nanos[index] / 1_000_000L;
        assertTrue(p95Ms < 300L, "POST /api/submissions p95 was " + p95Ms + "ms");
    }
}
