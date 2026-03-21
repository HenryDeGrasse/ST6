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
 * @param avgEstimatedHoursPerWeek   average estimated hours across weeks with estimate data
 * @param avgActualHoursPerWeek      average actual hours across weeks with actual-hour data
 * @param hoursAccuracyRatio         {@code totalActualHours / totalEstimatedHours} across only
 *                                   weeks that have both values, or {@code null}
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
        Double avgEstimatedHoursPerWeek,
        Double avgActualHoursPerWeek,
        Double hoursAccuracyRatio,
        Map<String, Double> priorityDistribution,
        Map<String, Double> categoryDistribution,
        List<WeekTrendPoint> weekPoints,
        List<TrendInsight> insights
) {
    /**
     * Backwards-compatible constructor for callers that do not provide hour metrics.
     */
    public TrendsResponse(
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
        this(
                weeksAnalyzed,
                windowStart,
                windowEnd,
                strategicAlignmentRate,
                teamStrategicAlignmentRate,
                avgCarryForwardPerWeek,
                carryForwardStreak,
                avgConfidence,
                completionAccuracy,
                confidenceAccuracyGap,
                null,
                null,
                null,
                priorityDistribution,
                categoryDistribution,
                weekPoints,
                insights
        );
    }
}
