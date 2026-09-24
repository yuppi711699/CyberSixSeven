package com.cybersixseven.platformapi.dto;

import java.util.List;
import java.util.UUID;

public record StaffQuestionResponse(
        UUID id, String prompt, List<Integer> options, int correctAnswer, int maxPoints, int displayOrder) {

    public StaffQuestionResponse {
        options = List.copyOf(options);
    }
}
