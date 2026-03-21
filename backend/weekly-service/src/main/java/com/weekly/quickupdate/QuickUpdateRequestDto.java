package com.weekly.quickupdate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request body for the POST /api/v1/plans/{planId}/quick-update endpoint.
 *
 * <p>The {@code updates} list must not be null and each item is individually
 * validated via {@code @Valid}.
 *
 * @param updates the list of per-commit check-in items; must not be null
 */
public record QuickUpdateRequestDto(
        @Valid @NotNull List<QuickUpdateItemDto> updates
) {}
