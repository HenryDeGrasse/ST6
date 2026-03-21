package com.weekly.admin;

import java.util.List;

/**
 * RCDO health report for the admin dashboard.
 *
 * <p>Ranks outcomes by commit coverage over a recent window and identifies
 * outcomes in the RCDO tree that have received zero commits (stale/uncovered).
 * Helps leadership see which strategic objectives are being worked on and which
 * are neglected.
 */
public record RcdoHealthReport(
        String generatedAt,
        int windowWeeks,
        int totalOutcomes,
        int coveredOutcomes,
        List<OutcomeHealthItem> topOutcomes,
        List<OutcomeHealthItem> staleOutcomes
) {

    /**
     * Commit coverage data for a single RCDO outcome.
     *
     * @param outcomeId    UUID string of the outcome
     * @param outcomeName  display name of the outcome
     * @param objectiveId  UUID string of the parent objective
     * @param objectiveName display name of the parent objective
     * @param rallyCryId   UUID string of the parent rally cry
     * @param rallyCryName display name of the parent rally cry
     * @param commitCount  number of commits linked to this outcome in the window
     */
    public record OutcomeHealthItem(
            String outcomeId,
            String outcomeName,
            String objectiveId,
            String objectiveName,
            String rallyCryId,
            String rallyCryName,
            int commitCount
    ) {}
}
