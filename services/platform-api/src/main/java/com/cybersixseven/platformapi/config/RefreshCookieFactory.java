package com.cybersixseven.platformapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    public static final String COOKIE_PATH = "/api/auth";

    private final boolean secure;

    public RefreshCookieFactory(@Value("${app.auth.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(String token, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
