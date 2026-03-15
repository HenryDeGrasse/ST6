package com.weekly.rcdo;

/**
 * Detailed outcome info used for snapshot population at lock time.
 */
public record RcdoOutcomeDetail(
        String outcomeId,
        String outcomeName,
        String objectiveId,
        String objectiveName,
        String rallyCryId,
        String rallyCryName
) {}
