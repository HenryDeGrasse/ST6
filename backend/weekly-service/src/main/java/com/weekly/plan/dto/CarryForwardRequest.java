package com.weekly.plan.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for carry-forward operation.
 */
public record CarryForwardRequest(
        @NotEmpty List<String> commitIds
) {}
