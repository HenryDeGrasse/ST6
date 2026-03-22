package com.weekly.issues.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for creating a weekly assignment directly on a plan (Phase 6).
 *
 * <p>Used by {@code POST /weeks/{weekStart}/plan/assignments}.
 */
public record CreateWeeklyAssignmentRequest(
        @NotNull UUID issueId,
        String chessPriorityOverride,
        String expectedResult,
        Double confidence
) {}
