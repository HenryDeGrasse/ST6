package com.weekly.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UrgencyComputeJob}.
 */
class UrgencyComputeJobTest {

    private OutcomeMetadataRepository outcomeMetadataRepository;
    private UrgencyComputeService urgencyComputeService;
    private SimpleMeterRegistry meterRegistry;
    private UrgencyComputeJob job;

    @BeforeEach
    void setUp() {
        outcomeMetadataRepository = mock(OutcomeMetadataRepository.class);
        urgencyComputeService = mock(UrgencyComputeService.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new UrgencyComputeJob(
                urgencyComputeService,
                outcomeMetadataRepository,
                meterRegistry
        );
    }

    @Test
    void doesNothingWhenNoOrgsHaveOutcomeMetadata() {
        when(outcomeMetadataRepository.findDistinctOrgIds()).thenReturn(List.of());

        job.recomputeAll();

        verify(outcomeMetadataRepository).findDistinctOrgIds();
        verify(urgencyComputeService, never()).computeUrgencyForOrg(any());
        assertEquals(0.0, recomputeCount(), "No recomputations should be counted");
    }

    @Test
    void recomputesUrgencyForEveryOrgAndIncrementsCounterPerSuccess() {
        UUID firstOrgId = UUID.randomUUID();
        UUID secondOrgId = UUID.randomUUID();
        when(outcomeMetadataRepository.findDistinctOrgIds())
                .thenReturn(List.of(firstOrgId, secondOrgId));

        job.recomputeAll();

        verify(urgencyComputeService).computeUrgencyForOrg(firstOrgId);
        verify(urgencyComputeService).computeUrgencyForOrg(secondOrgId);
        assertEquals(
                2.0,
                recomputeCount(),
                "Each successful org should increment the counter once"
        );
    }

    @Test
    void continuesProcessingOtherOrgsWhenOneOrgFails() {
        UUID failingOrgId = UUID.randomUUID();
        UUID succeedingOrgId = UUID.randomUUID();
        when(outcomeMetadataRepository.findDistinctOrgIds())
                .thenReturn(List.of(failingOrgId, succeedingOrgId));
        doThrow(new RuntimeException("simulated failure"))
                .when(urgencyComputeService)
                .computeUrgencyForOrg(failingOrgId);

        job.recomputeAll();

        verify(urgencyComputeService).computeUrgencyForOrg(failingOrgId);
        verify(urgencyComputeService).computeUrgencyForOrg(succeedingOrgId);
        assertEquals(
                1.0,
                recomputeCount(),
                "Only successful org recomputations should be counted"
        );
    }

    private double recomputeCount() {
        Counter counter = meterRegistry
                .find(UrgencyComputeJob.COUNTER_URGENCY_RECOMPUTE_TOTAL)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
