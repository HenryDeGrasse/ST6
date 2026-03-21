package com.weekly.admin;

/**
 * AI feature usage metrics for the admin dashboard.
 *
 * <p>Surfaces suggestion acceptance rates from {@code ai_suggestion_feedback},
 * org-scoped cache efficiency derived from {@link com.weekly.ai.AiCacheService}
 * hit/miss counters, and coarse token-spend estimates inferred from those
 * cache misses/hits.
 */
public record AiUsageMetrics(
        int weeks,
        String windowStart,
        String windowEnd,
        long totalFeedbackCount,
        long acceptedCount,
        long deferredCount,
        long declinedCount,
        double acceptanceRate,
        long cacheHits,
        long cacheMisses,
        double cacheHitRate,
        long approximateTokensSpent,
        long approximateTokensSaved
) {}
