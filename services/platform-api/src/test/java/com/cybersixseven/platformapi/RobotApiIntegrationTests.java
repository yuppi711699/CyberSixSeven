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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RobotApiIntegrationTests extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper;
    private AuthSupport auth;

    @Autowired
    RobotApiIntegrationTests(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void auth() throws IOException, InterruptedException {
        auth = new AuthSupport(port, objectMapper);
        auth.bootstrapCsrf();
    }

    @Test
    void anonymousCreateIsUnauthorized() throws IOException, InterruptedException {
        HttpResponse<String> response = auth.send(HttpRequest.newBuilder(auth.uri("/api/robots"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"a\",\"model\":\"m\"}"))
                .build());
        assertEquals(401, response.statusCode());
    }

    @Test
    void ownerCanCreateReadUpdateAndDelete() throws IOException, InterruptedException {
        AuthSupport.Session session = auth.registerStudent();
        HttpResponse<String> created = send(session.accessToken(), "POST", "/api/robots",
                "{\"name\":\"alpha\",\"model\":\"mk1\"}");
        assertEquals(201, created.statusCode());
        String id = objectMapper.readTree(created.body()).get("id").asText();

        HttpResponse<String> listed = send(session.accessToken(), "GET", "/api/robots", null);
        assertEquals(200, listed.statusCode());
        assertTrue(listed.body().contains("alpha"));

        HttpResponse<String> fetched = send(session.accessToken(), "GET", "/api/robots/" + id, null);
        assertEquals(200, fetched.statusCode());
        JsonNode robot = objectMapper.readTree(fetched.body());
        assertEquals("mk1", robot.get("model").asText());

        HttpResponse<String> updated = send(session.accessToken(), "PUT", "/api/robots/" + id,
                "{\"name\":\"beta\",\"model\":\"mk2\"}");
        assertEquals(200, updated.statusCode());
        assertEquals(id, objectMapper.readTree(updated.body()).get("id").asText());
        assertEquals("beta", objectMapper.readTree(updated.body()).get("name").asText());

        HttpResponse<String> deleted = send(session.accessToken(), "DELETE", "/api/robots/" + id, null);
        assertEquals(204, deleted.statusCode());

        HttpResponse<String> missing = send(session.accessToken(), "GET", "/api/robots/" + id, null);
        assertEquals(404, missing.statusCode());
        assertTrue(missing.body().contains("ROBOT_NOT_FOUND"));
    }

    private HttpResponse<String> send(String token, String method, String path, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = auth.bearer(
                HttpRequest.newBuilder(auth.uri(path)).header("Content-Type", "application/json"), token);
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return auth.send(builder.build());
    }
}
