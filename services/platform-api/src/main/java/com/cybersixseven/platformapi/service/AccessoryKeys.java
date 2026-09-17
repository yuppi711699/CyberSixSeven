package com.cybersixseven.platformapi.service;

import java.util.UUID;

public final class AccessoryKeys {

    public static final String OBJECT_NAME = "reward.stl";

    private AccessoryKeys() {}

    public static String forSubmission(UUID submissionId) {
        if (submissionId == null) {
            throw new IllegalArgumentException("submissionId is required");
        }
        return "accessories/" + submissionId + "/" + OBJECT_NAME;
    }

    public static boolean isCanonical(UUID submissionId, String accessoryKey) {
        return forSubmission(submissionId).equals(accessoryKey);
    }
}
