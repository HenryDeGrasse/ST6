package com.weekly.shared;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Abstraction for manager-dashboard data needed by the AI insight flow.
 *
 * <p>This lives in the shared package so the AI module can read manager-week
 * context without depending directly on plan-module services or repositories.
 */
public interface ManagerInsightDataProvider {

    /**
     * Returns the team context needed to draft manager insights for a week.
     */
    ManagerWeekContext getManagerWeekContext(UUID orgId, UUID managerId, LocalDate weekStart);

    /**
     * Team-level context for a manager and week.
     */
    record ManagerWeekContext(
            String weekStart,
            ReviewCounts reviewCounts,
            List<TeamMemberContext> teamMembers,
            List<RcdoFocusContext> rcdoFocuses
    ) {}

    /**
     * Review-status aggregates.
     */
    record ReviewCounts(
            int pending,
            int approved,
            int changesRequested
    ) {}

    /**
     * Per-direct-report summary context.
     */
    record TeamMemberContext(
            String userId,
            String state,
            String reviewStatus,
            int commitCount,
            int incompleteCount,
            int issueCount,
            int nonStrategicCount,
            int kingCount,
            int queenCount,
            boolean stale,
            boolean lateLock
    ) {}

    /**
     * Strategic focus summary from the RCDO roll-up.
     */
    record RcdoFocusContext(
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName,
            int commitCount,
            int kingCount,
            int queenCount
    ) {}
}
