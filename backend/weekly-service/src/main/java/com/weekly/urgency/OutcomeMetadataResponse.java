package com.weekly.urgency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API response DTO for outcome metadata.
 *
 * <p>Returned by:
 * <ul>
 *   <li>{@code GET /api/v1/outcomes/metadata} (list)</li>
 *   <li>{@code GET /api/v1/outcomes/{outcomeId}/metadata} (single)</li>
 *   <li>{@code PUT /api/v1/outcomes/{outcomeId}/metadata} (upsert)</li>
 *   <li>{@code PATCH /api/v1/outcomes/{outcomeId}/progress} (progress update)</li>
 * </ul>
 *
 * @param orgId             the organisation UUID
 * @param outcomeId         the RCDO outcome UUID
 * @param targetDate        target completion date; {@code null} if not set
 * @param progressType      progress tracking model ({@code ACTIVITY},
 *                          {@code METRIC}, or {@code MILESTONE})
 * @param metricName        descriptive name of the tracked metric; {@code null}
 *                          when not applicable
 * @param targetValue       numeric goal value; {@code null} when not applicable
 * @param currentValue      current metric value; {@code null} when not applicable
 * @param unit              unit of measurement; {@code null} when not applicable
 * @param milestones        JSON array of milestone objects (raw string); {@code null}
 *                          when not applicable
 * @param progressPct       computed progress percentage (0–100); {@code null} if
 *                          urgency has not been computed yet
 * @param urgencyBand       urgency classification: {@code NO_TARGET},
 *                          {@code ON_TRACK}, {@code NEEDS_ATTENTION},
 *                          {@code AT_RISK}, or {@code CRITICAL}; {@code null} if
 *                          not yet computed
 * @param lastComputedAt    ISO-8601 timestamp of the last urgency computation;
 *                          {@code null} if not yet computed
 * @param createdAt         ISO-8601 timestamp when the metadata row was created
 * @param updatedAt         ISO-8601 timestamp of the last update to this row
 */
public record OutcomeMetadataResponse(
        UUID orgId,
        UUID outcomeId,
        LocalDate targetDate,
        String progressType,
        String metricName,
        BigDecimal targetValue,
        BigDecimal currentValue,
        String unit,
        String milestones,
        BigDecimal progressPct,
        String urgencyBand,
        String lastComputedAt,
        String createdAt,
        String updatedAt
) {

    /**
     * Maps an {@link OutcomeMetadataEntity} to a response DTO.
     *
     * @param entity the JPA entity to convert
     * @return a new {@link OutcomeMetadataResponse} populated from the entity
     */
    public static OutcomeMetadataResponse from(OutcomeMetadataEntity entity) {
        return new OutcomeMetadataResponse(
                entity.getOrgId(),
                entity.getOutcomeId(),
                entity.getTargetDate(),
                entity.getProgressType(),
                entity.getMetricName(),
                entity.getTargetValue(),
                entity.getCurrentValue(),
                entity.getUnit(),
                entity.getMilestones(),
                entity.getProgressPct(),
                entity.getUrgencyBand(),
                entity.getLastComputedAt() != null ? entity.getLastComputedAt().toString() : null,
                entity.getCreatedAt().toString(),
                entity.getUpdatedAt().toString()
        );
    }
}
