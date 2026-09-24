package com.cybersixseven.platformapi.service;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("email already registered");
    }
}
