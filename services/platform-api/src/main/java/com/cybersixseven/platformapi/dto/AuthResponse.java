package com.cybersixseven.platformapi.dto;

public record AuthResponse(String accessToken, long expiresInSeconds, AuthUserResponse user) {}
