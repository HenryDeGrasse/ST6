package com.weekly.plan.dto;

import com.weekly.plan.domain.WeeklyPlanEntity;

import java.time.format.DateTimeFormatter;

/**
 * API response DTO for a weekly plan.
 */
public record WeeklyPlanResponse(
        String id,
        String orgId,
        String ownerUserId,
        String weekStartDate,
        String state,
        String reviewStatus,
        String lockType,
        String lockedAt,
        String carryForwardExecutedAt,
        int version,
        String createdAt,
        String updatedAt
) {

    public static WeeklyPlanResponse from(WeeklyPlanEntity entity) {
        return new WeeklyPlanResponse(
                entity.getId().toString(),
                entity.getOrgId().toString(),
                entity.getOwnerUserId().toString(),
                entity.getWeekStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                entity.getState().name(),
                entity.getReviewStatus().name(),
                entity.getLockType() != null ? entity.getLockType().name() : null,
                entity.getLockedAt() != null ? entity.getLockedAt().toString() : null,
                entity.getCarryForwardExecutedAt() != null
                        ? entity.getCarryForwardExecutedAt().toString() : null,
                entity.getVersion(),
                entity.getCreatedAt().toString(),
                entity.getUpdatedAt().toString()
        );
    }
}
