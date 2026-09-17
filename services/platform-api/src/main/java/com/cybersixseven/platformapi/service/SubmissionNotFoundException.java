package com.cybersixseven.platformapi.service;

public class SubmissionNotFoundException extends RuntimeException {

    public SubmissionNotFoundException() {
        super("submission not found");
    }
}
