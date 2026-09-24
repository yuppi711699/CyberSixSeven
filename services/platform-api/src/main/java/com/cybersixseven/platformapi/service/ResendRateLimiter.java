package com.cybersixseven.platformapi.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ResendRateLimiter {

    static final int LIMIT = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public boolean tryAcquire(UUID staffUserId, UUID deviceId) {
        String staffKey = "staff:" + staffUserId;
        String deviceKey = "device:" + deviceId;
        synchronized (this) {
            Instant now = Instant.now();
            if (count(staffKey, now) >= LIMIT || count(deviceKey, now) >= LIMIT) {
                return false;
            }
            record(staffKey, now);
            record(deviceKey, now);
            return true;
        }
    }

    public void reset() {
        hits.clear();
    }

    private int count(String key, Instant now) {
        Deque<Instant> window = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        prune(window, now);
        return window.size();
    }

    private void record(String key, Instant now) {
        hits.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(now);
    }

    private static void prune(Deque<Instant> window, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
