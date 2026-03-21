package com.weekly.integration;

import java.util.UUID;

/**
 * Service for posting outbound comments on external issue-tracker tickets
 * when reconciliation or review status changes occur (ADR-010, Wave 4).
 *
 * <p>All operations are best-effort: failures from the external provider
 * are logged and swallowed so they never block the main domain flow.
 */
public interface OutboundCommentService {

    /**
     * Posts a reconciliation summary comment on every external ticket linked
     * to any commit in the given plan.
     *
     * <p>Called after {@link com.weekly.plan.service.PlanService#submitReconciliation}
     * transitions the plan to the {@code RECONCILED} state.
     *
     * @param orgId   the org owning the plan
     * @param planId  the plan whose commits should be commented on
     * @param summary a human-readable summary of the reconciliation outcome
     */
    void postReconciliationComment(UUID orgId, UUID planId, String summary);
}
