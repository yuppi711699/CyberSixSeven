package com.cybersixseven.platformapi.dto;

import java.util.List;
import java.util.UUID;

public record QuestionResponse(UUID id, String prompt, List<Integer> options, int displayOrder) {

    public QuestionResponse {
        options = List.copyOf(options);
    }
}
