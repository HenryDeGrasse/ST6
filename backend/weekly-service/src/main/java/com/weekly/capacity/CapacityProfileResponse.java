package com.weekly.capacity;

import java.math.BigDecimal;

/**
 * API response DTO for a user's capacity profile.
 *
 * <p>Returned by {@code GET /api/v1/users/me/capacity}.
 *
 * @param orgId                  the organisation ID
 * @param userId                 the user ID
 * @param weeksAnalyzed          number of weeks of history included in the profile
 * @param avgEstimatedHours      mean estimated hours per week (1 d.p.)
 * @param avgActualHours         mean actual hours per week (1 d.p.)
 * @param estimationBias         ratio of avg-actual / avg-estimated, or {@code null}
 *                               when no estimated hours were recorded
 * @param realisticWeeklyCap     p50 of actual weekly totals (1 d.p.) — the sustainable cap
 * @param categoryBiasJson       JSON array of per-category bias breakdowns
 * @param priorityCompletionJson JSON array of per-priority completion statistics
 * @param confidenceLevel        data quality tier: {@code LOW}, {@code MEDIUM}, or {@code HIGH}
 * @param computedAt             ISO-8601 timestamp of the most recent profile computation
 */
public record CapacityProfileResponse(
        String orgId,
        String userId,
        int weeksAnalyzed,
        BigDecimal avgEstimatedHours,
        BigDecimal avgActualHours,
        BigDecimal estimationBias,
        BigDecimal realisticWeeklyCap,
        String categoryBiasJson,
        String priorityCompletionJson,
        String confidenceLevel,
        String computedAt) {

    /**
     * Maps a {@link CapacityProfileEntity} to its API response representation.
     *
     * @param entity the capacity profile entity to convert; must not be {@code null}
     * @return the corresponding response DTO
     */
    public static CapacityProfileResponse from(CapacityProfileEntity entity) {
        return new CapacityProfileResponse(
                entity.getOrgId().toString(),
                entity.getUserId().toString(),
                entity.getWeeksAnalyzed(),
                entity.getAvgEstimatedHours(),
                entity.getAvgActualHours(),
                entity.getEstimationBias(),
                entity.getRealisticWeeklyCap(),
                entity.getCategoryBiasJson(),
                entity.getPriorityCompletionJson(),
                entity.getConfidenceLevel(),
                entity.getComputedAt() != null ? entity.getComputedAt().toString() : null);
    }
}
