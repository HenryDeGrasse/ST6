package com.weekly.issues.service;

import java.util.UUID;

/**
 * Thrown when an issue is not found (Phase 6).
 */
public class IssueNotFoundException extends RuntimeException {

    private final UUID issueId;

    public IssueNotFoundException(UUID issueId) {
        super("Issue not found: " + issueId);
        this.issueId = issueId;
    }

    public UUID getIssueId() {
        return issueId;
    }
}
