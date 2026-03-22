package com.weekly.team.service;

import java.util.UUID;

/**
 * Thrown when a team access request is not found for the specified team.
 */
public class TeamAccessRequestNotFoundException extends RuntimeException {

    private final UUID requestId;

    public TeamAccessRequestNotFoundException(UUID requestId) {
        super("Team access request not found: " + requestId);
        this.requestId = requestId;
    }

    public UUID getRequestId() {
        return requestId;
    }
}
