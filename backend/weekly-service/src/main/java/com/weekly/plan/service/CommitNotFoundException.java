package com.weekly.plan.service;

import java.util.UUID;

/**
 * Thrown when a commit is not found by ID.
 */
public class CommitNotFoundException extends RuntimeException {

    private final UUID commitId;

    public CommitNotFoundException(UUID commitId) {
        super("Commit not found: " + commitId);
        this.commitId = commitId;
    }

    public UUID getCommitId() {
        return commitId;
    }
}
