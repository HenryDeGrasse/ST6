package com.weekly.team.service;

/**
 * Thrown when a user attempts an owner-only team operation without being the owner (Phase 6).
 */
public class TeamAccessDeniedException extends RuntimeException {

    public TeamAccessDeniedException(String message) {
        super(message);
    }
}
