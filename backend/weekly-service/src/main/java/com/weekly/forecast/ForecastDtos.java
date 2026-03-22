package com.weekly.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTOs for Phase 5 target-date forecasting surfaces.
 */
public final class ForecastDtos {

    private ForecastDtos() {
    }

    public record OutcomeForecastResponse(
            String outcomeId,
            String outcomeName,
            LocalDate targetDate,
            LocalDate projectedTargetDate,
            BigDecimal projectedProgressPct,
            BigDecimal projectedVelocity,
            BigDecimal confidenceScore,
            String confidenceBand,
            String forecastStatus,
            String modelVersion,
            List<ForecastFactorResponse> contributingFactors,
            List<String> recommendations,
            String computedAt
    ) {}

    public record ForecastFactorResponse(
            String type,
            String label,
            BigDecimal score,
            String detail
    ) {}

    public record OutcomeForecastListResponse(
            List<OutcomeForecastResponse> forecasts
    ) {}
}
