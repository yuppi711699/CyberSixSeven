package com.cybersixseven.platformapi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTests {

    @Test
    void claimMarkPublishedAndReleaseLeaseMutateLeaseFields() {
        UUID commandId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID submissionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant created = Instant.parse("2026-09-16T00:00:00Z");
        OutboxEvent event = new OutboxEvent(
                commandId,
                new DeviceCommandEvent(commandId, submissionId, "esp32-dev-001", "correct", 3),
                created);

        Instant until = Instant.parse("2026-09-16T00:00:30Z");
        event.claim("publisher-a", until);
        assertEquals("publisher-a", event.getLeaseOwner());
        assertEquals(until, event.getLeaseUntil());

        event.releaseLease();
        assertNull(event.getLeaseOwner());
        assertNull(event.getLeaseUntil());

        Instant published = Instant.parse("2026-09-16T00:01:00Z");
        event.claim("publisher-a", until);
        event.markPublished(published);
        assertEquals(published, event.getPublishedAt());
        assertNull(event.getLeaseOwner());
        assertNull(event.getLeaseUntil());
        assertEquals(commandId, event.getPayload().commandId());
    }
}
