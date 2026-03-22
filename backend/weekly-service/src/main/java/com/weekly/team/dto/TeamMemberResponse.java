package com.weekly.team.dto;

import com.weekly.team.domain.TeamMemberEntity;

/**
 * API response DTO for a team member (Phase 6).
 */
public record TeamMemberResponse(
        String teamId,
        String userId,
        String orgId,
        String role,
        String joinedAt
) {

    public static TeamMemberResponse from(TeamMemberEntity entity) {
        return new TeamMemberResponse(
                entity.getTeamId().toString(),
                entity.getUserId().toString(),
                entity.getOrgId().toString(),
                entity.getRole().name(),
                entity.getJoinedAt().toString()
        );
    }
}
