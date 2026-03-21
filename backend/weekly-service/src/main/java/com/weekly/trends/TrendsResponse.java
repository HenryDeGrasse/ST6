package com.weekly.trends;

import java.util.List;
import java.util.Map;

/**
 * Rolling-window aggregation result for a single user's planning trends.
 *
 * <p>Returned by {@code GET /api/v1/users/me/trends?weeks=N}.
 *
 * @param weeksAnalyzed              actual number of weeks with at least one commit
 * @param windowStart                ISO date of the earliest Monday in the window
 * @param windowEnd                  ISO date of the most recent Monday in the window
 * @param strategicAlignmentRate     fraction of commits linked to an RCDO outcome (0–1)
 * @param teamStrategicAlignmentRate org-wide strategic alignment rate for comparison (0–1)
 * @param avgCarryForwardPerWeek     average carry-forward commits per week with data
 * @param carryForwardStreak         consecutive recent weeks with any carry-forward commit
 * @param avgConfidence              mean confidence across all commits in the window (0–1)
 * @param completionAccuracy         mean completion rate across reconciled weeks (0–1)
 * @param confidenceAccuracyGap      {@code avgConfidence - completionAccuracy} (positive = overconfident)
 * @param priorityDistribution       fraction of commits per ChessPriority name (values sum ≤ 1)
 * @param categoryDistribution       fraction of commits per CommitCategory name (values sum ≤ 1)
 * @param weekPoints                 per-week breakdown, oldest to newest
 * @param insights                   generated insight objects for notable patterns
 */
public record TrendsResponse(
        int weeksAnalyzed,
        String windowStart,
        String windowEnd,
        double strategicAlignmentRate,
        double teamStrategicAlignmentRate,
        double avgCarryForwardPerWeek,
        int carryForwardStreak,
        double avgConfidence,
        double completionAccuracy,
        double confidenceAccuracyGap,
        Map<String, Double> priorityDistribution,
        Map<String, Double> categoryDistribution,
        List<WeekTrendPoint> weekPoints,
        List<TrendInsight> insights
) {
}
