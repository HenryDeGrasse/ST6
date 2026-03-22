package com.weekly.team.dto;

import java.util.List;

/**
 * API response DTO returning a team with its member list (Phase 6).
 */
public record TeamDetailResponse(
        TeamResponse team,
        List<TeamMemberResponse> members
) {}
