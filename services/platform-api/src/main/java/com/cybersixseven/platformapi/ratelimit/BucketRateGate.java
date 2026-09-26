package com.cybersixseven.platformapi.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local token buckets. The pinned {@code bucket4j-spring-boot-starter:0.14.0} is on the
 * classpath and disabled ({@code bucket4j.enabled=false}): its servlet auto-config is built
 * against Boot 4.0.3 and cannot express an IP bucket plus a separate user bucket. This gate
 * uses {@code bucket4j_jdk17-core}, which that starter already pulls in.
 */
public final class BucketRateGate {

    private static final int MAX_KEYS = 10_000;

    private final int capacity;
    private final Duration window;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public BucketRateGate(int capacity, Duration window) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.capacity = capacity;
        this.window = window;
    }

    public boolean tryConsume(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (buckets.size() > MAX_KEYS) {
            buckets.clear();
        }
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        return Bucket.builder().addLimit(Bandwidth.simple(capacity, window)).build();
    }

    Map<String, Bucket> buckets() {
        return buckets;
    }
}
