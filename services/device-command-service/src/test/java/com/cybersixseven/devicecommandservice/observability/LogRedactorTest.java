package com.cybersixseven.devicecommandservice.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LogRedactorTest {

    @Test
    void redactsSecretsAndLeavesIds() {
        String secret = "password=hunter2 cookie=session api-key=abc";
        String redacted = LogRedactor.redact(secret);
        assertFalse(redacted.contains("hunter2"));
        assertFalse(redacted.contains("session"));
        assertFalse(redacted.contains("api-key=abc"));
        assertEquals("submissionId=abc", LogRedactor.redact("submissionId=abc"));
        assertNull(LogRedactor.redact(null));
        assertEquals("", LogRedactor.redact(""));
    }
}
