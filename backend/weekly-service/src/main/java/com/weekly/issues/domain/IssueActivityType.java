package com.weekly.issues.domain;

/**
 * Audit activity types that can be recorded on an issue (Phase 6).
 *
 * <p>All 16 values correspond 1-to-1 with the {@code chk_activity_type}
 * CHECK constraint in V16__teams_issues_assignments.sql.
 */
public enum IssueActivityType {
    CREATED,
    STATUS_CHANGE,
    ASSIGNMENT_CHANGE,
    PRIORITY_CHANGE,
    EFFORT_TYPE_CHANGE,
    ESTIMATE_CHANGE,
    COMMENT,
    TIME_ENTRY,
    OUTCOME_CHANGE,
    COMMITTED_TO_WEEK,
    RELEASED_TO_BACKLOG,
    CARRIED_FORWARD,
    BLOCKED,
    UNBLOCKED,
    DESCRIPTION_CHANGE,
    TITLE_CHANGE
}
