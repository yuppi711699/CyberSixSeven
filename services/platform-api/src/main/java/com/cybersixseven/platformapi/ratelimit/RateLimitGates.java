package com.cybersixseven.platformapi.ratelimit;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RateLimitGates {

    private final BucketRateGate authByIp;
    private final BucketRateGate submissionByIp;
    private final BucketRateGate submissionByUser;

    public RateLimitGates(
            @Value("${app.rate-limit.auth.capacity}") int authCapacity,
            @Value("${app.rate-limit.auth.window}") Duration authWindow,
            @Value("${app.rate-limit.submission.capacity}") int submissionCapacity,
            @Value("${app.rate-limit.submission.window}") Duration submissionWindow) {
        this.authByIp = new BucketRateGate(authCapacity, authWindow);
        this.submissionByIp = new BucketRateGate(submissionCapacity, submissionWindow);
        this.submissionByUser = new BucketRateGate(submissionCapacity, submissionWindow);
    }

    public BucketRateGate authByIp() {
        return authByIp;
    }

    public BucketRateGate submissionByIp() {
        return submissionByIp;
    }

    public BucketRateGate submissionByUser() {
        return submissionByUser;
    }
}
