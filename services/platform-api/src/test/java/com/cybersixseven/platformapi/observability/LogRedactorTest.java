package com.cybersixseven.platformapi.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LogRedactorTest {

    @Test
    void redactsSecretsTokensAndJwts() {
        String raw = "password=hunter2 Authorization: Bearer aaa.bbb.ccc token=abc "
                + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature";
        String redacted = LogRedactor.redact(raw);
        assertFalse(redacted.contains("hunter2"));
        assertFalse(redacted.contains("aaa.bbb.ccc"));
        assertFalse(redacted.contains("eyJhbGci"));
        assertFalse(redacted.contains("token=abc"));
    }

    @Test
    void leavesCorrelationIds() {
        String raw = "submissionId=11111111-1111-1111-1111-111111111111 commandId=22222222-2222-2222-2222-222222222222";
        assertEquals(raw, LogRedactor.redact(raw));
    }

    @Test
    void emptyAndNullPassThrough() {
        assertEquals("", LogRedactor.redact(""));
        assertEquals(null, LogRedactor.redact(null));
    }
}
