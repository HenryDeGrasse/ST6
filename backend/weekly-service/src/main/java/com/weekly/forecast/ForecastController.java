package com.weekly.forecast;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.forecast.ForecastDtos.OutcomeForecastListResponse;
import com.weekly.forecast.ForecastDtos.OutcomeForecastResponse;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing persisted target-date forecasts.
 */
@RestController
@RequestMapping("/api/v1/outcomes")
public class ForecastController {

    private final TargetDateForecastService targetDateForecastService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public ForecastController(
            TargetDateForecastService targetDateForecastService,
            AuthenticatedUserContext authenticatedUserContext) {
        this.targetDateForecastService = targetDateForecastService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /** GET /api/v1/outcomes/forecasts */
    @GetMapping("/forecasts")
    public ResponseEntity<OutcomeForecastListResponse> getOutcomeForecasts() {
        return ResponseEntity.ok(new OutcomeForecastListResponse(
                targetDateForecastService.getOrComputeOrgForecasts(authenticatedUserContext.orgId())));
    }

    /** GET /api/v1/outcomes/{outcomeId}/forecast */
    @GetMapping("/{outcomeId}/forecast")
    public ResponseEntity<?> getOutcomeForecast(@PathVariable UUID outcomeId) {
        Optional<OutcomeForecastResponse> forecast =
                targetDateForecastService.getOrComputeOutcomeForecast(authenticatedUserContext.orgId(), outcomeId);
        return forecast.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                        .body(ApiErrorResponse.of(
                                ErrorCode.NOT_FOUND,
                                "No target-date forecast found for outcome " + outcomeId)));
    }
}
