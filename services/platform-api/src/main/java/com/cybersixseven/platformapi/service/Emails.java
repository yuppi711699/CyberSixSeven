package com.cybersixseven.platformapi.service;

import java.util.Locale;

public final class Emails {

    private Emails() {}

    public static String normalize(String email) {
        if (email == null) {
            throw new InvalidAuthException("email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || !normalized.contains("@") || normalized.startsWith("@")
                || normalized.endsWith("@")) {
            throw new InvalidAuthException("email is invalid");
        }
        return normalized;
    }
}
