package com.weekly.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstraction for manager-dashboard data needed by the AI insight flow.
 *
 * <p>This lives in the shared package so the AI module can read manager-week
 * context without depending directly on plan-module services or repositories.
 */
public interface ManagerInsightDataProvider {

    /** Default rolling window of weeks to include in historical context. */
    int DEFAULT_WINDOW_WEEKS = 4;

    /**
     * Returns the team context needed to draft manager insights for a week,
     * using the default {@link #DEFAULT_WINDOW_WEEKS}-week historical window.
     */
    default ManagerWeekContext getManagerWeekContext(UUID orgId, UUID managerId, LocalDate weekStart) {
        return getManagerWeekContext(orgId, managerId, weekStart, DEFAULT_WINDOW_WEEKS);
    }

    /**
     * Returns the team context needed to draft manager insights for a week,
     * including historical data across the given number of preceding weeks.
     *
     * @param windowWeeks number of weeks of history to include (must be ≥ 1)
     */
    ManagerWeekContext getManagerWeekContext(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks);

    // ── Context records ──────────────────────────────────────────────────────

    /**
     * Team-level context for a manager and week, including multi-week history.
     */
    record ManagerWeekContext(
            String weekStart,
            ReviewCounts reviewCounts,
            List<TeamMemberContext> teamMembers,
            List<RcdoFocusContext> rcdoFocuses,
            // Historical context (populated when windowWeeks > 1)
            List<CarryForwardStreak> carryForwardStreaks,
            List<OutcomeCoverageTrend> outcomeCoverageTrends,
            List<LateLockPattern> lateLockPatterns,
            ReviewTurnaroundStats reviewTurnaroundStats,
            // Cross-module prompt-enrichment context
            DiagnosticContext diagnosticContext,
            List<OutcomeUrgencyContext> outcomeUrgencies,
            StrategicSlackContext strategicSlackContext
    ) {
        /**
         * Backward-compatible constructor for callers that only populate the
         * original snapshot + historical fields.
         */
        public ManagerWeekContext(
                String weekStart,
                ReviewCounts reviewCounts,
                List<TeamMemberContext> teamMembers,
                List<RcdoFocusContext> rcdoFocuses,
                List<CarryForwardStreak> carryForwardStreaks,
                List<OutcomeCoverageTrend> outcomeCoverageTrends,
                List<LateLockPattern> lateLockPatterns,
                ReviewTurnaroundStats reviewTurnaroundStats
        ) {
            this(
                    weekStart,
                    reviewCounts,
                    teamMembers,
                    rcdoFocuses,
                    carryForwardStreaks,
                    outcomeCoverageTrends,
                    lateLockPatterns,
                    reviewTurnaroundStats,
                    null,
                    List.of(),
                    null
            );
        }
    }

    /**
     * Review-status aggregates.
     */
    record ReviewCounts(
            int pending,
            int approved,
            int changesRequested
    ) {}

    /**
     * Per-direct-report summary context.
     */
    record TeamMemberContext(
            String userId,
            String state,
            String reviewStatus,
            int commitCount,
            int incompleteCount,
            int issueCount,
            int nonStrategicCount,
            int kingCount,
            int queenCount,
            boolean stale,
            boolean lateLock
    ) {}

    /**
     * Strategic focus summary from the RCDO roll-up.
     */
    record RcdoFocusContext(
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName,
            int commitCount,
            int kingCount,
            int queenCount
    ) {}

    // ── Historical context records ────────────────────────────────────────────

    /**
     * Carry-forward streak for a single team member.
     *
     * <p>A streak counts consecutive weeks (most-recent-first) where the member
     * had 2 or more items carried forward from the previous week.
     *
     * @param userId           the direct report's user ID
     * @param streakWeeks      number of consecutive weeks in the current streak
     * @param carriedItemTitles commit titles of items carried in the most recent streak week
     */
    record CarryForwardStreak(
            String userId,
            int streakWeeks,
            List<String> carriedItemTitles
    ) {}

    /**
     * Commit count for a single outcome in a single week.
     */
    record WeeklyCommitCount(
            String weekStart,
            int commitCount
    ) {}

    /**
     * Per-outcome trend of commit coverage across the rolling window.
     *
     * <p>Useful for detecting declining strategic attention — e.g. an outcome
     * that had 4 commits in week 1 but only 1 in week 4.
     *
     * @param outcomeId   the RCDO outcome ID
     * @param outcomeName snapshot outcome name (may be the ID string if no snapshot)
     * @param weekCounts  ordered list from oldest week to most recent
     */
    record OutcomeCoverageTrend(
            String outcomeId,
            String outcomeName,
            List<WeeklyCommitCount> weekCounts
    ) {}

    /**
     * Late-lock frequency for a single team member across the rolling window.
     *
     * @param userId        the direct report's user ID
     * @param lateLockWeeks number of weeks in the window where the plan was late-locked
     * @param windowWeeks   total weeks in the measurement window
     */
    record LateLockPattern(
            String userId,
            int lateLockWeeks,
            int windowWeeks
    ) {}

    /**
     * Aggregate review-turnaround statistics for the team across the rolling window.
     *
     * <p>Turnaround is measured from plan lock time to first manager-review submission,
     * which is the best available approximation given the current schema
     * (no explicit {@code reconciledAt} timestamp).
     *
     * @param avgDaysToReview average days from lock to first review
     * @param sampleSize      number of plans included in the average
     */
    record ReviewTurnaroundStats(
            double avgDaysToReview,
            int sampleSize
    ) {}

    /**
     * Consolidated diagnostic context derived from {@link DiagnosticDataProvider}.
     */
    record DiagnosticContext(
            List<UserCategoryShiftContext> categoryShifts,
            List<UserOutcomeCoverageContext> outcomeCoverages,
            List<UserBlockerFrequencyContext> blockerFrequencies
    ) {}

    /**
     * Category-distribution shift for a single team member.
     */
    record UserCategoryShiftContext(
            String userId,
            Map<String, Double> currentPeriod,
            Map<String, Double> priorPeriod
    ) {}

    /**
     * Per-user outcome-coverage detail for the rolling window.
     */
    record UserOutcomeCoverageContext(
            String userId,
            List<UserOutcomeWeeklyCountContext> outcomes
    ) {}

    /**
     * Per-outcome weekly commit count for a team member.
     */
    record UserOutcomeWeeklyCountContext(
            String outcomeId,
            String weekStart,
            int commitCount
    ) {}

    /**
     * Blocker / at-risk frequency summary for a single team member.
     */
    record UserBlockerFrequencyContext(
            String userId,
            int atRiskCount,
            int blockedCount,
            int totalCheckIns
    ) {}

    /**
     * Urgency snapshot for a single outcome relevant to the prompt context.
     */
    record OutcomeUrgencyContext(
            String outcomeId,
            String outcomeName,
            String targetDate,
            BigDecimal progressPct,
            BigDecimal expectedProgressPct,
            String urgencyBand,
            long daysRemaining
    ) {}

    /**
     * Strategic slack summary relevant to the manager prompt context.
     */
    record StrategicSlackContext(
            String slackBand,
            BigDecimal strategicFocusFloor,
            int atRiskCount,
            int criticalCount
    ) {}
}
