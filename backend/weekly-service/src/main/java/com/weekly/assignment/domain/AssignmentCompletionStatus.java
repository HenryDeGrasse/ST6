package com.weekly.assignment.domain;

/**
 * Completion status for a weekly assignment actual (Phase 6).
 *
 * <p>Mirrors {@code com.weekly.plan.domain.CompletionStatus} to keep the
 * {@code assignment} module free of cycles with the {@code plan} module.
 * A bidirectional mapping is maintained in {@code DualWriteService}.
 */
public enum AssignmentCompletionStatus {
    DONE,
    PARTIALLY,
    NOT_DONE,
    DROPPED
}
