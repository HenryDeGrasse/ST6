package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlanRetentionJob}.
 */
class PlanRetentionJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-18T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private PlanRetentionJob retentionJob;

    @BeforeEach
    void setUp() {
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        retentionJob = new PlanRetentionJob(weeklyPlanRepository, weeklyCommitRepository,
                3, 90, FIXED_CLOCK);
    }

    // ── Constructor validation ───────────────────────────────

    @Test
    void rejectsNonPositiveSoftDeleteYears() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PlanRetentionJob(weeklyPlanRepository, weeklyCommitRepository,
                        0, 90, FIXED_CLOCK));

        assertEquals("weekly.plan.retention.soft-delete-years must be greater than 0",
                ex.getMessage());
    }

    @Test
    void rejectsNonPositiveHardDeleteGraceDays() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new PlanRetentionJob(weeklyPlanRepository, weeklyCommitRepository,
                        3, 0, FIXED_CLOCK));

        assertEquals("weekly.plan.retention.hard-delete-grace-days must be greater than 0",
                ex.getMessage());
    }

    // ── Accessor tests ───────────────────────────────────────

    @Test
    void returnConfiguredSoftDeleteYears() {
        assertEquals(3, retentionJob.getSoftDeleteYears());
    }

    @Test
    void returnConfiguredHardDeleteGraceDays() {
        assertEquals(90, retentionJob.getHardDeleteGraceDays());
    }

    // ── runRetention — soft-delete phase ────────────────────

    @Test
    void softDeletesPlansOlderThanConfiguredYears() {
        Instant expectedSoftCutoff = FIXED_NOW.minus(3 * 365L, ChronoUnit.DAYS);
        when(weeklyCommitRepository.softDeleteCommitsBefore(expectedSoftCutoff)).thenReturn(7);
        when(weeklyPlanRepository.softDeletePlansBefore(expectedSoftCutoff)).thenReturn(4);
        when(weeklyPlanRepository.hardDeleteSoftDeletedPlansBefore(any())).thenReturn(0);

        retentionJob.runRetention();

        verify(weeklyCommitRepository).softDeleteCommitsBefore(expectedSoftCutoff);
        verify(weeklyPlanRepository).softDeletePlansBefore(expectedSoftCutoff);
    }

    @Test
    void softDeletePhaseHandlesZeroRowsGracefully() {
        when(weeklyCommitRepository.softDeleteCommitsBefore(any())).thenReturn(0);
        when(weeklyPlanRepository.softDeletePlansBefore(any())).thenReturn(0);
        when(weeklyPlanRepository.hardDeleteSoftDeletedPlansBefore(any())).thenReturn(0);

        retentionJob.runRetention(); // must not throw

        verify(weeklyCommitRepository).softDeleteCommitsBefore(any());
        verify(weeklyPlanRepository).softDeletePlansBefore(any());
    }

    // ── runRetention — hard-delete phase ────────────────────

    @Test
    void hardDeletesPlansOutsideGracePeriod() {
        Instant expectedGraceCutoff = FIXED_NOW.minus(90, ChronoUnit.DAYS);
        when(weeklyCommitRepository.softDeleteCommitsBefore(any())).thenReturn(0);
        when(weeklyPlanRepository.softDeletePlansBefore(any())).thenReturn(0);
        when(weeklyPlanRepository.hardDeleteSoftDeletedPlansBefore(expectedGraceCutoff))
                .thenReturn(2);

        retentionJob.runRetention();

        verify(weeklyPlanRepository).hardDeleteSoftDeletedPlansBefore(expectedGraceCutoff);
    }

    @Test
    void hardDeletePhaseHandlesZeroRowsGracefully() {
        when(weeklyCommitRepository.softDeleteCommitsBefore(any())).thenReturn(0);
        when(weeklyPlanRepository.softDeletePlansBefore(any())).thenReturn(0);
        when(weeklyPlanRepository.hardDeleteSoftDeletedPlansBefore(any())).thenReturn(0);

        retentionJob.runRetention(); // must not throw

        verify(weeklyPlanRepository).hardDeleteSoftDeletedPlansBefore(any());
    }

    // ── runRetention — both phases called ───────────────────

    @Test
    void runRetentionExecutesBothPhases() {
        when(weeklyCommitRepository.softDeleteCommitsBefore(any())).thenReturn(8);
        when(weeklyPlanRepository.softDeletePlansBefore(any())).thenReturn(5);
        when(weeklyPlanRepository.hardDeleteSoftDeletedPlansBefore(any())).thenReturn(2);

        retentionJob.runRetention();

        verify(weeklyCommitRepository).softDeleteCommitsBefore(any());
        verify(weeklyPlanRepository).softDeletePlansBefore(any());
        verify(weeklyPlanRepository).hardDeleteSoftDeletedPlansBefore(any());
    }

    // ── Custom configuration ─────────────────────────────────

    @Test
    void respectsCustomSoftDeleteYears() {
        PlanRetentionJob customJob = new PlanRetentionJob(weeklyPlanRepository,
                weeklyCommitRepository, 7, 180, FIXED_CLOCK);
        Instant expectedSoftCutoff = FIXED_NOW.minus(7 * 365L, ChronoUnit.DAYS);
        Instant expectedGraceCutoff = FIXED_NOW.minus(180, ChronoUnit.DAYS);

        when(weeklyCommitRepository.softDeleteCommitsBefore(expectedSoftCutoff)).thenReturn(3);
        when(weeklyPlanRepository.softDeletePlansBefore(expectedSoftCutoff)).thenReturn(1);
        when(weeklyPlanRepository.hardDeleteSoftDeletedPlansBefore(expectedGraceCutoff))
                .thenReturn(0);

        customJob.runRetention();

        verify(weeklyCommitRepository).softDeleteCommitsBefore(expectedSoftCutoff);
        verify(weeklyPlanRepository).softDeletePlansBefore(expectedSoftCutoff);
        verify(weeklyPlanRepository).hardDeleteSoftDeletedPlansBefore(expectedGraceCutoff);
    }

    @Test
    void doesNotCallRepositoryWhenNotInvoked() {
        verify(weeklyCommitRepository, never()).softDeleteCommitsBefore(any());
        verify(weeklyPlanRepository, never()).softDeletePlansBefore(any());
        verify(weeklyPlanRepository, never()).hardDeleteSoftDeletedPlansBefore(any());
    }
}
