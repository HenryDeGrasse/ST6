package com.weekly.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.analytics.dto.CarryForwardHeatmap;
import com.weekly.analytics.dto.OutcomeCoverageTimeline;
import com.weekly.analytics.dto.OutcomeCoverageWeek;
import com.weekly.analytics.dto.UserCategoryShift;
import com.weekly.analytics.dto.UserEstimationAccuracy;
import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Unit tests for {@link AnalyticsService}: trend direction computation,
 * week-by-week data assembly with zero-filling, carry-forward heatmap
 * grid building, category shift analysis, and estimation accuracy metrics.
 *
 * <p>Uses an isolated H2 in-memory database per test (PostgreSQL mode) to
 * exercise the full SQL path without a live database.
 */
class AnalyticsServiceTest {

    private static final UUID ORG_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OUTCOME_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID USER_1_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID USER_2_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000005");

    private JdbcTemplate jdbcTemplate;
    private OrgGraphClient orgGraphClient;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:analytics-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        orgGraphClient = mock(OrgGraphClient.class);
        createSchema();
        service = new AnalyticsService(jdbcTemplate, orgGraphClient);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE mv_outcome_coverage_weekly (
                    org_id UUID NOT NULL,
                    outcome_id UUID NOT NULL,
                    week_start_date DATE NOT NULL,
                    commit_count INT,
                    contributor_count INT,
                    high_priority_count INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE mv_user_weekly_summary (
                    org_id UUID NOT NULL,
                    owner_user_id UUID NOT NULL,
                    week_start_date DATE NOT NULL,
                    carried_commits INT,
                    avg_confidence DOUBLE PRECISION,
                    done_count INT,
                    total_commits INT,
                    reconciled_count INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE weekly_plans (
                    id UUID NOT NULL,
                    org_id UUID NOT NULL,
                    owner_user_id UUID NOT NULL,
                    week_start_date DATE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE weekly_commits (
                    org_id UUID NOT NULL,
                    weekly_plan_id UUID NOT NULL,
                    category VARCHAR(100)
                )
                """);
    }

    // ── computeTrendDirection ─────────────────────────────────────────────────

    @Nested
    class ComputeTrendDirection {

        @Test
        void risingWhenLastWeekExceedsPreviousWeek() {
            List<OutcomeCoverageWeek> weeks = List.of(
                    week("2026-01-05", 2),
                    week("2026-01-12", 4),
                    week("2026-01-19", 3),
                    week("2026-01-26", 6)
            );
            assertEquals(AnalyticsService.TREND_RISING,
                    service.computeTrendDirection(weeks));
        }

        @Test
        void fallingWhenLastWeekBelowPreviousWeek() {
            List<OutcomeCoverageWeek> weeks = List.of(
                    week("2026-01-05", 6),
                    week("2026-01-12", 5),
                    week("2026-01-19", 4),
                    week("2026-01-26", 3)
            );
            assertEquals(AnalyticsService.TREND_FALLING,
                    service.computeTrendDirection(weeks));
        }

        @Test
        void stableWhenLastTwoWeeksAreEqual() {
            List<OutcomeCoverageWeek> weeks = List.of(
                    week("2026-01-05", 3),
                    week("2026-01-12", 5),
                    week("2026-01-19", 4),
                    week("2026-01-26", 4)
            );
            assertEquals(AnalyticsService.TREND_STABLE,
                    service.computeTrendDirection(weeks));
        }

        @Test
        void stableForSingleWeek() {
            List<OutcomeCoverageWeek> single = List.of(week("2026-01-05", 5));
            assertEquals(AnalyticsService.TREND_STABLE,
                    service.computeTrendDirection(single));
        }

        @Test
        void stableForEmptyList() {
            assertEquals(AnalyticsService.TREND_STABLE,
                    service.computeTrendDirection(List.of()));
        }

        private OutcomeCoverageWeek week(String date, int commitCount) {
            return new OutcomeCoverageWeek(date, commitCount, 1, 0);
        }
    }

    // ── getOutcomeCoverageTimeline ─────────────────────────────────────────────

    @Nested
    class OutcomeCoverageTimelineTests {

        @Test
        void returnsWeekByWeekDataWithZeroFillForMissingWeeks() {
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            // Insert weeks at -3w, -1w, and 0w; leave -2w missing → zero-fill
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd.minusWeeks(3), 2, 1, 0);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd.minusWeeks(1), 4, 2, 1);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd, 6, 3, 2);

            OutcomeCoverageTimeline timeline =
                    service.getOutcomeCoverageTimeline(ORG_ID, OUTCOME_ID, 4);

            assertEquals(4, timeline.weeks().size());
            assertEquals(2, timeline.weeks().get(0).commitCount());  // -3w
            assertEquals(0, timeline.weeks().get(1).commitCount());  // -2w zero-filled
            assertEquals(4, timeline.weeks().get(2).commitCount());  // -1w
            assertEquals(6, timeline.weeks().get(3).commitCount());  // current week
            assertEquals(AnalyticsService.TREND_RISING, timeline.trendDirection()); // 6>4
        }

        @Test
        void returnsRisingTrendWhenLastWeekExceedsPrevious() {
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd.minusWeeks(1), 3, 2, 0);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd, 7, 4, 1);

            OutcomeCoverageTimeline timeline =
                    service.getOutcomeCoverageTimeline(ORG_ID, OUTCOME_ID, 2);

            assertEquals(AnalyticsService.TREND_RISING, timeline.trendDirection());
        }

        @Test
        void returnsFallingTrendWhenLastWeekDeclines() {
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd.minusWeeks(1), 8, 4, 2);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd, 3, 2, 1);

            OutcomeCoverageTimeline timeline =
                    service.getOutcomeCoverageTimeline(ORG_ID, OUTCOME_ID, 2);

            assertEquals(AnalyticsService.TREND_FALLING, timeline.trendDirection());
        }

        @Test
        void returnsStableTrendWhenLastTwoWeeksHaveEqualCounts() {
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd.minusWeeks(1), 5, 3, 1);
            insertCoverage(ORG_ID, OUTCOME_ID, weekEnd, 5, 3, 1);

            OutcomeCoverageTimeline timeline =
                    service.getOutcomeCoverageTimeline(ORG_ID, OUTCOME_ID, 2);

            assertEquals(AnalyticsService.TREND_STABLE, timeline.trendDirection());
        }

        @Test
        void returnsAllZeroWeeksForUnknownOutcomeId() {
            UUID unknownOutcomeId = UUID.randomUUID();

            OutcomeCoverageTimeline timeline =
                    service.getOutcomeCoverageTimeline(ORG_ID, unknownOutcomeId, 4);

            assertEquals(4, timeline.weeks().size());
            assertTrue(timeline.weeks().stream().allMatch(w -> w.commitCount() == 0));
            assertEquals(AnalyticsService.TREND_STABLE, timeline.trendDirection());
        }
    }

    // ── getTeamCarryForwardHeatmap ─────────────────────────────────────────────

    @Nested
    class TeamCarryForwardHeatmapTests {

        @Test
        void returnsMatrixWithCorrectUserAndWeekStructure() {
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            LocalDate w1 = weekEnd.minusWeeks(1);
            LocalDate w0 = weekEnd;

            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of(
                            new DirectReport(USER_1_ID, "Alice"),
                            new DirectReport(USER_2_ID, "Bob")
                    ));

            // USER_1: both weeks present
            insertCarryForward(ORG_ID, USER_1_ID, w1, 3);
            insertCarryForward(ORG_ID, USER_1_ID, w0, 1);
            // USER_2: only w1 present; w0 must be zero-filled
            insertCarryForward(ORG_ID, USER_2_ID, w1, 2);

            CarryForwardHeatmap heatmap =
                    service.getTeamCarryForwardHeatmap(ORG_ID, MANAGER_ID, 2);

            assertEquals(2, heatmap.users().size());

            var user1 = heatmap.users().stream()
                    .filter(u -> u.userId().equals(USER_1_ID.toString()))
                    .findFirst().orElseThrow();
            assertEquals("Alice", user1.displayName());
            assertEquals(2, user1.weekCells().size());
            assertEquals(3, user1.weekCells().get(0).carriedCount()); // w1
            assertEquals(1, user1.weekCells().get(1).carriedCount()); // w0

            var user2 = heatmap.users().stream()
                    .filter(u -> u.userId().equals(USER_2_ID.toString()))
                    .findFirst().orElseThrow();
            assertEquals("Bob", user2.displayName());
            assertEquals(2, user2.weekCells().size());
            assertEquals(2, user2.weekCells().get(0).carriedCount()); // w1
            assertEquals(0, user2.weekCells().get(1).carriedCount()); // w0 zero-filled
        }

        @Test
        void zeroFillsAllWeeksWhenDirectReportsHaveNoSummaryRows() {
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of(
                            new DirectReport(USER_1_ID, "Alice"),
                            new DirectReport(USER_2_ID, "Bob")
                    ));

            CarryForwardHeatmap heatmap =
                    service.getTeamCarryForwardHeatmap(ORG_ID, MANAGER_ID, 3);

            assertEquals(2, heatmap.users().size());
            assertTrue(heatmap.users().stream().allMatch(user -> user.weekCells().size() == 3));
            assertTrue(heatmap.users().stream()
                    .flatMap(user -> user.weekCells().stream())
                    .allMatch(cell -> cell.carriedCount() == 0));
        }

        @Test
        void returnsEmptyHeatmapWhenManagerHasNoDirectReports() {
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of());

            CarryForwardHeatmap heatmap =
                    service.getTeamCarryForwardHeatmap(ORG_ID, MANAGER_ID, 4);

            assertTrue(heatmap.users().isEmpty());
        }
    }

    // ── getCategoryShiftAnalysis ───────────────────────────────────────────────

    @Nested
    class CategoryShiftAnalysisTests {

        @Test
        void computesDistributionChangeBetweenPriorAndRecentHalf() {
            // 4-week window splits into two halves of 2 weeks each:
            //   prior:  [weekEnd-3w, weekEnd-2w)  (weeks -3w and -2w)
            //   recent: [weekEnd-1w, weekEnd+1w)  (weeks -1w and 0w = current)
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            LocalDate w3 = weekEnd.minusWeeks(3);
            LocalDate w2 = weekEnd.minusWeeks(2);
            LocalDate w1 = weekEnd.minusWeeks(1);
            LocalDate w0 = weekEnd;

            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of(new DirectReport(USER_1_ID, "Alice")));

            // Prior half: 4 DELIVERY (w3) + 1 OPERATIONS (w2)
            //   → DELIVERY=0.8, OPERATIONS=0.2
            UUID plan1 = insertPlan(ORG_ID, USER_1_ID, w3);
            insertCommits(ORG_ID, plan1, "DELIVERY", 4);
            UUID plan2 = insertPlan(ORG_ID, USER_1_ID, w2);
            insertCommits(ORG_ID, plan2, "OPERATIONS", 1);

            // Recent half: 1 DELIVERY (w1) + 3 MANAGEMENT (w0)
            //   → DELIVERY=0.25, MANAGEMENT=0.75
            UUID plan3 = insertPlan(ORG_ID, USER_1_ID, w1);
            insertCommits(ORG_ID, plan3, "DELIVERY", 1);
            UUID plan4 = insertPlan(ORG_ID, USER_1_ID, w0);
            insertCommits(ORG_ID, plan4, "MANAGEMENT", 3);

            List<UserCategoryShift> shifts =
                    service.getCategoryShiftAnalysis(ORG_ID, MANAGER_ID, 4);

            assertEquals(1, shifts.size());
            UserCategoryShift shift = shifts.get(0);
            assertEquals(USER_1_ID.toString(), shift.userId());

            // Current (recent) distribution
            assertEquals(0.25, shift.currentDistribution().get("DELIVERY"), 0.001);
            assertEquals(0.75, shift.currentDistribution().get("MANAGEMENT"), 0.001);

            // Prior distribution
            assertEquals(0.8, shift.priorDistribution().get("DELIVERY"), 0.001);
            assertEquals(0.2, shift.priorDistribution().get("OPERATIONS"), 0.001);

            // Biggest shift: MANAGEMENT gained +0.75 (0→0.75) which beats
            // DELIVERY's -0.55 (0.8→0.25) and OPERATIONS' -0.2 (0.2→0)
            assertNotNull(shift.biggestShift());
            assertEquals("MANAGEMENT", shift.biggestShift().category());
            assertEquals(0.75, shift.biggestShift().delta(), 0.001);
        }

        @Test
        void returnsEmptyListWhenManagerHasNoDirectReports() {
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of());

            List<UserCategoryShift> shifts =
                    service.getCategoryShiftAnalysis(ORG_ID, MANAGER_ID, 4);

            assertTrue(shifts.isEmpty());
        }

        @Test
        void excludesUsersWithNoCommitsInEitherHalf() {
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of(new DirectReport(USER_1_ID, "Alice")));
            // No commits inserted for USER_1

            List<UserCategoryShift> shifts =
                    service.getCategoryShiftAnalysis(ORG_ID, MANAGER_ID, 4);

            assertTrue(shifts.isEmpty());
        }
    }

    // ── getEstimationAccuracyDistribution ─────────────────────────────────────

    @Nested
    class EstimationAccuracyDistributionTests {

        @Test
        void computesCalibrationGapFromReconciledWeeks() {
            // Two reconciled weeks:
            //   w1: avg_confidence=0.9, done=8, total=10
            //   w0: avg_confidence=0.7, done=6, total=10
            // AVG(avg_confidence) = 0.8
            // completionRate = SUM(done)/SUM(total) = 14/20 = 0.7
            // calibrationGap = 0.8 - 0.7 = 0.1
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of(new DirectReport(USER_1_ID, "Alice")));

            insertEstimationRow(ORG_ID, USER_1_ID, weekEnd.minusWeeks(1), 0.9, 8, 10, 1);
            insertEstimationRow(ORG_ID, USER_1_ID, weekEnd, 0.7, 6, 10, 1);

            List<UserEstimationAccuracy> accuracies =
                    service.getEstimationAccuracyDistribution(ORG_ID, MANAGER_ID, 4);

            assertEquals(1, accuracies.size());
            UserEstimationAccuracy accuracy = accuracies.get(0);
            assertEquals(USER_1_ID.toString(), accuracy.userId());
            assertEquals(0.8, accuracy.avgConfidence(), 0.001);
            assertEquals(0.7, accuracy.completionRate(), 0.001);
            assertEquals(0.1, accuracy.calibrationGap(), 0.001);
        }

        @Test
        void excludesNonReconciledWeeks() {
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of(new DirectReport(USER_1_ID, "Alice")));

            // reconciled_count=0 → must be excluded from the query
            insertEstimationRow(ORG_ID, USER_1_ID, weekEnd.minusWeeks(1), 0.9, 8, 10, 0);

            List<UserEstimationAccuracy> accuracies =
                    service.getEstimationAccuracyDistribution(ORG_ID, MANAGER_ID, 4);

            assertTrue(accuracies.isEmpty());
        }

        @Test
        void setsCompletionRateToZeroWhenTotalCommitsIsZero() {
            LocalDate weekEnd = LocalDate.now().with(DayOfWeek.MONDAY);
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of(new DirectReport(USER_1_ID, "Alice")));

            // total_commits=0 → completionRate=0.0, calibrationGap=avgConfidence
            insertEstimationRow(ORG_ID, USER_1_ID, weekEnd.minusWeeks(1), 0.8, 0, 0, 1);

            List<UserEstimationAccuracy> accuracies =
                    service.getEstimationAccuracyDistribution(ORG_ID, MANAGER_ID, 4);

            assertEquals(1, accuracies.size());
            assertEquals(0.0, accuracies.get(0).completionRate(), 0.001);
            assertEquals(0.8, accuracies.get(0).calibrationGap(), 0.001);
        }

        @Test
        void returnsEmptyListWhenManagerHasNoDirectReports() {
            when(orgGraphClient.getDirectReportsWithNames(ORG_ID, MANAGER_ID))
                    .thenReturn(List.of());

            List<UserEstimationAccuracy> accuracies =
                    service.getEstimationAccuracyDistribution(ORG_ID, MANAGER_ID, 4);

            assertTrue(accuracies.isEmpty());
        }
    }

    // ── helper methods ────────────────────────────────────────────────────────

    private void insertCoverage(
            UUID orgId, UUID outcomeId, LocalDate weekStart,
            int commitCount, int contributorCount, int highPriorityCount) {
        jdbcTemplate.update(
                "INSERT INTO mv_outcome_coverage_weekly "
                        + "(org_id, outcome_id, week_start_date, commit_count,"
                        + " contributor_count, high_priority_count)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                orgId, outcomeId, weekStart, commitCount, contributorCount, highPriorityCount);
    }

    private void insertCarryForward(
            UUID orgId, UUID userId, LocalDate weekStart, int carriedCommits) {
        jdbcTemplate.update(
                "INSERT INTO mv_user_weekly_summary "
                        + "(org_id, owner_user_id, week_start_date, carried_commits,"
                        + " avg_confidence, done_count, total_commits, reconciled_count)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                orgId, userId, weekStart, carriedCommits, 0.0, 0, 0, 0);
    }

    private void insertEstimationRow(
            UUID orgId, UUID userId, LocalDate weekStart,
            double avgConfidence, int doneCount, int totalCommits, int reconciledCount) {
        jdbcTemplate.update(
                "INSERT INTO mv_user_weekly_summary "
                        + "(org_id, owner_user_id, week_start_date, carried_commits,"
                        + " avg_confidence, done_count, total_commits, reconciled_count)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                orgId, userId, weekStart, 0, avgConfidence, doneCount, totalCommits,
                reconciledCount);
    }

    private UUID insertPlan(UUID orgId, UUID ownerId, LocalDate weekStart) {
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date)"
                        + " VALUES (?, ?, ?, ?)",
                planId, orgId, ownerId, weekStart);
        return planId;
    }

    private void insertCommits(UUID orgId, UUID planId, String category, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT INTO weekly_commits (org_id, weekly_plan_id, category)"
                            + " VALUES (?, ?, ?)",
                    orgId, planId, category);
        }
    }
}
