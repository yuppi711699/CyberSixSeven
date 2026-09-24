package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
class OauthExchangeServiceTests {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private final Map<String, String> store = new HashMap<>();
    private OauthExchangeService oauthExchangeService;

    @BeforeEach
    void setUp() {
        store.clear();
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (store.containsKey(key)) {
                return false;
            }
            store.put(key, invocation.getArgument(1));
            return true;
        });
        when(values.getAndDelete(anyString()))
                .thenAnswer(invocation -> store.remove((String) invocation.getArgument(0)));
        oauthExchangeService =
                new OauthExchangeService(redis, JsonMapper.builder().build(), Duration.ofSeconds(60));
    }

    @Test
    void consumeIsSingleUseAndRejectsTheWrongOrigin() {
        UUID userId = UUID.randomUUID();
        String code = oauthExchangeService.issue(userId, "http://localhost:3000");

        AuthUnauthorizedException wrongOrigin = assertThrows(
                AuthUnauthorizedException.class,
                () -> oauthExchangeService.consume(code, "http://localhost:3001"));
        assertEquals("OAUTH_EXCHANGE_INVALID", wrongOrigin.getCode());

        String replay = oauthExchangeService.issue(userId, "http://localhost:3000");
        OauthExchangeService.Binding binding = oauthExchangeService.consume(replay, "http://localhost:3000");
        assertEquals(userId, binding.userId());
        assertEquals("http://localhost:3000", binding.frontendOrigin());
        assertThrows(
                AuthUnauthorizedException.class,
                () -> oauthExchangeService.consume(replay, "http://localhost:3000"));
    }
}
