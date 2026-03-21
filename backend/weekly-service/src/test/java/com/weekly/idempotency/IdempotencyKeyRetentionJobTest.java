package com.weekly.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IdempotencyKeyRetentionJob}.
 */
class IdempotencyKeyRetentionJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-14T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private IdempotencyKeyRepository idempotencyKeyRepository;
    private IdempotencyKeyRetentionJob retentionJob;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository = mock(IdempotencyKeyRepository.class);
        retentionJob = new IdempotencyKeyRetentionJob(idempotencyKeyRepository, 72, FIXED_CLOCK);
    }

    @Test
    void purgesKeysOlderThanRetentionPeriod() {
        Instant expectedCutoff = FIXED_NOW.minusSeconds(72L * 3600);
        when(idempotencyKeyRepository.deleteByCreatedAtBefore(expectedCutoff)).thenReturn(5);

        retentionJob.purgeExpiredKeys();

        verify(idempotencyKeyRepository).deleteByCreatedAtBefore(expectedCutoff);
    }

    @Test
    void handlesNoExpiredKeysGracefully() {
        Instant expectedCutoff = FIXED_NOW.minusSeconds(72L * 3600);
        when(idempotencyKeyRepository.deleteByCreatedAtBefore(expectedCutoff)).thenReturn(0);

        retentionJob.purgeExpiredKeys();

        verify(idempotencyKeyRepository).deleteByCreatedAtBefore(expectedCutoff);
    }

    @Test
    void usesConfiguredRetentionHours() {
        IdempotencyKeyRetentionJob customJob =
                new IdempotencyKeyRetentionJob(idempotencyKeyRepository, 48, FIXED_CLOCK);
        Instant expectedCutoff = FIXED_NOW.minusSeconds(48L * 3600);
        assertEquals(48, customJob.getRetentionHours());
        when(idempotencyKeyRepository.deleteByCreatedAtBefore(expectedCutoff)).thenReturn(3);

        customJob.purgeExpiredKeys();

        verify(idempotencyKeyRepository).deleteByCreatedAtBefore(expectedCutoff);
    }

    @Test
    void defaultRetentionIs72Hours() {
        assertEquals(72, retentionJob.getRetentionHours());
    }

    @Test
    void rejectsNonPositiveRetentionHours() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyKeyRetentionJob(idempotencyKeyRepository, 0, FIXED_CLOCK));

        assertEquals("weekly.idempotency.retention-hours must be greater than 0", exception.getMessage());
    }
}
