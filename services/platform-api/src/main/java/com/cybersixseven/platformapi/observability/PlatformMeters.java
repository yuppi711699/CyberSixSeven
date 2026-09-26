package com.cybersixseven.platformapi.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class PlatformMeters {

    private final Counter submissions;
    private final Counter outboxSuccess;
    private final Counter outboxFailure;
    private final MeterRegistry registry;
    private final Timer submissionLatency;

    public PlatformMeters(MeterRegistry registry) {
        this.registry = registry;
        this.submissions = Counter.builder("submissions")
                .description("Accepted submission creates")
                .register(registry);
        this.outboxSuccess = Counter.builder("outbox.publish")
                .tag("result", "success")
                .description("SNS publish confirmed and outbox row marked published")
                .register(registry);
        this.outboxFailure = Counter.builder("outbox.publish")
                .tag("result", "failure")
                .description("SNS publish failed; outbox row left unpublished")
                .register(registry);
        this.submissionLatency = Timer.builder("sli.submission")
                .description("POST /api/submissions handler time")
                .register(registry);
    }

    public void submissionCreated() {
        submissions.increment();
    }

    public void outboxPublished() {
        outboxSuccess.increment();
    }

    public void outboxFailed() {
        outboxFailure.increment();
    }

    public void resend(String result) {
        Counter.builder("resend")
                .tag("result", result)
                .description("Admin resend outcomes")
                .register(registry)
                .increment();
    }

    public Timer submissionLatency() {
        return submissionLatency;
    }
}
