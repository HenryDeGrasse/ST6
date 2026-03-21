package com.weekly.capacity;

/**
 * Per-priority completion insight for the coaching response.
 *
 * @param priority       the chess priority name (e.g. {@code KING})
 * @param completionRate fraction of commits with status DONE (0.00–1.00)
 * @param sampleSize     number of historical commits in this priority tier
 */
public record PriorityInsight(
        String priority,
        Double completionRate,
        int sampleSize) {
}
