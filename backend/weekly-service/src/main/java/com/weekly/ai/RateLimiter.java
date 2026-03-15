package com.weekly.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process sliding-window rate limiter for AI requests.
 *
 * <p>Rate limit: configurable requests per user per window (default: 20/min).
 * In production, this would be backed by Redis for cross-instance consistency.
 */
public class RateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Map<UUID, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.window = window;
    }

    /**
     * Attempts to acquire a permit for the given user.
     *
     * @param userId the user requesting
     * @return true if the request is allowed
     */
    public boolean tryAcquire(UUID userId) {
        WindowCounter counter = counters.compute(userId, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.isExpired(now, window)) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return counter.count().get() <= maxRequests;
    }

    /**
     * Returns the remaining requests for a user in the current window.
     */
    public int remaining(UUID userId) {
        WindowCounter counter = counters.get(userId);
        if (counter == null || counter.isExpired(Instant.now(), window)) {
            return maxRequests;
        }
        return Math.max(0, maxRequests - counter.count().get());
    }

    private record WindowCounter(Instant windowStart, AtomicInteger count) {
        boolean isExpired(Instant now, Duration windowDuration) {
            return now.isAfter(windowStart.plus(windowDuration));
        }
    }
}
