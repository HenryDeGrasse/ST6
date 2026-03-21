package com.weekly.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.ai.AiCacheService;
import com.weekly.ai.AiSuggestionFeedbackEntity;
import com.weekly.ai.AiSuggestionFeedbackRepository;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.ReviewStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultAdminDashboardService}: aggregate metric computations.
 */
class AdminDashboardServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private AiSuggestionFeedbackRepository feedbackRepository;
    private AiCacheService aiCacheService;
    private InMemoryRcdoClient rcdoClient;
    private DefaultAdminDashboardService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        feedbackRepository = mock(AiSuggestionFeedbackRepository.class);
        aiCacheService = new AiCacheService(Duration.ofHours(1));
        rcdoClient = new InMemoryRcdoClient();
        service = new DefaultAdminDashboardService(
                planRepository, commitRepository,
                feedbackRepository, aiCacheService, rcdoClient);

        // Default: no plans
        when(planRepository.findByOrgIdAndWeekStartDateBetween(
                eq(ORG_ID), any(), any())).thenReturn(List.of());
        // Default: no feedback
        when(feedbackRepository.findByOrgIdAndCreatedAtAfter(
                eq(ORG_ID), any())).thenReturn(List.of());
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private WeeklyPlanEntity makeDraftPlan(UUID userId, LocalDate weekStart) {
        return new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, userId, weekStart);
    }

    private WeeklyPlanEntity makeLockedPlan(UUID userId, LocalDate weekStart) {
        WeeklyPlanEntity plan = makeDraftPlan(userId, weekStart);
        plan.lock(LockType.ON_TIME);
        return plan;
    }

    private WeeklyPlanEntity makeLateLockPlan(UUID userId, LocalDate weekStart) {
        WeeklyPlanEntity plan = makeDraftPlan(userId, weekStart);
        plan.lock(LockType.LATE_LOCK);
        return plan;
    }

    private WeeklyPlanEntity makeReconciledPlan(UUID userId, LocalDate weekStart) {
        WeeklyPlanEntity plan = makeLockedPlan(userId, weekStart);
        plan.startReconciliation();
        plan.submitReconciliation();
        return plan;
    }

    private WeeklyPlanEntity makeReviewedPlan(UUID userId, LocalDate weekStart) {
        WeeklyPlanEntity plan = makeReconciledPlan(userId, weekStart);
        plan.setReviewStatus(ReviewStatus.APPROVED);
        return plan;
    }

    // ── Adoption Metrics ──────────────────────────────────────────────────────

    @Nested
    class AdoptionMetricsTests {

        @Test
        void emptyDataReturnsZeroMetrics() {
            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 4);

            assertEquals(4, result.weeks());
            assertEquals(0, result.totalActiveUsers());
            assertEquals(0.0, result.cadenceComplianceRate());
            assertEquals(4, result.weeklyPoints().size());
            result.weeklyPoints().forEach(p -> {
                assertEquals(0, p.activeUsers());
                assertEquals(0, p.plansCreated());
                assertEquals(0, p.plansLocked());
                assertEquals(0, p.plansReconciled());
                assertEquals(0, p.plansReviewed());
            });
        }

        @Test
        void countsDistinctUsersCorrectly() {
            LocalDate monday = currentMonday();
            WeeklyPlanEntity planA = makeDraftPlan(USER_A, monday);
            WeeklyPlanEntity planB = makeDraftPlan(USER_B, monday);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(planA, planB));

            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 1);

            assertEquals(2, result.totalActiveUsers());
            AdoptionMetrics.WeeklyAdoptionPoint point = result.weeklyPoints().get(0);
            assertEquals(2, point.activeUsers());
            assertEquals(2, point.plansCreated());
        }

        @Test
        void funnelCountsReflectPlanStates() {
            LocalDate monday = currentMonday();
            // User A: draft
            WeeklyPlanEntity draft = makeDraftPlan(USER_A, monday);
            // User B: locked (on time)
            WeeklyPlanEntity locked = makeLockedPlan(USER_B, monday);
            // Additional user: reconciled
            UUID userC = UUID.randomUUID();
            WeeklyPlanEntity reconciled = makeReconciledPlan(userC, monday);
            // Another user: reviewed (approved)
            UUID userD = UUID.randomUUID();
            WeeklyPlanEntity reviewed = makeReviewedPlan(userD, monday);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(draft, locked, reconciled, reviewed));

            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 1);

            AdoptionMetrics.WeeklyAdoptionPoint point = result.weeklyPoints().get(0);
            assertEquals(4, point.plansCreated());
            // locked or beyond: locked + reconciled + reviewed = 3
            assertEquals(3, point.plansLocked());
            // reconciled or beyond: reconciled + reviewed = 2
            assertEquals(2, point.plansReconciled());
            // reviewed = approved only = 1
            assertEquals(1, point.plansReviewed());
        }

        @Test
        void cadenceComplianceRateIsCorrect() {
            LocalDate monday = currentMonday();
            WeeklyPlanEntity onTime = makeLockedPlan(USER_A, monday);
            WeeklyPlanEntity late = makeLateLockPlan(USER_B, monday);
            // draft has no lock_type
            UUID userC = UUID.randomUUID();
            WeeklyPlanEntity draft = makeDraftPlan(userC, monday);

            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(onTime, late, draft));

            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 1);

            // 1 ON_TIME / 2 total locked = 0.5
            assertEquals(0.5, result.cadenceComplianceRate(), 0.001);
        }

        @Test
        void cadenceComplianceRateIsZeroWhenNoLockedPlans() {
            LocalDate monday = currentMonday();
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(makeDraftPlan(USER_A, monday)));

            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 1);

            assertEquals(0.0, result.cadenceComplianceRate());
        }

        @Test
        void multiWeekWindowHasCorrectPointCount() {
            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 4);
            assertEquals(4, result.weeklyPoints().size());
        }

        @Test
        void weeklyPointsHaveCorrectDates() {
            LocalDate monday = currentMonday();
            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 3);

            List<AdoptionMetrics.WeeklyAdoptionPoint> points = result.weeklyPoints();
            assertEquals(3, points.size());
            assertEquals(monday.minusWeeks(2).toString(), points.get(0).weekStart());
            assertEquals(monday.minusWeeks(1).toString(), points.get(1).weekStart());
            assertEquals(monday.toString(), points.get(2).weekStart());
        }

        @Test
        void clampedWeeksRespectsMinimum() {
            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 0);
            assertEquals(AdminDashboardService.MIN_WEEKS, result.weeks());
        }

        @Test
        void clampedWeeksRespectsMaximum() {
            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 100);
            assertEquals(AdminDashboardService.MAX_WEEKS, result.weeks());
        }

        @Test
        void changesRequestedCountsAsReviewed() {
            LocalDate monday = currentMonday();
            WeeklyPlanEntity plan = makeReconciledPlan(USER_A, monday);
            plan.setReviewStatus(ReviewStatus.CHANGES_REQUESTED);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(plan));

            AdoptionMetrics result = service.getAdoptionMetrics(ORG_ID, 1);

            assertEquals(1, result.weeklyPoints().get(0).plansReviewed());
        }
    }

    // ── AI Usage Metrics ──────────────────────────────────────────────────────

    @Nested
    class AiUsageMetricsTests {

        @Test
        void emptyFeedbackReturnsZeroRates() {
            AiUsageMetrics result = service.getAiUsageMetrics(ORG_ID, 4);

            assertEquals(0, result.totalFeedbackCount());
            assertEquals(0, result.acceptedCount());
            assertEquals(0, result.deferredCount());
            assertEquals(0, result.declinedCount());
            assertEquals(0.0, result.acceptanceRate());
        }

        @Test
        void acceptanceRateIsCorrect() {
            AiSuggestionFeedbackEntity accept = makeFeedback("ACCEPT");
            AiSuggestionFeedbackEntity defer = makeFeedback("DEFER");
            AiSuggestionFeedbackEntity decline = makeFeedback("DECLINE");
            AiSuggestionFeedbackEntity decline2 = makeFeedback("DECLINE");

            when(feedbackRepository.findByOrgIdAndCreatedAtAfter(eq(ORG_ID), any()))
                    .thenReturn(List.of(accept, defer, decline, decline2));

            AiUsageMetrics result = service.getAiUsageMetrics(ORG_ID, 4);

            assertEquals(4, result.totalFeedbackCount());
            assertEquals(1, result.acceptedCount());
            assertEquals(1, result.deferredCount());
            assertEquals(2, result.declinedCount());
            assertEquals(0.25, result.acceptanceRate(), 0.001);
        }

        @Test
        void cacheHitRateReflectsAiCacheServiceCounters() {
            // Simulate 3 hits and 1 miss for the org under test
            String key = "test-key";
            aiCacheService.put(ORG_ID, key, "value");
            aiCacheService.get(ORG_ID, key, String.class); // hit
            aiCacheService.get(ORG_ID, key, String.class); // hit
            aiCacheService.get(ORG_ID, key, String.class); // hit
            aiCacheService.get(ORG_ID, "missing", String.class); // miss

            AiUsageMetrics result = service.getAiUsageMetrics(ORG_ID, 4);

            assertEquals(3, result.cacheHits());
            assertEquals(1, result.cacheMisses());
            assertEquals(0.75, result.cacheHitRate(), 0.001);
            assertEquals(1_000, result.approximateTokensSpent());
            assertEquals(3_000, result.approximateTokensSaved());
        }

        @Test
        void cacheHitRateIsZeroWhenNoCacheActivity() {
            AiUsageMetrics result = service.getAiUsageMetrics(ORG_ID, 4);

            assertEquals(0, result.cacheHits());
            assertEquals(0, result.cacheMisses());
            assertEquals(0.0, result.cacheHitRate());
            assertEquals(0, result.approximateTokensSpent());
            assertEquals(0, result.approximateTokensSaved());
        }

        @Test
        void isolatesCacheStatsToRequestedOrg() {
            UUID otherOrgId = UUID.randomUUID();
            String key = "shared-key";
            aiCacheService.put(otherOrgId, key, "value");
            aiCacheService.get(otherOrgId, key, String.class);
            aiCacheService.get(otherOrgId, "missing", String.class);

            AiUsageMetrics result = service.getAiUsageMetrics(ORG_ID, 4);

            assertEquals(0, result.cacheHits());
            assertEquals(0, result.cacheMisses());
            assertEquals(0, result.approximateTokensSpent());
            assertEquals(0, result.approximateTokensSaved());
        }

        @Test
        void windowStartAndEndAreSet() {
            AiUsageMetrics result = service.getAiUsageMetrics(ORG_ID, 4);

            assertNotNull(result.windowStart());
            assertNotNull(result.windowEnd());
            assertEquals(4, result.weeks());
        }

        private AiSuggestionFeedbackEntity makeFeedback(String action) {
            return new AiSuggestionFeedbackEntity(
                    UUID.randomUUID(), ORG_ID, USER_A, UUID.randomUUID(),
                    action, null, null, null);
        }
    }

    // ── RCDO Health ───────────────────────────────────────────────────────────

    @Nested
    class RcdoHealthTests {

        private final UUID outcome1 = UUID.randomUUID();
        private final UUID outcome2 = UUID.randomUUID();
        private final UUID outcome3 = UUID.randomUUID();

        @BeforeEach
        void setUpRcdo() {
            rcdoClient.setTree(ORG_ID, new RcdoTree(List.of(
                    new RcdoTree.RallyCry("rc-1", "Grow Revenue", List.of(
                            new RcdoTree.Objective("obj-1", "Expand Market", "rc-1", List.of(
                                    new RcdoTree.Outcome(outcome1.toString(), "Win Enterprise Deals", "obj-1"),
                                    new RcdoTree.Outcome(outcome2.toString(), "Improve Retention", "obj-1")
                            ))
                    )),
                    new RcdoTree.RallyCry("rc-2", "Improve Quality", List.of(
                            new RcdoTree.Objective("obj-2", "Reduce Bugs", "rc-2", List.of(
                                    new RcdoTree.Outcome(outcome3.toString(), "Zero P0 Bugs", "obj-2")
                            ))
                    ))
            )));
        }

        @Test
        void emptyCommitsAllOutcomesAreStale() {
            // No plans, no commits
            RcdoHealthReport report = service.getRcdoHealth(ORG_ID);

            assertEquals(3, report.totalOutcomes());
            assertEquals(0, report.coveredOutcomes());
            assertEquals(0, report.topOutcomes().size());
            assertEquals(3, report.staleOutcomes().size());
        }

        @Test
        void commitsIncreaseCoveredCount() {
            LocalDate monday = currentMonday();
            WeeklyPlanEntity plan = makeLockedPlan(USER_A, monday);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Win deals");
            // Populate snapshot to simulate lock-time outcome assignment
            commit.populateSnapshot(null, null, null, null,
                    outcome1, "Win Enterprise Deals");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of(commit));

            RcdoHealthReport report = service.getRcdoHealth(ORG_ID);

            assertEquals(3, report.totalOutcomes());
            assertEquals(1, report.coveredOutcomes());
            assertEquals(1, report.topOutcomes().size());
            assertEquals(outcome1.toString(), report.topOutcomes().get(0).outcomeId());
            assertEquals(1, report.topOutcomes().get(0).commitCount());
            assertEquals(2, report.staleOutcomes().size());
        }

        @Test
        void topOutcomesAreSortedByCommitCountDescending() {
            LocalDate monday = currentMonday();
            WeeklyPlanEntity plan = makeLockedPlan(USER_A, monday);
            when(planRepository.findByOrgIdAndWeekStartDateBetween(
                    eq(ORG_ID), any(), any()))
                    .thenReturn(List.of(plan));

            // 3 commits for outcome 1, 1 for outcome 2
            List<WeeklyCommitEntity> commits = List.of(
                    makeLockedCommit(plan.getId(), outcome1),
                    makeLockedCommit(plan.getId(), outcome1),
                    makeLockedCommit(plan.getId(), outcome1),
                    makeLockedCommit(plan.getId(), outcome2)
            );
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(commits);

            RcdoHealthReport report = service.getRcdoHealth(ORG_ID);

            assertEquals(2, report.coveredOutcomes());
            assertEquals(2, report.topOutcomes().size());
            assertEquals(3, report.topOutcomes().get(0).commitCount());
            assertEquals(1, report.topOutcomes().get(1).commitCount());
        }

        @Test
        void staleOutcomesHaveZeroCommitCount() {
            RcdoHealthReport report = service.getRcdoHealth(ORG_ID);

            report.staleOutcomes().forEach(item ->
                    assertEquals(0, item.commitCount()));
        }

        @Test
        void emptyRcdoTreeReturnsEmptyReport() {
            rcdoClient.setTree(ORG_ID, new RcdoTree(List.of()));

            RcdoHealthReport report = service.getRcdoHealth(ORG_ID);

            assertEquals(0, report.totalOutcomes());
            assertEquals(0, report.coveredOutcomes());
            assertTrue(report.topOutcomes().isEmpty());
            assertTrue(report.staleOutcomes().isEmpty());
        }

        @Test
        void generatedAtIsRecentTimestamp() {
            RcdoHealthReport report = service.getRcdoHealth(ORG_ID);

            assertNotNull(report.generatedAt());
            Instant reported = Instant.parse(report.generatedAt());
            assertTrue(reported.isAfter(Instant.now().minusSeconds(5)));
        }

        @Test
        void windowWeeksMatchesConstant() {
            RcdoHealthReport report = service.getRcdoHealth(ORG_ID);
            assertEquals(DefaultAdminDashboardService.RCDO_WINDOW_WEEKS, report.windowWeeks());
        }

        private WeeklyCommitEntity makeLockedCommit(UUID planId, UUID outcomeId) {
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "Some task");
            commit.populateSnapshot(null, null, null, null, outcomeId, "outcome");
            return commit;
        }
    }
}
