package com.weekly.plan.dto;

import com.weekly.plan.domain.ProgressStatus;
import com.weekly.shared.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the POST /api/v1/commits/{commitId}/check-in endpoint.
 *
 * <p>{@code status} is mandatory and must match one of the allowed values.
 * {@code note} is optional; an absent or null note is stored as an empty string.
 */
public record CheckInRequest(
        @NotBlank(message = "status must not be blank")
        @ValueOfEnum(enumClass = ProgressStatus.class)
        String status,
        String note
) {
}
