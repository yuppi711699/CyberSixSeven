package com.cybersixseven.platformapi.dto;

import java.util.List;

public record CreateSubmissionRequest(List<AnswerSubmissionRequest> answers) {

    public CreateSubmissionRequest {
        answers = answers == null ? null : List.copyOf(answers);
    }
}
