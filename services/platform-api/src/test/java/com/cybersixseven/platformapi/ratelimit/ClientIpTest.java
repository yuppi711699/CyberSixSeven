package com.cybersixseven.platformapi.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void usesRightmostForwardedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.8");
        assertEquals("10.0.0.8", ClientIp.resolve(request));
    }

    @Test
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", ClientIp.resolve(request));
    }

    @Test
    void skipsUnknownTokensAndBlankHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "unknown, 198.51.100.4");
        assertEquals("198.51.100.4", ClientIp.resolve(request));

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setRemoteAddr("192.0.2.10");
        blank.addHeader("X-Forwarded-For", "  , unknown");
        assertEquals("192.0.2.10", ClientIp.resolve(blank));
    }
}
