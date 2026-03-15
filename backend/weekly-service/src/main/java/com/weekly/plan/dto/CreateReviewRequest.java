package com.weekly.plan.dto;

import com.weekly.plan.domain.ReviewDecision;
import com.weekly.shared.validation.ValueOfEnum;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for submitting a manager review.
 */
public record CreateReviewRequest(
        @NotBlank @ValueOfEnum(enumClass = ReviewDecision.class) String decision,
        @NotBlank String comments
) {}
