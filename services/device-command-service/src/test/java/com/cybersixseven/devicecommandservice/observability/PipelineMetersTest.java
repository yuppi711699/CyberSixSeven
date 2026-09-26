package com.cybersixseven.devicecommandservice.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PipelineMetersTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void countersTimersAndCorrelation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMeters meters = new PipelineMeters(registry);
        Correlation.put(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "esp32-dev-001");
        assertEquals("esp32-dev-001", MDC.get(Correlation.DEVICE_ID));
        Correlation.clear();
        assertNull(MDC.get(Correlation.DEVICE_ID));

        meters.queueSuccess();
        meters.queueFailure();
        meters.queueRedelivery();
        meters.rewardFallback();
        meters.mqttPublished("cmd-1");
        meters.mqttAck("cmd-1");
        meters.mqttAck("never-published");
        meters.snsToMqtt(null);
        meters.snsToMqtt(Instant.now().plusSeconds(30));
        meters.snsToMqtt(Instant.now().minusMillis(20));

        assertEquals(1.0, registry.get("queue.handle").tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("queue.handle").tag("result", "failure").counter().count());
        assertEquals(1.0, registry.get("queue.handle").tag("result", "redelivery").counter().count());
        assertEquals(1.0, registry.get("reward.fallback").counter().count());
        assertEquals(1.0, registry.get("mqtt.publish").tag("result", "success").counter().count());
        assertEquals(2.0, registry.get("mqtt.ack").tag("result", "handled").counter().count());
        assertEquals(1, registry.get("mqtt.ack.latency").timer().count());
        assertEquals(1, registry.get("sli.sns_to_mqtt").timer().count());
    }

    @Test
    void inFlightMapDropsWhenItExceedsTheCap() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PipelineMeters meters = new PipelineMeters(registry);
        for (int i = 0; i < 4098; i++) {
            meters.mqttPublished("cmd-" + i);
        }
        meters.mqttAck("cmd-0");
        assertEquals(0, registry.get("mqtt.ack.latency").timer().count());
        meters.mqttAck("cmd-4097");
        assertEquals(1, registry.get("mqtt.ack.latency").timer().count());
    }
}
