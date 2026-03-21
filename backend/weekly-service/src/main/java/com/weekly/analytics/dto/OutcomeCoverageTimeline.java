package com.weekly.analytics.dto;

import java.util.List;

/**
 * Multi-week outcome coverage timeline for the manager dashboard.
 *
 * <p>Contains per-week commit activity for a single RCDO outcome and a
 * derived trend direction comparing the two most recent weeks.
 *
 * @param weeks          ordered list of per-week data points (oldest first)
 * @param trendDirection RISING, FALLING, or STABLE based on the last two weeks
 */
public record OutcomeCoverageTimeline(
        List<OutcomeCoverageWeek> weeks,
        String trendDirection
) {}
