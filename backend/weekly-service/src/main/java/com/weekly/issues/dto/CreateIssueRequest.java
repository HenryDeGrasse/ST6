package com.weekly.issues.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a backlog issue (Phase 6).
 *
 * <p>Issues are team-scoped: {@code teamId} comes from the path parameter,
 * not the request body, matching the {@code POST /teams/{teamId}/issues} route.
 */
public record CreateIssueRequest(
        @NotBlank @Size(min = 1, max = 500) String title,
        @Size(max = 10000) String description,
        String effortType,
        Double estimatedHours,
        String chessPriority,
        String outcomeId,
        String nonStrategicReason,
        String assigneeUserId,
        String blockedByIssueId
) {}
