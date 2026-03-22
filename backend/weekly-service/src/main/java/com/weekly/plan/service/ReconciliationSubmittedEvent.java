package com.weekly.plan.service;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published by {@link PlanService#submitReconciliation}
 * after a plan transitions to the {@code RECONCILED} state.
 *
 * <p>Consumed by:
 * <ul>
 *   <li>The integration layer ({@code com.weekly.integration.DefaultOutboundCommentService})
 *       to post outbound comments on linked external tickets (ADR-010 Wave 4).</li>
 *   <li>The assignment layer ({@code com.weekly.issues.service.AssignmentReconciliationListener})
 *       to apply DONE/PARTIALLY/NOT_DONE/DROPPED issue status transitions (Phase 6).</li>
 * </ul>
 *
 * <p>Using Spring's application event bus prevents cyclic package dependencies
 * between the {@code plan}, {@code integration}, and {@code assignment} modules.
 */
public class ReconciliationSubmittedEvent extends ApplicationEvent {

    private final UUID orgId;
    private final UUID planId;
    private final UUID actorUserId;
    private final String summary;

    public ReconciliationSubmittedEvent(
            Object source, UUID orgId, UUID planId, UUID actorUserId, String summary
    ) {
        super(source);
        this.orgId = orgId;
        this.planId = planId;
        this.actorUserId = actorUserId;
        this.summary = summary;
    }

    /** The organisation that owns the reconciled plan. */
    public UUID orgId() {
        return orgId;
    }

    /** The ID of the plan that was just reconciled. */
    public UUID planId() {
        return planId;
    }

    /** The user who submitted the reconciliation. */
    public UUID actorUserId() {
        return actorUserId;
    }

    /** Human-readable reconciliation outcome summary for use in external comments. */
    public String summary() {
        return summary;
    }
}
