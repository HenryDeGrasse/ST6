package com.weekly.plan.dto;

/**
 * Summary of a single team member's plan status for a given week.
 * Used in the manager dashboard.
 */
public record TeamMemberSummaryResponse(
        String userId,
        String displayName,
        String planId,
        String state,
        String reviewStatus,
        int commitCount,
        /** Count of saved actuals with completionStatus != DONE for RECONCILED/CARRY_FORWARD plans. */
        int incompleteCount,
        /** Count of commits with validation errors (missing required fields). */
        int issueCount,
        int nonStrategicCount,
        int kingCount,
        int queenCount,
        String lastUpdated,
        boolean isStale,
        boolean isLateLock
) {}
