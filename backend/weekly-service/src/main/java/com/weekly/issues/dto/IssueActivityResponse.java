package com.weekly.issues.dto;

import com.weekly.issues.domain.IssueActivityEntity;

/**
 * API response DTO for an issue activity log entry (Phase 6).
 */
public record IssueActivityResponse(
        String id,
        String orgId,
        String issueId,
        String actorUserId,
        String activityType,
        String oldValue,
        String newValue,
        String commentText,
        Double hoursLogged,
        String createdAt
) {

    public static IssueActivityResponse from(IssueActivityEntity entity) {
        return new IssueActivityResponse(
                entity.getId().toString(),
                entity.getOrgId().toString(),
                entity.getIssueId().toString(),
                entity.getActorUserId().toString(),
                entity.getActivityType().name(),
                entity.getOldValue(),
                entity.getNewValue(),
                entity.getCommentText(),
                entity.getHoursLogged() != null ? entity.getHoursLogged().doubleValue() : null,
                entity.getCreatedAt().toString()
        );
    }
}
