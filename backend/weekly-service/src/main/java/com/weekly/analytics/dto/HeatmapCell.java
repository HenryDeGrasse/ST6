package com.weekly.analytics.dto;

/**
 * A single cell in the carry-forward heatmap, representing one user's
 * carry-forward count for one week.
 *
 * @param weekStart    ISO-8601 date string (Monday) for this cell's week
 * @param carriedCount number of commits the user carried forward into this week
 */
public record HeatmapCell(
        String weekStart,
        int carriedCount
) {}
