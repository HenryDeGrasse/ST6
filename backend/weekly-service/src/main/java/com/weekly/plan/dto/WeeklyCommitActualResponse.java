package com.weekly.plan.dto;

import com.weekly.plan.domain.WeeklyCommitActualEntity;

/**
 * API response DTO for reconciliation actuals.
 */
public record WeeklyCommitActualResponse(
        String commitId,
        String actualResult,
        String completionStatus,
        String deltaReason,
        Integer timeSpent,
        Double actualHours
) {

    public static WeeklyCommitActualResponse from(WeeklyCommitActualEntity entity) {
        return new WeeklyCommitActualResponse(
                entity.getCommitId().toString(),
                entity.getActualResult(),
                entity.getCompletionStatus().name(),
                entity.getDeltaReason(),
                entity.getTimeSpent(),
                entity.getActualHours() != null ? entity.getActualHours().doubleValue() : null
        );
    }
}
