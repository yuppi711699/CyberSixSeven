package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration ttl;
    private final String issuer;

    public AccessTokenService(
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.ttl}") Duration ttl,
            @Value("${app.jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public IssuedAccessToken issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("nickname", user.getNickname())
                .build();
        Jwt jwt = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new IssuedAccessToken(jwt.getTokenValue(), ttl.toSeconds(), expiresAt);
    }

    public Duration ttl() {
        return ttl;
    }

    public record IssuedAccessToken(String token, long expiresInSeconds, Instant expiresAt) {}
}
