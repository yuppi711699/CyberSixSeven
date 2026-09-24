package com.cybersixseven.platformapi.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class JwtConfigTests {

    @Test
    void rejectsShortSecrets() {
        JwtConfig config = new JwtConfig();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> config.jwtSecretKey("tooshort"));
    }

    @Test
    void acceptsAtLeast32Bytes() {
        SecretKey key = new JwtConfig().jwtSecretKey("test-jwt-secret-that-is-at-least-32b");
        assertTrue(key.getEncoded().length >= 32);
    }

    @Test
    void converterMapsRoleClaimToSpringAuthority() {
        JwtAuthenticationConverter converter = new JwtConfig().jwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("role", "TEACHER")
                .issuedAt(Instant.parse("2026-09-16T00:00:00Z"))
                .expiresAt(Instant.parse("2026-09-16T00:10:00Z"))
                .build();
        List<String> authorities = java.util.Objects.requireNonNull(converter.convert(jwt))
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertTrue(authorities.contains("ROLE_TEACHER"));
    }
}
