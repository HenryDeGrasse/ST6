package com.weekly.shared;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Abstraction for plan commit data needed by the AI plan-quality check.
 *
 * <p>Lives in the shared package so the AI module can query plan quality
 * signals without depending directly on plan module internals
 * (ArchUnit boundary: AI must not depend on plan).
 */
public interface PlanQualityDataProvider {

    /**
     * Returns quality context for the given plan owned by the given user.
     *
     * <p>If the plan does not exist, belongs to a different org, or is owned by a
     * different user, implementations should return {@link PlanQualityContext#empty()}.
     * This keeps the AI endpoint from leaking cross-user plan existence.
     *
     * @param orgId  the organisation ID
     * @param planId the plan ID
     * @param userId the authenticated user requesting the quality check
     * @return quality context; {@link PlanQualityContext#empty()} if inaccessible
     */
    PlanQualityContext getPlanQualityContext(UUID orgId, UUID planId, UUID userId);

    /**
     * Returns quality context for the given user's plan in the previous week.
     *
     * @param orgId            the organisation ID
     * @param userId           the plan owner's user ID
     * @param currentWeekStart the Monday of the current week (previous = currentWeekStart minus 1 week)
     * @return quality context; {@link PlanQualityContext#empty()} if no previous plan exists
     */
    PlanQualityContext getPreviousWeekQualityContext(UUID orgId, UUID userId, LocalDate currentWeekStart);

    /**
     * Returns the strategic alignment rate (fraction of commits with an RCDO outcome)
     * across the entire team for the given org and week.
     *
     * @param orgId     the organisation ID
     * @param weekStart the Monday of the target week
     * @return a value in [0.0, 1.0], or 0.0 if no commits exist
     */
    double getTeamStrategicAlignmentRate(UUID orgId, LocalDate weekStart);

    /**
     * Quality context for a single plan.
     *
     * @param planFound  whether the plan exists and belongs to the org
     * @param weekStart  ISO-8601 date of the plan's week start (null when planFound=false)
     * @param commits    per-commit quality summaries; empty when planFound=false
     */
    record PlanQualityContext(
            boolean planFound,
            String weekStart,
            List<CommitQualitySummary> commits
    ) {
        /** Returns a context representing a missing plan. */
        public static PlanQualityContext empty() {
            return new PlanQualityContext(false, null, List.of());
        }
    }

    /**
     * Per-commit quality summary used for data-driven checks.
     *
     * @param category        commit category name (e.g., "DELIVERY"); null if unset
     * @param chessPriority   chess priority name (e.g., "KING"); null if unset
     * @param outcomeId       RCDO outcome UUID string; null if commit is non-strategic
     * @param rallyCryId      snapshot rally cry UUID string; null before lock or if no RCDO link
     */
    record CommitQualitySummary(
            String category,
            String chessPriority,
            String outcomeId,
            String rallyCryId
    ) {}
}
