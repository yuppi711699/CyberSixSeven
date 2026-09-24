package com.cybersixseven.platformapi.dto;

public record DeviceHeartbeatRequest(
        String deviceId, String submissionId, String commandId, String status) {}
