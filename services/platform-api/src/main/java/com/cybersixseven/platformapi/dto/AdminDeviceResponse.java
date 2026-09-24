package com.cybersixseven.platformapi.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminDeviceResponse(
        UUID id, String hardwareId, UUID studentId, boolean active, Instant lastSeenAt, Instant createdAt) {}
