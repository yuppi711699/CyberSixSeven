package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "app.rate-limit.auth.capacity=2",
            "app.rate-limit.submission.capacity=2",
        })
class ApiRateLimitIntegrationTests extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper;
    private AuthSupport auth;

    @Autowired
    ApiRateLimitIntegrationTests(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void csrf() throws IOException, InterruptedException {
        auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
    }

    @Test
    void authPostsAreLimitedPerResolvedClientIp() throws IOException, InterruptedException {
        String body = "{\"email\":\"nobody@example.test\",\"password\":\"password1\"}";
        assertEquals(401, login(body, null).statusCode());
        assertEquals(401, login(body, null).statusCode());
        HttpResponse<String> limited = login(body, null);
        assertEquals(429, limited.statusCode());
        assertTrue(limited.body().contains("RATE_LIMITED"));

        HttpResponse<String> otherClient = login(body, "203.0.113.50");
        assertEquals(401, otherClient.statusCode());
    }

    private HttpResponse<String> login(String body, String forwardedFor) throws IOException, InterruptedException {
        HttpRequest.Builder builder = auth.authPost("/api/auth/login", body);
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return auth.send(builder.build());
    }
}
