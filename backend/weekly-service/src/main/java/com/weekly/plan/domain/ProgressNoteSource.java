package com.weekly.plan.domain;

/**
 * Provenance for a quick-update note captured on a progress entry.
 *
 * <p>This allows downstream learning jobs to distinguish notes a user typed
 * from notes they accepted from a suggestion surface.
 */
public enum ProgressNoteSource {
    UNKNOWN,
    USER_TYPED,
    SUGGESTION_ACCEPTED
}
