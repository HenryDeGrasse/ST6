package com.weekly.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new weekly commit.
 */
public record CreateCommitRequest(
        @NotBlank @Size(min = 1, max = 500) String title,
        String description,
        String chessPriority,
        String category,
        String outcomeId,
        String nonStrategicReason,
        String expectedResult,
        Double confidence,
        String[] tags
) {}
