package com.cybersixseven.platformapi.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxLagProbe {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong lagSeconds = new AtomicLong();

    public OutboxLagProbe(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("outbox.lag.seconds", lagSeconds, AtomicLong::doubleValue)
                .baseUnit("seconds")
                .description("Age in seconds of the oldest unpublished outbox row")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.lag-sample-ms:5000}")
    @Transactional(readOnly = true)
    public void sample() {
        Number age = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(EXTRACT(EPOCH FROM (clock_timestamp() - MIN(created_at))), 0)
                FROM outbox_events
                WHERE published_at IS NULL
                """,
                Number.class);
        lagSeconds.set(age == null ? 0L : age.longValue());
    }

    public long lagSeconds() {
        return lagSeconds.get();
    }
}
