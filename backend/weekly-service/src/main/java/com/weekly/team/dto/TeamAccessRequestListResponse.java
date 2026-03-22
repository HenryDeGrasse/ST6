package com.weekly.team.dto;

import java.util.List;

/**
 * API response DTO listing team access requests (Phase 6).
 */
public record TeamAccessRequestListResponse(List<TeamAccessRequestResponse> requests) {}
