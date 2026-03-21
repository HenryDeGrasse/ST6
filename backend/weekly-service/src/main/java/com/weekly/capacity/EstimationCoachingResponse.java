package com.weekly.capacity;

import java.util.List;

/**
 * API response DTO for the estimation-coaching endpoint.
 *
 * <p>Returned by {@code GET /api/v1/users/me/estimation-coaching?planId=X}.
 *
 * @param thisWeekEstimated sum of estimated hours for all commits in the requested plan,
 *                          or {@code 0.0} when no estimates were recorded
 * @param thisWeekActual    sum of actual hours for all actuals in the requested plan,
 *                          or {@code 0.0} when no actuals were recorded
 * @param accuracyRatio     {@code thisWeekActual / thisWeekEstimated} ratio (2 d.p.),
 *                          or {@code null} when {@code thisWeekEstimated} is zero
 * @param overallBias       historical {@code avgActual / avgEstimated} ratio from the
 *                          user's capacity profile, or {@code null} when no profile exists
 * @param confidenceLevel   profile data quality tier: {@code LOW}, {@code MEDIUM}, or
 *                          {@code HIGH}; defaults to {@code "LOW"} when no profile exists
 * @param categoryInsights  per-category bias breakdown with optional coaching tips;
 *                          tips are non-null only for categories whose bias deviates
 *                          from 1.0 by ≥15%
 * @param priorityInsights  per-priority completion-rate statistics from the rolling profile
 */
public record EstimationCoachingResponse(
        Double thisWeekEstimated,
        Double thisWeekActual,
        Double accuracyRatio,
        Double overallBias,
        String confidenceLevel,
        List<CategoryInsight> categoryInsights,
        List<PriorityInsight> priorityInsights) {
}
