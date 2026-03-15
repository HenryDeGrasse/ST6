package com.weekly.plan.dto;

/**
 * Roll-up of commits grouped by RCDO outcome for the manager dashboard.
 */
public record RcdoRollupItem(
        String outcomeId,
        String outcomeName,
        String objectiveId,
        String objectiveName,
        String rallyCryId,
        String rallyCryName,
        int commitCount,
        int kingCount,
        int queenCount,
        int rookCount,
        int bishopCount,
        int knightCount,
        int pawnCount
) {}
