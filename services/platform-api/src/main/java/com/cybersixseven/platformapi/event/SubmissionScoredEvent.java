package com.cybersixseven.platformapi.event;

import java.util.UUID;

public record SubmissionScoredEvent(UUID submissionId, UUID studentId, int awardedPoints) {}
