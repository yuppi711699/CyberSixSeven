package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.entity.DeviceCommandEvent;
import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.event.OutboxCommittedEvent;
import com.cybersixseven.platformapi.observability.Correlation;
import com.cybersixseven.platformapi.observability.PlatformMeters;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxPublisher {

    public static final String SNS_PUBLISHED_AT = "snsPublishedAt";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService outboxClaimService;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final PlatformMeters meters;
    private final String topicArn;
    private final Duration lease;
    private final int batchSize;
    private final String publisherId = UUID.randomUUID().toString();
    private final Object lock = new Object();

    public OutboxPublisher(
            OutboxClaimService outboxClaimService,
            SnsClient snsClient,
            ObjectMapper objectMapper,
            PlatformMeters meters,
            @Value("${app.aws.sns-topic-arn}") String topicArn,
            @Value("${app.outbox.lease-seconds}") int leaseSeconds,
            @Value("${app.outbox.batch-size}") int batchSize) {
        this.outboxClaimService = outboxClaimService;
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.topicArn = topicArn;
        this.lease = Duration.ofSeconds(leaseSeconds);
        this.batchSize = batchSize;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(OutboxCommittedEvent event) {
        Correlation.put(null, event.commandId(), null);
        try {
            log.info("outbox wake");
        } finally {
            Correlation.clear();
        }
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
                Correlation.put(submissionId, commandId, payload.deviceId());
                try {
                    String json = objectMapper.writeValueAsString(payload);
                    // One business attribute. The javaagent adds trace context on top.
                    // AWS drops injection silently at 10 attributes; stay at or under 8 business attrs.
                    String publishedAt = Instant.now().toString();
                    snsClient.publish(PublishRequest.builder()
                            .topicArn(topicArn)
                            .message(json)
                            .messageAttributes(Map.of(
                                    SNS_PUBLISHED_AT,
                                    MessageAttributeValue.builder()
                                            .dataType("String")
                                            .stringValue(publishedAt)
                                            .build()))
                            .build());
                    outboxClaimService.markPublished(commandId, Instant.now());
                    meters.outboxPublished();
                    log.info("outbox published");
                } catch (RuntimeException ex) {
                    outboxClaimService.releaseLease(commandId);
                    meters.outboxFailed();
                    log.error("outbox publish failed", ex);
                } finally {
                    Correlation.clear();
                }
            }
        }
    }
}
