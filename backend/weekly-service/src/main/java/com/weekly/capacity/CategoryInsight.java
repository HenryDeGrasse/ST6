package com.weekly.capacity;

/**
 * Per-category estimation insight for the coaching response.
 *
 * @param category the commit category name (e.g. {@code DELIVERY})
 * @param bias     historical actual/estimated ratio for this category,
 *                 or {@code null} when no history is available
 * @param tip      human-readable coaching tip when the bias is significant
 *                 (i.e. deviates from 1.0 by ≥15%), or {@code null} otherwise
 */
public record CategoryInsight(
        String category,
        Double bias,
        String tip) {
}
