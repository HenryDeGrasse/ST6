package com.weekly.config;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for org-level policy configuration (admin-only).
 *
 * <p>Provides two endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/admin/org-policy} — returns the current org policy including
 *       digest schedule.</li>
 *   <li>{@code PATCH /api/v1/admin/org-policy/digest} — updates the weekly digest
 *       schedule ({@code digestDay} / {@code digestTime}).</li>
 * </ul>
 *
 * <p>Both endpoints require the {@code ADMIN} role. The PATCH endpoint evicts the
 * in-memory policy cache via {@link OrgPolicyService#evict(java.util.UUID)} so the
 * DigestJob picks up the new schedule on its next run.
 */
@RestController
@RequestMapping("/api/v1/admin/org-policy")
public class OrgPolicyController {

    private final OrgPolicyService orgPolicyService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public OrgPolicyController(
            OrgPolicyService orgPolicyService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.orgPolicyService = orgPolicyService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * Returns the current org policy for the authenticated admin's organisation.
     *
     * @return 200 with the org policy, or 403 if the caller lacks the ADMIN role
     */
    @GetMapping
    public ResponseEntity<?> getOrgPolicy() {
        if (!authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Admin role required"));
        }

        OrgPolicyService.OrgPolicy policy = orgPolicyService.getPolicy(authenticatedUserContext.orgId());
        return ResponseEntity.ok(OrgPolicyResponse.from(policy));
    }

    /**
     * Updates the weekly digest schedule for the authenticated admin's organisation.
     *
     * <p>Only {@code digestDay} and {@code digestTime} are mutated; all other policy
     * fields are unchanged. The policy cache is evicted so the DigestJob picks up
     * the new schedule on its next hourly run.
     *
     * @param request the new digest schedule
     * @return 200 with the updated org policy, or 403 if the caller lacks the ADMIN role
     */
    @PatchMapping("/digest")
    public ResponseEntity<?> updateDigestConfig(@Valid @RequestBody UpdateDigestConfigRequest request) {
        if (!authenticatedUserContext.isAdmin()) {
            return ResponseEntity.status(403)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Admin role required"));
        }

        orgPolicyService.updateDigestConfig(
                authenticatedUserContext.orgId(),
                request.digestDay(),
                request.digestTime()
        );
        orgPolicyService.evict(authenticatedUserContext.orgId());

        OrgPolicyService.OrgPolicy updated = orgPolicyService.getPolicy(authenticatedUserContext.orgId());
        return ResponseEntity.ok(OrgPolicyResponse.from(updated));
    }

    // ── Request / Response records ────────────────────────────

    /**
     * Request body for the {@code PATCH /admin/org-policy/digest} endpoint.
     */
    public record UpdateDigestConfigRequest(
            @NotBlank
            @Pattern(regexp = "(?i)MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY",
                    message = "digestDay must be a valid day-of-week (e.g. FRIDAY)")
            String digestDay,

            @NotBlank
            @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d",
                    message = "digestTime must be a valid 24-hour HH:mm time (e.g. 17:00)")
            String digestTime
    ) {}

    /**
     * Response body for both org-policy endpoints.
     */
    public record OrgPolicyResponse(
            boolean chessKingRequired,
            int chessMaxKing,
            int chessMaxQueen,
            String lockDay,
            String lockTime,
            String reconcileDay,
            String reconcileTime,
            boolean blockLockOnStaleRcdo,
            int rcdoStalenessThresholdMinutes,
            String digestDay,
            String digestTime
    ) {
        static OrgPolicyResponse from(OrgPolicyService.OrgPolicy p) {
            return new OrgPolicyResponse(
                    p.chessKingRequired(),
                    p.chessMaxKing(),
                    p.chessMaxQueen(),
                    p.lockDay(),
                    p.lockTime(),
                    p.reconcileDay(),
                    p.reconcileTime(),
                    p.blockLockOnStaleRcdo(),
                    p.rcdoStalenessThresholdMinutes(),
                    p.digestDay(),
                    p.digestTime()
            );
        }
    }
}
