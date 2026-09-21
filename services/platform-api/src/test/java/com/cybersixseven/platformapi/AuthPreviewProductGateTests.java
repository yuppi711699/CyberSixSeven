package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.product-enabled=false")
class AuthPreviewProductGateTests extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper;

    @Autowired
    AuthPreviewProductGateTests(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Test
    void productRoutesAreUnavailableAndAuthStaysUp() throws Exception {
        AuthSupport auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
        AuthSupport.Session session = auth.registerStudent();

        HttpResponse<String> questions = auth.send(auth.bearer(
                        HttpRequest.newBuilder(auth.uri("/api/questions")).GET(), session.accessToken())
                .build());
        assertEquals(503, questions.statusCode());
        assertEquals("PRODUCT_UNAVAILABLE", objectMapper.readTree(questions.body()).get("code").asText());

        HttpResponse<String> submit = auth.send(auth.bearer(
                        HttpRequest.newBuilder(auth.uri("/api/submissions"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"answers\":[]}")),
                        session.accessToken())
                .build());
        assertEquals(503, submit.statusCode());

        HttpResponse<String> health = auth.send(HttpRequest.newBuilder(auth.uri("/actuator/health")).GET().build());
        assertEquals(200, health.statusCode());
        assertTrue(auth.send(HttpRequest.newBuilder(auth.uri("/api/csrf")).GET().build()).statusCode() == 200);
    }
}
