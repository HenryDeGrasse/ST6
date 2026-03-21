package com.weekly.admin;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.config.CorrelationIdFilter;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for privileged administrative operations.
 *
 * <p>All endpoints in this controller require the {@code ADMIN} role. An
 * authenticated principal without that role receives {@code 403 Forbidden}.
 *
 * <p>{@code orgId} is sourced exclusively from the validated
 * {@link com.weekly.auth.UserPrincipal} (§9.1) — the admin's own
 * {@code orgId} claim scopes operations to their organisation.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final UserDataDeletionService userDataDeletionService;
    private final AdminDashboardService adminDashboardService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public AdminController(
            UserDataDeletionService userDataDeletionService,
            AdminDashboardService adminDashboardService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.userDataDeletionService = userDataDeletionService;
        this.adminDashboardService = adminDashboardService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * GDPR right-to-be-forgotten endpoint (PRD §14.7).
     *
     * <p>Erases all personal data for the specified user within the authenticated
     * admin's organisation:
     * <ul>
     *   <li>Soft-deletes {@code weekly_plans} and {@code weekly_commits}.</li>
     *   <li>Anonymises {@code audit_events} (rows retained for chain integrity).</li>
     *   <li>Hard-deletes {@code notifications} and {@code idempotency_keys}.</li>
     * </ul>
     *
     * <p>The deletion request itself is recorded as an audit event, using the
     * admin's {@code userId} as the actor.
     *
     * <p>Returns {@code 204 No Content} on success, {@code 403 Forbidden} if the
     * caller lacks the {@code ADMIN} role.
     *
     * @param userId  the user whose data should be erased
     * @param request the HTTP request (used for IP address and correlation-ID extraction)
     * @return 204 No Content on success, 403 Forbidden if the caller lacks ADMIN role
     */
    @DeleteMapping("/users/{userId}/data")
    public ResponseEntity<ApiErrorResponse> deleteUserData(
            @PathVariable UUID userId,
            HttpServletRequest request
    ) {
        if (!authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                    .body(ApiErrorResponse.of(
                            ErrorCode.FORBIDDEN,
                            "Admin role required to perform GDPR data deletion"
                    ));
        }

        String ipAddress = request.getRemoteAddr();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        userDataDeletionService.deleteUserData(
                authenticatedUserContext.orgId(),
                userId,
                authenticatedUserContext.userId(),
                ipAddress,
                correlationId
        );

        return ResponseEntity.noContent().build();
    }

    // ── Dashboard endpoints ───────────────────────────────────────────────────

    /**
     * Returns weekly adoption funnel metrics for the admin's organisation.
     *
     * <p>Computes per-week breakdowns of plan creation, lock, reconciliation,
     * and manager-review counts, plus total active users and cadence-compliance
     * rate over a rolling window.
     *
     * @param weeks rolling-window size (1–26, default 8)
     * @return 200 with {@link AdoptionMetrics}, 403 if not ADMIN, 422 if weeks out of range
     */
    @GetMapping("/adoption-metrics")
    public ResponseEntity<?> getAdoptionMetrics(
            @RequestParam(defaultValue = "8") int weeks
    ) {
        if (!authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Admin role required"));
        }
        if (weeks < AdminDashboardService.MIN_WEEKS || weeks > AdminDashboardService.MAX_WEEKS) {
            return ResponseEntity.unprocessableEntity()
                    .body(ApiErrorResponse.of(
                            ErrorCode.VALIDATION_ERROR,
                            "weeks must be between " + AdminDashboardService.MIN_WEEKS
                                    + " and " + AdminDashboardService.MAX_WEEKS
                    ));
        }
        AdoptionMetrics metrics = adminDashboardService.getAdoptionMetrics(
                authenticatedUserContext.orgId(), weeks);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Returns AI feature usage metrics for the admin's organisation.
     *
     * <p>Includes suggestion acceptance/defer/decline rates from
     * {@code ai_suggestion_feedback} and in-process cache hit/miss stats.
     *
     * @param weeks rolling-window size (1–26, default 8)
     * @return 200 with {@link AiUsageMetrics}, 403 if not ADMIN, 422 if weeks out of range
     */
    @GetMapping("/ai-usage")
    public ResponseEntity<?> getAiUsage(
            @RequestParam(defaultValue = "8") int weeks
    ) {
        if (!authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Admin role required"));
        }
        if (weeks < AdminDashboardService.MIN_WEEKS || weeks > AdminDashboardService.MAX_WEEKS) {
            return ResponseEntity.unprocessableEntity()
                    .body(ApiErrorResponse.of(
                            ErrorCode.VALIDATION_ERROR,
                            "weeks must be between " + AdminDashboardService.MIN_WEEKS
                                    + " and " + AdminDashboardService.MAX_WEEKS
                    ));
        }
        AiUsageMetrics metrics = adminDashboardService.getAiUsageMetrics(
                authenticatedUserContext.orgId(), weeks);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Returns the RCDO health report for the admin's organisation.
     *
     * <p>Examines locked commits from the last 8 weeks and produces a ranked list
     * of outcomes by commit coverage. Outcomes with zero commits are listed as stale.
     *
     * @return 200 with {@link RcdoHealthReport}, 403 if not ADMIN
     */
    @GetMapping("/rcdo-health")
    public ResponseEntity<?> getRcdoHealth() {
        if (!authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Admin role required"));
        }
        RcdoHealthReport report = adminDashboardService.getRcdoHealth(
                authenticatedUserContext.orgId());
        return ResponseEntity.ok(report);
    }
}
