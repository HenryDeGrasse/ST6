package com.weekly.issues.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for committing an issue to a weekly plan (Phase 6).
 */
public record CommitIssueToWeekRequest(
        @NotBlank String weekStart,
        String chessPriorityOverride,
        String expectedResult,
        Double confidence
) {}
