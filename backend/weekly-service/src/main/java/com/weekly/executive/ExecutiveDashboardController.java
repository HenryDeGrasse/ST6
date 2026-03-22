package com.weekly.executive;

import com.weekly.ai.AiFeatureFlags;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregate-only executive dashboard endpoints.
 */
@RestController
@RequestMapping("/api/v1/executive")
public class ExecutiveDashboardController {

    private final ExecutiveDashboardService executiveDashboardService;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final AiFeatureFlags featureFlags;

    public ExecutiveDashboardController(
            ExecutiveDashboardService executiveDashboardService,
            AuthenticatedUserContext authenticatedUserContext,
            AiFeatureFlags featureFlags) {
        this.executiveDashboardService = executiveDashboardService;
        this.authenticatedUserContext = authenticatedUserContext;
        this.featureFlags = featureFlags;
    }

    @GetMapping("/strategic-health")
    public ResponseEntity<?> getStrategicHealth(@RequestParam(required = false) LocalDate weekStart) {
        ResponseEntity<?> accessGuard = accessGuard();
        if (accessGuard != null) {
            return accessGuard;
        }

        if (!featureFlags.isExecutiveDashboardEnabled()) {
            return ResponseEntity.ok(new ExecutiveDashboardUnavailableResponse("unavailable"));
        }

        return ResponseEntity.ok(executiveDashboardService.getStrategicHealth(
                authenticatedUserContext.orgId(),
                weekStart));
    }

    private ResponseEntity<?> accessGuard() {
        if (!authenticatedUserContext.isManager() && !authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Manager or admin role required"));
        }
        return null;
    }

    public record ExecutiveDashboardUnavailableResponse(String status) {
    }
}
