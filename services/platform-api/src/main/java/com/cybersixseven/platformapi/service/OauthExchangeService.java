package com.cybersixseven.platformapi.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class OauthExchangeService {

    static final String KEY_PREFIX = "c67:oauth:exchange:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final SecureRandom secureRandom = new SecureRandom();

    public OauthExchangeService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.auth.exchange-ttl}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public String issue(UUID userId, String frontendOrigin) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            throw new IllegalArgumentException("frontendOrigin is required");
        }
        String code = randomCode();
        Binding binding = new Binding(userId, frontendOrigin);
        Boolean stored = redis.opsForValue()
                .setIfAbsent(KEY_PREFIX + code, objectMapper.writeValueAsString(binding), ttl);
        if (!Boolean.TRUE.equals(stored)) {
            throw new IllegalStateException("oauth exchange code collided");
        }
        return code;
    }

    public Binding consume(String code, String frontendOrigin) {
        if (code == null || code.isBlank()) {
            throw new AuthUnauthorizedException("OAUTH_EXCHANGE_INVALID", "exchange code required");
        }
        String json = redis.opsForValue().getAndDelete(KEY_PREFIX + code);
        if (json == null) {
            throw new AuthUnauthorizedException("OAUTH_EXCHANGE_INVALID", "exchange code invalid");
        }
        Binding binding = objectMapper.readValue(json, Binding.class);
        if (!binding.frontendOrigin().equals(frontendOrigin)) {
            throw new AuthUnauthorizedException("OAUTH_EXCHANGE_INVALID", "exchange code invalid");
        }
        return binding;
    }

    private String randomCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Binding(UUID userId, String frontendOrigin) {}
}
