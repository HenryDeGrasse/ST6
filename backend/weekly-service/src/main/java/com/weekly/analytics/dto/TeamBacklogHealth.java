package com.weekly.analytics.dto;

/**
 * Backlog health snapshot for a single team, sourced from the
 * {@code mv_team_backlog_health} materialized view.
 *
 * <p>All counts refer to {@code OPEN} and {@code IN_PROGRESS} issues
 * with a null {@code archived_at}. {@code avgCycleTimeDays} is
 * derived from {@code DONE} issues and may be 0.0 when no issues have
 * been completed yet.
 *
 * @param teamId             team UUID (string)
 * @param openIssueCount     number of currently open / in-progress issues
 * @param avgIssueAgeDays    average age of open issues in days (0.0 when empty)
 * @param blockedCount       number of open issues blocked by another issue
 * @param buildCount         open issues with effort_type = 'BUILD'
 * @param maintainCount      open issues with effort_type = 'MAINTAIN'
 * @param collaborateCount   open issues with effort_type = 'COLLABORATE'
 * @param learnCount         open issues with effort_type = 'LEARN'
 * @param avgCycleTimeDays   average days from creation to completion (0.0 when no done issues)
 */
public record TeamBacklogHealth(
        String teamId,
        long openIssueCount,
        double avgIssueAgeDays,
        long blockedCount,
        long buildCount,
        long maintainCount,
        long collaborateCount,
        long learnCount,
        double avgCycleTimeDays
) {}
