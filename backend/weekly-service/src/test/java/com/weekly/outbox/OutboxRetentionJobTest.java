package com.weekly.outbox;

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
 * Unit tests for {@link OutboxRetentionJob}.
 */
class OutboxRetentionJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-14T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private OutboxEventRepository outboxEventRepository;
    private OutboxRetentionJob retentionJob;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        retentionJob = new OutboxRetentionJob(outboxEventRepository, 30, FIXED_CLOCK);
    }

    @Test
    void purgesEventsOlderThanRetentionPeriod() {
        Instant expectedCutoff = FIXED_NOW.minusSeconds(30L * 24 * 60 * 60);
        when(outboxEventRepository.deleteByOccurredAtBefore(expectedCutoff)).thenReturn(5);

        retentionJob.purgeExpiredEvents();

        verify(outboxEventRepository).deleteByOccurredAtBefore(expectedCutoff);
    }

    @Test
    void handlesNoExpiredEventsGracefully() {
        Instant expectedCutoff = FIXED_NOW.minusSeconds(30L * 24 * 60 * 60);
        when(outboxEventRepository.deleteByOccurredAtBefore(expectedCutoff)).thenReturn(0);

        retentionJob.purgeExpiredEvents();

        verify(outboxEventRepository).deleteByOccurredAtBefore(expectedCutoff);
    }

    @Test
    void usesConfiguredRetentionDays() {
        OutboxRetentionJob customJob = new OutboxRetentionJob(outboxEventRepository, 7, FIXED_CLOCK);
        Instant expectedCutoff = FIXED_NOW.minusSeconds(7L * 24 * 60 * 60);
        assertEquals(7, customJob.getRetentionDays());
        when(outboxEventRepository.deleteByOccurredAtBefore(expectedCutoff)).thenReturn(3);

        customJob.purgeExpiredEvents();

        verify(outboxEventRepository).deleteByOccurredAtBefore(expectedCutoff);
    }

    @Test
    void defaultRetentionIs30Days() {
        assertEquals(30, retentionJob.getRetentionDays());
    }

    @Test
    void rejectsNonPositiveRetentionDays() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new OutboxRetentionJob(outboxEventRepository, 0, FIXED_CLOCK));

        assertEquals("weekly.outbox.retention-days must be greater than 0", exception.getMessage());
    }
}
