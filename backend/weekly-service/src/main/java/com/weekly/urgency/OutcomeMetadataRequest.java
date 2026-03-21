package com.weekly.urgency;

import com.weekly.shared.validation.NullOrNotBlank;
import com.weekly.shared.validation.ValueOfEnum;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body DTO for creating or updating outcome metadata.
 *
 * <p>Used by {@code PUT /api/v1/outcomes/{outcomeId}/metadata}.
 * All fields are optional — omitted fields leave the existing persisted value
 * unchanged on update.
 *
 * @param targetDate   target completion date for the outcome; {@code null} removes
 *                     target-date tracking and sets the urgency band to
 *                     {@code NO_TARGET}
 * @param progressType progress tracking model: {@code ACTIVITY} (default),
 *                     {@code METRIC}, or {@code MILESTONE}
 * @param metricName   descriptive name of the metric tracked (METRIC type only)
 * @param targetValue  numeric goal for the metric (METRIC type only)
 * @param currentValue current metric value (METRIC type only)
 * @param unit         unit of measurement, e.g. {@code "%"} or {@code "USD"}
 *                     (METRIC type only)
 * @param milestones   JSON array of milestone objects; each object has
 *                     {@code status} ({@code PENDING}, {@code IN_PROGRESS},
 *                     {@code DONE}) and an optional {@code weight} (defaults to
 *                     1.0) — stored as JSONB (MILESTONE type only)
 */
public record OutcomeMetadataRequest(
        LocalDate targetDate,
        @ValueOfEnum(enumClass = OutcomeProgressType.class)
        String progressType,
        @NullOrNotBlank
        @Size(max = 200)
        String metricName,
        BigDecimal targetValue,
        BigDecimal currentValue,
        @NullOrNotBlank
        @Size(max = 50)
        String unit,
        @NullOrNotBlank
        String milestones
) {}
