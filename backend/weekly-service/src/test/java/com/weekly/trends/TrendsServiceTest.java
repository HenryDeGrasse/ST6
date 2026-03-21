package com.weekly.trends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultTrendsService}: rolling-window aggregations and insight generation.
 */
class TrendsServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private DefaultTrendsService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        service = new DefaultTrendsService(planRepository, commitRepository, actualRepository);

        // Default: no team plans
        when(planRepository.findByOrgIdAndWeekStartDateBetween(
                eq(ORG_ID), any(), any())).thenReturn(List.of());
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private WeeklyPlanEntity makePlan(UUID planId, LocalDate weekStart) {
        return new WeeklyPlanEntity(planId, ORG_ID, USER_ID, weekStart);
    }

    private WeeklyPlanEntity makeReconciledPlan(UUID planId, LocalDate weekStart) {
        WeeklyPlanEntity plan = makePlan(planId, weekStart);
        plan.lock(com.weekly.plan.domain.LockType.ON_TIME);
        plan.startReconciliation();
        plan.submitReconciliation();
        return plan;
    }

    private WeeklyCommitEntity makeCommit(UUID planId, ChessPriority priority) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Task");
        commit.setChessPriority(priority);
        return commit;
    }

    private WeeklyCommitEntity makeStrategicCommit(UUID planId) {
        WeeklyCommitEntity commit = makeCommit(planId, ChessPriority.QUEEN);
        commit.setOutcomeId(UUID.randomUUID());
        return commit;
    }

    private WeeklyCommitEntity makeCarryForwardCommit(UUID planId) {
        WeeklyCommitEntity commit = makeCommit(planId, ChessPriority.ROOK);
        commit.setCarriedFromCommitId(UUID.randomUUID());
        return commit;
    }

    private WeeklyCommitActualEntity makeActual(UUID commitId, CompletionStatus status) {
        WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commitId, ORG_ID);
        actual.setCompletionStatus(status);
        return actual;
    }

    // ─── Empty data ───────────────────────────────────────────────────────────

    @Nested
    class EmptyData {

        @Test
        void returnsZeroMetricsWhenNoPlanData() {
            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 4);

            assertNotNull(response);
            assertEquals(0, response.weeksAnalyzed());
            assertEquals(0.0, response.strategicAlignmentRate());
            assertEquals(0.0, response.teamStrategicAlignmentRate());
            assertEquals(0.0, response.avgCarryForwardPerWeek());
            assertEquals(0, response.carryForwardStreak());
            assertEquals(0.0, response.avgConfidence());
            assertEquals(0.0, response.completionAccuracy());
            assertTrue(response.insights().isEmpty());
        }

        @Test
        void weekPointsContainsOneEntryPerWeekInWindow() {
            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 4);

            assertEquals(4, response.weekPoints().size());
        }
    }

    // ─── Strategic alignment ──────────────────────────────────────────────────

    @Nested
    class StrategicAlignment {

        @Test
        void computesStrategicRateCorrectly() {
            List<WeeklyCommitEntity> commits = List.of(
                    makeStrategicCommit(UUID.randomUUID()),
                    makeStrategicCommit(UUID.randomUUID()),
                    makeCommit(UUID.randomUUID(), ChessPriority.KING)
            );

            double rate = service.computeStrategicRate(commits);

            assertEquals(2.0 / 3.0, rate, 0.001);
        }

        @Test
        void returnsZeroForNoCommits() {
            double rate = service.computeStrategicRate(List.of());

            assertEquals(0.0, rate);
        }

        @Test
        void returnsOneWhenAllStrategic() {
            UUID planId = UUID.randomUUID();
            List<WeeklyCommitEntity> commits = List.of(
                    makeStrategicCommit(planId),
                    makeStrategicCommit(planId)
            );

            double rate = service.computeStrategicRate(commits);

            assertEquals(1.0, rate, 0.001);
        }
    }

    // ─── Carry-forward metrics ────────────────────────────────────────────────

    @Nested
    class CarryForwardMetrics {

        @Test
        void computesStreakFromMostRecentWeeks() {
            // Simulate week points: [0 cf, 1 cf, 1 cf, 1 cf] → streak = 3
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 3, 2, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 3, 2, 1, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-19", 3, 2, 2, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-26", 3, 2, 1, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            int streak = service.computeCarryForwardStreak(points);

            assertEquals(3, streak);
        }

        @Test
        void streakBreaksOnNonCarryForwardWeek() {
            // [1 cf, 0 cf, 1 cf] → streak = 1 (only most recent)
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 3, 2, 1, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 3, 2, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-19", 3, 2, 1, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            int streak = service.computeCarryForwardStreak(points);

            assertEquals(1, streak);
        }

        @Test
        void streakIgnoresTrailingWeeksWithoutPlanData() {
            // [1 cf, 1 cf, no plan yet] → streak = 2
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 3, 2, 1, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 3, 2, 1, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-19", 0, 0, 0, 0.0, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            int streak = service.computeCarryForwardStreak(points);

            assertEquals(2, streak);
        }

        @Test
        void avgCarryForwardComputedOverActiveWeeksOnly() {
            // Week with 0 commits is excluded from average
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 0, 0, 0, 0.0, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 4, 2, 2, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-19", 4, 2, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            double avg = service.computeAvgCarryForward(points);

            // Only 2 active weeks: (2 + 0) / 2 = 1.0
            assertEquals(1.0, avg, 0.001);
        }
    }

    // ─── Completion accuracy ──────────────────────────────────────────────────

    @Nested
    class CompletionAccuracy {

        @Test
        void computesCompletionAccuracyAcrossReconciledWeeks() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 4, 2, 0, 0.8, 0.75, true, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 4, 2, 0, 0.8, 1.0, true, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-19", 4, 2, 0, 0.8, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            double accuracy = service.computeCompletionAccuracy(points);

            // Average of reconciled weeks: (0.75 + 1.0) / 2 = 0.875
            assertEquals(0.875, accuracy, 0.001);
        }

        @Test
        void returnsZeroWhenNoReconciledWeeks() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 4, 2, 0, 0.8, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 4, 2, 0, 0.8, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            double accuracy = service.computeCompletionAccuracy(points);

            assertEquals(0.0, accuracy);
        }
    }

    // ─── Priority distribution ────────────────────────────────────────────────

    @Nested
    class PriorityDistribution {

        @Test
        void computesPriorityDistributionCorrectly() {
            UUID planId = UUID.randomUUID();
            List<WeeklyCommitEntity> commits = List.of(
                    makeCommit(planId, ChessPriority.KING),
                    makeCommit(planId, ChessPriority.KING),
                    makeCommit(planId, ChessPriority.QUEEN)
            );

            java.util.Map<String, Double> dist = service.computePriorityDistribution(commits);

            assertEquals(2.0 / 3.0, dist.get("KING"), 0.001);
            assertEquals(1.0 / 3.0, dist.get("QUEEN"), 0.001);
            assertEquals(0.0, dist.get("ROOK"), 0.001);
        }

        @Test
        void allZeroWhenNoCommitsHavePriority() {
            List<WeeklyCommitEntity> commits = List.of(
                    new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, UUID.randomUUID(), "Task")
            );

            java.util.Map<String, Double> dist = service.computePriorityDistribution(commits);

            for (ChessPriority p : ChessPriority.values()) {
                assertEquals(0.0, dist.get(p.name()), 0.001);
            }
        }
    }

    // ─── Category distribution ────────────────────────────────────────────────

    @Nested
    class CategoryDistribution {

        @Test
        void computesCategoryDistributionCorrectly() {
            UUID planId = UUID.randomUUID();
            WeeklyCommitEntity c1 = makeCommit(planId, ChessPriority.KING);
            c1.setCategory(CommitCategory.DELIVERY);
            WeeklyCommitEntity c2 = makeCommit(planId, ChessPriority.QUEEN);
            c2.setCategory(CommitCategory.DELIVERY);
            WeeklyCommitEntity c3 = makeCommit(planId, ChessPriority.ROOK);
            c3.setCategory(CommitCategory.OPERATIONS);

            java.util.Map<String, Double> dist = service.computeCategoryDistribution(List.of(c1, c2, c3));

            assertEquals(2.0 / 3.0, dist.get("DELIVERY"), 0.001);
            assertEquals(1.0 / 3.0, dist.get("OPERATIONS"), 0.001);
            assertEquals(0.0, dist.get("CUSTOMER"), 0.001);
        }
    }

    // ─── computeTrends integration ────────────────────────────────────────────

    @Nested
    class ComputeTrends {

        @Test
        void fullIntegrationWithMixedData() {
            LocalDate monday = currentMonday();
            UUID planId1 = UUID.randomUUID();
            UUID planId2 = UUID.randomUUID();

            WeeklyPlanEntity plan1 = makeReconciledPlan(planId1, monday.minusWeeks(1));
            WeeklyPlanEntity plan2 = makePlan(planId2, monday);

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan1, plan2));

            WeeklyCommitEntity commit1 = makeStrategicCommit(planId1);
            commit1.setConfidence(new BigDecimal("0.8"));
            WeeklyCommitEntity commit2 = makeCarryForwardCommit(planId1);
            WeeklyCommitEntity commit3 = makeStrategicCommit(planId2);
            commit3.setConfidence(new BigDecimal("0.9"));

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of(commit1, commit2, commit3));

            WeeklyCommitActualEntity actual1 = makeActual(commit1.getId(), CompletionStatus.DONE);
            WeeklyCommitActualEntity actual2 = makeActual(commit2.getId(), CompletionStatus.NOT_DONE);

            when(actualRepository.findByOrgIdAndCommitIdIn(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of(actual1, actual2));

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 2);

            assertNotNull(response);
            assertEquals(2, response.weeksAnalyzed());
            // 2 of 3 commits have outcomeId → 0.667
            assertEquals(2.0 / 3.0, response.strategicAlignmentRate(), 0.001);
            // 1 carry-forward commit in 2 active weeks → 0.5
            assertEquals(0.5, response.avgCarryForwardPerWeek(), 0.001);
            // plan2 has carry-forward=0 so streak from most recent = 0
            assertEquals(0, response.carryForwardStreak());
            // avg confidence across commits with confidence set: (0.8 + 0.9) / 2
            assertEquals(0.85, response.avgConfidence(), 0.001);
            // plan1 is reconciled: 1 DONE / 2 total = 0.5
            assertEquals(0.5, response.completionAccuracy(), 0.001);
            assertEquals(2, response.weekPoints().size());
        }

        @Test
        void windowSizeClampedToMaxWeeks() {
            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 999);

            // Window should be clamped to MAX_WEEKS
            assertEquals(DefaultTrendsService.MAX_WEEKS, response.weekPoints().size());
        }

        @Test
        void windowSizeClampedToMinWeeks() {
            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 0);

            // Window should be clamped to MIN_WEEKS
            assertEquals(DefaultTrendsService.MIN_WEEKS, response.weekPoints().size());
        }
    }

    // ─── Insight generation ───────────────────────────────────────────────────

    @Nested
    class InsightGeneration {

        private List<WeekTrendPoint> emptyWeekPoints() {
            return List.of();
        }

        @Test
        void generatesCarryForwardStreakWarning() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 3, 0.7, 0.7, 0.0, emptyWeekPoints());

            assertTrue(insights.stream()
                    .anyMatch(i -> "CARRY_FORWARD_STREAK".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void noCarryForwardInsightForStreakLessThan3() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 2, 0.7, 0.7, 0.0, emptyWeekPoints());

            assertFalse(insights.stream()
                    .anyMatch(i -> "CARRY_FORWARD_STREAK".equals(i.type())));
        }

        @Test
        void generatesConfidenceGapWarning() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 0, 0.9, 0.5, 0.4, emptyWeekPoints());

            assertTrue(insights.stream()
                    .anyMatch(i -> "CONFIDENCE_ACCURACY_GAP".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void noConfidenceGapInsightWhenGapSmall() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 0, 0.7, 0.6, 0.1, emptyWeekPoints());

            assertFalse(insights.stream()
                    .anyMatch(i -> "CONFIDENCE_ACCURACY_GAP".equals(i.type())));
        }

        @Test
        void generatesZeroCategoryStreakWarning() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 3, 1, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 3, 1, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-19", 3, 1, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            List<TrendInsight> insights = service.generateInsights(
                    0.3, 0.5, 0, 0.7, 0.7, 0.0, points);

            assertTrue(insights.stream()
                    .anyMatch(i -> "ZERO_CATEGORY_STREAK".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void generatesZeroStrategicWeeksWarning() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 3, 0, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 3, 0, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-19", 3, 0, 0, 0.7, 0.0, false, java.util.Map.of(), java.util.Map.of())
            );

            List<TrendInsight> insights = service.generateInsights(
                    0.0, 0.5, 0, 0.7, 0.7, 0.0, points);

            assertTrue(insights.stream()
                    .anyMatch(i -> "ZERO_STRATEGIC_WEEKS".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void generatesBelowTeamAverageWarning() {
            // User at 30%, team at 60% → gap 30 pp > 20 pp threshold
            List<TrendInsight> insights = service.generateInsights(
                    0.30, 0.60, 0, 0.7, 0.7, 0.0, emptyWeekPoints());

            assertTrue(insights.stream()
                    .anyMatch(i -> "BELOW_TEAM_STRATEGIC_AVERAGE".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void generatesHighCompletionRatePositive() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 4, 2, 0, 0.9, 0.90, true, java.util.Map.of(), java.util.Map.of())
            );

            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 0, 0.9, 0.90, 0.0, points);

            assertTrue(insights.stream()
                    .anyMatch(i -> "HIGH_COMPLETION_RATE".equals(i.type())
                            && "POSITIVE".equals(i.severity())));
        }

        @Test
        void generatesHighStrategicAlignmentPositive() {
            List<TrendInsight> insights = service.generateInsights(
                    0.85, 0.60, 0, 0.7, 0.7, 0.0, emptyWeekPoints());

            assertTrue(insights.stream()
                    .anyMatch(i -> "HIGH_STRATEGIC_ALIGNMENT".equals(i.type())
                            && "POSITIVE".equals(i.severity())));
        }

        @Test
        void noInsightsForNeutralData() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 3, 1, 0, 0.7, 0.70, true, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 3, 1, 0, 0.7, 0.70, true, java.util.Map.of(), java.util.Map.of())
            );

            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 1, 0.7, 0.70, 0.0, points);

            // streak=1 (<3), gap=0.0 (<0.2), strategicRate=0.5 (<0.8),
            // teamDiff=0 (<0.2), completionAccuracy=0.70 (<0.85)
            // Expect no insights
            assertTrue(insights.isEmpty(),
                    "Expected no insights for neutral data but got: " + insights);
        }
    }
}
