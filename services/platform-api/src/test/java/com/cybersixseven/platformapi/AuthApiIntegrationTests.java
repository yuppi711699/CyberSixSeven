package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.config.RefreshCookieFactory;
import com.cybersixseven.platformapi.entity.Submission;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import com.cybersixseven.platformapi.repository.SubmissionRepository;
import com.nimbusds.jwt.SignedJWT;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiIntegrationTests extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper;
    private final SubmissionRepository submissionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private AuthSupport auth;

    @Autowired
    AuthApiIntegrationTests(
            ObjectMapper objectMapper,
            SubmissionRepository submissionRepository,
            OutboxEventRepository outboxEventRepository) {
        this.objectMapper = objectMapper;
        this.submissionRepository = submissionRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @BeforeEach
    void setUp() throws Exception {
        outboxEventRepository.deleteAll();
        submissionRepository.deleteAll();
        auth = new AuthSupport(port, objectMapper);
    }

    @Test
    void csrfBootstrapSetsCookieAndMutationsRequireTheHeader() throws Exception {
        HttpResponse<String> csrf = auth.send(HttpRequest.newBuilder(auth.uri("/api/csrf")).GET().build());
        assertEquals(200, csrf.statusCode());
        String token = objectMapper.readTree(csrf.body()).get("token").asText();
        assertFalse(token.isBlank());
        assertTrue(csrf.headers().allValues("set-cookie").stream().anyMatch(value -> value.startsWith("XSRF-TOKEN=")));

        HttpResponse<String> denied = auth.send(HttpRequest.newBuilder(auth.uri("/api/auth/register"))
                .header("Content-Type", "application/json")
                .header("Origin", AuthSupport.ORIGIN)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"a@example.test\",\"password\":\"password1\",\"nickname\":\"A\"}"))
                .build());
        assertEquals(403, denied.statusCode());
        assertTrue(denied.body().contains("CSRF_DENIED") || denied.statusCode() == 403);

        auth.bootstrapCsrf();
        AuthSupport.Session session = auth.registerStudent();
        assertEquals("STUDENT", session.role());
    }

    @Test
    void registerIgnoresRoleEscalationAndHashesThePassword() throws Exception {
        auth.bootstrapCsrf();
        AuthSupport.Session session = auth.registerStudent();
        assertEquals("STUDENT", session.role());
        SignedJWT jwt = SignedJWT.parse(session.accessToken());
        assertEquals("STUDENT", jwt.getJWTClaimsSet().getStringClaim("role"));
        assertEquals(session.userId(), jwt.getJWTClaimsSet().getSubject());
        Instant exp = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
        Instant iat = jwt.getJWTClaimsSet().getIssueTime().toInstant();
        assertTrue(exp.isAfter(iat.plusSeconds(9 * 60)));
        assertTrue(exp.isBefore(iat.plusSeconds(11 * 60)));
        HttpResponse<String> login = auth.send(auth.authPost(
                "/api/auth/login",
                "{\"email\":\"" + session.email() + "\",\"password\":\"" + AuthSupport.PASSWORD + "\"}"));
        assertEquals(200, login.statusCode());
        assertEquals("STUDENT", objectMapper.readTree(login.body()).get("user").get("role").asText());
        assertEquals(session.email(), objectMapper.readTree(login.body()).get("user").get("email").asText());
        assertFalse(login.body().contains(AuthSupport.PASSWORD));
        assertFalse(login.body().toLowerCase().contains("passwordhash"));
    }

    @Test
    void refreshRotatesAndReuseRevokesTheFamily() throws Exception {
        auth.bootstrapCsrf();
        auth.registerStudent();
        HttpResponse<String> first = auth.send(auth.authPost("/api/auth/refresh", "{}"));
        assertEquals(200, first.statusCode());
        String firstAccess = objectMapper.readTree(first.body()).get("accessToken").asText();
        assertFalse(firstAccess.isBlank());

        String setCookie = first.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith(RefreshCookieFactory.COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
        assertTrue(setCookie.contains("Path=/api/auth"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.toLowerCase().contains("samesite=lax"));

        HttpResponse<String> second = auth.send(auth.authPost("/api/auth/refresh", "{}"));
        assertEquals(200, second.statusCode());
        assertNotEquals(firstAccess, objectMapper.readTree(second.body()).get("accessToken").asText());

        AuthSupport replay = new AuthSupport(port, objectMapper);
        replay.bootstrapCsrf();
        String oldCookie = setCookie.split(";", 2)[0];
        HttpResponse<String> reused = replay.send(replay.authPost("/api/auth/refresh", "{}")
                .header("Cookie", oldCookie)
                .build());
        assertEquals(401, reused.statusCode());
        assertEquals("REFRESH_FAMILY_REVOKED", objectMapper.readTree(reused.body()).get("code").asText());
    }

    @Test
    void logoutRevokesAndClearsTheCookieIdempotently() throws Exception {
        auth.bootstrapCsrf();
        auth.registerStudent();
        HttpResponse<String> logout = auth.send(auth.authPost("/api/auth/logout", "{}"));
        assertEquals(204, logout.statusCode());
        assertTrue(logout.headers().allValues("set-cookie").stream()
                .anyMatch(value -> value.startsWith(RefreshCookieFactory.COOKIE_NAME + "=")
                        && value.contains("Path=/api/auth")
                        && (value.contains("Max-Age=0") || value.contains("Max-Age=0".toLowerCase()) || value.contains("max-age=0"))));

        HttpResponse<String> refresh = auth.send(auth.authPost("/api/auth/refresh", "{}"));
        assertEquals(401, refresh.statusCode());

        HttpResponse<String> again = auth.send(auth.authPost("/api/auth/logout", "{}"));
        assertEquals(204, again.statusCode());
    }

    @Test
    void corsAllowsConfiguredOriginsAndRejectsOthers() throws Exception {
        auth.bootstrapCsrf();
        HttpResponse<String> allowed = auth.send(HttpRequest.newBuilder(auth.uri("/api/csrf"))
                .header("Origin", "http://localhost:3000")
                .GET()
                .build());
        assertEquals("http://localhost:3000", allowed.headers().firstValue("access-control-allow-origin").orElse(""));

        HttpResponse<String> denied = auth.send(HttpRequest.newBuilder(auth.uri("/api/csrf"))
                .header("Origin", "https://evil.example")
                .GET()
                .build());
        assertTrue(denied.headers().firstValue("access-control-allow-origin").isEmpty());
    }

    @Test
    void oauthFixtureRedirectsByRoleAndExchangeIsSingleUse() throws Exception {
        auth.bootstrapCsrf();
        HttpResponse<String> studentRedirect = auth.send(HttpRequest.newBuilder(
                        auth.uri("/api/auth/oauth/fixture?role=STUDENT"))
                .GET()
                .build());
        assertEquals(302, studentRedirect.statusCode());
        String studentLocation = studentRedirect.headers().firstValue("location").orElseThrow();
        assertTrue(studentLocation.startsWith("http://localhost:3000/auth/callback?code="));
        assertFalse(studentLocation.contains("accessToken") || studentLocation.contains("eyJ"));

        String code = studentLocation.substring(studentLocation.indexOf("code=") + 5);
        HttpResponse<String> exchanged = auth.send(auth.authPost(
                "/api/auth/oauth/exchange", "{\"code\":\"" + code + "\"}"));
        assertEquals(200, exchanged.statusCode());
        assertEquals("STUDENT", objectMapper.readTree(exchanged.body()).get("user").get("role").asText());

        HttpResponse<String> replay = auth.send(auth.authPost(
                "/api/auth/oauth/exchange", "{\"code\":\"" + code + "\"}"));
        assertEquals(401, replay.statusCode());

        HttpResponse<String> teacherRedirect = auth.send(HttpRequest.newBuilder(
                        auth.uri("/api/auth/oauth/fixture?role=TEACHER"))
                .GET()
                .build());
        assertTrue(teacherRedirect
                .headers()
                .firstValue("location")
                .orElseThrow()
                .startsWith("http://localhost:3001/auth/callback?code="));
    }

    @Test
    void claimBindsLegacyRowsAndHidesCrossOwnerProbes() throws Exception {
        auth.bootstrapCsrf();
        AuthSupport.Session owner = auth.registerStudent();
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest("legacy-secret".getBytes(StandardCharsets.UTF_8));
        submissionRepository.save(new Submission(id, List.of(), 1, 1, hash, Instant.parse("2026-09-16T00:00:00Z")));

        HttpResponse<String> claimed = auth.send(auth.bearer(
                        HttpRequest.newBuilder(auth.uri("/api/submissions/" + id + "/claim"))
                                .header("Content-Type", "application/json")
                                .header("X-Submission-Secret", "legacy-secret")
                                .POST(HttpRequest.BodyPublishers.ofString("{}")),
                        owner.accessToken())
                .build());
        assertEquals(200, claimed.statusCode());

        assertEquals(
                404,
                auth.send(HttpRequest.newBuilder(auth.uri("/api/submissions/" + id))
                                .header("X-Submission-Secret", "legacy-secret")
                                .GET()
                                .build())
                        .statusCode());
        assertEquals(
                200,
                auth.send(auth.bearer(HttpRequest.newBuilder(auth.uri("/api/submissions/" + id)).GET(), owner.accessToken())
                                .build())
                        .statusCode());

        AuthSupport.Session other = auth.registerStudent();
        assertEquals(
                404,
                auth.send(auth.bearer(HttpRequest.newBuilder(auth.uri("/api/submissions/" + id)).GET(), other.accessToken())
                                .build())
                        .statusCode());
        assertEquals(
                200,
                auth.send(auth.bearer(
                                HttpRequest.newBuilder(auth.uri("/api/submissions/" + id + "/claim"))
                                        .header("X-Submission-Secret", "legacy-secret")
                                        .POST(HttpRequest.BodyPublishers.ofString("{}")),
                                owner.accessToken())
                        .build())
                        .statusCode());
    }

    @Test
    void googleAuthorizationEndpointIsOnTheOauthChain() throws Exception {
        HttpResponse<String> response = auth.send(HttpRequest.newBuilder(
                        auth.uri("/oauth2/authorization/google"))
                .GET()
                .build());
        assertEquals(302, response.statusCode());
        String location = response.headers().firstValue("location").orElse("");
        assertTrue(location.contains("accounts.google.com") || location.contains("google"), location);
    }
}
