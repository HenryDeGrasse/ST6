package com.weekly.plan.domain;

/**
 * Status values for a daily check-in progress entry.
 *
 * <p>Used by {@link ProgressEntryEntity} to signal the current state
 * of a commit mid-week. Values mirror the CHECK constraint in
 * {@code V4__progress_entries.sql}.
 */
public enum ProgressStatus {
    ON_TRACK,
    AT_RISK,
    BLOCKED,
    DONE_EARLY
}
