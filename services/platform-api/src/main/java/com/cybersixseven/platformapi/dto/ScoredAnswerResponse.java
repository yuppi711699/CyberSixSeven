package com.cybersixseven.platformapi.dto;

import java.util.List;
import java.util.UUID;

public record ScoredAnswerResponse(
        UUID questionId,
        String prompt,
        List<Integer> options,
        int submittedAnswer,
        int correctAnswer,
        boolean correct,
        int awardedPoints,
        int maxPoints,
        int displayOrder) {

    public ScoredAnswerResponse {
        options = List.copyOf(options);
    }
}
