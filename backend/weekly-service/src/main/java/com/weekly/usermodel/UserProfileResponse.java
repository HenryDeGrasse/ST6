package com.weekly.usermodel;

import java.util.List;
import java.util.Map;

/**
 * API response DTO for a user's model profile.
 *
 * <p>Returned by {@code GET /api/v1/users/me/profile}.
 *
 * @param userId             the user ID as a string
 * @param weeksAnalyzed      number of weeks of history used to derive this profile
 * @param performanceProfile computed performance metrics
 * @param preferences        derived user preferences
 * @param trends             directional trend signals
 */
public record UserProfileResponse(
        String userId,
        int weeksAnalyzed,
        PerformanceProfile performanceProfile,
        Preferences preferences,
        Trends trends
) {

    /**
     * Derived performance metrics for the user.
     *
     * @param estimationAccuracy      confidence accuracy against completion outcomes
     * @param completionReliability   fraction of reconciled commits marked DONE
     * @param avgCommitsPerWeek       average number of commits per week
     * @param avgCarryForwardPerWeek  average number of carry-forward commits per week
     * @param topCategories           top categories by DONE completion rate (up to 3)
     * @param categoryCompletionRates DONE rate per {@code CommitCategory}
     * @param priorityCompletionRates DONE rate per {@code ChessPriority}
     */
    public record PerformanceProfile(
            double estimationAccuracy,
            double completionReliability,
            double avgCommitsPerWeek,
            double avgCarryForwardPerWeek,
            List<String> topCategories,
            Map<String, Double> categoryCompletionRates,
            Map<String, Double> priorityCompletionRates
    ) {}

    /**
     * Derived preference signals for the user.
     *
     * @param typicalPriorityPattern average weekly chess-priority mix, e.g. {@code 1K-2Q-3R}
     * @param recurringCommitTitles  commonly repeated commit titles from recent history
     * @param avgCheckInsPerWeek     average number of progress entries per analyzed week
     * @param preferredUpdateDays    day-of-week names (e.g. {@code MONDAY}) ordered by frequency
     */
    public record Preferences(
            String typicalPriorityPattern,
            List<String> recurringCommitTitles,
            double avgCheckInsPerWeek,
            List<String> preferredUpdateDays
    ) {}

    /**
     * Directional trend signals derived from the rolling window.
     *
     * <p>Each field is one of: {@code IMPROVING}, {@code STABLE}, {@code WORSENING}.
     *
     * @param strategicAlignmentTrend directional signal for strategic alignment
     * @param completionTrend         directional signal for completion reliability
     * @param carryForwardTrend       directional signal for carry-forward velocity
     */
    public record Trends(
            String strategicAlignmentTrend,
            String completionTrend,
            String carryForwardTrend
    ) {}
}
