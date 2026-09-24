package com.cybersixseven.platformapi.entity;

import java.util.UUID;

public record DeviceCommandEvent(
        UUID commandId,
        UUID submissionId,
        String deviceId,
        String event,
        int intensity,
        Integer score,
        Integer totalQuestions) {

    public DeviceCommandEvent(
            UUID commandId, UUID submissionId, String deviceId, String event, int intensity) {
        this(commandId, submissionId, deviceId, event, intensity, null, null);
    }
}
