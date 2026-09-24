package com.cybersixseven.platformapi.service;

public class InvalidHeartbeatException extends RuntimeException {

    public InvalidHeartbeatException(String message) {
        super(message);
    }
}
