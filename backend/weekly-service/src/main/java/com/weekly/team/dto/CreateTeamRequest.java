package com.weekly.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new team (Phase 6).
 *
 * <p>{@code keyPrefix} is optional. If omitted, the service auto-derives it
 * from the first 4 uppercase characters of {@code name}, deduplicating by
 * appending a digit when a collision exists.
 */
public record CreateTeamRequest(
        @NotBlank @Size(min = 1, max = 100) String name,
        @Pattern(regexp = "[A-Z0-9]{1,10}",
                 message = "keyPrefix must be 1–10 uppercase letters/digits")
        String keyPrefix,
        @Size(max = 2000) String description
) {}
