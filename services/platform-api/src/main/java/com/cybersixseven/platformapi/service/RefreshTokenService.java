package com.cybersixseven.platformapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class RefreshTokenService {

    static final String TOKEN_PREFIX = "c67:refresh:token:";
    static final String FAMILY_PREFIX = "c67:refresh:family:";
    static final String LOCK_PREFIX = "c67:refresh:lock:";
    static final String REUSE_CODE = "REFRESH_FAMILY_REVOKED";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.auth.refresh-ttl}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public IssuedRefreshToken issue(UUID userId) {
        return rotate(userId, UUID.randomUUID());
    }

    public IssuedRefreshToken rotate(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            throw new AuthUnauthorizedException("UNAUTHORIZED", "refresh token required");
        }
        String presentedHash = hash(presentedToken);
        TokenRecord tokenRecord = readToken(presentedHash);
        if (tokenRecord == null) {
            throw new AuthUnauthorizedException("UNAUTHORIZED", "refresh token invalid");
        }
        String lockKey = LOCK_PREFIX + tokenRecord.familyId();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(locked)) {
            throw new AuthUnauthorizedException("UNAUTHORIZED", "refresh token busy");
        }
        try {
            FamilyRecord family = readFamily(tokenRecord.familyId());
            if (family == null || family.revoked()) {
                deleteToken(presentedHash);
                throw new AuthUnauthorizedException(REUSE_CODE, "refresh token family revoked");
            }
            if (!presentedHash.equals(family.currentTokenHash())) {
                revokeFamily(tokenRecord.familyId());
                throw new AuthUnauthorizedException(REUSE_CODE, "refresh token family revoked");
            }
            return rotate(tokenRecord.userId(), tokenRecord.familyId());
        } finally {
            redis.delete(lockKey);
        }
    }

    public void revokePresented(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        TokenRecord tokenRecord = readToken(hash(presentedToken));
        if (tokenRecord == null) {
            return;
        }
        revokeFamily(tokenRecord.familyId());
    }

    public Duration ttl() {
        return ttl;
    }

    private IssuedRefreshToken rotate(UUID userId, UUID familyId) {
        String plaintext = randomToken();
        String tokenHash = hash(plaintext);
        writeToken(tokenHash, new TokenRecord(userId, familyId));
        writeFamily(familyId, new FamilyRecord(userId, tokenHash, false));
        return new IssuedRefreshToken(plaintext, userId, familyId, ttl.toSeconds());
    }

    private void revokeFamily(UUID familyId) {
        FamilyRecord family = readFamily(familyId);
        if (family == null) {
            return;
        }
        writeFamily(familyId, new FamilyRecord(family.userId(), family.currentTokenHash(), true));
    }

    private TokenRecord readToken(String tokenHash) {
        String json = redis.opsForValue().get(TOKEN_PREFIX + tokenHash);
        return json == null ? null : objectMapper.readValue(json, TokenRecord.class);
    }

    private FamilyRecord readFamily(UUID familyId) {
        String json = redis.opsForValue().get(FAMILY_PREFIX + familyId);
        return json == null ? null : objectMapper.readValue(json, FamilyRecord.class);
    }

    private void writeToken(String tokenHash, TokenRecord record) {
        redis.opsForValue().set(TOKEN_PREFIX + tokenHash, objectMapper.writeValueAsString(record), ttl);
    }

    private void writeFamily(UUID familyId, FamilyRecord record) {
        redis.opsForValue().set(FAMILY_PREFIX + familyId, objectMapper.writeValueAsString(record), ttl);
    }

    private void deleteToken(String tokenHash) {
        redis.delete(TOKEN_PREFIX + tokenHash);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String plaintext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(String token, UUID userId, UUID familyId, long ttlSeconds) {}

    record TokenRecord(UUID userId, UUID familyId) {}

    record FamilyRecord(UUID userId, String currentTokenHash, boolean revoked) {}
}
