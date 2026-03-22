package com.weekly.issues.events;

import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link com.weekly.issues.service.AssignmentService#reconcileAssignmentStatus}
 * after all issue status transitions for a reconciled plan have been applied.
 *
 * <p>Reconciliation adds rich context to an issue's embedding (completion status,
 * actual result text), so the async pipeline
 * ({@link com.weekly.ai.rag.IssueEmbeddingJob}) schedules a re-embed with the
 * standard 30-second debounce for each affected issue.
 */
public class AssignmentReconciledEvent extends ApplicationEvent {

    private final UUID orgId;
    private final UUID planId;
    private final List<UUID> issueIds;

    public AssignmentReconciledEvent(Object source, UUID orgId, UUID planId, List<UUID> issueIds) {
        super(source);
        this.orgId = orgId;
        this.planId = planId;
        this.issueIds = List.copyOf(issueIds);
    }

    /** The organisation that owns the reconciled plan. */
    public UUID orgId() {
        return orgId;
    }

    /** The UUID of the plan that was reconciled. */
    public UUID planId() {
        return planId;
    }

    /**
     * The UUIDs of issues whose status was transitioned during reconciliation.
     * The embedding job schedules a re-embed for each.
     */
    public List<UUID> issueIds() {
        return issueIds;
    }
}
