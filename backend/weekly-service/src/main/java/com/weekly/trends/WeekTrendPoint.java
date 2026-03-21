package com.weekly.trends;

import java.util.Map;

/**
 * Per-week snapshot of a user's planning metrics.
 *
 * <p>One point is generated for every Monday in the rolling window,
 * even if no plan existed for that week (zeroed metrics, {@code hasActuals = false}).
 *
 * @param weekStart          ISO date of the Monday for this week
 * @param totalCommits       number of commits in the plan (0 if no plan)
 * @param strategicCommits   commits linked to an RCDO outcome
 * @param carryForwardCommits commits copied from the previous week
 * @param avgConfidence      mean confidence across commits (0.0 if none set)
 * @param completionRate     fraction of DONE actuals (0.0 if not yet reconciled)
 * @param hasActuals         true if the plan reached RECONCILED or CARRY_FORWARD
 * @param priorityCounts     raw counts per ChessPriority name
 * @param categoryCounts     raw counts per CommitCategory name
 */
public record WeekTrendPoint(
        String weekStart,
        int totalCommits,
        int strategicCommits,
        int carryForwardCommits,
        double avgConfidence,
        double completionRate,
        boolean hasActuals,
        Map<String, Integer> priorityCounts,
        Map<String, Integer> categoryCounts
) {
}
