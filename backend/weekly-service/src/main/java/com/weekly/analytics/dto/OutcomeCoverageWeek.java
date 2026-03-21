package com.weekly.analytics.dto;

/**
 * A single week's commit activity for a specific RCDO outcome.
 *
 * <p>Sourced from the {@code mv_outcome_coverage_weekly} materialized view.
 * Used as a data point in the {@link OutcomeCoverageTimeline}.
 *
 * @param weekStart         ISO-8601 date string (Monday) for this week
 * @param commitCount       total strategic commits linked to the outcome this week
 * @param contributorCount  distinct contributors who made those commits
 * @param highPriorityCount commits with chess priority KING or QUEEN
 */
public record OutcomeCoverageWeek(
        String weekStart,
        int commitCount,
        int contributorCount,
        int highPriorityCount
) {}
