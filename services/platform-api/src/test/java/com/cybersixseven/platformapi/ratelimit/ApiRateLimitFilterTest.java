package com.cybersixseven.platformapi.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ApiRateLimitFilterTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authMutationsShareAnIpBucketAndOtherPathsDoNot() throws Exception {
        ApiRateLimitFilter filter = filter(1, 5);
        assertEquals(200, call(filter, "POST", "/api/auth/login", "203.0.113.1", false));
        assertEquals(429, call(filter, "POST", "/api/auth/login", "203.0.113.1", false));
        assertTrue(lastBody(filter, "POST", "/api/auth/register", "203.0.113.1").contains("RATE_LIMITED"));
        assertEquals(200, call(filter, "POST", "/api/auth/refresh", "203.0.113.9", false));
        assertEquals(200, call(filter, "GET", "/api/csrf", "203.0.113.1", false));
        assertEquals(200, call(filter, "GET", "/api/questions", "203.0.113.1", false));
        assertEquals(200, call(filter, "POST", "/internal/heartbeat", "203.0.113.1", false));
    }

    @Test
    void submissionLimitAppliesToCreateAndClaimPerIpAndPerUser() throws Exception {
        ApiRateLimitFilter filter = filter(20, 1);
        assertEquals(200, call(filter, "POST", "/api/submissions", "203.0.113.4", true));
        assertEquals(429, call(filter, "POST", "/api/submissions", "198.51.100.8", true));
        SecurityContextHolder.clearContext();
        assertEquals(429, call(filter, "POST", "/api/submissions/11111111-1111-1111-1111-111111111111/claim", "203.0.113.4", false));
        assertEquals(200, call(filter, "GET", "/api/submissions", "203.0.113.4", false));
    }

    @Test
    void fixtureExchangeAndLogoutAreAuthLimited() throws Exception {
        ApiRateLimitFilter filter = filter(1, 5);
        assertEquals(200, call(filter, "POST", "/api/auth/logout", "203.0.113.7", false));
        assertEquals(429, call(filter, "POST", "/api/auth/oauth/exchange", "203.0.113.7", false));
        assertEquals(429, call(filter, "POST", "/api/auth/oauth/fixture/student", "203.0.113.7", false));
    }

    private static ApiRateLimitFilter filter(int authCapacity, int submissionCapacity) {
        return new ApiRateLimitFilter(new RateLimitGates(
                authCapacity, Duration.ofMinutes(1), submissionCapacity, Duration.ofMinutes(1)));
    }

    private static int call(
            ApiRateLimitFilter filter, String method, String path, String remote, boolean asUser)
            throws ServletException, IOException {
        if (asUser) {
            Jwt jwt = new Jwt(
                    "token",
                    Instant.now(),
                    Instant.now().plusSeconds(60),
                    Map.of("alg", "none"),
                    Map.of("sub", "user-1"));
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        }
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remote);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger passed = new AtomicInteger();
        filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());
        if (response.getStatus() == 200) {
            assertEquals(1, passed.get());
        }
        return response.getStatus();
    }

    private static String lastBody(ApiRateLimitFilter filter, String method, String path, String remote)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remote);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        return response.getContentAsString();
    }
}
