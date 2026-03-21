package com.weekly.capacity;

import java.math.BigDecimal;

/**
 * Capacity summary for a single team member in a given week.
 *
 * <p>Used as the per-member entry in {@link TeamCapacityResponse} returned by
 * {@code GET /api/v1/team/capacity}.
 *
 * @param userId           the team member's user ID
 * @param name             human-readable display name from the org graph
 * @param estimatedHours   sum of raw {@code estimated_hours} across all commits for the week
 * @param adjustedEstimate bias-adjusted estimated total (accounts for per-category
 *                         under/over-estimation history); equals {@code estimatedHours}
 *                         when no profile bias is available
 * @param realisticCap     the member's p50 realistic weekly capacity cap,
 *                         or {@code null} if no capacity profile has been computed yet
 * @param overcommitLevel  overcommitment severity: {@code NONE}, {@code MODERATE},
 *                         or {@code HIGH}
 */
public record TeamMemberCapacity(
        String userId,
        String name,
        BigDecimal estimatedHours,
        BigDecimal adjustedEstimate,
        BigDecimal realisticCap,
        String overcommitLevel) {
}
