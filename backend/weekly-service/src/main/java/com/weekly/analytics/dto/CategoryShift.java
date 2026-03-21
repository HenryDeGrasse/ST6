package com.weekly.analytics.dto;

/**
 * The single largest shift in commit category distribution for a user
 * between the prior half-window and the recent half-window.
 *
 * @param category the commit category name (e.g. DELIVERY, OPERATIONS)
 * @param delta    signed change in proportion (positive = increased, negative = decreased)
 */
public record CategoryShift(
        String category,
        double delta
) {}
