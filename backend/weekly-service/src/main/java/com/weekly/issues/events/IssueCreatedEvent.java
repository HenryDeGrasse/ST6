package com.weekly.issues.events;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link com.weekly.issues.service.IssueService#createIssue} after a new issue
 * is persisted.
 *
 * <p>Consumed by the async embedding pipeline
 * ({@link com.weekly.ai.rag.IssueEmbeddingJob}) to schedule the initial vector
 * creation with a 30-second debounce.
 */
public class IssueCreatedEvent extends ApplicationEvent {

    private final UUID orgId;
    private final UUID issueId;

    public IssueCreatedEvent(Object source, UUID orgId, UUID issueId) {
        super(source);
        this.orgId = orgId;
        this.issueId = issueId;
    }

    /** The organisation that owns the newly created issue. */
    public UUID orgId() {
        return orgId;
    }

    /** The UUID of the newly created issue. */
    public UUID issueId() {
        return issueId;
    }
}
