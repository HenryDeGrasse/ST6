package com.weekly.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.config.OrgPolicyService;
import com.weekly.notification.NotificationEntity;
import com.weekly.notification.NotificationRepository;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.repository.ManagerReviewRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.EventType;
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
 * Unit tests for {@link DigestJob}.
 */
class DigestJobTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();

    // 2026-03-16 = Monday, 2026-03-20 = Friday
    private static final Instant FRIDAY_18PM = Instant.parse("2026-03-20T18:00:00Z");
    private static final Instant FRIDAY_16PM = Instant.parse("2026-03-20T16:00:00Z");
    private static final Instant MONDAY_9AM  = Instant.parse("2026-03-23T09:00:00Z");
    private static final Instant WEDNESDAY   = Instant.parse("2026-03-18T10:00:00Z");

    private OrgPolicyService orgPolicyService;
    private WeeklyPlanRepository weeklyPlanRepository;
    private ManagerReviewRepository managerReviewRepository;
    private NotificationRepository notificationRepository;
    private DigestService digestService;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        orgPolicyService = mock(OrgPolicyService.class);
        weeklyPlanRepository = mock(WeeklyPlanRepository.class);
        managerReviewRepository = mock(ManagerReviewRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        digestService = mock(DigestService.class);
        outboxService = mock(OutboxService.class);

        when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(orgPolicyService.getPolicy(ORG_ID)).thenReturn(defaultFridayPolicy());
        when(managerReviewRepository.findDistinctReviewerUserIdsByOrgId(ORG_ID))
                .thenReturn(List.of(MANAGER_ID));
        when(notificationRepository
                .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        any(), any(), any(), any()))
                .thenReturn(List.of());
        when(digestService.buildDigestPayload(any(), any(), any()))
                .thenReturn(emptyPayload("2026-03-16"));
    }

    @Nested
    class ScheduleTrigger {

        @Test
        void publishesDigestWhenTodayIsFridayAfterDigestTime() {
            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            verify(outboxService).publish(
                    eq(EventType.WEEKLY_DIGEST),
                    eq("WeeklyDigest"),
                    eq(MANAGER_ID),
                    eq(ORG_ID),
                    any()
            );
        }

        @Test
        void doesNotPublishBeforeDigestTime() {
            // Friday at 16:00, but policy says 17:00
            DigestJob job = jobWithClock(FRIDAY_16PM);
            job.sendWeeklyDigests();

            verify(outboxService, never()).publish(any(), any(), any(), any(), any());
        }

        @Test
        void doesNotPublishOnWrongDay() {
            DigestJob job = jobWithClock(WEDNESDAY);
            job.sendWeeklyDigests();

            verify(outboxService, never()).publish(any(), any(), any(), any(), any());
        }

        @Test
        void doesNotPublishWhenNoOrgsHavePlans() {
            when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of());

            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            verify(outboxService, never()).publish(any(), any(), any(), any(), any());
        }

        @Test
        void doesNotPublishWhenNoManagersInOrg() {
            when(managerReviewRepository.findDistinctReviewerUserIdsByOrgId(ORG_ID))
                    .thenReturn(List.of());

            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            verify(outboxService, never()).publish(any(), any(), any(), any(), any());
        }
    }

    @Nested
    class WeekStartDetermination {

        @Test
        void fridayDigestCoversCurrentWeek() {
            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            // Friday 2026-03-20 → current week Monday = 2026-03-16
            verify(digestService).buildDigestPayload(ORG_ID, MANAGER_ID, LocalDate.of(2026, 3, 16));
        }

        @Test
        void mondayDigestCoversPreviousWeek() {
            when(orgPolicyService.getPolicy(ORG_ID)).thenReturn(defaultMondayPolicy());
            when(digestService.buildDigestPayload(any(), any(), any()))
                    .thenReturn(emptyPayload("2026-03-16"));

            DigestJob job = jobWithClock(MONDAY_9AM);
            job.sendWeeklyDigests();

            // Monday 2026-03-23 → previous week Monday = 2026-03-16
            verify(digestService).buildDigestPayload(ORG_ID, MANAGER_ID, LocalDate.of(2026, 3, 16));
        }
    }

    @Nested
    class Idempotency {

        @Test
        void skipsPublishWhenDigestAlreadySentForSameManagerAndWeek() {
            NotificationEntity existing = new NotificationEntity(
                    ORG_ID, MANAGER_ID, "WEEKLY_DIGEST",
                    Map.of("weekStart", "2026-03-16", "totalMemberCount", 3)
            );
            when(notificationRepository
                    .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            eq(ORG_ID), eq(MANAGER_ID), eq("WEEKLY_DIGEST"), any()))
                    .thenReturn(List.of(existing));

            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            verify(outboxService, never()).publish(any(), any(), any(), any(), any());
        }

        @Test
        void publishesWhenExistingDigestIsForDifferentWeek() {
            NotificationEntity existing = new NotificationEntity(
                    ORG_ID, MANAGER_ID, "WEEKLY_DIGEST",
                    Map.of("weekStart", "2026-03-09") // previous week
            );
            when(notificationRepository
                    .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            eq(ORG_ID), eq(MANAGER_ID), eq("WEEKLY_DIGEST"), any()))
                    .thenReturn(List.of(existing));

            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            verify(outboxService).publish(any(), any(), any(), any(), any());
        }
    }

    @Nested
    class EventPayload {

        @Test
        void publishedPayloadContainsManagerIdAndWeekStart() {
            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            verify(outboxService).publish(
                    eq(EventType.WEEKLY_DIGEST),
                    eq("WeeklyDigest"),
                    eq(MANAGER_ID),
                    eq(ORG_ID),
                    argThat(payload ->
                            MANAGER_ID.toString().equals(payload.get("managerId"))
                                    && "2026-03-16".equals(payload.get("weekStart"))
                    )
            );
        }
    }

    @Nested
    class ParsingHelpers {

        @Test
        void parseDayOfWeekHandlesMixedCase() {
            assertEquals(DayOfWeek.FRIDAY, DigestJob.parseDayOfWeek("friday"));
            assertEquals(DayOfWeek.MONDAY, DigestJob.parseDayOfWeek("MONDAY"));
            assertEquals(DayOfWeek.WEDNESDAY, DigestJob.parseDayOfWeek("Wednesday"));
        }

        @Test
        void parseTimeHandlesHHmm() {
            assertEquals(LocalTime.of(17, 0), DigestJob.parseTime("17:00"));
            assertEquals(LocalTime.of(8, 0), DigestJob.parseTime("08:00"));
        }

        @Test
        void toEventPayloadIncludesAllDigestFields() {
            DigestPayload payload = new DigestPayload(
                    "2026-03-16", 5, 3, 1, 1, 0, 2,
                    List.of("user-a"), List.of("user-stale"), List.of("user-b"), 0.75, 0.6, 2
            );

            Map<String, Object> eventPayload = DigestJob.toEventPayload(MANAGER_ID, payload);

            assertEquals(MANAGER_ID.toString(), eventPayload.get("managerId"));
            assertEquals("2026-03-16", eventPayload.get("weekStart"));
            assertEquals(5, eventPayload.get("totalMemberCount"));
            assertEquals(3, eventPayload.get("reconciledCount"));
            assertEquals(1, eventPayload.get("lockedCount"));
            assertEquals(1, eventPayload.get("draftCount"));
            assertEquals(0, eventPayload.get("staleCount"));
            assertEquals(2, eventPayload.get("reviewQueueSize"));
            assertEquals(List.of("user-a"), eventPayload.get("carryForwardStreakUserIds"));
            assertEquals(List.of("user-stale"), eventPayload.get("stalePlanUserIds"));
            assertEquals(List.of("user-b"), eventPayload.get("lateLockUserIds"));
            assertEquals(0.75, (double) eventPayload.get("rcdoAlignmentRate"), 0.001);
            assertEquals(0.6, (double) eventPayload.get("previousRcdoAlignmentRate"), 0.001);
            assertEquals(2, eventPayload.get("doneEarlyCount"));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void continuesProcessingOtherOrgsWhenOneOrgFails() {
            UUID secondOrgId = UUID.randomUUID();
            UUID secondManagerId = UUID.randomUUID();

            when(weeklyPlanRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID, secondOrgId));
            when(orgPolicyService.getPolicy(secondOrgId)).thenReturn(defaultFridayPolicy());
            when(managerReviewRepository.findDistinctReviewerUserIdsByOrgId(secondOrgId))
                    .thenReturn(List.of(secondManagerId));
            when(digestService.buildDigestPayload(eq(ORG_ID), eq(MANAGER_ID), any()))
                    .thenThrow(new RuntimeException("simulated failure"));
            when(digestService.buildDigestPayload(eq(secondOrgId), eq(secondManagerId), any()))
                    .thenReturn(emptyPayload("2026-03-16"));

            DigestJob job = jobWithClock(FRIDAY_18PM);
            job.sendWeeklyDigests();

            // First org failed, second org should still publish
            verify(outboxService).publish(
                    any(), any(), eq(secondManagerId), eq(secondOrgId), any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DigestJob jobWithClock(Instant fixedInstant) {
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        return new DigestJob(
                orgPolicyService,
                weeklyPlanRepository,
                managerReviewRepository,
                notificationRepository,
                digestService,
                outboxService,
                fixedClock
        );
    }

    private static OrgPolicyService.OrgPolicy defaultFridayPolicy() {
        return new OrgPolicyService.OrgPolicy(
                true, 1, 2,
                "MONDAY", "10:00",
                "FRIDAY", "16:00",
                true, 60,
                "FRIDAY", "17:00"
        );
    }

    private static OrgPolicyService.OrgPolicy defaultMondayPolicy() {
        return new OrgPolicyService.OrgPolicy(
                true, 1, 2,
                "MONDAY", "10:00",
                "FRIDAY", "16:00",
                true, 60,
                "MONDAY", "08:00"
        );
    }

    private static DigestPayload emptyPayload(String weekStart) {
        return new DigestPayload(
                weekStart, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), 0.0, null, 0
        );
    }
}
