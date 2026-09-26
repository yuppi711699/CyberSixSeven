package com.cybersixseven.platformapi.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PlatformMetersTest {

    @Test
    void everyCounterAndTheSubmissionTimerRecord() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformMeters meters = new PlatformMeters(registry);

        meters.submissionCreated();
        meters.outboxPublished();
        meters.outboxFailed();
        meters.resend("ACCEPTED");
        meters.resend("RATE_LIMITED");
        meters.submissionLatency().record(java.time.Duration.ofMillis(4));

        assertEquals(1.0, registry.get("submissions").counter().count());
        assertEquals(1.0, registry.get("outbox.publish").tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("outbox.publish").tag("result", "failure").counter().count());
        assertEquals(1.0, registry.get("resend").tag("result", "ACCEPTED").counter().count());
        assertEquals(1.0, registry.get("resend").tag("result", "RATE_LIMITED").counter().count());
        assertEquals(1, meters.submissionLatency().count());
        assertTrue(registry.get("sli.submission").timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0);
        assertNull(registry.find("resend").tag("result", "TIMEOUT").counter());
    }
}
