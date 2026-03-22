package com.weekly.issues.events;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link com.weekly.issues.service.IssueService} whenever an issue's
 * title, description, status, assignee, or other indexed field changes.
 *
 * <p>Consumed by the async embedding pipeline
 * ({@link com.weekly.ai.rag.IssueEmbeddingJob}) to re-embed the issue after a
 * 30-second debounce window (multiple rapid updates coalesce into a single embed).
 */
public class IssueUpdatedEvent extends ApplicationEvent {

    private final UUID orgId;
    private final UUID issueId;

    public IssueUpdatedEvent(Object source, UUID orgId, UUID issueId) {
        super(source);
        this.orgId = orgId;
        this.issueId = issueId;
    }

    /** The organisation that owns the updated issue. */
    public UUID orgId() {
        return orgId;
    }

    /** The UUID of the updated issue. */
    public UUID issueId() {
        return issueId;
    }
}
