package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.entity.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenServiceTests {

    private static final String SECRET = "test-jwt-secret-that-is-at-least-32b";
    private static final String ISSUER = "https://api.aiastrologyperdictions.com";

    private AccessTokenService accessTokenService;
    private JwtDecoder jwtDecoder;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        accessTokenService =
                new AccessTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), Duration.ofMinutes(10), ISSUER);
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        user = new UserAccount(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "pat@example.test",
                "hash",
                "Pat",
                UserRole.STUDENT,
                Instant.parse("2026-09-16T00:00:00Z"));
    }

    @Test
    void issuePutsUniqueJtiAndTenMinuteExpiryOnTheJwt() {
        AccessTokenService.IssuedAccessToken first = accessTokenService.issue(user);
        AccessTokenService.IssuedAccessToken second = accessTokenService.issue(user);

        assertNotEquals(first.token(), second.token());
        Jwt jwt = jwtDecoder.decode(first.token());
        assertNotNull(jwt.getId());
        assertNotEquals(jwt.getId(), jwtDecoder.decode(second.token()).getId());
        assertEquals(user.getId().toString(), jwt.getSubject());
        assertEquals("STUDENT", jwt.getClaimAsString("role"));
        assertEquals("Pat", jwt.getClaimAsString("nickname"));
        assertEquals(ISSUER, jwt.getIssuer().toString());
        assertEquals(600, first.expiresInSeconds());
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plusSeconds(9 * 60)));
        assertTrue(jwt.getExpiresAt().isBefore(jwt.getIssuedAt().plusSeconds(11 * 60)));
    }
}
