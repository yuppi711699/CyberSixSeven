package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.config.RefreshCookieFactory;
import com.cybersixseven.platformapi.dto.AuthResponse;
import com.cybersixseven.platformapi.dto.LoginRequest;
import com.cybersixseven.platformapi.dto.OauthExchangeRequest;
import com.cybersixseven.platformapi.dto.RegisterRequest;
import com.cybersixseven.platformapi.service.AuthService;
import com.cybersixseven.platformapi.service.OauthExchangeService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OauthExchangeService oauthExchangeService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AuthController(
            AuthService authService,
            OauthExchangeService oauthExchangeService,
            RefreshCookieFactory refreshCookieFactory) {
        this.authService = authService;
        this.oauthExchangeService = oauthExchangeService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request, HttpServletResponse response) {
        return writeSession(authService.register(request), response);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        return writeSession(authService.login(request), response);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return writeSession(authService.refresh(readRefreshCookie(request)), response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(readRefreshCookie(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString());
    }

    @PostMapping("/oauth/exchange")
    public AuthResponse exchange(
            @RequestBody OauthExchangeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String origin = httpRequest.getHeader(HttpHeaders.ORIGIN);
        OauthExchangeService.Binding binding = oauthExchangeService.consume(request == null ? null : request.code(), origin);
        return writeSession(authService.exchange(binding.userId()), response);
    }

    private AuthResponse writeSession(AuthService.IssuedSession session, HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookieFactory.issue(session.refreshToken(), session.refreshTtlSeconds()).toString());
        return session.response();
    }

    static String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (RefreshCookieFactory.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
