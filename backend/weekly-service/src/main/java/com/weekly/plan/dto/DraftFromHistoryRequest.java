package com.weekly.plan.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the POST /api/v1/plans/draft-from-history endpoint.
 */
public record DraftFromHistoryRequest(
        @NotBlank String weekStart
) {
}
