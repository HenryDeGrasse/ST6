package com.weekly.trends;

/**
 * A structured insight about a user's planning trend patterns.
 *
 * <p>Insights are generated from rolling-window aggregations and highlight
 * notable patterns (carry-forward streaks, confidence gaps, strategic alignment).
 *
 * @param type     machine-readable type identifier (e.g. CARRY_FORWARD_STREAK)
 * @param message  human-readable explanation
 * @param severity one of INFO, WARNING, POSITIVE
 */
public record TrendInsight(
        String type,
        String message,
        String severity
) {
}
