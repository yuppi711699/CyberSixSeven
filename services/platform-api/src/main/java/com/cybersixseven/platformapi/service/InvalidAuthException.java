package com.cybersixseven.platformapi.service;

public class InvalidAuthException extends RuntimeException {

    public InvalidAuthException(String message) {
        super(message);
    }
}
