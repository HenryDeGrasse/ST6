package com.weekly.trends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.capacity.CapacityProfileEntity;
import com.weekly.capacity.CapacityProfileService;
import com.weekly.issues.domain.EffortType;
import com.weekly.issues.repository.IssueRepository;
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
    private CapacityProfileService capacityProfileService;
    private IssueRepository issueRepository;
    private DefaultTrendsService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        capacityProfileService = mock(CapacityProfileService.class);
        issueRepository = mock(IssueRepository.class);
        service = new DefaultTrendsService(
                planRepository,
                commitRepository,
                actualRepository,
                capacityProfileService,
                issueRepository);

        // Default: no team plans or saved capacity profile
        when(planRepository.findByOrgIdAndWeekStartDateBetween(
                eq(ORG_ID), any(), any())).thenReturn(List.of());
        when(capacityProfileService.getProfile(eq(ORG_ID), eq(USER_ID))).thenReturn(java.util.Optional.empty());
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

    private CapacityProfileEntity makeCapacityProfile(String estimationBias, String realisticWeeklyCap) {
        CapacityProfileEntity profile = new CapacityProfileEntity(ORG_ID, USER_ID);
        if (estimationBias != null) {
            profile.setEstimationBias(new BigDecimal(estimationBias));
        }
        if (realisticWeeklyCap != null) {
            profile.setRealisticWeeklyCap(new BigDecimal(realisticWeeklyCap));
        }
        profile.setConfidenceLevel("MEDIUM");
        return profile;
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

    // ─── Hours metrics ────────────────────────────────────────────────────────

    @Nested
    class HoursMetrics {

        @Test
        void hoursAccuracyRatioUsesOnlyWeeksWithBothEstimateAndActualData() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint(
                            "2026-01-05", 3, 1, 0, 0.7, 0.0, false,
                            java.util.Map.of(), java.util.Map.of(),
                            10.0, null, null),
                    new WeekTrendPoint(
                            "2026-01-12", 3, 1, 0, 0.7, 0.0, true,
                            java.util.Map.of(), java.util.Map.of(),
                            8.0, 12.0, 1.5),
                    new WeekTrendPoint(
                            "2026-01-19", 3, 1, 0, 0.7, 0.0, true,
                            java.util.Map.of(), java.util.Map.of(),
                            null, 5.0, null)
            );

            Double ratio = service.computeHoursAccuracyRatio(points);

            assertEquals(12.0 / 8.0, ratio, 0.001);
        }

        @Test
        void hoursAccuracyRatioReturnsNullWhenNoComparableWeeksExist() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint(
                            "2026-01-05", 3, 1, 0, 0.7, 0.0, false,
                            java.util.Map.of(), java.util.Map.of(),
                            10.0, null, null),
                    new WeekTrendPoint(
                            "2026-01-12", 3, 1, 0, 0.7, 0.0, true,
                            java.util.Map.of(), java.util.Map.of(),
                            null, 5.0, null)
            );

            assertNull(service.computeHoursAccuracyRatio(points));
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
            commit1.setEstimatedHours(new BigDecimal("4.0"));
            WeeklyCommitEntity commit2 = makeCarryForwardCommit(planId1);
            commit2.setEstimatedHours(new BigDecimal("2.0"));
            WeeklyCommitEntity commit3 = makeStrategicCommit(planId2);
            commit3.setConfidence(new BigDecimal("0.9"));
            commit3.setEstimatedHours(new BigDecimal("3.0"));

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of(commit1, commit2, commit3));

            WeeklyCommitActualEntity actual1 = makeActual(commit1.getId(), CompletionStatus.DONE);
            actual1.setActualHours(new BigDecimal("5.0"));
            WeeklyCommitActualEntity actual2 = makeActual(commit2.getId(), CompletionStatus.NOT_DONE);
            actual2.setActualHours(new BigDecimal("1.5"));

            when(actualRepository.findByOrgIdAndCommitIdIn(
                    eq(ORG_ID), any()))
                    .thenReturn(List.of(actual1, actual2));
            when(capacityProfileService.getProfile(eq(ORG_ID), eq(USER_ID)))
                    .thenReturn(java.util.Optional.of(makeCapacityProfile("1.18", "30.0")));

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
            assertEquals(4.5, response.avgEstimatedHoursPerWeek(), 0.001);
            assertEquals(6.5, response.avgActualHoursPerWeek(), 0.001);
            assertEquals(6.5 / 6.0, response.hoursAccuracyRatio(), 0.001);
            assertEquals(2, response.weekPoints().size());
            assertTrue(response.insights().stream().anyMatch(i -> "CAPACITY_PROFILE_SUMMARY".equals(i.type())));
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
                    0.5, 0.5, 3, 0.7, 0.7, 0.0, null, emptyWeekPoints(), java.util.Optional.empty());

            assertTrue(insights.stream()
                    .anyMatch(i -> "CARRY_FORWARD_STREAK".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void noCarryForwardInsightForStreakLessThan3() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 2, 0.7, 0.7, 0.0, null, emptyWeekPoints(), java.util.Optional.empty());

            assertFalse(insights.stream()
                    .anyMatch(i -> "CARRY_FORWARD_STREAK".equals(i.type())));
        }

        @Test
        void generatesConfidenceGapWarning() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 0, 0.9, 0.5, 0.4, null, emptyWeekPoints(), java.util.Optional.empty());

            assertTrue(insights.stream()
                    .anyMatch(i -> "CONFIDENCE_ACCURACY_GAP".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void noConfidenceGapInsightWhenGapSmall() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 0, 0.7, 0.6, 0.1, null, emptyWeekPoints(), java.util.Optional.empty());

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
                    0.3, 0.5, 0, 0.7, 0.7, 0.0, null, points, java.util.Optional.empty());

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
                    0.0, 0.5, 0, 0.7, 0.7, 0.0, null, points, java.util.Optional.empty());

            assertTrue(insights.stream()
                    .anyMatch(i -> "ZERO_STRATEGIC_WEEKS".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void generatesBelowTeamAverageWarning() {
            // User at 30%, team at 60% → gap 30 pp > 20 pp threshold
            List<TrendInsight> insights = service.generateInsights(
                    0.30, 0.60, 0, 0.7, 0.7, 0.0, null, emptyWeekPoints(), java.util.Optional.empty());

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
                    0.5, 0.5, 0, 0.9, 0.90, 0.0, null, points, java.util.Optional.empty());

            assertTrue(insights.stream()
                    .anyMatch(i -> "HIGH_COMPLETION_RATE".equals(i.type())
                            && "POSITIVE".equals(i.severity())));
        }

        @Test
        void generatesHighStrategicAlignmentPositive() {
            List<TrendInsight> insights = service.generateInsights(
                    0.85, 0.60, 0, 0.7, 0.7, 0.0, null, emptyWeekPoints(), java.util.Optional.empty());

            assertTrue(insights.stream()
                    .anyMatch(i -> "HIGH_STRATEGIC_ALIGNMENT".equals(i.type())
                            && "POSITIVE".equals(i.severity())));
        }

        @Test
        void generatesHoursUnderestimationWarning() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 0, 0.7, 0.7, 0.0, 1.25, emptyWeekPoints(), java.util.Optional.empty());

            assertTrue(insights.stream()
                    .anyMatch(i -> "HOURS_UNDERESTIMATION".equals(i.type())
                            && "WARNING".equals(i.severity())));
        }

        @Test
        void generatesCapacityProfileSummaryInsight() {
            List<TrendInsight> insights = service.generateInsights(
                    0.5,
                    0.5,
                    0,
                    0.7,
                    0.7,
                    0.0,
                    null,
                    emptyWeekPoints(),
                    java.util.Optional.of(makeCapacityProfile("1.10", "32.0")));

            assertTrue(insights.stream()
                    .anyMatch(i -> "CAPACITY_PROFILE_SUMMARY".equals(i.type())
                            && "INFO".equals(i.severity())
                            && i.message().contains("32.0 hours")));
        }

        @Test
        void noInsightsForNeutralData() {
            List<WeekTrendPoint> points = List.of(
                    new WeekTrendPoint("2026-01-05", 3, 1, 0, 0.7, 0.70, true, java.util.Map.of(), java.util.Map.of()),
                    new WeekTrendPoint("2026-01-12", 3, 1, 0, 0.7, 0.70, true, java.util.Map.of(), java.util.Map.of())
            );

            List<TrendInsight> insights = service.generateInsights(
                    0.5, 0.5, 1, 0.7, 0.70, 0.0, null, points, java.util.Optional.empty());

            // streak=1 (<3), gap=0.0 (<0.2), strategicRate=0.5 (<0.8),
            // teamDiff=0 (<0.2), completionAccuracy=0.70 (<0.85)
            // Expect no insights
            assertTrue(insights.isEmpty(),
                    "Expected no insights for neutral data but got: " + insights);
        }
    }

    // ─── EffortType distribution ──────────────────────────────────────────────

    @Nested
    class EffortTypeDistribution {

        private WeeklyCommitEntity makeCommitWithCategory(UUID planId, CommitCategory category) {
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "Task");
            commit.setCategory(category);
            return commit;
        }

        @Test
        void computesEffortTypeDistributionCorrectly() {
            UUID planId = UUID.randomUUID();
            // DELIVERY → BUILD (×2), OPERATIONS → MAINTAIN (×1)
            List<WeeklyCommitEntity> commits = List.of(
                    makeCommitWithCategory(planId, CommitCategory.DELIVERY),
                    makeCommitWithCategory(planId, CommitCategory.DELIVERY),
                    makeCommitWithCategory(planId, CommitCategory.OPERATIONS)
            );

            java.util.Map<String, Double> dist = service.computeEffortTypeDistribution(
                    commits, java.util.Map.of());

            assertEquals(2.0 / 3.0, dist.get(EffortType.BUILD.name()), 0.001);
            assertEquals(1.0 / 3.0, dist.get(EffortType.MAINTAIN.name()), 0.001);
            assertEquals(0.0, dist.get(EffortType.COLLABORATE.name()), 0.001);
            assertEquals(0.0, dist.get(EffortType.LEARN.name()), 0.001);
        }

        @Test
        void allZeroWhenNoCommitsHaveCategory() {
            UUID planId = UUID.randomUUID();
            List<WeeklyCommitEntity> commits = List.of(
                    new WeeklyCommitEntity(UUID.randomUUID(), ORG_ID, planId, "Task")
            );

            java.util.Map<String, Double> dist = service.computeEffortTypeDistribution(
                    commits, java.util.Map.of());

            for (EffortType et : EffortType.values()) {
                assertEquals(0.0, dist.get(et.name()), 0.001, "Expected 0.0 for " + et);
            }
        }

        @Test
        void allZeroForEmptyCommitList() {
            java.util.Map<String, Double> dist = service.computeEffortTypeDistribution(
                    List.of(), java.util.Map.of());

            for (EffortType et : EffortType.values()) {
                assertEquals(0.0, dist.get(et.name()), 0.001, "Expected 0.0 for " + et);
            }
        }

        @Test
        void gtmAlsoMapsToBuild() {
            UUID planId = UUID.randomUUID();
            List<WeeklyCommitEntity> commits = List.of(
                    makeCommitWithCategory(planId, CommitCategory.GTM)
            );

            java.util.Map<String, Double> dist = service.computeEffortTypeDistribution(
                    commits, java.util.Map.of());

            assertEquals(1.0, dist.get(EffortType.BUILD.name()), 0.001);
        }

        @Test
        void crosswalkIssueEffortTypeOverridesCommitCategoryMapping() {
            UUID planId = UUID.randomUUID();
            UUID issueId = UUID.randomUUID();
            // Commit has DELIVERY (→ BUILD via mapper), but the linked issue says LEARN
            WeeklyCommitEntity commit = makeCommitWithCategory(planId, CommitCategory.DELIVERY);
            commit.setSourceIssueId(issueId);
            List<WeeklyCommitEntity> commits = List.of(commit);

            // Issue effort type map: the linked issue explicitly says LEARN
            java.util.Map<UUID, com.weekly.issues.domain.EffortType> issueEffortTypes =
                    java.util.Map.of(issueId, com.weekly.issues.domain.EffortType.LEARN);

            java.util.Map<String, Double> dist = service.computeEffortTypeDistribution(
                    commits, issueEffortTypes);

            // Crosswalk wins: LEARN should be 1.0, BUILD 0.0
            assertEquals(1.0, dist.get(EffortType.LEARN.name()), 0.001,
                    "Crosswalk issue effort type (LEARN) should override category-based mapping (BUILD)");
            assertEquals(0.0, dist.get(EffortType.BUILD.name()), 0.001,
                    "Category-based BUILD should be 0.0 when crosswalk overrides");
        }

        @Test
        void fallsBackToCategoryMappingWhenIssueHasNoEffortType() {
            UUID planId = UUID.randomUUID();
            UUID issueId = UUID.randomUUID();
            // Commit has DELIVERY → BUILD, linked issue has no effort type in the map
            WeeklyCommitEntity commit = makeCommitWithCategory(planId, CommitCategory.DELIVERY);
            commit.setSourceIssueId(issueId);
            List<WeeklyCommitEntity> commits = List.of(commit);

            // Issue map is empty (issue has no effort type set)
            java.util.Map<String, Double> dist = service.computeEffortTypeDistribution(
                    commits, java.util.Map.of());

            // Category fallback: DELIVERY → BUILD
            assertEquals(1.0, dist.get(EffortType.BUILD.name()), 0.001,
                    "Should fall back to category-based BUILD when issue has no effort type");
        }

        @Test
        void computeTrendsResponseContainsEffortTypeDistribution() {
            LocalDate monday = currentMonday();
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(planId, monday);

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit1 = makeCommitWithCategory(planId, CommitCategory.DELIVERY);
            WeeklyCommitEntity commit2 = makeCommitWithCategory(planId, CommitCategory.LEARNING);

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit1, commit2));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 1);

            assertNotNull(response.effortTypeDistribution());
            assertEquals(0.5, response.effortTypeDistribution().get(EffortType.BUILD.name()), 0.001);
            assertEquals(0.0, response.effortTypeDistribution().get(EffortType.MAINTAIN.name()), 0.001);
            assertEquals(0.0, response.effortTypeDistribution().get(EffortType.COLLABORATE.name()), 0.001);
            assertEquals(0.5, response.effortTypeDistribution().get(EffortType.LEARN.name()), 0.001);
        }

        @Test
        void weekPointContainsEffortTypeCounts() {
            LocalDate monday = currentMonday();
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(planId, monday);

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit1 = makeCommitWithCategory(planId, CommitCategory.TECH_DEBT);
            WeeklyCommitEntity commit2 = makeCommitWithCategory(planId, CommitCategory.PEOPLE);

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit1, commit2));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 1);

            WeekTrendPoint point = response.weekPoints().get(0);
            assertNotNull(point.effortTypeCounts());
            // TECH_DEBT → MAINTAIN, PEOPLE → COLLABORATE
            assertEquals(1, point.effortTypeCounts().getOrDefault(EffortType.MAINTAIN.name(), 0));
            assertEquals(1, point.effortTypeCounts().getOrDefault(EffortType.COLLABORATE.name(), 0));
            assertEquals(0, point.effortTypeCounts().getOrDefault(EffortType.BUILD.name(), 0));
            assertEquals(0, point.effortTypeCounts().getOrDefault(EffortType.LEARN.name(), 0));
        }

        @Test
        void weekPointCrosswalkIssueEffortTypeOverridesCommitCategoryMapping() {
            LocalDate monday = currentMonday();
            UUID planId = UUID.randomUUID();
            UUID issueId = UUID.randomUUID();
            WeeklyPlanEntity plan = makePlan(planId, monday);

            when(planRepository
                    .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                            eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit = makeCommitWithCategory(planId, CommitCategory.DELIVERY);
            commit.setSourceIssueId(issueId);

            com.weekly.issues.domain.IssueEntity issue = new com.weekly.issues.domain.IssueEntity(
                    issueId, ORG_ID, UUID.randomUUID(), "ISS-1", 1, "Investigate spike", USER_ID);
            issue.setEffortType(EffortType.LEARN);

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());
            when(issueRepository.findAllByOrgIdAndIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(issue));

            TrendsResponse response = service.computeTrends(ORG_ID, USER_ID, 1);

            WeekTrendPoint point = response.weekPoints().get(0);
            assertEquals(1, point.effortTypeCounts().getOrDefault(EffortType.LEARN.name(), 0));
            assertEquals(0, point.effortTypeCounts().getOrDefault(EffortType.BUILD.name(), 0));
        }
    }
}
