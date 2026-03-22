package com.weekly.issues.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for updating a backlog issue (Phase 6).
 *
 * <p>All fields are optional; only non-null values are applied (patch semantics).
 */
public record UpdateIssueRequest(
        @Size(min = 1, max = 500) String title,
        @Size(max = 10000) String description,
        String effortType,
        Double estimatedHours,
        String chessPriority,
        String outcomeId,
        String nonStrategicReason,
        String assigneeUserId,
        String blockedByIssueId,
        String status
) {}
