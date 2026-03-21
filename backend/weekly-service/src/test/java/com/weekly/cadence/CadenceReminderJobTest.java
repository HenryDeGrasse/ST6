package com.weekly.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.config.OrgPolicyService;
import com.weekly.notification.NotificationEntity;
import com.weekly.notification.NotificationRepository;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyPlanRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CadenceReminderJob}.
 */
class CadenceReminderJobTest {

    private static final String LOCK_DAY = "MONDAY";
    private static final String LOCK_TIME = "10:00";
    private static final String RECONCILE_DAY = "FRIDAY";
    private static final String RECONCILE_TIME = "16:00";

    // 2026-03-16 is Monday, 2026-03-20 is Friday.
    private static final Instant MONDAY_11AM = Instant.parse("2026-03-16T11:00:00Z");
    private static final Instant MONDAY_9AM = Instant.parse("2026-03-16T09:00:00Z");
    private static final Instant WEDNESDAY_9AM = Instant.parse("2026-03-18T09:00:00Z");
    private static final Instant FRIDAY_17PM = Instant.parse("2026-03-20T17:00:00Z");

    private OrgPolicyService orgPolicyService;
    private WeeklyPlanRepository weeklyPlanRepository;
    private NotificationRepository notificationRepository;
    private SimpleMeterRegistry meterRegistry;
    private UUID defaultOrgId;

    @BeforeEach
    void setUp() {
        orgPolicyService = mock(OrgPolicyService.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        meterRegistry = new SimpleMeterRegistry();

        defaultOrgId = UUID.randomUUID();
        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(defaultOrgId));
        when(orgPolicyService.getPolicy(defaultOrgId)).thenReturn(defaultPolicy());
        when(notificationRepository
                .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void parseDayOfWeekHandlesMixedCase() {
        assertEquals(DayOfWeek.MONDAY, CadenceReminderJob.parseDayOfWeek("monday"));
        assertEquals(DayOfWeek.FRIDAY, CadenceReminderJob.parseDayOfWeek("FRIDAY"));
        assertEquals(DayOfWeek.WEDNESDAY, CadenceReminderJob.parseDayOfWeek("Wednesday"));
    }

    @Test
    void parseTimeHandlesHHmm() {
        assertEquals(LocalTime.of(10, 0), CadenceReminderJob.parseTime("10:00"));
        assertEquals(LocalTime.of(16, 0), CadenceReminderJob.parseTime("16:00"));
        assertEquals(LocalTime.of(9, 30), CadenceReminderJob.parseTime("09:30"));
    }

    @Nested
    class PlanStillDraftTrigger {

        @Test
        void createsNotificationWhenPlanIsDraftOnLockDayAfterLockTime() {
            UUID ownerId = UUID.randomUUID();
            LocalDate monday = LocalDate.of(2026, 3, 16);

            WeeklyPlanEntity draftPlan = buildPlan(
                    UUID.randomUUID(), defaultOrgId, ownerId, monday, PlanState.DRAFT);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, monday)).thenReturn(List.of(draftPlan));
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.LOCKED, monday)).thenReturn(List.of());

            CadenceReminderJob job = jobWithClock(MONDAY_11AM);
            job.sendCadenceReminders();

            verify(notificationRepository).save(argThat(n ->
                    n.getType().equals(CadenceReminderJob.TYPE_PLAN_STILL_DRAFT)
                            && n.getUserId().equals(ownerId)
                            && n.getOrgId().equals(defaultOrgId)
                            && monday.toString().equals(n.getPayload().get("weekStartDate"))
            ));
        }

        @Test
        void doesNotCreateNotificationBeforeLockTime() {
            LocalDate monday = LocalDate.of(2026, 3, 16);
            WeeklyPlanEntity draftPlan = buildPlan(
                    UUID.randomUUID(), defaultOrgId, UUID.randomUUID(), monday, PlanState.DRAFT);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, monday)).thenReturn(List.of(draftPlan));

            CadenceReminderJob job = jobWithClock(MONDAY_9AM);
            job.sendCadenceReminders();

            verify(notificationRepository, never()).save(
                    argThat(n -> n.getType().equals(CadenceReminderJob.TYPE_PLAN_STILL_DRAFT)));
        }

        @Test
        void doesNotCreateNotificationOnWrongDay() {
            CadenceReminderJob job = jobWithClock(WEDNESDAY_9AM);
            job.sendCadenceReminders();

            verify(notificationRepository, never()).save(any(NotificationEntity.class));
        }
    }

    @Nested
    class TimeToReconcileTrigger {

        @Test
        void createsNotificationWhenPlanIsLockedOnReconcileDayAfterReconcileTime() {
            UUID ownerId = UUID.randomUUID();
            LocalDate monday = LocalDate.of(2026, 3, 16);

            WeeklyPlanEntity lockedPlan = buildPlan(
                    UUID.randomUUID(), defaultOrgId, ownerId, monday, PlanState.LOCKED);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, monday)).thenReturn(List.of());
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.LOCKED, monday)).thenReturn(List.of(lockedPlan));

            CadenceReminderJob job = jobWithClock(FRIDAY_17PM);
            job.sendCadenceReminders();

            verify(notificationRepository).save(argThat(n ->
                    n.getType().equals(CadenceReminderJob.TYPE_TIME_TO_RECONCILE)
                            && n.getUserId().equals(ownerId)
                            && monday.toString().equals(n.getPayload().get("weekStartDate"))
            ));
        }
    }

    @Nested
    class ReconciliationOverdueTrigger {

        @Test
        void createsNotificationForPreviousWeekIncompletePlan() {
            UUID ownerId = UUID.randomUUID();
            LocalDate previousMonday = LocalDate.of(2026, 3, 9);
            LocalDate currentMonday = LocalDate.of(2026, 3, 16);

            WeeklyPlanEntity overduePlan = buildPlan(
                    UUID.randomUUID(), defaultOrgId, ownerId, previousMonday, PlanState.LOCKED);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, currentMonday)).thenReturn(List.of());
            when(weeklyPlanRepository.findByOrgIdAndStateInAndWeekStartDateBefore(
                    eq(defaultOrgId),
                    eq(List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILING)),
                    eq(currentMonday))).thenReturn(List.of(overduePlan));
            when(weeklyPlanRepository.findByOrgIdAndStateInAndWeekStartDateBefore(
                    eq(defaultOrgId),
                    eq(List.of(PlanState.DRAFT)),
                    eq(currentMonday))).thenReturn(List.of());

            CadenceReminderJob job = jobWithClock(MONDAY_11AM);
            job.sendCadenceReminders();

            verify(notificationRepository).save(argThat(n ->
                    n.getType().equals(CadenceReminderJob.TYPE_RECONCILIATION_OVERDUE)
                            && n.getUserId().equals(ownerId)
                            && previousMonday.toString().equals(n.getPayload().get("weekStartDate"))
            ));
        }
    }

    @Nested
    class PlanStaleManagerTrigger {

        @Test
        void createsStaleBadgeNotificationForDraftPlanFromPreviousWeek() {
            UUID ownerId = UUID.randomUUID();
            LocalDate previousMonday = LocalDate.of(2026, 3, 9);
            LocalDate currentMonday = LocalDate.of(2026, 3, 16);

            WeeklyPlanEntity staleDraft = buildPlan(
                    UUID.randomUUID(), defaultOrgId, ownerId, previousMonday, PlanState.DRAFT);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, currentMonday)).thenReturn(List.of());
            when(weeklyPlanRepository.findByOrgIdAndStateInAndWeekStartDateBefore(
                    eq(defaultOrgId),
                    eq(List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILING)),
                    eq(currentMonday))).thenReturn(List.of());
            when(weeklyPlanRepository.findByOrgIdAndStateInAndWeekStartDateBefore(
                    eq(defaultOrgId),
                    eq(List.of(PlanState.DRAFT)),
                    eq(currentMonday))).thenReturn(List.of(staleDraft));

            CadenceReminderJob job = jobWithClock(MONDAY_11AM);
            job.sendCadenceReminders();

            verify(notificationRepository).save(argThat(n ->
                    n.getType().equals(CadenceReminderJob.TYPE_PLAN_STALE_MANAGER)
                            && n.getUserId().equals(ownerId)
                            && previousMonday.toString().equals(n.getPayload().get("weekStartDate"))
            ));
        }
    }

    @Nested
    class Idempotency {

        @Test
        void doesNotCreateDuplicateNotificationWhenSamePlanReminderAlreadySentThisWeek() {
            UUID planId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            LocalDate monday = LocalDate.of(2026, 3, 16);

            WeeklyPlanEntity draftPlan = buildPlan(planId, defaultOrgId, ownerId, monday, PlanState.DRAFT);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, monday)).thenReturn(List.of(draftPlan));

            when(notificationRepository
                    .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            eq(defaultOrgId), eq(ownerId),
                            eq(CadenceReminderJob.TYPE_PLAN_STILL_DRAFT), any()))
                    .thenReturn(List.of(existingNotification(
                            defaultOrgId,
                            ownerId,
                            CadenceReminderJob.TYPE_PLAN_STILL_DRAFT,
                            planId,
                            monday)));

            CadenceReminderJob job = jobWithClock(MONDAY_11AM);
            job.sendCadenceReminders();

            verify(notificationRepository, never()).save(
                    argThat(n -> n.getType().equals(CadenceReminderJob.TYPE_PLAN_STILL_DRAFT)));
        }

        @Test
        void createsNotificationsForDifferentWeeksEvenWhenOwnerAndTypeMatch() {
            UUID ownerId = UUID.randomUUID();
            LocalDate previousMonday = LocalDate.of(2026, 3, 9);
            LocalDate twoWeeksAgoMonday = LocalDate.of(2026, 3, 2);
            LocalDate currentMonday = LocalDate.of(2026, 3, 16);

            WeeklyPlanEntity overduePlan1 = buildPlan(
                    UUID.randomUUID(), defaultOrgId, ownerId, previousMonday, PlanState.LOCKED);
            WeeklyPlanEntity overduePlan2 = buildPlan(
                    UUID.randomUUID(), defaultOrgId, ownerId, twoWeeksAgoMonday, PlanState.RECONCILING);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, currentMonday)).thenReturn(List.of());
            when(weeklyPlanRepository.findByOrgIdAndStateInAndWeekStartDateBefore(
                    eq(defaultOrgId),
                    eq(List.of(PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILING)),
                    eq(currentMonday))).thenReturn(List.of(overduePlan1, overduePlan2));
            when(weeklyPlanRepository.findByOrgIdAndStateInAndWeekStartDateBefore(
                    eq(defaultOrgId),
                    eq(List.of(PlanState.DRAFT)),
                    eq(currentMonday))).thenReturn(List.of());
            when(notificationRepository
                    .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            eq(defaultOrgId), eq(ownerId),
                            eq(CadenceReminderJob.TYPE_RECONCILIATION_OVERDUE), any()))
                    .thenReturn(List.of());

            CadenceReminderJob job = jobWithClock(MONDAY_11AM);
            job.sendCadenceReminders();

            verify(notificationRepository, times(2)).save(
                    argThat(n -> n.getType().equals(CadenceReminderJob.TYPE_RECONCILIATION_OVERDUE)));
        }

        @Test
        void createsBothNotificationsWhenTwoDifferentUsersNeedReminders() {
            LocalDate monday = LocalDate.of(2026, 3, 16);
            UUID ownerId1 = UUID.randomUUID();
            UUID ownerId2 = UUID.randomUUID();

            WeeklyPlanEntity plan1 = buildPlan(UUID.randomUUID(), defaultOrgId, ownerId1, monday, PlanState.DRAFT);
            WeeklyPlanEntity plan2 = buildPlan(UUID.randomUUID(), defaultOrgId, ownerId2, monday, PlanState.DRAFT);
            when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                    defaultOrgId, PlanState.DRAFT, monday)).thenReturn(List.of(plan1, plan2));

            CadenceReminderJob job = jobWithClock(MONDAY_11AM);
            job.sendCadenceReminders();

            verify(notificationRepository, times(2)).save(
                    argThat(n -> n.getType().equals(CadenceReminderJob.TYPE_PLAN_STILL_DRAFT)));
        }
    }

    @Test
    void incrementsTaggedCounterForEachReminderSent() {
        LocalDate monday = LocalDate.of(2026, 3, 16);
        UUID ownerId = UUID.randomUUID();

        WeeklyPlanEntity plan = buildPlan(UUID.randomUUID(), defaultOrgId, ownerId, monday, PlanState.DRAFT);
        when(weeklyPlanRepository.findByOrgIdAndStateAndWeekStartDate(
                defaultOrgId, PlanState.DRAFT, monday)).thenReturn(List.of(plan));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CadenceReminderJob job = jobWithClock(MONDAY_11AM);
        job.sendCadenceReminders();

        double count = meterRegistry.get("cadence_reminders_sent_total")
                .tag("reminder_type", CadenceReminderJob.TYPE_PLAN_STILL_DRAFT)
                .counter()
                .count();
        assertEquals(1.0, count, 0.001);
    }

    @Test
    void doesNothingWhenNoOrgsHavePlans() {
        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of());

        CadenceReminderJob job = jobWithClock(MONDAY_11AM);
        job.sendCadenceReminders();

        verify(notificationRepository, never()).save(any(NotificationEntity.class));
        verify(orgPolicyService, never()).getPolicy(any());
    }

    private CadenceReminderJob jobWithClock(Instant fixedInstant) {
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        return new CadenceReminderJob(
                orgPolicyService,
                weeklyPlanRepository,
                notificationRepository,
                meterRegistry,
                fixedClock
        );
    }

    private static OrgPolicyService.OrgPolicy defaultPolicy() {
        return new OrgPolicyService.OrgPolicy(
                true, 1, 2,
                LOCK_DAY, LOCK_TIME,
                RECONCILE_DAY, RECONCILE_TIME,
                true, 60,
                "FRIDAY", "17:00"
        );
    }

    private static WeeklyPlanEntity buildPlan(
            UUID planId, UUID orgId, UUID ownerId, LocalDate weekStart, PlanState state) {
        WeeklyPlanEntity entity = new WeeklyPlanEntity(planId, orgId, ownerId, weekStart);
        entity.setState(state);
        return entity;
    }

    private static NotificationEntity existingNotification(
            UUID orgId,
            UUID userId,
            String type,
            UUID planId,
            LocalDate weekStartDate
    ) {
        return new NotificationEntity(
                orgId,
                userId,
                type,
                Map.of(
                        "planId", planId.toString(),
                        "weekStartDate", weekStartDate.toString(),
                        "message", "existing"
                )
        );
    }
}
