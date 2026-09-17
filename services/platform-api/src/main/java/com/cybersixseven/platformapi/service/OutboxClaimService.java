package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {

    private final EntityManager entityManager;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxClaimService(EntityManager entityManager, OutboxEventRepository outboxEventRepository) {
        this.entityManager = entityManager;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public List<OutboxEvent> claim(String owner, Instant now, Duration lease, int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("batchSize must be between 1 and 100");
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager
                .createNativeQuery(
                        """
                        SELECT id FROM outbox_events
                        WHERE published_at IS NULL
                          AND (lease_until IS NULL OR lease_until < :now)
                        ORDER BY created_at
                        FOR UPDATE SKIP LOCKED
                        LIMIT %d
                        """.formatted(batchSize))
                .setParameter("now", Timestamp.from(now))
                .getResultList();

        List<UUID> ids = new ArrayList<>(rows.size());
        for (Object row : rows) {
            ids.add(toUuid(row));
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        List<OutboxEvent> events = outboxEventRepository.findAllById(ids);
        Instant until = now.plus(lease);
        for (OutboxEvent event : events) {
            event.claim(owner, until);
        }
        return events;
    }

    @Transactional
    public void markPublished(UUID commandId, Instant publishedAt) {
        OutboxEvent event = outboxEventRepository.findById(commandId).orElseThrow();
        event.markPublished(publishedAt);
    }

    @Transactional
    public void releaseLease(UUID commandId) {
        outboxEventRepository.findById(commandId).ifPresent(OutboxEvent::releaseLease);
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}
