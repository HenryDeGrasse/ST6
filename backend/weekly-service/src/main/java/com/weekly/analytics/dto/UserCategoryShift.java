package com.weekly.analytics.dto;

import java.util.Map;

/**
 * Per-user category distribution shift between the prior and recent half-windows.
 *
 * <p>Used by the category shift analysis to surface users whose commit category
 * mix has changed significantly over the analysis window.
 *
 * @param userId              the team member's user ID
 * @param currentDistribution category → proportion for the recent half-window
 * @param priorDistribution   category → proportion for the prior half-window
 * @param biggestShift        the category with the largest absolute change in proportion
 */
public record UserCategoryShift(
        String userId,
        Map<String, Double> currentDistribution,
        Map<String, Double> priorDistribution,
        CategoryShift biggestShift
) {}
