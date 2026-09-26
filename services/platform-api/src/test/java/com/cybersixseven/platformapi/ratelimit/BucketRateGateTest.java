package com.cybersixseven.platformapi.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BucketRateGateTest {

    @Test
    void allowsCapacityThenRejects() {
        BucketRateGate gate = new BucketRateGate(2, Duration.ofMinutes(1));
        assertTrue(gate.tryConsume("203.0.113.1"));
        assertTrue(gate.tryConsume("203.0.113.1"));
        assertFalse(gate.tryConsume("203.0.113.1"));
        assertTrue(gate.tryConsume("203.0.113.2"));
    }

    @Test
    void blankKeyIsRejected() {
        BucketRateGate gate = new BucketRateGate(1, Duration.ofSeconds(1));
        assertFalse(gate.tryConsume(null));
        assertFalse(gate.tryConsume("  "));
    }

    @Test
    void rejectsNonPositiveCapacityOrWindow() {
        assertThrows(IllegalArgumentException.class, () -> new BucketRateGate(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new BucketRateGate(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new BucketRateGate(1, null));
    }
}
