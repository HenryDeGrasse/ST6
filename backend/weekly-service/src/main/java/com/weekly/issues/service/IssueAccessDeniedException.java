package com.weekly.issues.service;

/**
 * Thrown when a user attempts an unauthorized issue mutation (Phase 6).
 */
public class IssueAccessDeniedException extends RuntimeException {

    public IssueAccessDeniedException(String message) {
        super(message);
    }
}
