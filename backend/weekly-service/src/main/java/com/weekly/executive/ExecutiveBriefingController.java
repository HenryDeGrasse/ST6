package com.weekly.executive;

import com.weekly.ai.AiFeatureFlags;
import com.weekly.ai.RateLimiter;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-generated executive briefing endpoint.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class ExecutiveBriefingController {

    private final ExecutiveDashboardService executiveDashboardService;
    private final ExecutiveBriefingService executiveBriefingService;
    private final AiFeatureFlags featureFlags;
    private final RateLimiter rateLimiter;
    private final AuthenticatedUserContext authenticatedUserContext;

    public ExecutiveBriefingController(
            ExecutiveDashboardService executiveDashboardService,
            ExecutiveBriefingService executiveBriefingService,
            AiFeatureFlags featureFlags,
            RateLimiter rateLimiter,
            AuthenticatedUserContext authenticatedUserContext) {
        this.executiveDashboardService = executiveDashboardService;
        this.executiveBriefingService = executiveBriefingService;
        this.featureFlags = featureFlags;
        this.rateLimiter = rateLimiter;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    @PostMapping("/executive-briefing")
    public ResponseEntity<?> executiveBriefing(@RequestBody @Valid ExecutiveBriefingRequest request) {
        ResponseEntity<?> accessGuard = accessGuard();
        if (accessGuard != null) {
            return accessGuard;
        }

        if (!featureFlags.isExecutiveDashboardEnabled()) {
            return ResponseEntity.ok(ExecutiveBriefingService.ExecutiveBriefingResult.unavailable());
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        LocalDate weekStart = parseWeekStart(request.weekStart());
        ExecutiveDashboardService.ExecutiveDashboardResult dashboard = executiveDashboardService.getStrategicHealth(
                authenticatedUserContext.orgId(),
                weekStart);
        return ResponseEntity.ok(executiveBriefingService.createBriefing(authenticatedUserContext.orgId(), dashboard));
    }

    private ResponseEntity<?> accessGuard() {
        if (!authenticatedUserContext.isManager() && !authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Manager or admin role required"));
        }
        return null;
    }

    private LocalDate parseWeekStart(String rawWeekStart) {
        if (rawWeekStart == null || rawWeekStart.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(rawWeekStart);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("weekStart must be an ISO-8601 date");
        }
    }

    public record ExecutiveBriefingRequest(@NotBlank String weekStart) {
    }
}
