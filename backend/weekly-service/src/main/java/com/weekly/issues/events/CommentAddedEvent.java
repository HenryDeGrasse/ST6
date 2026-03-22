package com.weekly.issues.events;

import java.util.UUID;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link com.weekly.issues.service.IssueService#addComment} after a
 * comment activity is persisted on an issue.
 *
 * <p>Comments enrich an issue's embedding context, so the async pipeline
 * ({@link com.weekly.ai.rag.IssueEmbeddingJob}) schedules a re-embed with the
 * standard 30-second debounce after a comment is added.
 */
public class CommentAddedEvent extends ApplicationEvent {

    private final UUID orgId;
    private final UUID issueId;

    public CommentAddedEvent(Object source, UUID orgId, UUID issueId) {
        super(source);
        this.orgId = orgId;
        this.issueId = issueId;
    }

    /** The organisation that owns the issue. */
    public UUID orgId() {
        return orgId;
    }

    /** The UUID of the issue that received the comment. */
    public UUID issueId() {
        return issueId;
    }
}
