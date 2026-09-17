package com.cybersixseven.platformapi.dto;

import java.util.List;
import java.util.UUID;

public record SubmissionDetailResponse(
        UUID id, int score, int maxScore, List<ScoredAnswerResponse> answers, String accessoryStatus) {

    public static final String PENDING = "PENDING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    public SubmissionDetailResponse {
        answers = List.copyOf(answers);
    }
}
