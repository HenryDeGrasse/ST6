package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.OrgRosterEntry;
import com.weekly.auth.OrgTeamGroup;
import com.weekly.config.OrgPolicyService;
import com.weekly.notification.NotificationRepository;
import com.weekly.notification.NotificationService;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MisalignmentDetectorServiceTest {

    private static final UUID ORG_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID OUTCOME_ID = UUID.fromString("20000000-0000-0000-0000-000000000004");

    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private CapacityProfileProvider capacityProfileProvider;
    private UrgencyDataProvider urgencyDataProvider;
    private NotificationService notificationService;
    private NotificationRepository notificationRepository;
    private OrgGraphClient orgGraphClient;
    private OrgPolicyService orgPolicyService;

    @BeforeEach
    void setUp() {
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        capacityProfileProvider = mock(CapacityProfileProvider.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        notificationService = mock(NotificationService.class);
        notificationRepository = mock(NotificationRepository.class);
        orgGraphClient = mock(OrgGraphClient.class);
        orgPolicyService = mock(OrgPolicyService.class);
    }

    @Test
    void detectCurrentWeekMisalignmentCreatesManagerBriefingAfterLockThreshold() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-25T18:45:00Z"), ZoneOffset.UTC);
        MisalignmentDetectorService service = new MisalignmentDetectorService(
                weeklyPlanRepository,
                weeklyCommitRepository,
                capacityProfileProvider,
                urgencyDataProvider,
                notificationService,
                notificationRepository,
                orgGraphClient,
                orgPolicyService,
                clock);

        WeeklyPlanEntity lockedPlan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, LocalDate.of(2026, 3, 23));
        lockedPlan.lock(com.weekly.plan.domain.LockType.ON_TIME);

        WeeklyCommitEntity overloadedUrgentCommit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, lockedPlan.getId(), "Stabilize launch");
        overloadedUrgentCommit.setOutcomeId(OUTCOME_ID);
        overloadedUrgentCommit.setEstimatedHours(new BigDecimal("30.0"));

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(orgPolicyService.getPolicy(ORG_ID)).thenReturn(new OrgPolicyService.OrgPolicy(
                true, 1, 2,
                "MONDAY", "09:00",
                "WEDNESDAY", "18:00",
                true, 60,
                "FRIDAY", "17:00"));
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of(new UrgencyInfo(
                OUTCOME_ID,
                "Launch readiness",
                LocalDate.of(2026, 4, 1),
                new BigDecimal("50.00"),
                new BigDecimal("70.00"),
                "CRITICAL",
                7L)));
        when(orgGraphClient.getOrgTeamGroups(ORG_ID)).thenReturn(Map.of(
                MANAGER_ID,
                new OrgTeamGroup(MANAGER_ID, "Dana", List.of(new OrgRosterEntry(USER_ID, "Taylor", MANAGER_ID, "UTC")))));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(ORG_ID, LocalDate.of(2026, 3, 23), List.of(USER_ID)))
                .thenReturn(List.of(lockedPlan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(lockedPlan.getId())))
                .thenReturn(List.of(overloadedUrgentCommit));
        when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID)).thenReturn(Optional.of(
                new CapacityProfileProvider.CapacityProfileSnapshot(USER_ID, 8, BigDecimal.ONE,
                        new BigDecimal("20.0"), "HIGH", "2026-03-22T00:00:00Z")));
        when(notificationRepository.findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of());

        int notifications = service.detectCurrentWeekMisalignment();

        assertEquals(1, notifications);
        verify(notificationService).notify(eq(ORG_ID), eq(MANAGER_ID),
                eq(MisalignmentDetectorService.NOTIFICATION_TYPE_PLAN_MISALIGNMENT_BRIEFING), any(Map.class));
    }

    @Test
    void detectCurrentWeekMisalignmentSkipsBeforeLockThreshold() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-25T17:30:00Z"), ZoneOffset.UTC);
        MisalignmentDetectorService service = new MisalignmentDetectorService(
                weeklyPlanRepository,
                weeklyCommitRepository,
                capacityProfileProvider,
                urgencyDataProvider,
                notificationService,
                notificationRepository,
                orgGraphClient,
                orgPolicyService,
                clock);

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(orgPolicyService.getPolicy(ORG_ID)).thenReturn(new OrgPolicyService.OrgPolicy(
                true, 1, 2,
                "MONDAY", "09:00",
                "WEDNESDAY", "18:00",
                true, 60,
                "FRIDAY", "17:00"));

        int notifications = service.detectCurrentWeekMisalignment();

        assertEquals(0, notifications);
        verify(notificationService, never()).notify(any(), any(), any(), any());
    }
}
