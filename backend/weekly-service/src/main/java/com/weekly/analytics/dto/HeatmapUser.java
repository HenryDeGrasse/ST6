package com.weekly.analytics.dto;

import java.util.List;

/**
 * A row in the carry-forward heatmap representing one team member.
 *
 * <p>Contains an ordered list of cells (one per week in the analysis window).
 * Weeks with no data for the user have a {@code carriedCount} of zero.
 *
 * @param userId      the team member's user ID
 * @param displayName human-readable name from the org-graph / HRIS
 * @param weekCells   ordered list of per-week carry-forward counts (oldest first)
 */
public record HeatmapUser(
        String userId,
        String displayName,
        List<HeatmapCell> weekCells
) {}
