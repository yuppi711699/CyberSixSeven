package com.cybersixseven.platformapi.service;

public class InvalidQuestionException extends RuntimeException {

    public InvalidQuestionException(String message) {
        super(message);
    }
}
