package com.cybersixseven.platformapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshCookieFactoryTests {

    @Test
    void issuedCookieHasTheExactAuthPathAttributes() {
        RefreshCookieFactory factory = new RefreshCookieFactory(true);
        ResponseCookie cookie = factory.issue("opaque", 120);
        assertEquals(RefreshCookieFactory.COOKIE_NAME, cookie.getName());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals("Lax", cookie.getSameSite());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("opaque", cookie.getValue());
    }

    @Test
    void clearUsesTheSamePath() {
        ResponseCookie cookie = new RefreshCookieFactory(true).clear();
        assertEquals("/api/auth", cookie.getPath());
        assertEquals(0, cookie.getMaxAge().getSeconds());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
    }
}
