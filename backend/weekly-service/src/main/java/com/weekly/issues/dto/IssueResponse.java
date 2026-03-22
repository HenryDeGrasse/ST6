package com.weekly.issues.dto;

import com.weekly.issues.domain.IssueEntity;

/**
 * API response DTO for a backlog issue (Phase 6).
 */
public record IssueResponse(
        String id,
        String orgId,
        String teamId,
        String issueKey,
        int sequenceNumber,
        String title,
        String description,
        String effortType,
        Double estimatedHours,
        String chessPriority,
        String outcomeId,
        String nonStrategicReason,
        String creatorUserId,
        String assigneeUserId,
        String blockedByIssueId,
        String status,
        Integer aiRecommendedRank,
        String aiRankRationale,
        String aiSuggestedEffortType,
        int embeddingVersion,
        int version,
        String createdAt,
        String updatedAt,
        String archivedAt
) {

    public static IssueResponse from(IssueEntity entity) {
        return new IssueResponse(
                entity.getId().toString(),
                entity.getOrgId().toString(),
                entity.getTeamId().toString(),
                entity.getIssueKey(),
                entity.getSequenceNumber(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getEffortType() != null ? entity.getEffortType().name() : null,
                entity.getEstimatedHours() != null
                        ? entity.getEstimatedHours().doubleValue() : null,
                entity.getChessPriority() != null ? entity.getChessPriority().name() : null,
                entity.getOutcomeId() != null ? entity.getOutcomeId().toString() : null,
                entity.getNonStrategicReason(),
                entity.getCreatorUserId().toString(),
                entity.getAssigneeUserId() != null
                        ? entity.getAssigneeUserId().toString() : null,
                entity.getBlockedByIssueId() != null
                        ? entity.getBlockedByIssueId().toString() : null,
                entity.getStatus().name(),
                entity.getAiRecommendedRank(),
                entity.getAiRankRationale(),
                entity.getAiSuggestedEffortType() != null
                        ? entity.getAiSuggestedEffortType().name() : null,
                entity.getEmbeddingVersion(),
                entity.getVersion(),
                entity.getCreatedAt().toString(),
                entity.getUpdatedAt().toString(),
                entity.getArchivedAt() != null ? entity.getArchivedAt().toString() : null
        );
    }
}
