package com.cybersixseven.platformapi.dto;

import java.time.Instant;
import java.util.UUID;

public record RobotResponse(UUID id, String name, String model, Instant createdAt) {}
