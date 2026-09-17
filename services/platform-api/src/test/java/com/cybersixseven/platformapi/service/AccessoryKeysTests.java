package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessoryKeysTests {

    @Test
    void canonicalKeyUsesSubmissionIdAndFixedObjectName() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertEquals("accessories/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/reward.stl", AccessoryKeys.forSubmission(id));
        assertTrue(AccessoryKeys.isCanonical(id, AccessoryKeys.forSubmission(id)));
    }

    @Test
    void rejectsNullSubmissionId() {
        assertThrows(IllegalArgumentException.class, () -> AccessoryKeys.forSubmission(null));
    }

    @Test
    void rejectsUrlsAndAlternateObjectNames() {
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertFalse(AccessoryKeys.isCanonical(id, "https://s3.amazonaws.com/bucket/reward.stl"));
        assertFalse(AccessoryKeys.isCanonical(id, "accessories/" + id + ".stl"));
        assertFalse(AccessoryKeys.isCanonical(id, "accessories/" + UUID.randomUUID() + "/reward.stl"));
        assertFalse(AccessoryKeys.isCanonical(id, null));
    }
}
