package com.weekly.team.dto;

import com.weekly.team.domain.TeamEntity;

/**
 * API response DTO for a team (Phase 6).
 */
public record TeamResponse(
        String id,
        String orgId,
        String name,
        String keyPrefix,
        String description,
        String ownerUserId,
        int issueSequence,
        int version,
        String createdAt,
        String updatedAt
) {

    public static TeamResponse from(TeamEntity entity) {
        return new TeamResponse(
                entity.getId().toString(),
                entity.getOrgId().toString(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getDescription(),
                entity.getOwnerUserId().toString(),
                entity.getIssueSequence(),
                entity.getVersion(),
                entity.getCreatedAt().toString(),
                entity.getUpdatedAt().toString()
        );
    }
}
