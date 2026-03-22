package com.weekly.team.dto;

import java.util.List;

/**
 * API response DTO for listing teams (Phase 6).
 */
public record TeamListResponse(List<TeamResponse> teams) {}
