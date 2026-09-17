package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.service.SubmissionCapabilityGenerator.GeneratedCapability;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SubmissionCapabilityGeneratorTests {

    private final SubmissionCapabilityGenerator generator = new SubmissionCapabilityGenerator();

    @Test
    void generatedPlaintextMatchesItsHashAndWrongSecretDoesNot() {
        GeneratedCapability capability = generator.generate();
        assertTrue(generator.matches(capability.hash(), capability.plaintext()));
        assertFalse(generator.matches(capability.hash(), capability.plaintext() + "x"));
        assertFalse(generator.matches(capability.hash(), null));
        assertFalse(generator.matches(null, capability.plaintext()));
        assertFalse(generator.matches("short".getBytes(StandardCharsets.UTF_8), capability.plaintext()));
        assertNotEquals(capability.plaintext(), new String(capability.hash(), StandardCharsets.UTF_8));
    }
}
