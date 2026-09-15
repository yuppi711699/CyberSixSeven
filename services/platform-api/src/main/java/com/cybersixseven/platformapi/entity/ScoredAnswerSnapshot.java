package com.cybersixseven.platformapi.entity;

import java.util.List;
import java.util.UUID;

public record ScoredAnswerSnapshot(
        UUID questionId,
        String prompt,
        List<Integer> options,
        int submittedAnswer,
        int correctAnswer,
        boolean correct,
        int awardedPoints,
        int maxPoints,
        int displayOrder) {

    public ScoredAnswerSnapshot {
        options = List.copyOf(options);
    }
}
