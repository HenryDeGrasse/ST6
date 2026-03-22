package com.weekly.team.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for approving or denying a team access request (Phase 6).
 */
public record TeamAccessRequestActionRequest(
        @NotBlank String status
) {}
