package com.weekly.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weekly.analytics.dto.Prediction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Unit tests for {@link PredictionService} covering rule evaluation and
 * alert aggregation behavior.
 *
 * <p>Uses an isolated H2 in-memory database per test (PostgreSQL mode) so
 * no live database is required.
 */
class PredictionServiceTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OUTCOME_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    /**
     * 2026-03-18 is a Wednesday → currentWeekStart() == 2026-03-16 (Monday).
     * Used for all tests that do NOT require the lock-day check to fire.
     */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-18T12:00:00Z"),
            ZoneOffset.UTC);

    /**
     * 2026-03-16 is a Monday → currentWeekStart() == 2026-03-16 AND isLockDay==true.
     * Used exclusively for late-lock tests where the prediction should fire.
     */
    private static final Clock MONDAY_CLOCK = Clock.fixed(
            Instant.parse("2026-03-16T09:00:00Z"),
            ZoneOffset.UTC);

    /** Current week start shared across tests using {@link #FIXED_CLOCK}. */
    private static final LocalDate CURRENT_WEEK = LocalDate.of(2026, 3, 16);

    private JdbcTemplate jdbcTemplate;
    private PredictionService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:prediction-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        service = new PredictionService(jdbcTemplate, FIXED_CLOCK);
    }

    // ── predictCarryForward ───────────────────────────────────────────────────

    @Nested
    class PredictCarryForwardTests {

        @Test
        void returnsLikelyTrueWhenUserCarriedInTwoOfLastThreeWeeksAndCurrentHasFiveCommits() {
            // 3 of last 3 history weeks: week-3 carries=4, week-2 carries=5, week-1 carries=1
            // → 2 weeks with carried_commits ≥ CARRY_FORWARD_THRESHOLD(3): weeks -3 and -2
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(3), 4, 2, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(2), 5, 3, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(1), 1, 2, null);
            // Current week: 6 commits ≥ CARRY_FORWARD_MIN_CURRENT_COMMITS(5)
            insertUserWeeklySummary(CURRENT_WEEK, 0, 6, null);

            Prediction prediction = service.predictCarryForward(ORG_ID, USER_ID);

            assertTrue(prediction.likely());
            assertEquals(PredictionService.TYPE_CARRY_FORWARD, prediction.type());
            assertEquals(PredictionService.CONFIDENCE_HIGH, prediction.confidence());
        }

        @Test
        void returnsLikelyFalseWhenCarryPatternDoesNotMatch() {
            // Only 1 high-carry week (needs 2) and current commits below threshold
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(3), 1, 2, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(2), 4, 3, null); // only 1 ≥ 3
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(1), 2, 4, null);
            // Current week: 3 commits < CARRY_FORWARD_MIN_CURRENT_COMMITS(5)
            insertUserWeeklySummary(CURRENT_WEEK, 0, 3, null);

            Prediction prediction = service.predictCarryForward(ORG_ID, USER_ID);

            assertFalse(prediction.likely());
            assertEquals(PredictionService.TYPE_CARRY_FORWARD, prediction.type());
            assertEquals(PredictionService.CONFIDENCE_LOW, prediction.confidence());
        }

        @Test
        void returnsLikelyFalseWhenCurrentCommitsBelowThresholdEvenWithHighCarryHistory() {
            // 3 of 3 history weeks have high carries
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(3), 4, 5, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(2), 5, 6, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(1), 3, 4, null);
            // Current week only has 2 commits — below the threshold of 5
            insertUserWeeklySummary(CURRENT_WEEK, 0, 2, null);

            Prediction prediction = service.predictCarryForward(ORG_ID, USER_ID);

            assertFalse(prediction.likely());
        }

        @Test
        void returnsLikelyFalseWithNoHistory() {
            // No rows at all for this user

            Prediction prediction = service.predictCarryForward(ORG_ID, USER_ID);

            assertFalse(prediction.likely());
            assertEquals(PredictionService.TYPE_CARRY_FORWARD, prediction.type());
        }
    }

    // ── predictLateLock ───────────────────────────────────────────────────────

    @Nested
    class PredictLateLockTests {

        @Test
        void returnsLikelyTrueWhenUserLateLockThreeOfFourWeeksAndPlanIsDraftOnLockDay() {
            // Use a Monday clock so isLockDay == true
            PredictionService mondayService = new PredictionService(jdbcTemplate, MONDAY_CLOCK);
            // currentWeekStart for MONDAY_CLOCK is also 2026-03-16
            LocalDate currentWeek = LocalDate.of(2026, 3, 16);

            // 3 of the last 4 history weeks have lock_type = LATE_LOCK
            insertUserWeeklySummary(currentWeek.minusWeeks(4), 0, 2, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(3), 0, 3, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(2), 0, 4, "ON_TIME");
            insertUserWeeklySummary(currentWeek.minusWeeks(1), 0, 2, "LATE_LOCK");

            // Current plan is DRAFT
            insertWeeklyPlan(currentWeek, "DRAFT");

            Prediction prediction = mondayService.predictLateLock(ORG_ID, USER_ID);

            assertTrue(prediction.likely());
            assertEquals(PredictionService.TYPE_LATE_LOCK, prediction.type());
            assertEquals(PredictionService.CONFIDENCE_HIGH, prediction.confidence());
        }

        @Test
        void returnsLikelyFalseForUserWhoLocksOnTime() {
            // Use a Monday clock so isLockDay == true
            PredictionService mondayService = new PredictionService(jdbcTemplate, MONDAY_CLOCK);
            LocalDate currentWeek = LocalDate.of(2026, 3, 16);

            // Only 1 late-lock week out of last 4 (needs ≥ LATE_LOCK_WEEKS_NEEDED=3)
            insertUserWeeklySummary(currentWeek.minusWeeks(4), 0, 3, "ON_TIME");
            insertUserWeeklySummary(currentWeek.minusWeeks(3), 0, 4, "ON_TIME");
            insertUserWeeklySummary(currentWeek.minusWeeks(2), 0, 3, "ON_TIME");
            insertUserWeeklySummary(currentWeek.minusWeeks(1), 0, 2, "LATE_LOCK");

            insertWeeklyPlan(currentWeek, "DRAFT");

            Prediction prediction = mondayService.predictLateLock(ORG_ID, USER_ID);

            assertFalse(prediction.likely());
            assertEquals(PredictionService.TYPE_LATE_LOCK, prediction.type());
            assertEquals(PredictionService.CONFIDENCE_LOW, prediction.confidence());
        }

        @Test
        void returnsLikelyFalseWhenPlanIsLockedEvenWithLateLockHistory() {
            // Monday clock AND sufficient late-lock history, but plan is already LOCKED
            PredictionService mondayService = new PredictionService(jdbcTemplate, MONDAY_CLOCK);
            LocalDate currentWeek = LocalDate.of(2026, 3, 16);

            insertUserWeeklySummary(currentWeek.minusWeeks(4), 0, 2, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(3), 0, 3, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(2), 0, 4, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(1), 0, 2, "LATE_LOCK");

            // Plan is already LOCKED → isDraft == false
            insertWeeklyPlan(currentWeek, "LOCKED");

            Prediction prediction = mondayService.predictLateLock(ORG_ID, USER_ID);

            assertFalse(prediction.likely());
        }

        @Test
        void returnsLikelyFalseOnNonLockDayEvenWithMatchingPattern() {
            // The FIXED_CLOCK is a Wednesday → isLockDay == false
            LocalDate currentWeek = CURRENT_WEEK;

            insertUserWeeklySummary(currentWeek.minusWeeks(4), 0, 2, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(3), 0, 3, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(2), 0, 4, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(1), 0, 2, "LATE_LOCK");

            insertWeeklyPlan(currentWeek, "DRAFT");

            // service uses FIXED_CLOCK (Wednesday) — not the lock day
            Prediction prediction = service.predictLateLock(ORG_ID, USER_ID);

            assertFalse(prediction.likely());
        }
    }

    // ── predictCoverageDecline ────────────────────────────────────────────────

    @Nested
    class PredictCoverageDeclineTests {

        @Test
        void returnsLikelyTrueForThreeConsecutiveDecliningWeeks() {
            // History weeks: 10 → 7 → 4 → 2 (3 consecutive declines in the 4-week history)
            // Current week: 0 commits (no new work)
            insertCoverage(CURRENT_WEEK.minusWeeks(4), 10);
            insertCoverage(CURRENT_WEEK.minusWeeks(3), 7);
            insertCoverage(CURRENT_WEEK.minusWeeks(2), 4);
            insertCoverage(CURRENT_WEEK.minusWeeks(1), 2);
            // current week has no row → zero-filled to 0

            Prediction prediction = service.predictCoverageDecline(ORG_ID, OUTCOME_ID);

            assertTrue(prediction.likely());
            assertEquals(PredictionService.TYPE_COVERAGE_DECLINE, prediction.type());
            assertEquals(PredictionService.CONFIDENCE_MEDIUM, prediction.confidence());
            assertTrue(prediction.reason().contains(OUTCOME_ID.toString()));
        }

        @Test
        void returnsLikelyFalseWhenCoverageIsStable() {
            // All history weeks have the same commit count → no declining pattern
            insertCoverage(CURRENT_WEEK.minusWeeks(4), 5);
            insertCoverage(CURRENT_WEEK.minusWeeks(3), 5);
            insertCoverage(CURRENT_WEEK.minusWeeks(2), 5);
            insertCoverage(CURRENT_WEEK.minusWeeks(1), 5);
            insertCoverage(CURRENT_WEEK, 5);

            Prediction prediction = service.predictCoverageDecline(ORG_ID, OUTCOME_ID);

            assertFalse(prediction.likely());
            assertEquals(PredictionService.TYPE_COVERAGE_DECLINE, prediction.type());
        }

        @Test
        void returnsLikelyFalseWhenCoverageIsRising() {
            insertCoverage(CURRENT_WEEK.minusWeeks(4), 1);
            insertCoverage(CURRENT_WEEK.minusWeeks(3), 2);
            insertCoverage(CURRENT_WEEK.minusWeeks(2), 4);
            insertCoverage(CURRENT_WEEK.minusWeeks(1), 7);
            insertCoverage(CURRENT_WEEK, 10);

            Prediction prediction = service.predictCoverageDecline(ORG_ID, OUTCOME_ID);

            assertFalse(prediction.likely());
        }

        @Test
        void treatsMissingWeeksAsZeroAndFiresWhenPatternMatches() {
            // Explicit declining weeks at -4, -3, -2; the -1 and current weeks are omitted
            // (materialized views do not emit zero-commit rows) and must be zero-filled.
            // History: [5, 4, 3, 0] → 3 consecutive declines; current: 0
            insertCoverage(CURRENT_WEEK.minusWeeks(4), 5);
            insertCoverage(CURRENT_WEEK.minusWeeks(3), 4);
            insertCoverage(CURRENT_WEEK.minusWeeks(2), 3);
            // week-1 and current week are intentionally omitted

            Prediction prediction = service.predictCoverageDecline(ORG_ID, OUTCOME_ID);

            assertTrue(prediction.likely());
            assertEquals(PredictionService.CONFIDENCE_MEDIUM, prediction.confidence());
            assertTrue(prediction.reason().contains(OUTCOME_ID.toString()));
        }

        @Test
        void returnsLikelyFalseWithSingleWeekOfData() {
            // Only one row: the current week itself
            insertCoverage(CURRENT_WEEK, 5);

            Prediction prediction = service.predictCoverageDecline(ORG_ID, OUTCOME_ID);

            // History is all zeros; 0 >= 0 so no strict decline → false
            assertFalse(prediction.likely());
        }

        @Test
        void returnsLikelyFalseWithNoHistory() {
            // No rows at all for this outcome

            Prediction prediction = service.predictCoverageDecline(ORG_ID, OUTCOME_ID);

            assertFalse(prediction.likely());
            assertEquals(PredictionService.TYPE_COVERAGE_DECLINE, prediction.type());
        }
    }

    // ── getUserPredictions ────────────────────────────────────────────────────

    @Nested
    class GetUserPredictionsTests {

        @Test
        void aggregatesMultiplePredictionTypesWhenPatternsFire() {
            // Set up carry-forward pattern
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(3), 4, 3, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(2), 5, 4, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(1), 1, 2, null);
            insertUserWeeklySummary(CURRENT_WEEK, 0, 7, null);

            // Set up coverage-decline pattern for an outcome the user is linked to
            insertCoverage(CURRENT_WEEK.minusWeeks(4), 10);
            insertCoverage(CURRENT_WEEK.minusWeeks(3), 7);
            insertCoverage(CURRENT_WEEK.minusWeeks(2), 4);
            insertCoverage(CURRENT_WEEK.minusWeeks(1), 2);
            // current week: no row (zero-filled to 0)

            // Link USER_ID → OUTCOME_ID via weekly_commits → weekly_plans
            UUID planId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date,"
                            + " state, deleted_at) VALUES (?, ?, ?, ?, ?, NULL)",
                    planId, ORG_ID, USER_ID, CURRENT_WEEK.minusWeeks(1), "LOCKED");
            jdbcTemplate.update(
                    "INSERT INTO weekly_commits (org_id, weekly_plan_id, outcome_id,"
                            + " deleted_at) VALUES (?, ?, ?, NULL)",
                    ORG_ID, planId, OUTCOME_ID);

            List<Prediction> predictions = service.getUserPredictions(ORG_ID, USER_ID);

            assertEquals(2, predictions.size());
            assertTrue(predictions.stream().anyMatch(
                    p -> PredictionService.TYPE_CARRY_FORWARD.equals(p.type())));
            assertTrue(predictions.stream().anyMatch(
                    p -> PredictionService.TYPE_COVERAGE_DECLINE.equals(p.type())));
            assertTrue(predictions.stream().allMatch(Prediction::likely));
        }

        @Test
        void aggregatesAllPredictionTypesWhenAllRulesFire() {
            PredictionService mondayService = new PredictionService(jdbcTemplate, MONDAY_CLOCK);
            LocalDate currentWeek = LocalDate.of(2026, 3, 16);

            // Carry-forward history: 2 of the last 3 weeks meet the carried-commit threshold.
            // Late-lock history: 3 of the last 4 weeks are marked LATE_LOCK.
            insertUserWeeklySummary(currentWeek.minusWeeks(4), 0, 2, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(3), 4, 5, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(2), 5, 6, "LATE_LOCK");
            insertUserWeeklySummary(currentWeek.minusWeeks(1), 1, 2, "ON_TIME");
            insertUserWeeklySummary(currentWeek, 0, 7, null);

            insertWeeklyPlan(currentWeek, "DRAFT");

            insertCoverage(currentWeek.minusWeeks(4), 10);
            insertCoverage(currentWeek.minusWeeks(3), 7);
            insertCoverage(currentWeek.minusWeeks(2), 4);
            insertCoverage(currentWeek.minusWeeks(1), 2);

            UUID planId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date,"
                            + " state, deleted_at) VALUES (?, ?, ?, ?, ?, NULL)",
                    planId, ORG_ID, USER_ID, currentWeek.minusWeeks(1), "LOCKED");
            jdbcTemplate.update(
                    "INSERT INTO weekly_commits (org_id, weekly_plan_id, outcome_id,"
                            + " deleted_at) VALUES (?, ?, ?, NULL)",
                    ORG_ID, planId, OUTCOME_ID);

            List<Prediction> predictions = mondayService.getUserPredictions(ORG_ID, USER_ID);

            assertEquals(3, predictions.size());
            assertTrue(predictions.stream().anyMatch(
                    p -> PredictionService.TYPE_CARRY_FORWARD.equals(p.type())));
            assertTrue(predictions.stream().anyMatch(
                    p -> PredictionService.TYPE_LATE_LOCK.equals(p.type())));
            assertTrue(predictions.stream().anyMatch(
                    p -> PredictionService.TYPE_COVERAGE_DECLINE.equals(p.type())));
            assertTrue(predictions.stream().allMatch(Prediction::likely));
        }

        @Test
        void returnsOnlyTriggeredAlerts() {
            // 2 of 3 history weeks with high carries → carry-forward fires
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(3), 3, 4, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(2), 4, 4, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(1), 1, 2, null);
            // Current week: 5 commits ≥ threshold
            insertUserWeeklySummary(CURRENT_WEEK, 0, 5, null);
            // No coverage-decline data; clock is Wednesday (late-lock won't fire)

            List<Prediction> predictions = service.getUserPredictions(ORG_ID, USER_ID);

            assertEquals(1, predictions.size());
            assertEquals(PredictionService.TYPE_CARRY_FORWARD, predictions.getFirst().type());
            assertTrue(predictions.getFirst().likely());
        }

        @Test
        void returnsEmptyListWhenNoPatternsFire() {
            // Only one carry-forward week (below threshold of 2)
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(3), 1, 2, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(2), 4, 3, null);
            insertUserWeeklySummary(CURRENT_WEEK.minusWeeks(1), 2, 4, null);
            // Current week: 2 commits (below MIN_CURRENT_COMMITS=5)
            insertUserWeeklySummary(CURRENT_WEEK, 0, 2, null);

            List<Prediction> predictions = service.getUserPredictions(ORG_ID, USER_ID);

            assertTrue(predictions.isEmpty());
        }

        @Test
        void returnsEmptyListWithNoHistoryAtAll() {
            // No rows inserted at all

            List<Prediction> predictions = service.getUserPredictions(ORG_ID, USER_ID);

            assertTrue(predictions.isEmpty());
        }
    }

    // ── hasConsecutiveDecline helper ──────────────────────────────────────────

    @Nested
    class HasConsecutiveDeclineTests {

        @Test
        void returnsTrueForStrictlyDecreasingTrailingPairs() {
            // [10, 7, 4, 2] → 3 consecutive declining pairs: 7<10, 4<7, 2<4
            assertTrue(service.hasConsecutiveDecline(List.of(10, 7, 4, 2), 3));
        }

        @Test
        void returnsFalseWhenOnePairBreaksTheDecline() {
            // [10, 7, 7, 2] → 7 >= 7 at position 1-2 breaks the pattern
            assertFalse(service.hasConsecutiveDecline(List.of(10, 7, 7, 2), 3));
        }

        @Test
        void returnsFalseForInsufficientData() {
            // List has only 3 elements but consecutiveCount=3 needs 4 (3+1)
            assertFalse(service.hasConsecutiveDecline(List.of(5, 3, 1), 3));
        }

        @Test
        void returnsFalseForEmptyList() {
            assertFalse(service.hasConsecutiveDecline(List.of(), 3));
        }

        @Test
        void returnsTrueForMinimalCaseOfOneDecline() {
            // [5, 3] → 1 consecutive declining pair: 3<5
            assertTrue(service.hasConsecutiveDecline(List.of(5, 3), 1));
        }
    }

    // ── schema + helpers ──────────────────────────────────────────────────────

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE mv_user_weekly_summary (
                    org_id UUID NOT NULL,
                    owner_user_id UUID NOT NULL,
                    week_start_date DATE NOT NULL,
                    state VARCHAR(50),
                    lock_type VARCHAR(50),
                    total_commits INT,
                    carried_commits INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE mv_outcome_coverage_weekly (
                    org_id UUID NOT NULL,
                    outcome_id UUID NOT NULL,
                    week_start_date DATE NOT NULL,
                    commit_count INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE weekly_plans (
                    id UUID NOT NULL,
                    org_id UUID NOT NULL,
                    owner_user_id UUID NOT NULL,
                    week_start_date DATE NOT NULL,
                    state VARCHAR(50),
                    deleted_at TIMESTAMP NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE weekly_commits (
                    org_id UUID NOT NULL,
                    weekly_plan_id UUID NOT NULL,
                    outcome_id UUID,
                    deleted_at TIMESTAMP NULL
                )
                """);
    }

    private void insertCoverage(LocalDate weekStartDate, int commitCount) {
        jdbcTemplate.update(
                "INSERT INTO mv_outcome_coverage_weekly"
                        + " (org_id, outcome_id, week_start_date, commit_count)"
                        + " VALUES (?, ?, ?, ?)",
                ORG_ID, OUTCOME_ID, weekStartDate, commitCount);
    }

    private void insertUserWeeklySummary(
            LocalDate weekStartDate, int carriedCommits, int totalCommits, String lockType) {
        jdbcTemplate.update(
                """
                INSERT INTO mv_user_weekly_summary (
                    org_id,
                    owner_user_id,
                    week_start_date,
                    state,
                    lock_type,
                    total_commits,
                    carried_commits
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                ORG_ID,
                USER_ID,
                weekStartDate,
                "LOCKED",
                lockType,
                totalCommits,
                carriedCommits);
    }

    private void insertWeeklyPlan(LocalDate weekStartDate, String state) {
        jdbcTemplate.update(
                "INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date,"
                        + " state, deleted_at) VALUES (?, ?, ?, ?, ?, NULL)",
                UUID.randomUUID(), ORG_ID, USER_ID, weekStartDate, state);
    }
}
