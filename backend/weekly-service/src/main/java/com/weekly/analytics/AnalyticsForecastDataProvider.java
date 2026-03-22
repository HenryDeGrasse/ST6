package com.weekly.analytics;

import com.weekly.analytics.dto.OutcomeCoverageTimeline;
import com.weekly.shared.ForecastAnalyticsProvider;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Shared adapter exposing analytics history to the forecasting module.
 */
@Service
public class AnalyticsForecastDataProvider implements ForecastAnalyticsProvider {

    private static final int DEFAULT_WEEKS = 8;

    private final AnalyticsService analyticsService;

    public AnalyticsForecastDataProvider(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public OutcomeCoverageHistory getOutcomeCoverageHistory(UUID orgId, UUID outcomeId, int weeks) {
        int effectiveWeeks = weeks > 0 ? weeks : DEFAULT_WEEKS;
        OutcomeCoverageTimeline timeline = analyticsService.getOutcomeCoverageTimeline(orgId, outcomeId, effectiveWeeks);
        return new OutcomeCoverageHistory(
                outcomeId,
                timeline.weeks().stream()
                        .map(week -> new OutcomeCoveragePoint(
                                week.weekStart(),
                                week.commitCount(),
                                week.contributorCount(),
                                week.highPriorityCount()))
                        .toList(),
                timeline.trendDirection());
    }
}
