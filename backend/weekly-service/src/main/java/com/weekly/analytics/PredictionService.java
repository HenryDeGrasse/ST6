package com.weekly.analytics;

import com.weekly.analytics.dto.Prediction;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule-based prediction service for the manager dashboard intelligence layer.
 *
 * <p>Evaluates three types of risk predictions by querying historical data from
 * the analytics materialized views ({@code mv_user_weekly_summary},
 * {@code mv_outcome_coverage_weekly}) and the live {@code weekly_plans} table for
 * current plan state.
 *
 * <p>Materialized-view queries filter by {@code org_id} explicitly because
 * Row-Level Security is not supported on materialized views. Queries on
 * {@code weekly_plans} and {@code weekly_commits} benefit from both explicit
 * {@code org_id} filtering and RLS set by
 * {@link com.weekly.config.TenantRlsTransactionListener}.
 *
 * <h2>Prediction rules</h2>
 * <ol>
 *   <li><b>CARRY_FORWARD</b>: user carried ≥ {@value #CARRY_FORWARD_THRESHOLD} items in
 *       ≥ {@value #CARRY_FORWARD_WEEKS_NEEDED} of the last {@value #CARRY_FORWARD_WINDOW}
 *       weeks AND current week has ≥ {@value #CARRY_FORWARD_MIN_CURRENT_COMMITS} commits
 *       → likely=true, HIGH confidence.</li>
 *   <li><b>LATE_LOCK</b>: user late-locked in ≥ {@value #LATE_LOCK_WEEKS_NEEDED} of the
 *       last {@value #LATE_LOCK_WINDOW} weeks AND current plan is still DRAFT on the lock
 *       day → likely=true, HIGH confidence.</li>
 *   <li><b>COVERAGE_DECLINE</b>: outcome coverage declined for
 *       {@value #COVERAGE_DECLINE_CONSECUTIVE} consecutive weeks AND no new commits this
 *       week → likely=true, MEDIUM confidence.</li>
 * </ol>
 */
@Service
public class PredictionService {

    // ── Constants ─────────────────────────────────────────────────────────────

    static final String TYPE_CARRY_FORWARD = "CARRY_FORWARD";
    static final String TYPE_LATE_LOCK = "LATE_LOCK";
    static final String TYPE_COVERAGE_DECLINE = "COVERAGE_DECLINE";

    static final String CONFIDENCE_HIGH = "HIGH";
    static final String CONFIDENCE_MEDIUM = "MEDIUM";
    static final String CONFIDENCE_LOW = "LOW";

    /** Minimum carried commits to count a week as a carry-forward week. */
    static final int CARRY_FORWARD_THRESHOLD = 3;
    /** Minimum carry-forward weeks (out of last N) required to fire the prediction. */
    static final int CARRY_FORWARD_WEEKS_NEEDED = 2;
    /** Rolling window size for carry-forward history. */
    static final int CARRY_FORWARD_WINDOW = 3;
    /** Minimum current-week commits required alongside the carry-forward pattern. */
    static final int CARRY_FORWARD_MIN_CURRENT_COMMITS = 5;

    /** Late-lock weeks (out of rolling window) required to fire the prediction. */
    static final int LATE_LOCK_WEEKS_NEEDED = 3;
    /** Rolling window size for late-lock history. */
    static final int LATE_LOCK_WINDOW = 4;

    /** Number of consecutive declining weeks required for coverage-decline prediction. */
    static final int COVERAGE_DECLINE_CONSECUTIVE = 3;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final NamedParameterJdbcTemplate namedJdbc;
    private final Clock clock;

    @Autowired
    public PredictionService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    PredictionService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.clock = clock;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Predicts whether the given user is likely to carry forward work this week.
     *
     * <p>Rule: user carried ≥ {@value #CARRY_FORWARD_THRESHOLD} items in
     * ≥ {@value #CARRY_FORWARD_WEEKS_NEEDED} of the last
     * {@value #CARRY_FORWARD_WINDOW} weeks AND current week already has
     * ≥ {@value #CARRY_FORWARD_MIN_CURRENT_COMMITS} commits.
     *
     * @param orgId  the organisation ID
     * @param userId the user to evaluate
     * @return {@link Prediction} with {@code type="CARRY_FORWARD"}
     */
    @Transactional(readOnly = true)
    public Prediction predictCarryForward(UUID orgId, UUID userId) {
        LocalDate currentWeekStart = currentWeekStart();
        LocalDate historyWindowStart = currentWeekStart.minusWeeks(CARRY_FORWARD_WINDOW);

        // Carried-commit counts for the N history weeks (excludes current week)
        String historySql = """
                SELECT carried_commits
                FROM mv_user_weekly_summary
                WHERE org_id = :orgId
                  AND owner_user_id = :userId
                  AND week_start_date >= :windowStart
                  AND week_start_date < :currentWeek
                ORDER BY week_start_date
                """;

        MapSqlParameterSource historyParams = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userId", userId)
                .addValue("windowStart", historyWindowStart)
                .addValue("currentWeek", currentWeekStart);

        List<Integer> carriedPerWeek = namedJdbc.query(
                historySql, historyParams, (rs, rowNum) -> rs.getInt("carried_commits"));

        long weeksWithHighCarry = carriedPerWeek.stream()
                .filter(c -> c >= CARRY_FORWARD_THRESHOLD)
                .count();

        // Total commits for the current week from the materialized view
        String currentSql = """
                SELECT COALESCE(total_commits, 0) AS total_commits
                FROM mv_user_weekly_summary
                WHERE org_id = :orgId
                  AND owner_user_id = :userId
                  AND week_start_date = :currentWeek
                """;

        MapSqlParameterSource currentParams = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userId", userId)
                .addValue("currentWeek", currentWeekStart);

        List<Integer> currentRows = namedJdbc.query(
                currentSql, currentParams, (rs, rowNum) -> rs.getInt("total_commits"));
        int currentCommits = currentRows.isEmpty() ? 0 : currentRows.get(0);

        boolean likely = weeksWithHighCarry >= CARRY_FORWARD_WEEKS_NEEDED
                && currentCommits >= CARRY_FORWARD_MIN_CURRENT_COMMITS;

        if (likely) {
            return new Prediction(
                    TYPE_CARRY_FORWARD, true, CONFIDENCE_HIGH,
                    "User carried ≥" + CARRY_FORWARD_THRESHOLD + " items in "
                            + weeksWithHighCarry + " of the last "
                            + CARRY_FORWARD_WINDOW + " weeks and has "
                            + currentCommits + " commits this week.");
        }
        return new Prediction(
                TYPE_CARRY_FORWARD, false, CONFIDENCE_LOW,
                "Carry-forward pattern not detected.");
    }

    /**
     * Predicts whether the given user is likely to lock their plan late this week.
     *
     * <p>Rule: user late-locked in ≥ {@value #LATE_LOCK_WEEKS_NEEDED} of the last
     * {@value #LATE_LOCK_WINDOW} weeks AND current plan is still {@code DRAFT} on
     * the lock day (Monday).
     *
     * @param orgId  the organisation ID
     * @param userId the user to evaluate
     * @return {@link Prediction} with {@code type="LATE_LOCK"}
     */
    @Transactional(readOnly = true)
    public Prediction predictLateLock(UUID orgId, UUID userId) {
        LocalDate currentWeekStart = currentWeekStart();
        LocalDate historyWindowStart = currentWeekStart.minusWeeks(LATE_LOCK_WINDOW);

        // Count weeks where the plan was late-locked in the history window
        String historySql = """
                SELECT COUNT(*) AS late_lock_count
                FROM mv_user_weekly_summary
                WHERE org_id = :orgId
                  AND owner_user_id = :userId
                  AND week_start_date >= :windowStart
                  AND week_start_date < :currentWeek
                  AND lock_type = 'LATE_LOCK'
                """;

        MapSqlParameterSource historyParams = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userId", userId)
                .addValue("windowStart", historyWindowStart)
                .addValue("currentWeek", currentWeekStart);

        Integer lateLockCount = namedJdbc.queryForObject(historySql, historyParams, Integer.class);
        int lateLockedWeeks = lateLockCount != null ? lateLockCount : 0;

        // Check if current plan is still DRAFT (queried from live table)
        String currentSql = """
                SELECT state
                FROM weekly_plans
                WHERE org_id = :orgId
                  AND owner_user_id = :userId
                  AND week_start_date = :currentWeek
                  AND deleted_at IS NULL
                LIMIT 1
                """;

        MapSqlParameterSource currentParams = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userId", userId)
                .addValue("currentWeek", currentWeekStart);

        List<String> stateRows = namedJdbc.query(
                currentSql, currentParams, (rs, rowNum) -> rs.getString("state"));
        String currentState = stateRows.isEmpty() ? null : stateRows.get(0);
        boolean isDraft = "DRAFT".equals(currentState);

        // Lock day is treated as Monday (start of the weekly planning cycle)
        boolean isLockDay = LocalDate.now(clock).getDayOfWeek() == DayOfWeek.MONDAY;

        boolean likely = lateLockedWeeks >= LATE_LOCK_WEEKS_NEEDED && isDraft && isLockDay;

        if (likely) {
            return new Prediction(
                    TYPE_LATE_LOCK, true, CONFIDENCE_HIGH,
                    "User late-locked in " + lateLockedWeeks + " of the last "
                            + LATE_LOCK_WINDOW + " weeks and current plan is still DRAFT.");
        }
        return new Prediction(
                TYPE_LATE_LOCK, false, CONFIDENCE_LOW,
                "Late-lock pattern not detected.");
    }

    /**
     * Predicts whether coverage for the given outcome is likely to decline further.
     *
     * <p>Rule: commit count declined for {@value #COVERAGE_DECLINE_CONSECUTIVE}
     * consecutive weeks AND no new commits for this outcome this week.
     *
     * @param orgId     the organisation ID
     * @param outcomeId the RCDO outcome to evaluate
     * @return {@link Prediction} with {@code type="COVERAGE_DECLINE"}
     */
    @Transactional(readOnly = true)
    public Prediction predictCoverageDecline(UUID orgId, UUID outcomeId) {
        LocalDate currentWeekStart = currentWeekStart();
        // Fetch enough weeks: CONSECUTIVE historical weeks + 1 (to detect CONSECUTIVE
        // transitions) plus the current week.
        LocalDate historyWindowStart =
                currentWeekStart.minusWeeks(COVERAGE_DECLINE_CONSECUTIVE + 1L);

        String sql = """
                SELECT week_start_date, commit_count
                FROM mv_outcome_coverage_weekly
                WHERE org_id = :orgId
                  AND outcome_id = :outcomeId
                  AND week_start_date >= :windowStart
                  AND week_start_date <= :currentWeek
                ORDER BY week_start_date
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("outcomeId", outcomeId)
                .addValue("windowStart", historyWindowStart)
                .addValue("currentWeek", currentWeekStart);

        // Zero-fill missing weeks because the materialized view omits zero-commit rows.
        Map<LocalDate, Integer> commitsByWeek = new HashMap<>();
        namedJdbc.query(sql, params, rs -> {
            commitsByWeek.put(
                    rs.getDate("week_start_date").toLocalDate(),
                    rs.getInt("commit_count")
            );
        });

        List<Integer> weeklyCounts = new ArrayList<>();
        LocalDate cursor = historyWindowStart;
        while (!cursor.isAfter(currentWeekStart)) {
            weeklyCounts.add(commitsByWeek.getOrDefault(cursor, 0));
            cursor = cursor.plusWeeks(1);
        }

        List<Integer> historyCommits = weeklyCounts.subList(0, weeklyCounts.size() - 1);
        int currentCommits = weeklyCounts.get(weeklyCounts.size() - 1);

        boolean consecutiveDecline = hasConsecutiveDecline(historyCommits, COVERAGE_DECLINE_CONSECUTIVE);
        boolean noCommitsThisWeek = currentCommits == 0;

        boolean likely = consecutiveDecline && noCommitsThisWeek;

        if (likely) {
            return new Prediction(
                    TYPE_COVERAGE_DECLINE, true, CONFIDENCE_MEDIUM,
                    "Outcome " + outcomeId + " coverage declined for "
                            + COVERAGE_DECLINE_CONSECUTIVE
                            + " consecutive weeks and has no commits this week.");
        }
        return new Prediction(
                TYPE_COVERAGE_DECLINE, false, CONFIDENCE_LOW,
                "Coverage decline pattern not detected.");
    }

    /**
     * Returns all applicable predictions for the given user.
     *
     * <p>Evaluates carry-forward and late-lock predictions for the user directly,
     * then iterates outcomes the user has recently committed to and generates
     * coverage-decline predictions for each. Only predictions where
     * {@code likely=true} are included in the result so the caller receives
     * actionable alerts rather than negative rule evaluations.
     *
     * @param orgId  the organisation ID
     * @param userId the user to evaluate
     * @return all applicable predictions for the user
     */
    @Transactional(readOnly = true)
    public List<Prediction> getUserPredictions(UUID orgId, UUID userId) {
        List<Prediction> predictions = new ArrayList<>();

        Prediction carryForwardPrediction = predictCarryForward(orgId, userId);
        if (carryForwardPrediction.likely()) {
            predictions.add(carryForwardPrediction);
        }

        Prediction lateLockPrediction = predictLateLock(orgId, userId);
        if (lateLockPrediction.likely()) {
            predictions.add(lateLockPrediction);
        }

        List<UUID> userOutcomes = getUserOutcomeIds(orgId, userId);
        for (UUID outcomeId : userOutcomes) {
            Prediction coveragePrediction = predictCoverageDecline(orgId, outcomeId);
            if (coveragePrediction.likely()) {
                predictions.add(coveragePrediction);
            }
        }

        return predictions;
    }

    // ── Package-private helpers (visible for unit tests) ─────────────────────

    /**
     * Returns the start of the current ISO week (Monday).
     */
    LocalDate currentWeekStart() {
        return LocalDate.now(clock).with(DayOfWeek.MONDAY);
    }

    /**
     * Checks whether the trailing {@code consecutiveCount} pairs in the given
     * ordered commit-count list show strictly declining values.
     *
     * <p>Returns {@code false} when the list has fewer than
     * {@code consecutiveCount + 1} entries (insufficient data).
     *
     * @param commitCounts     ordered commit counts per week (oldest first)
     * @param consecutiveCount number of consecutive declining pairs required
     * @return {@code true} when the last {@code consecutiveCount} consecutive
     *         pairs are strictly declining
     */
    boolean hasConsecutiveDecline(List<Integer> commitCounts, int consecutiveCount) {
        if (commitCounts.size() < consecutiveCount + 1) {
            return false;
        }
        int startIndex = commitCounts.size() - consecutiveCount - 1;
        for (int i = startIndex; i < startIndex + consecutiveCount; i++) {
            if (commitCounts.get(i + 1) >= commitCounts.get(i)) {
                return false;
            }
        }
        return true;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the distinct outcome IDs that the given user has committed to within
     * the recent look-back window ({@value #COVERAGE_DECLINE_CONSECUTIVE} + 2 weeks).
     *
     * <p>Queries {@code weekly_commits} joined to {@code weekly_plans}. Only
     * non-deleted commits with a non-null {@code outcome_id} are included.
     */
    private List<UUID> getUserOutcomeIds(UUID orgId, UUID userId) {
        LocalDate currentWeekStart = currentWeekStart();
        LocalDate lookbackStart = currentWeekStart.minusWeeks(COVERAGE_DECLINE_CONSECUTIVE + 2L);

        String sql = """
                SELECT DISTINCT wc.outcome_id
                FROM weekly_commits wc
                JOIN weekly_plans wp
                    ON wp.id      = wc.weekly_plan_id
                    AND wp.org_id = wc.org_id
                WHERE wc.org_id = :orgId
                  AND wp.owner_user_id = :userId
                  AND wp.week_start_date >= :lookbackStart
                  AND wc.outcome_id IS NOT NULL
                  AND wp.deleted_at IS NULL
                  AND wc.deleted_at IS NULL
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userId", userId)
                .addValue("lookbackStart", lookbackStart);

        return namedJdbc.query(sql, params, (rs, rowNum) -> (UUID) rs.getObject("outcome_id"));
    }
}
