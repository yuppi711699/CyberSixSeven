package com.cybersixseven.devicecommandservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class PipelineMeters {

    private static final int MAX_IN_FLIGHT = 4096;

    private final Counter queueSuccess;
    private final Counter queueFailure;
    private final Counter queueRedelivery;
    private final Counter mqttPublish;
    private final Counter mqttAck;
    private final Counter rewardFallback;
    private final Timer snsToMqtt;
    private final Timer ackLatency;
    private final ConcurrentHashMap<String, Long> publishedAtNanos = new ConcurrentHashMap<>();

    public PipelineMeters(MeterRegistry registry) {
        this.queueSuccess = counter(registry, "queue.handle", "success");
        this.queueFailure = counter(registry, "queue.handle", "failure");
        this.queueRedelivery = counter(registry, "queue.handle", "redelivery");
        this.mqttPublish = counter(registry, "mqtt.publish", "success");
        this.mqttAck = counter(registry, "mqtt.ack", "handled");
        this.rewardFallback = Counter.builder("reward.fallback")
                .description("Reward select failed; intensity fell back to 3")
                .register(registry);
        this.snsToMqtt = Timer.builder("sli.sns_to_mqtt")
                .description("Confirmed SNS publish attribute to MQTT publish")
                .register(registry);
        this.ackLatency = Timer.builder("mqtt.ack.latency")
                .description("MQTT publish to physical ACK in this process")
                .register(registry);
    }

    public void queueSuccess() {
        queueSuccess.increment();
    }

    public void queueFailure() {
        queueFailure.increment();
    }

    public void queueRedelivery() {
        queueRedelivery.increment();
    }

    public void rewardFallback() {
        rewardFallback.increment();
    }

    public void mqttPublished(String commandId) {
        mqttPublish.increment();
        if (publishedAtNanos.size() > MAX_IN_FLIGHT) {
            publishedAtNanos.clear();
        }
        publishedAtNanos.put(commandId, System.nanoTime());
    }

    public void mqttAck(String commandId) {
        mqttAck.increment();
        Long started = publishedAtNanos.remove(commandId);
        if (started != null) {
            ackLatency.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    public void snsToMqtt(Instant snsPublishedAt) {
        if (snsPublishedAt == null) {
            return;
        }
        Duration latency = Duration.between(snsPublishedAt, Instant.now());
        if (!latency.isNegative()) {
            snsToMqtt.record(latency);
        }
    }

    private static Counter counter(MeterRegistry registry, String name, String result) {
        return Counter.builder(name).tag("result", result).register(registry);
    }
}
