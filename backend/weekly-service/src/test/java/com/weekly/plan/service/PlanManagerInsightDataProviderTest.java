package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.ManagerReviewEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.RcdoRollupResponse;
import com.weekly.plan.dto.ReviewStatusCountsResponse;
import com.weekly.plan.dto.TeamMemberSummaryResponse;
import com.weekly.plan.dto.TeamSummaryResponseDto;
import com.weekly.plan.repository.ManagerReviewRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ManagerInsightDataProvider;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link PlanManagerInsightDataProvider}, focusing on the
 * multi-week historical context computation.
 *
 * <p>Uses a hand-rolled stub for {@link TeamDashboardService} (a concrete class) to
 * avoid Mockito inline-mock limitations with Java 21 / Spring AOP classes, following
 * the same pattern used by {@link com.weekly.ai.StubLlmClient}.
 */
class PlanManagerInsightDataProviderTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    private StubDashboardService dashboardService;
    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private ManagerReviewRepository reviewRepository;
    private PlanManagerInsightDataProvider provider;

    @BeforeEach
    void setUp() {
        dashboardService = new StubDashboardService();
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        reviewRepository = mock(ManagerReviewRepository.class);
        provider = new PlanManagerInsightDataProvider(
                dashboardService, planRepository, commitRepository, reviewRepository);
    }

    // ── Test double for TeamDashboardService ──────────────────────────────

    /**
     * Hand-rolled stub for {@link TeamDashboardService}.
     *
     * <p>Extends the concrete class with null-safe super constructor args, then
     * overrides only the two methods called by {@link PlanManagerInsightDataProvider}.
     * This avoids Mockito inline-mocking restrictions on concrete Spring services.
     */
    private static class StubDashboardService extends TeamDashboardService {

        private TeamSummaryResponseDto teamSummary;
        private RcdoRollupResponse rcdoRollup;

        StubDashboardService() {
            // Pass nulls for all repos — they are never used because both methods are overridden.
            super(null, null, null, null, new CommitValidator());
            // Default returns
            teamSummary = emptySummary();
            rcdoRollup = emptyRollup();
        }

        void setTeamSummary(TeamSummaryResponseDto ts) {
            this.teamSummary = ts;
        }

        void setRcdoRollup(RcdoRollupResponse rr) {
            this.rcdoRollup = rr;
        }

        @Override
        public TeamSummaryResponseDto getTeamSummary(
                UUID orgId, UUID managerId, LocalDate weekStart,
                int page, int size,
                String stateFilter, String outcomeIdFilter,
                Boolean incompleteFilter, Boolean nonStrategicFilter,
                String priorityFilter, String categoryFilter) {
            return teamSummary;
        }

        @Override
        public RcdoRollupResponse getRcdoRollup(UUID orgId, UUID managerId, LocalDate weekStart) {
            return rcdoRollup;
        }

        private static TeamSummaryResponseDto emptySummary() {
            return new TeamSummaryResponseDto(
                    WEEK_START.toString(), List.of(),
                    new ReviewStatusCountsResponse(0, 0, 0), 0, 0, 0, 0);
        }

        private static RcdoRollupResponse emptyRollup() {
            return new RcdoRollupResponse(WEEK_START.toString(), List.of(), 0);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID userId(String tag) {
        return UUID.nameUUIDFromBytes(tag.getBytes());
    }

    private TeamSummaryResponseDto summaryWithUser(UUID userId) {
        TeamMemberSummaryResponse member = new TeamMemberSummaryResponse(
                userId.toString(), "Test User", null, "DRAFT", null,
                0, 0, 0, 0, 0, 0, null, false, false);
        return new TeamSummaryResponseDto(
                WEEK_START.toString(), List.of(member),
                new ReviewStatusCountsResponse(0, 0, 0), 0, 1, 1, 1);
    }

    private WeeklyPlanEntity lockedPlan(UUID planId, UUID userId, LocalDate weekStart, LockType lockType) {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, userId, weekStart);
        plan.lock(lockType);
        return plan;
    }

    private WeeklyPlanEntity draftPlan(UUID planId, UUID userId, LocalDate weekStart) {
        return new WeeklyPlanEntity(planId, ORG_ID, userId, weekStart);
    }

    private WeeklyCommitEntity carriedCommit(UUID planId, String title) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, title);
        commit.setCarriedFromCommitId(UUID.randomUUID());
        return commit;
    }

    private WeeklyCommitEntity outcomeCommit(UUID planId, UUID outcomeId, String outcomeName) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Work item");
        commit.setOutcomeId(outcomeId);
        commit.populateSnapshot(null, null, null, null, outcomeId, outcomeName);
        return commit;
    }

    private LocalDate windowStart(int windowWeeks) {
        return WEEK_START.minusWeeks(windowWeeks - 1);
    }

    // ── windowWeeks = 1 (no history) ─────────────────────────────────────

    @Nested
    class WithWindowWeeksOne {

        @Test
        void returnsEmptyHistoricalContextWhenWindowIsOne() {
            UUID user = userId("alice");
            dashboardService.setTeamSummary(summaryWithUser(user));

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 1);

            assertTrue(ctx.carryForwardStreaks().isEmpty());
            assertTrue(ctx.outcomeCoverageTrends().isEmpty());
            assertTrue(ctx.lateLockPatterns().isEmpty());
            assertNull(ctx.reviewTurnaroundStats());
        }
    }

    @Test
    void rejectsWindowWeeksBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 0));
    }

    // ── Carry-forward streaks ─────────────────────────────────────────────

    @Nested
    class CarryForwardStreaks {

        @Test
        void detectsSingleWeekStreak() {
            UUID user = userId("bob");
            UUID planId = UUID.randomUUID();
            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity plan = draftPlan(planId, user, WEEK_START);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            carriedCommit(planId, "Task Alpha"),
                            carriedCommit(planId, "Task Beta")
                    ));
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertEquals(1, ctx.carryForwardStreaks().size());
            ManagerInsightDataProvider.CarryForwardStreak streak = ctx.carryForwardStreaks().get(0);
            assertEquals(user.toString(), streak.userId());
            assertEquals(1, streak.streakWeeks());
            assertEquals(2, streak.carriedItemTitles().size());
            assertTrue(streak.carriedItemTitles().contains("Task Alpha"));
            assertTrue(streak.carriedItemTitles().contains("Task Beta"));
        }

        @Test
        void detectsMultiWeekStreak() {
            UUID user = userId("carol");
            UUID planIdW0 = UUID.randomUUID();
            UUID planIdW1 = UUID.randomUUID();
            UUID planIdW2 = UUID.randomUUID();
            LocalDate week0 = WEEK_START;
            LocalDate week1 = WEEK_START.minusWeeks(1);
            LocalDate week2 = WEEK_START.minusWeeks(2);

            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity p0 = draftPlan(planIdW0, user, week0);
            WeeklyPlanEntity p1 = draftPlan(planIdW1, user, week1);
            WeeklyPlanEntity p2 = draftPlan(planIdW2, user, week2);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(p0, p1, p2));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            carriedCommit(planIdW0, "Item A"),
                            carriedCommit(planIdW0, "Item B"),
                            carriedCommit(planIdW1, "Item C"),
                            carriedCommit(planIdW1, "Item D"),
                            carriedCommit(planIdW2, "Item E"),
                            carriedCommit(planIdW2, "Item F")
                    ));
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertEquals(1, ctx.carryForwardStreaks().size());
            assertEquals(3, ctx.carryForwardStreaks().get(0).streakWeeks(),
                    "streak should span 3 consecutive weeks");
        }

        @Test
        void ignoresUserWithFewerThanTwoCarries() {
            UUID user = userId("dan");
            UUID planId = UUID.randomUUID();
            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity plan = draftPlan(planId, user, WEEK_START);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(carriedCommit(planId, "Only carry")));
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertTrue(ctx.carryForwardStreaks().isEmpty());
        }

        @Test
        void breaksStreakOnNonCarryWeek() {
            UUID user = userId("erin");
            UUID planIdW0 = UUID.randomUUID();
            UUID planIdW2 = UUID.randomUUID(); // week1 has no carries
            LocalDate week0 = WEEK_START;
            LocalDate week2 = WEEK_START.minusWeeks(2);

            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity p0 = draftPlan(planIdW0, user, week0);
            WeeklyPlanEntity p2 = draftPlan(planIdW2, user, week2);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(p0, p2));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            carriedCommit(planIdW0, "Recent A"),
                            carriedCommit(planIdW0, "Recent B"),
                            carriedCommit(planIdW2, "Old A"),
                            carriedCommit(planIdW2, "Old B")
                    ));
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            // Streak is only 1 week because week1 is missing (gap breaks the streak)
            assertEquals(1, ctx.carryForwardStreaks().size());
            assertEquals(1, ctx.carryForwardStreaks().get(0).streakWeeks(),
                    "gap in week1 should break the streak");
        }
    }

    // ── Outcome coverage trends ───────────────────────────────────────────

    @Nested
    class OutcomeCoverageTrends {

        @Test
        void computesTrendAcrossWeeks() {
            UUID user = userId("frank");
            UUID outcomeId = UUID.randomUUID();
            String outcomeName = "Grow ARR";

            UUID planIdW0 = UUID.randomUUID();
            UUID planIdW1 = UUID.randomUUID();
            LocalDate week0 = WEEK_START;
            LocalDate week1 = WEEK_START.minusWeeks(1);

            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity p0 = draftPlan(planIdW0, user, week0);
            WeeklyPlanEntity p1 = draftPlan(planIdW1, user, week1);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(p0, p1));
            // 1 commit in current week, 3 in the prior week (declining trend)
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(
                            outcomeCommit(planIdW0, outcomeId, outcomeName),
                            outcomeCommit(planIdW1, outcomeId, outcomeName),
                            outcomeCommit(planIdW1, outcomeId, outcomeName),
                            outcomeCommit(planIdW1, outcomeId, outcomeName)
                    ));
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertEquals(1, ctx.outcomeCoverageTrends().size());
            ManagerInsightDataProvider.OutcomeCoverageTrend trend =
                    ctx.outcomeCoverageTrends().get(0);
            assertEquals(outcomeId.toString(), trend.outcomeId());
            assertEquals(outcomeName, trend.outcomeName());
            // 4 entries (one per window week), oldest first
            assertEquals(4, trend.weekCounts().size());

            int currentWeekCount = trend.weekCounts().stream()
                    .filter(wc -> wc.weekStart().equals(week0.toString()))
                    .mapToInt(ManagerInsightDataProvider.WeeklyCommitCount::commitCount)
                    .sum();
            assertEquals(1, currentWeekCount);

            int priorWeekCount = trend.weekCounts().stream()
                    .filter(wc -> wc.weekStart().equals(week1.toString()))
                    .mapToInt(ManagerInsightDataProvider.WeeklyCommitCount::commitCount)
                    .sum();
            assertEquals(3, priorWeekCount);
        }

        @Test
        void excludesCommitsWithNoOutcomeId() {
            UUID user = userId("gina");
            UUID planId = UUID.randomUUID();
            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity plan = draftPlan(planId, user, WEEK_START);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(plan));
            // Non-strategic commit (no outcomeId)
            WeeklyCommitEntity nonStrategic = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "Non-strategic");
            nonStrategic.setNonStrategicReason("Admin work");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(nonStrategic));
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertTrue(ctx.outcomeCoverageTrends().isEmpty(),
                    "Non-strategic commits should not appear in outcome trends");
        }
    }

    // ── Late-lock patterns ────────────────────────────────────────────────

    @Nested
    class LateLockPatterns {

        @Test
        void detectsLateLockInWindow() {
            UUID user = userId("henry");
            dashboardService.setTeamSummary(summaryWithUser(user));

            UUID planId1 = UUID.randomUUID();
            UUID planId2 = UUID.randomUUID();
            WeeklyPlanEntity lateLocked = lockedPlan(
                    planId1, user, WEEK_START.minusWeeks(1), LockType.LATE_LOCK);
            WeeklyPlanEntity onTime = lockedPlan(planId2, user, WEEK_START, LockType.ON_TIME);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(lateLocked, onTime));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertEquals(1, ctx.lateLockPatterns().size());
            ManagerInsightDataProvider.LateLockPattern pattern = ctx.lateLockPatterns().get(0);
            assertEquals(user.toString(), pattern.userId());
            assertEquals(1, pattern.lateLockWeeks());
            assertEquals(4, pattern.windowWeeks());
        }

        @Test
        void excludesUsersWithNoLateLocks() {
            UUID user = userId("iris");
            UUID planId = UUID.randomUUID();
            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity onTime = lockedPlan(planId, user, WEEK_START, LockType.ON_TIME);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(onTime));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertTrue(ctx.lateLockPatterns().isEmpty());
        }

        @Test
        void countsMultipleLateLockWeeksForSameUser() {
            UUID user = userId("jack");
            dashboardService.setTeamSummary(summaryWithUser(user));

            WeeklyPlanEntity p1 = lockedPlan(
                    UUID.randomUUID(), user, WEEK_START.minusWeeks(1), LockType.LATE_LOCK);
            WeeklyPlanEntity p2 = lockedPlan(
                    UUID.randomUUID(), user, WEEK_START.minusWeeks(2), LockType.LATE_LOCK);
            WeeklyPlanEntity p3 = lockedPlan(
                    UUID.randomUUID(), user, WEEK_START, LockType.ON_TIME);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(p1, p2, p3));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertEquals(1, ctx.lateLockPatterns().size());
            assertEquals(2, ctx.lateLockPatterns().get(0).lateLockWeeks(),
                    "both late-locked weeks should be counted");
        }
    }

    // ── Review turnaround ─────────────────────────────────────────────────

    @Nested
    class ReviewTurnaround {

        @Test
        void returnsNullTurnaroundWhenNoReviewsExist() {
            UUID user = userId("kate");
            dashboardService.setTeamSummary(summaryWithUser(user));

            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = lockedPlan(planId, user, WEEK_START, LockType.ON_TIME);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertNull(ctx.reviewTurnaroundStats());
        }

        @Test
        void computesFractionalTurnaroundStatsWhenReviewsExist() {
            UUID user = userId("liam");
            dashboardService.setTeamSummary(summaryWithUser(user));

            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = lockedPlan(
                    planId, user, WEEK_START.minusWeeks(1), LockType.ON_TIME);
            Instant lockedAt = Instant.parse("2026-03-02T09:00:00Z");
            ReflectionTestUtils.setField(plan, "lockedAt", lockedAt);

            ManagerReviewEntity review = new ManagerReviewEntity(
                    UUID.randomUUID(), ORG_ID, planId, MANAGER_ID, "APPROVED", "");
            ReflectionTestUtils.setField(review, "createdAt", lockedAt.plusSeconds(36 * 60 * 60));

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(review));

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertNotNull(ctx.reviewTurnaroundStats());
            assertEquals(1.5, ctx.reviewTurnaroundStats().avgDaysToReview(), 0.0001);
            assertEquals(1, ctx.reviewTurnaroundStats().sampleSize());
        }

        @Test
        void includesSameInstantReviewAsZeroDayTurnaround() {
            UUID user = userId("mona");
            dashboardService.setTeamSummary(summaryWithUser(user));

            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = lockedPlan(planId, user, WEEK_START, LockType.ON_TIME);
            Instant lockedAt = Instant.parse("2026-03-09T09:00:00Z");
            ReflectionTestUtils.setField(plan, "lockedAt", lockedAt);

            ManagerReviewEntity review = new ManagerReviewEntity(
                    UUID.randomUUID(), ORG_ID, planId, MANAGER_ID, "APPROVED", "");
            ReflectionTestUtils.setField(review, "createdAt", lockedAt);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    ORG_ID, windowStart(4), WEEK_START))
                    .thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(review));

            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START, 4);

            assertNotNull(ctx.reviewTurnaroundStats());
            assertEquals(0.0, ctx.reviewTurnaroundStats().avgDaysToReview(), 0.0001);
            assertEquals(1, ctx.reviewTurnaroundStats().sampleSize());
        }
    }

    // ── Default-window delegation ─────────────────────────────────────────

    @Nested
    class DefaultWindow {

        @Test
        void defaultMethodDelegatesToFourWeekWindow() {
            UUID user = userId("mia");
            dashboardService.setTeamSummary(summaryWithUser(user));

            when(planRepository.findByOrgIdAndWeekStartDateBetween(any(), any(), any()))
                    .thenReturn(List.of());
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(any(), any()))
                    .thenReturn(List.of());
            when(reviewRepository.findByOrgIdAndWeeklyPlanIdIn(any(), any()))
                    .thenReturn(List.of());

            // Call the default 3-arg method — should delegate to window=4
            ManagerInsightDataProvider.ManagerWeekContext ctx =
                    provider.getManagerWeekContext(ORG_ID, MANAGER_ID, WEEK_START);

            // Historical fields are non-null lists (may be empty when no data)
            assertNotNull(ctx.carryForwardStreaks());
            assertNotNull(ctx.outcomeCoverageTrends());
            assertNotNull(ctx.lateLockPatterns());
        }
    }
}
