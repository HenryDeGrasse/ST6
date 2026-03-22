package com.weekly.team.dto;

import com.weekly.team.domain.TeamAccessRequestEntity;

/**
 * API response DTO for a team access request (Phase 6).
 */
public record TeamAccessRequestResponse(
        String id,
        String teamId,
        String requesterUserId,
        String orgId,
        String status,
        String decidedByUserId,
        String decidedAt,
        String createdAt
) {

    public static TeamAccessRequestResponse from(TeamAccessRequestEntity entity) {
        return new TeamAccessRequestResponse(
                entity.getId().toString(),
                entity.getTeamId().toString(),
                entity.getRequesterUserId().toString(),
                entity.getOrgId().toString(),
                entity.getStatus().name(),
                entity.getDecidedByUserId() != null
                        ? entity.getDecidedByUserId().toString() : null,
                entity.getDecidedAt() != null ? entity.getDecidedAt().toString() : null,
                entity.getCreatedAt().toString()
        );
    }
}
