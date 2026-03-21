package com.weekly.analytics.dto;

/**
 * Per-user estimation accuracy metrics derived from reconciled weeks.
 *
 * <p>Compares a team member's stated confidence against their actual
 * completion rate across reconciled planning weeks. A positive
 * {@code calibrationGap} indicates systematic over-confidence.
 *
 * @param userId          the team member's user ID
 * @param avgConfidence   average confidence score across reconciled weeks ([0, 1])
 * @param completionRate  ratio of DONE commits to total commits across reconciled weeks
 * @param calibrationGap  avgConfidence minus completionRate (positive = over-confident)
 */
public record UserEstimationAccuracy(
        String userId,
        double avgConfidence,
        double completionRate,
        double calibrationGap
) {}
