package com.weekly.shared;

import java.util.List;
import java.util.UUID;

/**
 * Shared analytics seam for the forecasting module.
 *
 * <p>Allows forecast logic to consume outcome-level historical coverage without
 * depending on analytics package internals.
 */
public interface ForecastAnalyticsProvider {

    OutcomeCoverageHistory getOutcomeCoverageHistory(UUID orgId, UUID outcomeId, int weeks);

    record OutcomeCoverageHistory(
            UUID outcomeId,
            List<OutcomeCoveragePoint> weeks,
            String trendDirection
    ) {}

    record OutcomeCoveragePoint(
            String weekStart,
            int commitCount,
            int contributorCount,
            int highPriorityCount
    ) {}
}
