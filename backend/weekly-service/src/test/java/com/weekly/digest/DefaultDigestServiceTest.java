package com.weekly.digest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.auth.OrgGraphClient;
import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ManagerInsightDataProvider;
import com.weekly.shared.ManagerInsightDataProvider.CarryForwardStreak;
import com.weekly.shared.ManagerInsightDataProvider.LateLockPattern;
import com.weekly.shared.ManagerInsightDataProvider.ManagerWeekContext;
import com.weekly.shared.ManagerInsightDataProvider.RcdoFocusContext;
import com.weekly.shared.ManagerInsightDataProvider.ReviewCounts;
import com.weekly.shared.ManagerInsightDataProvider.ReviewTurnaroundStats;
import com.weekly.shared.ManagerInsightDataProvider.TeamMemberContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link DefaultDigestService}.
 */
class DefaultDigestServiceTest {

    private ManagerInsightDataProvider managerInsightDataProvider;
    private OrgGraphClient orgGraphClient;
    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private ProgressEntryRepository progressEntryRepository;
    private DefaultDigestService service;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 16); // Monday

    @BeforeEach
    void setUp() {
        managerInsightDataProvider = mock(ManagerInsightDataProvider.class);
        orgGraphClient = mock(OrgGraphClient.class);
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);

        service = new DefaultDigestService(
                managerInsightDataProvider,
                orgGraphClient,
                planRepository,
                commitRepository,
                progressEntryRepository
        );

        // Default: no direct reports for done-early check
        when(orgGraphClient.getDirectReports(any(), any())).thenReturn(List.of());
    }

    @Nested
    class PlanStatusCounts {

        @Test
        void countsReconciledAndCarryForwardMembersAsReconciled() {
            ManagerWeekContext ctx = buildContext(List.of(
                    member("RECONCILED", false),
                    member("CARRY_FORWARD", false),
                    member("LOCKED", false)
            ), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(2, payload.reconciledCount());
            assertEquals(1, payload.lockedCount());
            assertEquals(0, payload.draftCount());
            assertEquals(0, payload.staleCount());
        }

        @Test
        void countsReconcilngAsLocked() {
            ManagerWeekContext ctx = buildContext(List.of(
                    member("RECONCILING", false)
            ), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(0, payload.reconciledCount());
            assertEquals(1, payload.lockedCount());
        }

        @Test
        void countsStaleMembersCorrectly() {
            ManagerWeekContext ctx = buildContext(List.of(
                    staleMember(),
                    member("DRAFT", false)
            ), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(1, payload.staleCount());
            assertEquals(1, payload.draftCount());
        }

        @Test
        void countsMembersWithNoPlansAsDraft() {
            ManagerWeekContext ctx = buildContext(List.of(
                    memberNoState()
            ), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(1, payload.draftCount());
            assertEquals(0, payload.staleCount());
        }

        @Test
        void exposesReviewQueueSizeFromReviewCounts() {
            ManagerWeekContext ctx = buildContextWithReviews(
                    List.of(member("RECONCILED", false)),
                    new ReviewCounts(3, 1, 0),
                    List.of(), List.of()
            );
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(3, payload.reviewQueueSize());
        }
    }

    @Nested
    class AttentionItems {

        @Test
        void listsCarryForwardStreakUserIds() {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            ManagerWeekContext ctx = buildContext(
                    List.of(member("LOCKED", false)),
                    List.of(
                            new CarryForwardStreak(userId1.toString(), 2, List.of("Task A")),
                            new CarryForwardStreak(userId2.toString(), 3, List.of("Task B", "Task C"))
                    ),
                    List.of()
            );
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(2, payload.carryForwardStreakUserIds().size());
            assertEquals(userId1.toString(), payload.carryForwardStreakUserIds().get(0));
            assertEquals(userId2.toString(), payload.carryForwardStreakUserIds().get(1));
        }

        @Test
        void listsLateLockUserIds() {
            UUID userId = UUID.randomUUID();
            ManagerWeekContext ctx = buildContext(
                    List.of(member("LOCKED", false)),
                    List.of(),
                    List.of(new LateLockPattern(userId.toString(), 2, 4))
            );
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(1, payload.lateLockUserIds().size());
            assertEquals(userId.toString(), payload.lateLockUserIds().get(0));
        }

        @Test
        void listsStalePlanUserIds() {
            TeamMemberContext staleOne = new TeamMemberContext(
                    UUID.randomUUID().toString(), "DRAFT", "REVIEW_NOT_APPLICABLE",
                    0, 0, 0, 0, 0, 0, true, false
            );
            TeamMemberContext freshOne = member("LOCKED", false);
            ManagerWeekContext ctx = buildContext(List.of(staleOne, freshOne), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(List.of(staleOne.userId()), payload.stalePlanUserIds());
        }
    }

    @Nested
    class RcdoAlignment {

        @Test
        void computesAlignmentRateFromRcdoFocusesAndCommitCounts() {
            // Team has 10 commits total; 6 are strategic (in rcdoFocuses)
            ManagerWeekContext ctx = buildContextWithRcdo(
                    List.of(
                            memberWithCommits("LOCKED", 4),
                            memberWithCommits("RECONCILED", 6)
                    ),
                    List.of(
                            rcdoFocus(4),
                            rcdoFocus(2)
                    ),
                    List.of(), List.of()
            );
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(0.6, payload.rcdoAlignmentRate(), 0.001);
        }

        @Test
        void returnsZeroAlignmentWhenTeamHasNoCommits() {
            ManagerWeekContext ctx = buildContextWithRcdo(
                    List.of(),
                    List.of(),
                    List.of(), List.of()
            );
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(0.0, payload.rcdoAlignmentRate(), 0.0001);
        }

        @Test
        void includesPreviousWeekAlignmentRateWhenAvailable() {
            ManagerWeekContext currentCtx = buildContextWithRcdo(
                    List.of(
                            memberWithCommits("LOCKED", 4),
                            memberWithCommits("RECONCILED", 6)
                    ),
                    List.of(rcdoFocus(7)),
                    List.of(), List.of()
            );
            ManagerWeekContext previousCtx = new ManagerWeekContext(
                    WEEK_START.minusWeeks(1).toString(),
                    new ReviewCounts(0, 0, 0),
                    List.of(memberWithCommits("LOCKED", 10)),
                    List.of(rcdoFocus(5)),
                    List.of(),
                    List.of(),
                    List.of(),
                    new ReviewTurnaroundStats(0, 0)
            );
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(currentCtx);
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START.minusWeeks(1)),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(previousCtx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(0.7, payload.rcdoAlignmentRate(), 0.001);
            assertEquals(0.5, payload.previousRcdoAlignmentRate(), 0.001);
        }
    }

    @Nested
    class DoneEarlyCount {

        @Test
        void countsDoneEarlyProgressEntries() {
            UUID userId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();

            when(orgGraphClient.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(List.of(userId));

            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, userId, WEEK_START);
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    ORG_ID, WEEK_START, List.of(userId)))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit1 = new WeeklyCommitEntity(commitId1, ORG_ID, planId, "Task 1");
            WeeklyCommitEntity commit2 = new WeeklyCommitEntity(commitId2, ORG_ID, planId, "Task 2");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(planId)))
                    .thenReturn(List.of(commit1, commit2));

            ProgressEntryEntity doneEarly = new ProgressEntryEntity(
                    UUID.randomUUID(), ORG_ID, commitId1, ProgressStatus.DONE_EARLY, "Finished early");
            ProgressEntryEntity onTrack = new ProgressEntryEntity(
                    UUID.randomUUID(), ORG_ID, commitId2, ProgressStatus.ON_TRACK, "On track");
            // Two DONE_EARLY entries for same commit — should count as 1
            ProgressEntryEntity doneEarly2 = new ProgressEntryEntity(
                    UUID.randomUUID(), ORG_ID, commitId1, ProgressStatus.DONE_EARLY, "Still done");
            ReflectionTestUtils.setField(doneEarly, "createdAt", Instant.parse("2026-03-17T09:00:00Z"));
            ReflectionTestUtils.setField(onTrack, "createdAt", Instant.parse("2026-03-18T09:00:00Z"));
            ReflectionTestUtils.setField(doneEarly2, "createdAt", Instant.parse("2026-03-19T09:00:00Z"));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    ORG_ID, List.of(commitId1, commitId2)))
                    .thenReturn(List.of(doneEarly, onTrack, doneEarly2));

            ManagerWeekContext ctx = buildContext(
                    List.of(memberWithCommits("LOCKED", 2)), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            // commitId1 had two DONE_EARLY entries — counts as 1 distinct commit
            assertEquals(1, payload.doneEarlyCount());
        }

        @Test
        void returnsZeroDoneEarlyWhenNoDirectReports() {
            when(orgGraphClient.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(List.of());

            ManagerWeekContext ctx = buildContext(List.of(), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(0, payload.doneEarlyCount());
        }

        @Test
        void ignoresDoneEarlyEntriesOutsideTargetWeek() {
            UUID userId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();

            when(orgGraphClient.getDirectReports(ORG_ID, MANAGER_ID)).thenReturn(List.of(userId));

            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, userId, WEEK_START);
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    ORG_ID, WEEK_START, List.of(userId)))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, planId, "Task 1");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(ORG_ID, List.of(planId)))
                    .thenReturn(List.of(commit));

            ProgressEntryEntity previousWeekDoneEarly = new ProgressEntryEntity(
                    UUID.randomUUID(), ORG_ID, commitId, ProgressStatus.DONE_EARLY, "Finished early");
            ProgressEntryEntity nextWeekDoneEarly = new ProgressEntryEntity(
                    UUID.randomUUID(), ORG_ID, commitId, ProgressStatus.DONE_EARLY, "Finished early again");
            ReflectionTestUtils.setField(previousWeekDoneEarly, "createdAt", Instant.parse("2026-03-15T23:59:59Z"));
            ReflectionTestUtils.setField(nextWeekDoneEarly, "createdAt", Instant.parse("2026-03-23T00:00:00Z"));
            when(progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                    ORG_ID, List.of(commitId)))
                    .thenReturn(List.of(previousWeekDoneEarly, nextWeekDoneEarly));

            ManagerWeekContext ctx = buildContext(
                    List.of(memberWithCommits("LOCKED", 1)), List.of(), List.of());
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertEquals(0, payload.doneEarlyCount());
        }
    }

    @Nested
    class GeneralPayloadStructure {

        @Test
        void payloadContainsWeekStartAndMemberCount() {
            ManagerWeekContext ctx = buildContext(
                    List.of(member("LOCKED", false), member("RECONCILED", false)),
                    List.of(), List.of()
            );
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(MANAGER_ID), eq(WEEK_START),
                    eq(DefaultDigestService.HISTORY_WINDOW_WEEKS)))
                    .thenReturn(ctx);

            DigestPayload payload = service.buildDigestPayload(ORG_ID, MANAGER_ID, WEEK_START);

            assertNotNull(payload);
            assertEquals("2026-03-16", payload.weekStart());
            assertEquals(2, payload.totalMemberCount());
        }
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private ManagerWeekContext buildContext(
            List<TeamMemberContext> members,
            List<CarryForwardStreak> streaks,
            List<LateLockPattern> lateLocks
    ) {
        return buildContextWithReviews(members, new ReviewCounts(0, 0, 0), streaks, lateLocks);
    }

    private ManagerWeekContext buildContextWithReviews(
            List<TeamMemberContext> members,
            ReviewCounts reviewCounts,
            List<CarryForwardStreak> streaks,
            List<LateLockPattern> lateLocks
    ) {
        return new ManagerWeekContext(
                WEEK_START.toString(),
                reviewCounts,
                members,
                List.of(),
                streaks,
                List.of(),
                lateLocks,
                new ReviewTurnaroundStats(0, 0)
        );
    }

    private ManagerWeekContext buildContextWithRcdo(
            List<TeamMemberContext> members,
            List<RcdoFocusContext> rcdoFocuses,
            List<CarryForwardStreak> streaks,
            List<LateLockPattern> lateLocks
    ) {
        return new ManagerWeekContext(
                WEEK_START.toString(),
                new ReviewCounts(0, 0, 0),
                members,
                rcdoFocuses,
                streaks,
                List.of(),
                lateLocks,
                new ReviewTurnaroundStats(0, 0)
        );
    }

    private static TeamMemberContext member(String state, boolean isStale) {
        return new TeamMemberContext(
                UUID.randomUUID().toString(), state, "REVIEW_NOT_APPLICABLE",
                1, 0, 0, 0, 0, 0, isStale, false
        );
    }

    private static TeamMemberContext memberWithCommits(String state, int commitCount) {
        return new TeamMemberContext(
                UUID.randomUUID().toString(), state, "REVIEW_NOT_APPLICABLE",
                commitCount, 0, 0, 0, 0, 0, false, false
        );
    }

    private static TeamMemberContext staleMember() {
        return new TeamMemberContext(
                UUID.randomUUID().toString(), "DRAFT", "REVIEW_NOT_APPLICABLE",
                0, 0, 0, 0, 0, 0, true, false
        );
    }

    private static TeamMemberContext memberNoState() {
        return new TeamMemberContext(
                UUID.randomUUID().toString(), null, null,
                0, 0, 0, 0, 0, 0, false, false
        );
    }

    private static RcdoFocusContext rcdoFocus(int commitCount) {
        return new RcdoFocusContext(
                UUID.randomUUID().toString(), "Outcome", "Objective", "Rally Cry",
                commitCount, 0, 0
        );
    }
}
