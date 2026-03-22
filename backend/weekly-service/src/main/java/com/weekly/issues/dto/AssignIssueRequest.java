package com.weekly.issues.dto;

/**
 * Request body for assigning or unassigning an issue (Phase 6).
 *
 * <p>A {@code null} value for {@code assigneeUserId} unassigns the issue.
 */
public record AssignIssueRequest(String assigneeUserId) {}
