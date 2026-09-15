package com.cybersixseven.platformapi.dto;

import java.util.UUID;

public record AnswerSubmissionRequest(UUID questionId, Integer answer) {}
