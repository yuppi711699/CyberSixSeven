package com.cybersixseven.platformapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class AuthSupport {

    static final String PASSWORD = "password1";
    static final String ORIGIN = "http://localhost:3000";

    private final int port;
    private final ObjectMapper objectMapper;
    final HttpClient httpClient;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private String csrfToken;

    AuthSupport(int port, ObjectMapper objectMapper) {
        this.port = port;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    String bootstrapCsrf() throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/csrf"))
                .header("Origin", ORIGIN)
                .GET()
                .build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "csrf bootstrap failed: " + response.statusCode() + " " + response.body());
        }
        csrfToken = objectMapper.readTree(response.body()).get("token").asText();
        return csrfToken;
    }

    Session registerStudent() throws IOException, InterruptedException {
        bootstrapCsrf();
        String email = "student-" + UUID.randomUUID() + "@example.test";
        String body =
                """
                {"email":"%s","password":"%s","nickname":"Pat","role":"ADMIN"}
                """
                        .formatted(email, PASSWORD);
        HttpResponse<String> response = send(authPost("/api/auth/register", body));
        if (response.statusCode() != 201) {
            throw new IllegalStateException("register failed: " + response.statusCode() + " " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        return new Session(
                json.get("accessToken").asText(),
                json.get("user").get("id").asText(),
                json.get("user").get("role").asText(),
                email);
    }

    Session login(String email, String password) throws IOException, InterruptedException {
        bootstrapCsrf();
        String body =
                """
                {"email":"%s","password":"%s"}
                """
                        .formatted(email, password);
        HttpResponse<String> response = send(authPost("/api/auth/login", body));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("login failed: " + response.statusCode() + " " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        return new Session(
                json.get("accessToken").asText(),
                json.get("user").get("id").asText(),
                json.get("user").get("role").asText(),
                email);
    }

    HttpRequest.Builder authPost(String path, String body) {
        return HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("Origin", ORIGIN)
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    HttpRequest.Builder bearer(HttpRequest.Builder builder, String accessToken) {
        return builder.header("Authorization", "Bearer " + accessToken);
    }

    HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        Map<String, String> merged = new LinkedHashMap<>(cookies);
        request.headers()
                .firstValue("Cookie")
                .ifPresent(raw -> {
                    for (String part : raw.split(";")) {
                        String pair = part.trim();
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            merged.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                        }
                    }
                });
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(request, (name, value) -> !"Cookie".equalsIgnoreCase(name));
        if (!merged.isEmpty()) {
            builder.header(
                    "Cookie",
                    merged.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining("; ")));
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        storeCookies(response);
        return response;
    }

    HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        return send(builder.build());
    }

    void storeCookies(HttpResponse<?> response) {
        for (String header : response.headers().allValues("set-cookie")) {
            String pair = header.split(";", 2)[0];
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (value.isEmpty()) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }

    String cookieHeader() {
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    String csrfToken() {
        return csrfToken;
    }

    record Session(String accessToken, String userId, String role, String email) {}
}
