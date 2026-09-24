package com.cybersixseven.platformapi.dto;

import java.time.Instant;

public record DeviceHeartbeatResponse(String deviceId, Instant lastSeenAt) {}
