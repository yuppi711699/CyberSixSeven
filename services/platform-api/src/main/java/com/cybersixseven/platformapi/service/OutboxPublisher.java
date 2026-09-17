package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.DeviceCommandEvent;
import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.event.OutboxCommittedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService outboxClaimService;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;
    private final Duration lease;
    private final int batchSize;
    private final String publisherId = UUID.randomUUID().toString();
    private final Object lock = new Object();

    public OutboxPublisher(
            OutboxClaimService outboxClaimService,
            SnsClient snsClient,
            ObjectMapper objectMapper,
            @Value("${app.aws.sns-topic-arn}") String topicArn,
            @Value("${app.outbox.lease-seconds}") int leaseSeconds,
            @Value("${app.outbox.batch-size}") int batchSize) {
        this.outboxClaimService = outboxClaimService;
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn;
        this.lease = Duration.ofSeconds(leaseSeconds);
        this.batchSize = batchSize;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(OutboxCommittedEvent event) {
        log.info("outbox wake commandId={}", event.commandId());
        publishDue();
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    public void poll() {
        publishDue();
    }

    public void publishDue() {
        synchronized (lock) {
            Instant now = Instant.now();
            List<OutboxEvent> claimed = outboxClaimService.claim(publisherId, now, lease, batchSize);
            for (OutboxEvent event : claimed) {
                DeviceCommandEvent payload = event.getPayload();
                UUID commandId = event.getId();
                UUID submissionId = payload.submissionId();
                try {
                    String json = objectMapper.writeValueAsString(payload);
                    snsClient.publish(PublishRequest.builder()
                            .topicArn(topicArn)
                            .message(json)
                            .build());
                    outboxClaimService.markPublished(commandId, Instant.now());
                    log.info(
                            "outbox published commandId={} submissionId={} deviceId={}",
                            commandId,
                            submissionId,
                            payload.deviceId());
                } catch (RuntimeException ex) {
                    outboxClaimService.releaseLease(commandId);
                    log.error(
                            "outbox publish failed commandId={} submissionId={}",
                            commandId,
                            submissionId,
                            ex);
                }
            }
        }
    }
}
