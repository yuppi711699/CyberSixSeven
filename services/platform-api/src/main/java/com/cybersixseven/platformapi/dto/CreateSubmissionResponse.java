package com.cybersixseven.platformapi.dto;

import java.util.List;
import java.util.UUID;

public record CreateSubmissionResponse(
        UUID id,
        String submissionSecret,
        int score,
        int maxScore,
        List<ScoredAnswerResponse> answers) {

    public CreateSubmissionResponse {
        answers = List.copyOf(answers);
    }
}
