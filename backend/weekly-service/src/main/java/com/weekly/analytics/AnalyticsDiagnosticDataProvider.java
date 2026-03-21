package com.weekly.analytics;

import com.weekly.auth.OrgGraphClient;
import com.weekly.shared.DiagnosticDataProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed implementation of {@link DiagnosticDataProvider} for the multi-week
 * analytics intelligence layer.
 *
 * <p>Resolves the manager's direct reports via {@link OrgGraphClient} and queries
 * the plan/commit/progress schema to produce diagnostic context objects consumed
 * by the prediction and AI suggestion services.
 *
 * <p>All queries filter by {@code org_id} explicitly. Queries on regular tables
 * also benefit from Row-Level Security set by
 * {@link com.weekly.config.TenantRlsTransactionListener}.
 */
@Component
public class AnalyticsDiagnosticDataProvider implements DiagnosticDataProvider {

    private final OrgGraphClient orgGraphClient;
    private final NamedParameterJdbcTemplate namedJdbc;

    public AnalyticsDiagnosticDataProvider(
            JdbcTemplate jdbcTemplate, OrgGraphClient orgGraphClient) {
        this.orgGraphClient = orgGraphClient;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private void validateWindowWeeks(int windowWeeks) {
        if (windowWeeks < 1) {
            throw new IllegalArgumentException("windowWeeks must be >= 1");
        }
    }

    // ── 1. Category Shifts ────────────────────────────────────────────────────

    /**
     * Computes per-user category distribution shifts between the prior half and
     * current half of the rolling window.
     *
     * <p>Queries {@code weekly_commits} joined with {@code weekly_plans} for the
     * manager's direct reports. The window is split so that when {@code windowWeeks}
     * is odd the current (more-recent) half receives the extra week.
     *
     * @param orgId       the organisation ID
     * @param managerId   the manager's user ID
     * @param weekStart   the most-recent week's Monday date
     * @param windowWeeks number of weeks to consider (must be ≥ 1)
     * @return category-shift context for the team
     */
    @Override
    @Transactional(readOnly = true)
    public CategoryShiftContext getCategoryShifts(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks) {

        validateWindowWeeks(windowWeeks);

        List<UUID> directReports = orgGraphClient.getDirectReports(orgId, managerId);
        if (directReports.isEmpty()) {
            return new CategoryShiftContext(List.of());
        }

        // The window spans [windowStart, weekStart] inclusive — i.e. windowWeeks weeks.
        // windowEndExclusive is the Monday after weekStart so the range predicate uses <.
        LocalDate windowEndExclusive = weekStart.plusWeeks(1);

        // Split evenly; when odd, current (recent) half gets the extra week.
        int currentHalfWeeks = (windowWeeks + 1) / 2;
        int priorHalfWeeks = windowWeeks - currentHalfWeeks;
        LocalDate currentHalfStart = windowEndExclusive.minusWeeks(currentHalfWeeks);
        LocalDate priorHalfStart = currentHalfStart.minusWeeks(priorHalfWeeks);

        Map<UUID, Map<String, Long>> currentCounts =
                queryCategoryCounts(orgId, directReports, currentHalfStart, windowEndExclusive);
        Map<UUID, Map<String, Long>> priorCounts =
                queryCategoryCounts(orgId, directReports, priorHalfStart, currentHalfStart);

        List<UserCategoryShift> shifts = new ArrayList<>();
        for (UUID userId : directReports) {
            Map<String, Long> currentUserCounts = currentCounts.getOrDefault(userId, Map.of());
            Map<String, Long> priorUserCounts = priorCounts.getOrDefault(userId, Map.of());

            if (currentUserCounts.isEmpty() && priorUserCounts.isEmpty()) {
                continue;
            }

            Map<String, Double> currentPeriod = toProportions(currentUserCounts);
            Map<String, Double> priorPeriod = toProportions(priorUserCounts);
            shifts.add(new UserCategoryShift(userId, currentPeriod, priorPeriod));
        }

        return new CategoryShiftContext(shifts);
    }

    // ── 2. Per-User Outcome Coverage ──────────────────────────────────────────

    /**
     * Returns per-user, per-outcome weekly commit counts for the manager's direct
     * reports over the rolling window.
     *
     * <p>Queries {@code mv_outcome_coverage_weekly} and joins back to
     * {@code weekly_plans} / {@code weekly_commits} so outcome coverage uses the
     * same non-draft materialized-view semantics while still recovering per-user
     * granularity. Only commits with a non-null {@code outcome_id} are included.
     *
     * @param orgId       the organisation ID
     * @param managerId   the manager's user ID
     * @param weekStart   the most-recent week's Monday date
     * @param windowWeeks number of weeks to consider (must be ≥ 1)
     * @return per-user outcome-coverage context
     */
    @Override
    @Transactional(readOnly = true)
    public PerUserOutcomeCoverageContext getPerUserOutcomeCoverage(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks) {

        validateWindowWeeks(windowWeeks);

        List<UUID> directReports = orgGraphClient.getDirectReports(orgId, managerId);
        if (directReports.isEmpty()) {
            return new PerUserOutcomeCoverageContext(List.of());
        }

        LocalDate windowStartDate = weekStart.minusWeeks(windowWeeks - 1L);

        String sql = """
                SELECT
                    wp.owner_user_id,
                    mv.outcome_id,
                    mv.week_start_date,
                    COUNT(wc.id) AS commit_count
                FROM mv_outcome_coverage_weekly mv
                JOIN weekly_plans wp
                    ON wp.org_id          = mv.org_id
                    AND wp.week_start_date = mv.week_start_date
                JOIN weekly_commits wc
                    ON wc.weekly_plan_id = wp.id
                    AND wc.org_id        = wp.org_id
                    AND wc.outcome_id    = mv.outcome_id
                WHERE mv.org_id = :orgId
                  AND wp.owner_user_id IN (:userIds)
                  AND mv.week_start_date >= :windowStart
                  AND mv.week_start_date <= :weekStart
                  AND wp.state IN ('LOCKED', 'RECONCILING', 'RECONCILED', 'CARRY_FORWARD')
                  AND wp.deleted_at IS NULL
                  AND wc.deleted_at IS NULL
                GROUP BY wp.owner_user_id, mv.outcome_id, mv.week_start_date
                ORDER BY wp.owner_user_id, mv.outcome_id, mv.week_start_date
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userIds", directReports)
                .addValue("windowStart", windowStartDate)
                .addValue("weekStart", weekStart);

        // Maintain insertion order so the list preserves (user, outcome, week) ordering.
        Map<UUID, List<OutcomeWeeklyCount>> byUser = new LinkedHashMap<>();
        namedJdbc.query(sql, params, rs -> {
            UUID userId = (UUID) rs.getObject("owner_user_id");
            UUID outcomeId = (UUID) rs.getObject("outcome_id");
            String weekStartStr = rs.getDate("week_start_date").toLocalDate().toString();
            int commitCount = rs.getInt("commit_count");
            byUser.computeIfAbsent(userId, k -> new ArrayList<>())
                    .add(new OutcomeWeeklyCount(outcomeId, weekStartStr, commitCount));
        });

        List<UserOutcomeCoverage> coverages = directReports.stream()
                .filter(byUser::containsKey)
                .map(userId -> new UserOutcomeCoverage(userId, byUser.get(userId)))
                .toList();

        return new PerUserOutcomeCoverageContext(coverages);
    }

    // ── 3. Blocker Frequency ─────────────────────────────────────────────────

    /**
     * Returns per-user counts of AT_RISK and BLOCKED check-in entries for the
     * manager's direct reports over the rolling window.
     *
     * <p>Queries {@code progress_entries} joined via {@code weekly_commits} to
     * {@code weekly_plans} so that entries can be filtered by owner and date range.
     * Direct reports with no check-ins in the window are included with zero counts.
     *
     * @param orgId       the organisation ID
     * @param managerId   the manager's user ID
     * @param weekStart   the most-recent week's Monday date
     * @param windowWeeks number of weeks to consider (must be ≥ 1)
     * @return blocker-frequency context for the team
     */
    @Override
    @Transactional(readOnly = true)
    public BlockerFrequencyContext getBlockerFrequency(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks) {

        validateWindowWeeks(windowWeeks);

        List<UUID> directReports = orgGraphClient.getDirectReports(orgId, managerId);
        if (directReports.isEmpty()) {
            return new BlockerFrequencyContext(List.of());
        }

        LocalDate windowStartDate = weekStart.minusWeeks(windowWeeks - 1L);

        String sql = """
                SELECT
                    wp.owner_user_id,
                    COUNT(*)                                              AS total_check_ins,
                    COUNT(*) FILTER (WHERE pe.status = 'AT_RISK')        AS at_risk_count,
                    COUNT(*) FILTER (WHERE pe.status = 'BLOCKED')        AS blocked_count
                FROM progress_entries pe
                JOIN weekly_commits wc
                    ON wc.id      = pe.commit_id
                    AND wc.org_id = pe.org_id
                JOIN weekly_plans wp
                    ON wp.id      = wc.weekly_plan_id
                    AND wp.org_id = wc.org_id
                WHERE pe.org_id = :orgId
                  AND wp.owner_user_id IN (:userIds)
                  AND wp.week_start_date >= :windowStart
                  AND wp.week_start_date <= :weekStart
                  AND wp.deleted_at IS NULL
                  AND wc.deleted_at IS NULL
                GROUP BY wp.owner_user_id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("userIds", directReports)
                .addValue("windowStart", windowStartDate)
                .addValue("weekStart", weekStart);

        Map<UUID, UserBlockerFrequency> queryResults = new HashMap<>();
        namedJdbc.query(sql, params, rs -> {
            UUID userId = (UUID) rs.getObject("owner_user_id");
            int totalCheckIns = rs.getInt("total_check_ins");
            int atRiskCount = rs.getInt("at_risk_count");
            int blockedCount = rs.getInt("blocked_count");
            queryResults.put(userId,
                    new UserBlockerFrequency(userId, atRiskCount, blockedCount, totalCheckIns));
        });

        // All direct reports are included; those without check-ins receive zero counts.
        List<UserBlockerFrequency> frequencies = directReports.stream()
                .map(userId -> queryResults.getOrDefault(
                        userId,
                        new UserBlockerFrequency(userId, 0, 0, 0)))
                .toList();

        return new BlockerFrequencyContext(frequencies);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Queries category commit counts per user over the given date range
     * (inclusive {@code from}, exclusive {@code to}).
     *
     * <p>Filters by non-null {@code category} only.
     */
    private Map<UUID, Map<String, Long>> queryCategoryCounts(
            UUID orgId, List<UUID> userIds, LocalDate from, LocalDate to) {

        String sql = """
                SELECT wp.owner_user_id, wc.category, COUNT(*) AS commit_count
                FROM weekly_commits wc
                JOIN weekly_plans wp
                    ON wp.id      = wc.weekly_plan_id
                    AND wp.org_id = wc.org_id
                WHERE wc.org_id = :orgId
                  AND wp.owner_user_id IN (:userIds)
                  AND wp.week_start_date >= :from
                  AND wp.week_start_date < :to
                  AND wp.deleted_at IS NULL
                  AND wc.deleted_at IS NULL
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
     * Converts raw category counts to proportions (each value divided by the total).
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
}
