package com.weekly.issues.dto;

import com.weekly.assignment.domain.WeeklyAssignmentEntity;

/**
 * API response DTO for a weekly assignment (Phase 6).
 */
public record WeeklyAssignmentResponse(
        String id,
        String orgId,
        String weeklyPlanId,
        String issueId,
        String chessPriorityOverride,
        String expectedResult,
        Double confidence,
        String snapshotRallyCryId,
        String snapshotRallyCryName,
        String snapshotObjectiveId,
        String snapshotObjectiveName,
        String snapshotOutcomeId,
        String snapshotOutcomeName,
        String[] tags,
        String legacyCommitId,
        int version,
        String createdAt,
        String updatedAt
) {

    public static WeeklyAssignmentResponse from(WeeklyAssignmentEntity entity) {
        return new WeeklyAssignmentResponse(
                entity.getId().toString(),
                entity.getOrgId().toString(),
                entity.getWeeklyPlanId().toString(),
                entity.getIssueId().toString(),
                entity.getChessPriorityOverride(),
                entity.getExpectedResult(),
                entity.getConfidence() != null ? entity.getConfidence().doubleValue() : null,
                entity.getSnapshotRallyCryId() != null
                        ? entity.getSnapshotRallyCryId().toString() : null,
                entity.getSnapshotRallyCryName(),
                entity.getSnapshotObjectiveId() != null
                        ? entity.getSnapshotObjectiveId().toString() : null,
                entity.getSnapshotObjectiveName(),
                entity.getSnapshotOutcomeId() != null
                        ? entity.getSnapshotOutcomeId().toString() : null,
                entity.getSnapshotOutcomeName(),
                entity.getTags(),
                entity.getLegacyCommitId() != null
                        ? entity.getLegacyCommitId().toString() : null,
                entity.getVersion(),
                entity.getCreatedAt().toString(),
                entity.getUpdatedAt().toString()
        );
    }
}
