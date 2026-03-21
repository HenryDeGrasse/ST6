package com.weekly.capacity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CapacityComputeJob}.
 */
class CapacityComputeJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-22T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 3, 16);
    private static final LocalDate WINDOW_START = WINDOW_END.minusWeeks(CapacityComputeJob.ROLLING_WEEKS - 1);

    private CapacityProfileService capacityProfileService;
    private WeeklyPlanRepository weeklyPlanRepository;
    private CapacityComputeJob capacityComputeJob;

    @BeforeEach
    void setUp() {
        capacityProfileService = mock(CapacityProfileService.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        capacityComputeJob = new CapacityComputeJob(capacityProfileService, weeklyPlanRepository, FIXED_CLOCK);
    }

    @Test
    void recomputesProfilesForUniqueActiveUsersAcrossOrgs() {
        UUID orgIdOne = UUID.randomUUID();
        UUID orgIdTwo = UUID.randomUUID();
        UUID userIdOne = UUID.randomUUID();
        UUID userIdTwo = UUID.randomUUID();
        UUID userIdThree = UUID.randomUUID();

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(orgIdOne, orgIdTwo));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(orgIdOne, WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        plan(orgIdOne, userIdOne, WINDOW_END),
                        plan(orgIdOne, userIdOne, WINDOW_END.minusWeeks(1)),
                        plan(orgIdOne, userIdTwo, WINDOW_END.minusWeeks(2))));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(orgIdTwo, WINDOW_START, WINDOW_END))
                .thenReturn(List.of(plan(orgIdTwo, userIdThree, WINDOW_END.minusWeeks(3))));

        capacityComputeJob.recomputeAllProfiles();

        verify(weeklyPlanRepository).findByOrgIdAndWeekStartDateBetween(orgIdOne, WINDOW_START, WINDOW_END);
        verify(weeklyPlanRepository).findByOrgIdAndWeekStartDateBetween(orgIdTwo, WINDOW_START, WINDOW_END);
        verify(capacityProfileService).computeProfile(orgIdOne, userIdOne, CapacityComputeJob.ROLLING_WEEKS);
        verify(capacityProfileService).computeProfile(orgIdOne, userIdTwo, CapacityComputeJob.ROLLING_WEEKS);
        verify(capacityProfileService).computeProfile(orgIdTwo, userIdThree, CapacityComputeJob.ROLLING_WEEKS);
    }

    @Test
    void continuesWhenUserProfileComputationFails() {
        UUID orgId = UUID.randomUUID();
        UUID failingUserId = UUID.randomUUID();
        UUID succeedingUserId = UUID.randomUUID();

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(orgId));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(orgId, WINDOW_START, WINDOW_END))
                .thenReturn(List.of(
                        plan(orgId, failingUserId, WINDOW_END),
                        plan(orgId, succeedingUserId, WINDOW_END.minusWeeks(1))));
        doThrow(new RuntimeException("boom"))
                .when(capacityProfileService)
                .computeProfile(orgId, failingUserId, CapacityComputeJob.ROLLING_WEEKS);

        capacityComputeJob.recomputeAllProfiles();

        verify(capacityProfileService).computeProfile(orgId, failingUserId, CapacityComputeJob.ROLLING_WEEKS);
        verify(capacityProfileService).computeProfile(orgId, succeedingUserId, CapacityComputeJob.ROLLING_WEEKS);
    }

    @Test
    void continuesWhenOneOrgFailsToLoadActiveUsers() {
        UUID failingOrgId = UUID.randomUUID();
        UUID succeedingOrgId = UUID.randomUUID();
        UUID succeedingUserId = UUID.randomUUID();

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(failingOrgId, succeedingOrgId));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(failingOrgId, WINDOW_START, WINDOW_END))
                .thenThrow(new RuntimeException("repo failure"));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(succeedingOrgId, WINDOW_START, WINDOW_END))
                .thenReturn(List.of(plan(succeedingOrgId, succeedingUserId, WINDOW_END)));

        capacityComputeJob.recomputeAllProfiles();

        verify(capacityProfileService).computeProfile(
                succeedingOrgId, succeedingUserId, CapacityComputeJob.ROLLING_WEEKS);
    }

    @Test
    void doesNothingWhenNoOrgsHavePlans() {
        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of());

        capacityComputeJob.recomputeAllProfiles();

        verify(weeklyPlanRepository, never()).findByOrgIdAndWeekStartDateBetween(
                any(), any(), any());
        verify(capacityProfileService, never()).computeProfile(any(), any(), anyInt());
    }

    private static WeeklyPlanEntity plan(UUID orgId, UUID userId, LocalDate weekStartDate) {
        return new WeeklyPlanEntity(UUID.randomUUID(), orgId, userId, weekStartDate);
    }
}
