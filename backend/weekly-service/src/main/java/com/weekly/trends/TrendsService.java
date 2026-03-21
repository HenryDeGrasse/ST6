package com.weekly.trends;

import java.util.UUID;

/**
 * Port for cross-week trend aggregation.
 *
 * <p>Implemented by {@link DefaultTrendsService}. Extracted as an interface
 * so controller unit tests can mock it without inline bytecode generation.
 */
public interface TrendsService {

    /**
     * Computes the rolling-window trend metrics for the given user.
     *
     * @param orgId  the organisation ID from the authenticated JWT
     * @param userId the user whose trends to compute
     * @param weeks  size of the rolling window (clamped to [1, 26])
     * @return aggregated trend data with per-week breakdown and insights
     */
    TrendsResponse computeTrends(UUID orgId, UUID userId, int weeks);
}
