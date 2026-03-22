package com.weekly.team.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for updating a team (Phase 6).
 *
 * <p>All fields are optional; only non-null values are applied.
 */
public record UpdateTeamRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 2000) String description
) {}
