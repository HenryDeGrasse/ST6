package com.weekly.quickupdate;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for the check-in option generation endpoint.
 *
 * @param commitId              the commitment to generate options for; must not be null
 * @param currentStatus         the current progress status (optional; used as context)
 * @param lastNote              the most recent check-in note (optional; used as context)
 * @param daysSinceLastCheckIn  number of days elapsed since the last check-in
 */
public record CheckInOptionRequestDto(
        @NotNull UUID commitId,
        String currentStatus,
        String lastNote,
        int daysSinceLastCheckIn
) {}
