package com.cybersixseven.platformapi.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSubmissionResponse(
        UUID id,
        UUID studentId,
        String nickname,
        int score,
        int maxScore,
        String accessoryStatus,
        Instant createdAt) {}
