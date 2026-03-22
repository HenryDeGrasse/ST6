package com.weekly.issues.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for logging time spent on an issue (Phase 6).
 */
public record LogTimeEntryRequest(
        @NotNull @DecimalMin(value = "0.01", message = "hoursLogged must be positive")
        Double hoursLogged,
        String note
) {}
