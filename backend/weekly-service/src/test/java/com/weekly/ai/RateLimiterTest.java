package com.weekly.ai;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RateLimiter}.
 */
class RateLimiterTest {

    @Test
    void allowsRequestsWithinLimit() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(1));
        UUID userId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(userId), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void blocksRequestsBeyondLimit() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1));
        UUID userId = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(userId));
        assertTrue(limiter.tryAcquire(userId));
        assertTrue(limiter.tryAcquire(userId));
        assertFalse(limiter.tryAcquire(userId), "4th request should be blocked");
    }

    @Test
    void tracksUsersIndependently() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(user1));
        assertTrue(limiter.tryAcquire(user2), "User 2 should be independent from User 1");
    }

    @Test
    void reportsRemainingCorrectly() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(1));
        UUID userId = UUID.randomUUID();

        assertEquals(5, limiter.remaining(userId));
        limiter.tryAcquire(userId);
        assertEquals(4, limiter.remaining(userId));
        limiter.tryAcquire(userId);
        assertEquals(3, limiter.remaining(userId));
    }

    @Test
    void zeroLimitBlocksAll() {
        RateLimiter limiter = new RateLimiter(0, Duration.ofMinutes(1));
        UUID userId = UUID.randomUUID();

        assertFalse(limiter.tryAcquire(userId));
    }
}
