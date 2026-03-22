package com.weekly.team.service;

import java.util.UUID;

/**
 * Thrown when a team is not found by ID (Phase 6).
 */
public class TeamNotFoundException extends RuntimeException {

    private final UUID teamId;

    public TeamNotFoundException(UUID teamId) {
        super("Team not found: " + teamId);
        this.teamId = teamId;
    }

    public UUID getTeamId() {
        return teamId;
    }
}
