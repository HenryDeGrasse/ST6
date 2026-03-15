package com.weekly.plan.dto;

import com.weekly.plan.domain.ManagerReviewEntity;

/**
 * API response DTO for a manager review.
 */
public record ManagerReviewResponse(
        String id,
        String weeklyPlanId,
        String reviewerUserId,
        String decision,
        String comments,
        String createdAt
) {

    public static ManagerReviewResponse from(ManagerReviewEntity entity) {
        return new ManagerReviewResponse(
                entity.getId().toString(),
                entity.getWeeklyPlanId().toString(),
                entity.getReviewerUserId().toString(),
                entity.getDecision(),
                entity.getComments(),
                entity.getCreatedAt().toString()
        );
    }
}
