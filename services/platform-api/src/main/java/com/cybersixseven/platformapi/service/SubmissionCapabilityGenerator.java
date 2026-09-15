package com.cybersixseven.platformapi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SubmissionCapabilityGenerator {

    private static final int CAPABILITY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedCapability generate() {
        byte[] randomBytes = new byte[CAPABILITY_BYTES];
        secureRandom.nextBytes(randomBytes);
        String plaintext =
                Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        return new GeneratedCapability(plaintext, sha256(plaintext));
    }

    private byte[] sha256(String plaintext) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record GeneratedCapability(String plaintext, byte[] hash) {

        public GeneratedCapability {
            hash = hash.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }
    }
}
