package com.weekly.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.OrgRosterEntry;
import com.weekly.notification.NotificationRepository;
import com.weekly.notification.NotificationService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.PredictionDataProvider;
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

class WeeklyPlanningAgentServiceTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID FORMER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000099");
    private static final UUID OUTCOME_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-23T06:00:00Z"), ZoneOffset.UTC);

    private WeeklyPlanRepository weeklyPlanRepository;
    private WeeklyCommitRepository weeklyCommitRepository;
    private WeeklyCommitActualRepository weeklyCommitActualRepository;
    private CapacityProfileProvider capacityProfileProvider;
    private PredictionDataProvider predictionDataProvider;
    private UrgencyDataProvider urgencyDataProvider;
    private NotificationService notificationService;
    private NotificationRepository notificationRepository;
    private OrgGraphClient orgGraphClient;
    private WeeklyPlanningAgentService service;

    @BeforeEach
    void setUp() {
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        weeklyCommitRepository = mock(WeeklyCommitRepository.class);
        weeklyCommitActualRepository = mock(WeeklyCommitActualRepository.class);
        capacityProfileProvider = mock(CapacityProfileProvider.class);
        predictionDataProvider = mock(PredictionDataProvider.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        notificationService = mock(NotificationService.class);
        notificationRepository = mock(NotificationRepository.class);
        orgGraphClient = mock(OrgGraphClient.class);
        service = new WeeklyPlanningAgentService(
                weeklyPlanRepository,
                weeklyCommitRepository,
                weeklyCommitActualRepository,
                capacityProfileProvider,
                predictionDataProvider,
                urgencyDataProvider,
                notificationService,
                notificationRepository,
                orgGraphClient,
                FIXED_CLOCK);
    }

    @Test
    void createDraftsForCurrentWeekBuildsBoundedDraftAndNotification() {
        WeeklyPlanEntity previousPlan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, LocalDate.of(2026, 3, 16));
        previousPlan.setState(PlanState.RECONCILED);

        WeeklyCommitEntity urgentCarry = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, previousPlan.getId(), "Close launch blocker");
        urgentCarry.setOutcomeId(OUTCOME_ID);
        urgentCarry.setChessPriority(ChessPriority.KING);
        urgentCarry.setExpectedResult("Unblock launch");
        urgentCarry.setEstimatedHours(new BigDecimal("7.0"));

        WeeklyCommitEntity recurring = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, previousPlan.getId(), "Review customer escalations");
        recurring.setEstimatedHours(new BigDecimal("8.0"));

        WeeklyPlanEntity olderPlan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, LocalDate.of(2026, 3, 9));
        olderPlan.setState(PlanState.RECONCILED);
        WeeklyCommitEntity recurringOlder = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, olderPlan.getId(), "Review customer escalations");
        recurringOlder.setEstimatedHours(new BigDecimal("8.0"));

        WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(urgentCarry.getId(), ORG_ID);
        actual.setCompletionStatus(CompletionStatus.NOT_DONE);

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(orgGraphClient.getOrgRoster(ORG_ID)).thenReturn(List.of(new OrgRosterEntry(USER_ID, "Taylor", null, "UTC")));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                .thenReturn(List.of(olderPlan, previousPlan));
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of(new UrgencyInfo(
                OUTCOME_ID,
                "Launch readiness",
                LocalDate.of(2026, 4, 15),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                "CRITICAL",
                23L)));
        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(eq(ORG_ID), eq(USER_ID), eq(LocalDate.of(2026, 3, 23))))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(eq(ORG_ID), eq(USER_ID), any(), any()))
                .thenReturn(List.of(olderPlan, previousPlan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(urgentCarry, recurring, recurringOlder));
        when(weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(actual));
        when(capacityProfileProvider.getLatestProfile(ORG_ID, USER_ID)).thenReturn(Optional.of(
                new CapacityProfileProvider.CapacityProfileSnapshot(
                        USER_ID, 8, BigDecimal.ONE, new BigDecimal("10.0"), "HIGH", "2026-03-22T00:00:00Z")));
        when(predictionDataProvider.getUserPredictions(ORG_ID, USER_ID)).thenReturn(List.of());
        when(weeklyPlanRepository.save(any(WeeklyPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(weeklyCommitRepository.save(any(WeeklyCommitEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int drafted = service.createDraftsForCurrentWeek();

        assertEquals(1, drafted);
        verify(weeklyCommitRepository).save(any(WeeklyCommitEntity.class));
        verify(notificationService).notify(eq(ORG_ID), eq(USER_ID),
                eq(WeeklyPlanningAgentService.NOTIFICATION_TYPE_WEEKLY_PLAN_DRAFT_READY),
                any(Map.class));
    }

    @Test
    void createDraftsForCurrentWeekSkipsExistingNonEmptyDraft() {
        WeeklyPlanEntity existingDraft = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, LocalDate.of(2026, 3, 23));

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(orgGraphClient.getOrgRoster(ORG_ID)).thenReturn(List.of(new OrgRosterEntry(USER_ID, "Taylor", null, "UTC")));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any())).thenReturn(List.of());
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of());
        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(eq(ORG_ID), eq(USER_ID), eq(LocalDate.of(2026, 3, 23))))
                .thenReturn(Optional.of(existingDraft));
        when(weeklyCommitRepository.countByOrgIdAndWeeklyPlanId(ORG_ID, existingDraft.getId())).thenReturn(2);

        int drafted = service.createDraftsForCurrentWeek();

        assertEquals(0, drafted);
        verify(notificationService, never()).notify(any(), any(), any(), any());
    }

    @Test
    void createDraftsForCurrentWeekSkipsHistoricalUsersNotInCurrentRoster() {
        WeeklyPlanEntity formerUserPlan = new WeeklyPlanEntity(
                UUID.randomUUID(), ORG_ID, FORMER_USER_ID, LocalDate.of(2026, 3, 16));
        formerUserPlan.setState(PlanState.RECONCILED);

        WeeklyCommitEntity carriedCommit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, formerUserPlan.getId(), "Legacy follow-up");
        carriedCommit.setEstimatedHours(new BigDecimal("4.0"));

        WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(carriedCommit.getId(), ORG_ID);
        actual.setCompletionStatus(CompletionStatus.NOT_DONE);

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(orgGraphClient.getOrgRoster(ORG_ID)).thenReturn(List.of(new OrgRosterEntry(USER_ID, "Taylor", null, "UTC")));
        when(weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(eq(ORG_ID), any(), any()))
                .thenReturn(List.of(formerUserPlan));
        when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(List.of());
        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(eq(ORG_ID), eq(USER_ID), eq(LocalDate.of(2026, 3, 23))))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        eq(ORG_ID), eq(USER_ID), any(), any()))
                .thenReturn(List.of());
        when(weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        eq(ORG_ID), eq(FORMER_USER_ID), any(), any()))
                .thenReturn(List.of(formerUserPlan));
        when(weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(carriedCommit));
        when(weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                .thenReturn(List.of(actual));
        when(capacityProfileProvider.getLatestProfile(any(), any())).thenReturn(Optional.empty());
        when(predictionDataProvider.getUserPredictions(any(), any())).thenReturn(List.of());
        when(weeklyPlanRepository.save(any(WeeklyPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(weeklyCommitRepository.save(any(WeeklyCommitEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int drafted = service.createDraftsForCurrentWeek();

        assertEquals(0, drafted);
        verify(notificationService, never()).notify(eq(ORG_ID), eq(FORMER_USER_ID), any(), any());
    }

    @Test
    void recurringSimilarityUsesNearMatchTitlesLikeDraftFromHistory() {
        double distance = WeeklyPlanningAgentService.normalizedLevenshteinDistance(
                "Review customer escalations",
                "Review customer escalation");

        assertTrue(distance < WeeklyPlanningAgentService.RECURRING_SIMILARITY_THRESHOLD);
    }
}
