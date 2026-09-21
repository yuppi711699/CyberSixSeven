package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private final Map<String, String> store = new HashMap<>();
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        store.clear();
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(values);
        org.mockito.Mockito.lenient()
                .doAnswer(invocation -> {
                    store.put(invocation.getArgument(0), invocation.getArgument(1));
                    return null;
                })
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        org.mockito.Mockito.lenient()
                .when(values.get(anyString()))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        org.mockito.Mockito.lenient()
                .when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    if (store.containsKey(key)) {
                        return false;
                    }
                    store.put(key, invocation.getArgument(1));
                    return true;
                });
        org.mockito.Mockito.lenient()
                .when(redis.delete(anyString()))
                .thenAnswer(invocation -> store.remove(invocation.getArgument(0)) != null);
        refreshTokenService =
                new RefreshTokenService(redis, JsonMapper.builder().build(), Duration.ofDays(14));
    }

    @Test
    void rotateIssuesANewTokenAndReuseRevokesTheFamily() {
        UUID userId = UUID.randomUUID();
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(userId);
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(issued.token());

        assertEquals(userId, rotated.userId());
        assertEquals(issued.familyId(), rotated.familyId());
        assertNotEquals(issued.token(), rotated.token());

        AuthUnauthorizedException reused =
                assertThrows(AuthUnauthorizedException.class, () -> refreshTokenService.rotate(issued.token()));
        assertEquals(RefreshTokenService.REUSE_CODE, reused.getCode());
        AuthUnauthorizedException afterRevoke =
                assertThrows(AuthUnauthorizedException.class, () -> refreshTokenService.rotate(rotated.token()));
        assertEquals(RefreshTokenService.REUSE_CODE, afterRevoke.getCode());
    }

    @Test
    void logoutRevokeIsIdempotentAndBlankTokensAreIgnored() {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(UUID.randomUUID());
        refreshTokenService.revokePresented(issued.token());
        refreshTokenService.revokePresented(issued.token());
        refreshTokenService.revokePresented(" ");
        assertThrows(AuthUnauthorizedException.class, () -> refreshTokenService.rotate(issued.token()));
    }
}
