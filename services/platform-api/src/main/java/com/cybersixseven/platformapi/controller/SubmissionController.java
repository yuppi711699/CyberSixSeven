package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.CreateSubmissionRequest;
import com.cybersixseven.platformapi.dto.CreateSubmissionResponse;
import com.cybersixseven.platformapi.service.SubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSubmissionResponse createSubmission(
            @RequestBody CreateSubmissionRequest request) {
        return submissionService.submit(request);
    }
}
