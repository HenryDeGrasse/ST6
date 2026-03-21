package com.weekly.analytics;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for manager-facing multi-week analytics.
 *
 * <p>All endpoints require the caller to have the {@code MANAGER} role.
 * A 403 Forbidden is returned for non-manager requests. A 422 Unprocessable
 * Entity is returned when the {@code weeks} parameter is outside the range
 * [{@value AnalyticsService#MIN_WEEKS}, {@value AnalyticsService#MAX_WEEKS}].
 *
 * <p>Base path: {@code /api/v1/analytics}
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public AnalyticsController(
            AnalyticsService analyticsService,
            AuthenticatedUserContext authenticatedUserContext) {
        this.analyticsService = analyticsService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * GET /api/v1/analytics/outcome-coverage
     *
     * <p>Returns per-week commit activity for a specific RCDO outcome over the
     * last {@code weeks} weeks.
     *
     * @param outcomeId UUID of the RCDO outcome to analyse
     * @param weeks     rolling-window size (1–26, default 8)
     * @return 200 with {@link com.weekly.analytics.dto.OutcomeCoverageTimeline},
     *         403 if not a manager, or 422 if {@code weeks} is out of range
     */
    @GetMapping("/outcome-coverage")
    public ResponseEntity<?> getOutcomeCoverage(
            @RequestParam UUID outcomeId,
            @RequestParam(defaultValue = "8") int weeks) {

        ResponseEntity<?> guard = managerGuard();
        if (guard != null) {
            return guard;
        }

        ResponseEntity<?> weeksError = validateWeeks(weeks);
        if (weeksError != null) {
            return weeksError;
        }

        return ResponseEntity.ok(
                analyticsService.getOutcomeCoverageTimeline(
                        authenticatedUserContext.orgId(), outcomeId, weeks));
    }

    /**
     * GET /api/v1/analytics/carry-forward-heatmap
     *
     * <p>Returns a carry-forward heatmap for the authenticated manager's direct
     * reports over the last {@code weeks} weeks.
     *
     * @param weeks rolling-window size (1–26, default 8)
     * @return 200 with {@link com.weekly.analytics.dto.CarryForwardHeatmap},
     *         403 if not a manager, or 422 if {@code weeks} is out of range
     */
    @GetMapping("/carry-forward-heatmap")
    public ResponseEntity<?> getCarryForwardHeatmap(
            @RequestParam(defaultValue = "8") int weeks) {

        ResponseEntity<?> guard = managerGuard();
        if (guard != null) {
            return guard;
        }

        ResponseEntity<?> weeksError = validateWeeks(weeks);
        if (weeksError != null) {
            return weeksError;
        }

        return ResponseEntity.ok(
                analyticsService.getTeamCarryForwardHeatmap(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId(),
                        weeks));
    }

    /**
     * GET /api/v1/analytics/category-shifts
     *
     * <p>Returns per-user commit category distribution shifts between the prior
     * and recent halves of the analysis window for the manager's direct reports.
     *
     * @param weeks rolling-window size (1–26, default 8)
     * @return 200 with list of {@link com.weekly.analytics.dto.UserCategoryShift},
     *         403 if not a manager, or 422 if {@code weeks} is out of range
     */
    @GetMapping("/category-shifts")
    public ResponseEntity<?> getCategoryShifts(
            @RequestParam(defaultValue = "8") int weeks) {

        ResponseEntity<?> guard = managerGuard();
        if (guard != null) {
            return guard;
        }

        ResponseEntity<?> weeksError = validateWeeks(weeks);
        if (weeksError != null) {
            return weeksError;
        }

        return ResponseEntity.ok(
                analyticsService.getCategoryShiftAnalysis(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId(),
                        weeks));
    }

    /**
     * GET /api/v1/analytics/estimation-accuracy
     *
     * <p>Returns per-user confidence vs actual completion-rate accuracy metrics
     * for the manager's direct reports over the last {@code weeks} weeks.
     *
     * @param weeks rolling-window size (1–26, default 8)
     * @return 200 with list of {@link com.weekly.analytics.dto.UserEstimationAccuracy},
     *         403 if not a manager, or 422 if {@code weeks} is out of range
     */
    @GetMapping("/estimation-accuracy")
    public ResponseEntity<?> getEstimationAccuracy(
            @RequestParam(defaultValue = "8") int weeks) {

        ResponseEntity<?> guard = managerGuard();
        if (guard != null) {
            return guard;
        }

        ResponseEntity<?> weeksError = validateWeeks(weeks);
        if (weeksError != null) {
            return weeksError;
        }

        return ResponseEntity.ok(
                analyticsService.getEstimationAccuracyDistribution(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId(),
                        weeks));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Returns a 403 response if the current user is not a manager, or
     * {@code null} if the check passes.
     */
    private ResponseEntity<?> managerGuard() {
        if (!authenticatedUserContext.isManager()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(
                            ErrorCode.FORBIDDEN,
                            "Manager role required"));
        }
        return null;
    }

    /**
     * Returns a 422 response if {@code weeks} is outside the allowed range, or
     * {@code null} if the value is valid.
     */
    private ResponseEntity<?> validateWeeks(int weeks) {
        if (weeks < AnalyticsService.MIN_WEEKS || weeks > AnalyticsService.MAX_WEEKS) {
            return ResponseEntity.unprocessableEntity()
                    .body(ApiErrorResponse.of(
                            ErrorCode.VALIDATION_ERROR,
                            "weeks must be between " + AnalyticsService.MIN_WEEKS
                                    + " and " + AnalyticsService.MAX_WEEKS));
        }
        return null;
    }
}
