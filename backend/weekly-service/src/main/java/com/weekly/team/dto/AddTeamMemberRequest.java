package com.weekly.team.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for adding a member to a team (Phase 6).
 *
 * <p>{@code role} defaults to {@code MEMBER} when omitted.
 */
public record AddTeamMemberRequest(
        @NotNull UUID userId,
        String role
) {}
