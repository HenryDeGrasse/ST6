package com.weekly.urgency;

import com.weekly.shared.validation.NullOrNotBlank;
import java.math.BigDecimal;

/**
 * Request body DTO for a lightweight progress update on an existing outcome
 * metadata record.
 *
 * <p>Used by {@code PATCH /api/v1/outcomes/{outcomeId}/progress}.
 * Both fields are optional — supply only the ones that need updating.
 *
 * @param currentValue updated current value of the tracked metric; {@code null}
 *                     if the metric value should not change
 * @param milestones   updated JSON array of milestone objects; {@code null} if
 *                     milestones should not change
 */
public record ProgressUpdateRequest(
        BigDecimal currentValue,
        @NullOrNotBlank
        String milestones
) {}
