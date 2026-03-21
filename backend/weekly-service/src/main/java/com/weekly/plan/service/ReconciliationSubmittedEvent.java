package com.weekly.plan.service;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published by {@link PlanService#submitReconciliation}
 * after a plan transitions to the {@code RECONCILED} state.
 *
 * <p>Consumed by the integration layer ({@link com.weekly.integration.DefaultOutboundCommentService})
 * to post outbound comments on linked external tickets (ADR-010 Wave 4).
 *
 * <p>Using Spring's application event bus prevents a cyclic package dependency
 * between the {@code plan} and {@code integration} modules.
 */
public class ReconciliationSubmittedEvent extends ApplicationEvent {

    private final UUID orgId;
    private final UUID planId;
    private final String summary;

    public ReconciliationSubmittedEvent(Object source, UUID orgId, UUID planId, String summary) {
        super(source);
        this.orgId = orgId;
        this.planId = planId;
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

    /** Human-readable reconciliation outcome summary for use in external comments. */
    public String summary() {
        return summary;
    }
}
