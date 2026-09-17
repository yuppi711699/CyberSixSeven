package com.cybersixseven.platformapi.entity;

import java.util.UUID;

public record DeviceCommandEvent(
        UUID commandId,
        UUID submissionId,
        String deviceId,
        String event,
        int intensity) {}
