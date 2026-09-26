package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.platformapi.entity.DeviceCommandEvent;
import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.observability.OutboxLagProbe;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OutboxLagIntegrationTests extends PostgresIntegrationTest {

    private final OutboxLagProbe probe;
    private final OutboxEventRepository outboxEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    @Autowired
    OutboxLagIntegrationTests(
            OutboxLagProbe probe,
            OutboxEventRepository outboxEventRepository,
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry) {
        this.probe = probe;
        this.outboxEventRepository = outboxEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Test
    void lagIsZeroWhenTheOutboxIsCaughtUpAndRisesWhilePublishingIsStopped() {
        outboxEventRepository.deleteAll();
        probe.sample();
        assertEquals(0L, probe.lagSeconds());

        UUID commandId = UUID.randomUUID();
        outboxEventRepository.save(new OutboxEvent(
                commandId,
                new DeviceCommandEvent(commandId, UUID.randomUUID(), "esp32-dev-001", "correct", 3),
                Instant.now()));
        jdbcTemplate.update(
                "UPDATE outbox_events SET created_at = clock_timestamp() - interval '90 seconds' WHERE id = ?::uuid",
                commandId.toString());

        probe.sample();

        assertTrue(probe.lagSeconds() >= 60L, "lagSeconds=" + probe.lagSeconds());
        assertTrue(meterRegistry.get("outbox.lag.seconds").gauge().value() >= 60.0);
    }
}
