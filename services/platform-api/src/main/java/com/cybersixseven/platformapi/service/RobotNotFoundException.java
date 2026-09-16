package com.cybersixseven.platformapi.service;

import java.util.UUID;

public class RobotNotFoundException extends RuntimeException {

    public RobotNotFoundException(UUID id) {
        super("robot not found: " + id);
    }
}
