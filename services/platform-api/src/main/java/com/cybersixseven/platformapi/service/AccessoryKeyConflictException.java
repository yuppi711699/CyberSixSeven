package com.cybersixseven.platformapi.service;

import java.util.UUID;

public class AccessoryKeyConflictException extends RuntimeException {

    public AccessoryKeyConflictException(String message) {
        super(message);
    }

    public AccessoryKeyConflictException(UUID submissionId) {
        super("accessory key conflict for submission " + submissionId);
    }
}
