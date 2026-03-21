package com.weekly.analytics.dto;

import java.util.List;

/**
 * Team-level carry-forward heatmap for the manager dashboard.
 *
 * <p>Shows how many commits each direct report carried forward each week
 * over the analysis window. High carry-forward counts may indicate
 * scope creep, estimation problems, or capacity constraints.
 *
 * @param users ordered list of team members with per-week carry-forward data
 */
public record CarryForwardHeatmap(
        List<HeatmapUser> users
) {}
