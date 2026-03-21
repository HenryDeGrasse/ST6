package com.weekly.shared;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstraction for multi-week diagnostic data needed by the analytics intelligence layer.
 *
 * <p>This lives in the shared package so the analytics module can read
 * diagnostic context without depending directly on plan-module services or
 * repositories.
 *
 * <p>All types used here are from {@code java.*} and {@code java.time.*} only —
 * no module-package dependencies (see ModuleBoundaryTest).
 */
public interface DiagnosticDataProvider {

    /**
     * Returns per-user category distribution shifts across a rolling window.
     *
     * @param orgId       the organisation ID
     * @param managerId   the manager's user ID
     * @param weekStart   the most-recent week's Monday date
     * @param windowWeeks number of weeks to consider (must be ≥ 1)
     */
    CategoryShiftContext getCategoryShifts(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks);

    /**
     * Returns per-user outcome-coverage counts across a rolling window.
     *
     * @param orgId       the organisation ID
     * @param managerId   the manager's user ID
     * @param weekStart   the most-recent week's Monday date
     * @param windowWeeks number of weeks to consider (must be ≥ 1)
     */
    PerUserOutcomeCoverageContext getPerUserOutcomeCoverage(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks);

    /**
     * Returns per-user blocker and at-risk frequency counts across a rolling window.
     *
     * @param orgId       the organisation ID
     * @param managerId   the manager's user ID
     * @param weekStart   the most-recent week's Monday date
     * @param windowWeeks number of weeks to consider (must be ≥ 1)
     */
    BlockerFrequencyContext getBlockerFrequency(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks);

    // ── Context records ──────────────────────────────────────────────────────

    /**
     * Team-level category-shift context for a rolling window.
     *
     * @param shifts list of per-user category distribution shifts
     */
    record CategoryShiftContext(List<UserCategoryShift> shifts) {}

    /**
     * Category distribution shift for a single team member, comparing the most-recent
     * half of the window to the prior half.
     *
     * @param userId        the direct report's user ID
     * @param currentPeriod category → fractional share in the current period
     * @param priorPeriod   category → fractional share in the prior period
     */
    record UserCategoryShift(
            UUID userId,
            Map<String, Double> currentPeriod,
            Map<String, Double> priorPeriod
    ) {}

    /**
     * Team-level outcome-coverage context for a rolling window.
     *
     * @param coverages list of per-user outcome coverage data
     */
    record PerUserOutcomeCoverageContext(List<UserOutcomeCoverage> coverages) {}

    /**
     * Outcome coverage for a single team member.
     *
     * @param userId   the direct report's user ID
     * @param outcomes ordered list of per-outcome weekly commit counts
     */
    record UserOutcomeCoverage(
            UUID userId,
            List<OutcomeWeeklyCount> outcomes
    ) {}

    /**
     * Per-outcome commit count for a single week.
     *
     * @param outcomeId   the RCDO outcome ID
     * @param weekStart   the week's Monday date as an ISO-8601 string
     * @param commitCount number of commits linked to this outcome in this week
     */
    record OutcomeWeeklyCount(
            UUID outcomeId,
            String weekStart,
            int commitCount
    ) {}

    /**
     * Team-level blocker-frequency context for a rolling window.
     *
     * @param frequencies list of per-user blocker frequency data
     */
    record BlockerFrequencyContext(List<UserBlockerFrequency> frequencies) {}

    /**
     * Blocker and at-risk check-in frequency for a single team member.
     *
     * @param userId       the direct report's user ID
     * @param atRiskCount  number of check-ins with at-risk status in the window
     * @param blockedCount number of check-ins with blocked status in the window
     * @param totalCheckIns total check-ins for this user in the window
     */
    record UserBlockerFrequency(
            UUID userId,
            int atRiskCount,
            int blockedCount,
            int totalCheckIns
    ) {}
}
