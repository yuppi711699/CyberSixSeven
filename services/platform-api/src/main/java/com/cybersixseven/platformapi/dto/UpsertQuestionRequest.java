package com.cybersixseven.platformapi.dto;

import java.util.List;

public record UpsertQuestionRequest(
        String prompt, List<Integer> options, Integer correctAnswer, Integer maxPoints, Integer displayOrder) {}
