package com.weekly.shared;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Shared data seam for overcommit deferral suggestions (Phase 6, Step 14).
 *
 * <p>Exposes the current week's plan assignments (with basic priority metadata) to the
 * AI module without creating a direct AI → plan dependency. The concrete implementation
 * lives in {@code com.weekly.plan.service} and bridges the shared interface to the plan
 * and assignment persistence layers.
 */
public interface DeferralPlanDataProvider {

    /**
     * Returns the assignments in the user's current-week plan (any state).
     *
     * <p>Each entry includes enough information for the AI deferral service to score
     * assignments for deferral attractiveness: the assignment ID, the linked issue ID, and
     * any per-assignment chess priority override (as a plain string).
     *
     * <p>Returns an empty list when no plan exists for the given week.
     *
     * @param orgId     the organisation ID
     * @param userId    the plan owner's user ID
     * @param weekStart the Monday of the target week
     * @return assignment summaries; never null
     */
    List<PlanAssignmentSummary> getCurrentPlanAssignments(UUID orgId, UUID userId, LocalDate weekStart);

    /**
     * Minimal summary of a weekly assignment, returned by {@link #getCurrentPlanAssignments}.
     *
     * @param assignmentId          the weekly assignment UUID
     * @param issueId               the linked issue UUID
     * @param chessPriorityOverride chess priority override set on this assignment (may be null);
     *                              value is the name of a {@code ChessPriority} enum constant
     */
    record PlanAssignmentSummary(
            UUID assignmentId,
            UUID issueId,
            String chessPriorityOverride
    ) {}
}
