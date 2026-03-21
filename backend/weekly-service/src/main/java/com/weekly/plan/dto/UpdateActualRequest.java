package com.weekly.plan.dto;

import com.weekly.plan.domain.CompletionStatus;
import com.weekly.shared.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating reconciliation data on a commit.
 */
public record UpdateActualRequest(
        @NotBlank String actualResult,
        @NotBlank @ValueOfEnum(enumClass = CompletionStatus.class) String completionStatus,
        String deltaReason,
        Integer timeSpent,
        Double actualHours
) {}
