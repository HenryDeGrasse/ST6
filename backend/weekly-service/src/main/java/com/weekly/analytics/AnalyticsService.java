package com.weekly.analytics;

import com.weekly.analytics.dto.CarryForwardHeatmap;
import com.weekly.analytics.dto.CategoryShift;
import com.weekly.analytics.dto.HeatmapCell;
import com.weekly.analytics.dto.HeatmapUser;
import com.weekly.analytics.dto.OutcomeCoverageTimeline;
import com.weekly.analytics.dto.OutcomeCoverageWeek;
import com.weekly.analytics.dto.UserCategoryShift;
import com.weekly.analytics.dto.UserEstimationAccuracy;
import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Multi-week descriptive and diagnostic analytics for the manager dashboard.
 *
 * <p>Provides four query methods:
 * <ol>
 *   <li>{@link #getOutcomeCoverageTimeline} — per-outcome weekly commit activity
 *       from {@code mv_outcome_coverage_weekly}.</li>
 *   <li>{@link #getTeamCarryForwardHeatmap} — per-user, per-week carry-forward
 *       counts for the manager's direct reports from
 *       {@code mv_user_weekly_summary}.</li>
 *   <li>{@link #getCategoryShiftAnalysis} — per-user category distribution
 *       changes between the prior and recent half of the analysis window,
 *       queried from {@code weekly_commits} and {@code weekly_plans}.</li>
 *   <li>{@link #getEstimationAccuracyDistribution} — per-user confidence vs
 *       actual completion rate from {@code mv_user_weekly_summary}.</li>
 * </ol>
 *
 * <p>Materialized-view queries filter by {@code org_id} explicitly because
 * Row-Level Security is not supported on materialized views. Queries on regular
 * tables benefit from both explicit {@code org_id} filtering and RLS set by
 * {@link com.weekly.config.TenantRlsTransactionListener}.
 */
@Service
public class AnalyticsService {

    static final int MIN_WEEKS = 1;
    static final int MAX_WEEKS = 26;

    static final String TREND_RISING = "RISING";
    static final String TREND_FALLING = "FALLING";
    static final String TREND_STABLE = "STABLE";

    private final OrgGraphClient orgGraphClient;
    private final NamedParameterJdbcTemplate namedJdbc;

    public AnalyticsService(JdbcTemplate jdbcTemplate, OrgGraphClient orgGraphClient) {
        this.orgGraphClient = orgGraphClient;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // ── 1. Outcome Coverage Timeline ─────────────────────────────────────────

    /**
     * Returns the commit activity for a specific RCDO outcome over the last
     * {@code weeks} weeks, sourced from {@code mv_outcome_coverage_weekly}.
     *
     * <p>Weeks with no materialized-view row are returned with zero counts so the
     * timeline always spans the full requested window.
     * A {@code trendDirection} is derived by comparing the commit counts for the
     * final two weeks in the returned timeline: {@code RISING} when the latest
     * week exceeds the previous, {@code FALLING} when it is lower, or
     * {@code STABLE}.
     *
     * @param orgId     the organisation ID
     * @param outcomeId the RCDO outcome to query
     * @param weeks     rolling-window size (clamped to [{@value #MIN_WEEKS}, {@value #MAX_WEEKS}])
     * @return timeline with per-week data points and a trend direction
     */
    @Transactional(readOnly = true)
    public OutcomeCoverageTimeline getOutcomeCoverageTimeline(
            UUID orgId, UUID outcomeId, int weeks) {

        int clamped = clamp(weeks);
        LocalDate windowStart = windowStart(clamped);

        String sql = """
                SELECT week_start_date, commit_count, contributor_count, high_priority_count
                FROM mv_outcome_coverage_weekly
                WHERE org_id = :orgId
                  AND outcome_id = :outcomeId
                  AND week_start_date >= :windowStart
                ORDER BY week_start_date
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("outcomeId", outcomeId)
                .addValue("windowStart", windowStart);

        List<LocalDate> weekDates = buildWeekDates(windowStart);
        Map<LocalDate, OutcomeCoverageWeek> pointsByWeek = new HashMap<>();
        namedJdbc.query(sql, params, rs -> {
            LocalDate weekStartDate = rs.getDate("week_start_date").toLocalDate();
            pointsByWeek.put(
                    weekStartDate,
                    new OutcomeCoverageWeek(
                            weekStartDate.toString(),
                            rs.getInt("commit_count"),
                            rs.getInt("contributor_count"),
                            rs.getInt("high_priority_count")
                    )
            );
        });

        List<OutcomeCoverageWeek> weekPoints = weekDates.stream()
                .map(weekDate -> pointsByWeek.getOrDefault(
                        weekDate,
                        new OutcomeCoverageWeek(weekDate.toString(), 0, 0, 0)
                ))
                .toList();

        String trendDirection = computeTrendDirection(weekPoints);
        return new OutcomeCoverageTimeline(weekPoints, trendDirection);
    }

    // ── 2. Team Carry-Forward Heatmap ─────────────────────────────────────────

    /**
     * Returns a carry-forward heatmap for the manager's direct reports over the
     * last {@code weeks} weeks, sourced from {@code mv_user_weekly_summary}.
     *
     * <p>Each cell represents one user's carried-commit count for one week.
     * Weeks with no row in the materialized view are filled with a zero count.
     *
     * @param orgId     the organisation ID
     * @param managerId the manager whose direct reports are analysed
     * @param weeks     rolling-window size (clamped to [{@value #MIN_WEEKS}, {@value #MAX_WEEKS}])
     * @return heatmap grid indexed by user × week
     */
    @Transactional(readOnly = true)
    public CarryForwardHeatmap getTeamCarryForwardHeatmap(
            UUID orgId, UUID managerId, int weeks) {

        List<DirectReport> team = orgGraphClient.getDirectReportsWithNames(orgId, managerId);
        if (team.isEmpty()) {
            return new CarryForwardHeatmap(List.of());
        }

        int clamped = clamp(weeks);
        LocalDate windowStart = windowStart(clamped);
        List<LocalDate> weekDates = buildWeekDates(windowStart);

        List<UUID> userIds = team.stream().map(DirectReport::userId).toList();

        String sql = """
                SELECT owner_user_id, week_start_date, carried_commits
                FROM mv_user_weekly_summary
                WHERE org_id = :orgId
                  AND owner_user_id IN (:userIds)
                  AND week_start_date >= :windowStart
                ORDER BY owner_user_id, week_start_date
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userIds", userIds)
                .addValue("windowStart", windowStart);

        // Build a nested map: userId → (weekStart → carriedCount)
        Map<UUID, Map<LocalDate, Integer>> dataByUser = new HashMap<>();
        namedJdbc.query(sql, params, rs -> {
            UUID userId = (UUID) rs.getObject("owner_user_id");
            LocalDate weekDate = rs.getDate("week_start_date").toLocalDate();
            int carried = rs.getInt("carried_commits");
            dataByUser.computeIfAbsent(userId, k -> new HashMap<>()).put(weekDate, carried);
        });

        // Assemble full grid (fill missing weeks with zero)
        List<HeatmapUser> users = new ArrayList<>();
        for (DirectReport dr : team) {
            Map<LocalDate, Integer> userWeekData = dataByUser.getOrDefault(dr.userId(), Map.of());
            List<HeatmapCell> cells = weekDates.stream()
                    .map(week -> new HeatmapCell(
                            week.toString(),
                            userWeekData.getOrDefault(week, 0)
                    ))
                    .toList();
            users.add(new HeatmapUser(dr.userId().toString(), dr.displayName(), cells));
        }

        return new CarryForwardHeatmap(users);
    }

    // ── 3. Category Shift Analysis ────────────────────────────────────────────

    /**
     * Computes per-user commit category distribution shifts between the prior
     * half of the analysis window and the recent half.
     *
     * <p>Queries {@code weekly_commits} joined with {@code weekly_plans} for
     * the manager's direct reports. Only commits with a non-null category are
     * included. The window is split evenly; if {@code weeks} is odd the recent
     * half receives the extra week.
     *
     * @param orgId     the organisation ID
     * @param managerId the manager whose direct reports are analysed
     * @param weeks     rolling-window size (clamped to [{@value #MIN_WEEKS}, {@value #MAX_WEEKS}])
     * @return per-user list of category distribution shifts and the biggest shift per user
     */
    @Transactional(readOnly = true)
    public List<UserCategoryShift> getCategoryShiftAnalysis(
            UUID orgId, UUID managerId, int weeks) {

        List<DirectReport> team = orgGraphClient.getDirectReportsWithNames(orgId, managerId);
        if (team.isEmpty()) {
            return List.of();
        }

        int clamped = clamp(weeks);
        LocalDate windowEndExclusive = windowEnd().plusWeeks(1);

        // Split window into prior and recent halves; when odd, recent gets the extra week.
        int recentHalfWeeks = (clamped + 1) / 2;
        int priorHalfWeeks = clamped - recentHalfWeeks;
        LocalDate recentHalfStart = windowEndExclusive.minusWeeks(recentHalfWeeks);
        LocalDate priorHalfStart = recentHalfStart.minusWeeks(priorHalfWeeks);

        List<UUID> userIds = team.stream().map(DirectReport::userId).toList();

        // Recent half: category counts per user
        Map<UUID, Map<String, Long>> recentCounts = queryCategoryCounts(
                orgId, userIds, recentHalfStart, windowEndExclusive);

        // Prior half: category counts per user
        Map<UUID, Map<String, Long>> priorCounts = queryCategoryCounts(
                orgId, userIds, priorHalfStart, recentHalfStart);

        // Only include users who appear in either half
        List<UserCategoryShift> results = new ArrayList<>();
        for (DirectReport dr : team) {
            Map<String, Long> recentUserCounts = recentCounts.getOrDefault(dr.userId(), Map.of());
            Map<String, Long> priorUserCounts = priorCounts.getOrDefault(dr.userId(), Map.of());

            if (recentUserCounts.isEmpty() && priorUserCounts.isEmpty()) {
                continue;
            }

            Map<String, Double> recentDist = toProportions(recentUserCounts);
            Map<String, Double> priorDist = toProportions(priorUserCounts);
            CategoryShift biggestShift = computeBiggestShift(recentDist, priorDist);

            results.add(new UserCategoryShift(
                    dr.userId().toString(),
                    recentDist,
                    priorDist,
                    biggestShift
            ));
        }

        return results;
    }

    // ── 4. Estimation Accuracy Distribution ───────────────────────────────────

    /**
     * Returns per-user estimation accuracy metrics derived from reconciled weeks
     * in the analysis window, sourced from {@code mv_user_weekly_summary}.
     *
     * <p>Only weeks where {@code reconciled_count = 1} (state was RECONCILED) are
     * included. Users with no reconciled weeks in the window are excluded.
     *
     * @param orgId     the organisation ID
     * @param managerId the manager whose direct reports are analysed
     * @param weeks     rolling-window size (clamped to [{@value #MIN_WEEKS}, {@value #MAX_WEEKS}])
     * @return per-user list of confidence vs completion accuracy metrics
     */
    @Transactional(readOnly = true)
    public List<UserEstimationAccuracy> getEstimationAccuracyDistribution(
            UUID orgId, UUID managerId, int weeks) {

        List<DirectReport> team = orgGraphClient.getDirectReportsWithNames(orgId, managerId);
        if (team.isEmpty()) {
            return List.of();
        }

        int clamped = clamp(weeks);
        LocalDate windowStart = windowStart(clamped);
        List<UUID> userIds = team.stream().map(DirectReport::userId).toList();

        String sql = """
                SELECT
                    owner_user_id,
                    AVG(avg_confidence)            AS avg_confidence,
                    SUM(done_count)                AS total_done,
                    SUM(total_commits)             AS total_commits
                FROM mv_user_weekly_summary
                WHERE org_id = :orgId
                  AND owner_user_id IN (:userIds)
                  AND week_start_date >= :windowStart
                  AND reconciled_count = 1
                GROUP BY owner_user_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userIds", userIds)
                .addValue("windowStart", windowStart);

        return namedJdbc.query(sql, params, (rs, rowNum) -> {
            UUID userId = (UUID) rs.getObject("owner_user_id");
            double avgConfidence = rs.getDouble("avg_confidence");
            long totalDone = rs.getLong("total_done");
            long totalCommits = rs.getLong("total_commits");
            double completionRate = totalCommits > 0
                    ? (double) totalDone / totalCommits
                    : 0.0;
            double calibrationGap = avgConfidence - completionRate;
            return new UserEstimationAccuracy(
                    userId.toString(),
                    avgConfidence,
                    completionRate,
                    calibrationGap
            );
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private int clamp(int weeks) {
        return Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, weeks));
    }

    private LocalDate windowEnd() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private LocalDate windowStart(int clampedWeeks) {
        return windowEnd().minusWeeks(clampedWeeks - 1L);
    }

    private List<LocalDate> buildWeekDates(LocalDate windowStart) {
        LocalDate windowEnd = windowEnd();
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = windowStart;
        while (!cursor.isAfter(windowEnd)) {
            dates.add(cursor);
            cursor = cursor.plusWeeks(1);
        }
        return dates;
    }

    /**
     * Computes trend direction by comparing the commit counts of the final two
     * weeks in the returned timeline. Returns {@code STABLE} when fewer than
     * two weeks are present.
     */
    String computeTrendDirection(List<OutcomeCoverageWeek> weeks) {
        if (weeks.size() < 2) {
            return TREND_STABLE;
        }
        OutcomeCoverageWeek latest = weeks.get(weeks.size() - 1);
        OutcomeCoverageWeek previous = weeks.get(weeks.size() - 2);
        if (latest.commitCount() > previous.commitCount()) {
            return TREND_RISING;
        }
        if (latest.commitCount() < previous.commitCount()) {
            return TREND_FALLING;
        }
        return TREND_STABLE;
    }

    /**
     * Queries category commit counts per user over the given date range
     * (inclusive start, exclusive end) for the specified user IDs.
     *
     * <p>Filters by non-null {@code category} only. Results are grouped by
     * {@code owner_user_id} and {@code category}.
     */
    private Map<UUID, Map<String, Long>> queryCategoryCounts(
            UUID orgId, List<UUID> userIds, LocalDate from, LocalDate to) {

        String sql = """
                SELECT wp.owner_user_id, wc.category, COUNT(*) AS commit_count
                FROM weekly_commits wc
                JOIN weekly_plans wp
                    ON wp.id = wc.weekly_plan_id
                    AND wp.org_id = wc.org_id
                WHERE wc.org_id = :orgId
                  AND wp.owner_user_id IN (:userIds)
                  AND wp.week_start_date >= :from
                  AND wp.week_start_date < :to
                  AND wc.category IS NOT NULL
                GROUP BY wp.owner_user_id, wc.category
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userIds", userIds)
                .addValue("from", from)
                .addValue("to", to);

        Map<UUID, Map<String, Long>> result = new HashMap<>();
        namedJdbc.query(sql, params, rs -> {
            UUID userId = (UUID) rs.getObject("owner_user_id");
            String category = rs.getString("category");
            long count = rs.getLong("commit_count");
            result.computeIfAbsent(userId, k -> new HashMap<>()).put(category, count);
        });
        return result;
    }

    /**
     * Converts raw counts to proportions (each value divided by the total).
     * Returns an empty map if the input is empty.
     */
    private Map<String, Double> toProportions(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return Map.of();
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> proportions = new HashMap<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            proportions.put(entry.getKey(), (double) entry.getValue() / total);
        }
        return proportions;
    }

    /**
     * Finds the category with the largest absolute change in proportion
     * between the recent and prior distributions.
     *
     * @param recent recent half-window proportions
     * @param prior  prior half-window proportions
     * @return the biggest shift, or {@code null} if both distributions are empty
     */
    private CategoryShift computeBiggestShift(
            Map<String, Double> recent, Map<String, Double> prior) {

        // Collect all categories from both distributions
        Map<String, Double> deltas = new HashMap<>();
        for (String category : recent.keySet()) {
            double delta = recent.get(category) - prior.getOrDefault(category, 0.0);
            deltas.put(category, delta);
        }
        for (String category : prior.keySet()) {
            if (!deltas.containsKey(category)) {
                deltas.put(category, -prior.get(category));
            }
        }

        if (deltas.isEmpty()) {
            return null;
        }

        // Pick the category with the largest absolute delta
        return deltas.entrySet().stream()
                .max(Comparator.comparingDouble(e -> Math.abs(e.getValue())))
                .map(e -> new CategoryShift(e.getKey(), e.getValue()))
                .orElse(null);
    }
}
