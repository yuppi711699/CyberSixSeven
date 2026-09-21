package com.cybersixseven.platformapi.service;

public class AuthUnauthorizedException extends RuntimeException {

    private final String code;

    public AuthUnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
