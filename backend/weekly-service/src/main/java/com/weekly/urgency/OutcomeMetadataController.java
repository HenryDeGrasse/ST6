package com.weekly.urgency;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for outcome metadata CRUD and progress-update operations.
 *
 * <p>Base path: {@code /api/v1/outcomes}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /api/v1/outcomes/metadata}
 *       — list all outcome metadata for the authenticated user's org.</li>
 *   <li>{@code GET  /api/v1/outcomes/{outcomeId}/metadata}
 *       — single outcome metadata (404 if not found).</li>
 *   <li>{@code PUT  /api/v1/outcomes/{outcomeId}/metadata}
 *       — create or update outcome metadata; admin or manager only (403 otherwise).</li>
 *   <li>{@code PATCH /api/v1/outcomes/{outcomeId}/progress}
 *       — lightweight progress update; admin or manager only (403 otherwise).</li>
 * </ul>
 *
 * <p>The caller's {@code orgId} is sourced exclusively from the validated
 * {@link com.weekly.auth.UserPrincipal} exposed through
 * {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1/outcomes")
public class OutcomeMetadataController {

    private final OutcomeMetadataRepository outcomeMetadataRepository;
    private final UrgencyComputeService urgencyComputeService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public OutcomeMetadataController(
            OutcomeMetadataRepository outcomeMetadataRepository,
            UrgencyComputeService urgencyComputeService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.outcomeMetadataRepository = outcomeMetadataRepository;
        this.urgencyComputeService = urgencyComputeService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    // ── GET /api/v1/outcomes/metadata ─────────────────────────────────────────

    /**
     * Returns all outcome metadata rows for the authenticated user's organisation.
     *
     * @return 200 with list of {@link OutcomeMetadataResponse} DTOs
     */
    @GetMapping("/metadata")
    @Transactional(readOnly = true)
    public ResponseEntity<List<OutcomeMetadataResponse>> listMetadata() {
        UUID orgId = authenticatedUserContext.orgId();
        List<OutcomeMetadataResponse> responses = outcomeMetadataRepository
                .findByOrgId(orgId)
                .stream()
                .map(OutcomeMetadataResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // ── GET /api/v1/outcomes/{outcomeId}/metadata ─────────────────────────────

    /**
     * Returns outcome metadata for a single outcome in the authenticated user's
     * organisation.
     *
     * @param outcomeId the outcome UUID
     * @return 200 with {@link OutcomeMetadataResponse}, or 404 if not found
     */
    @GetMapping("/{outcomeId}/metadata")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMetadata(@PathVariable UUID outcomeId) {
        UUID orgId = authenticatedUserContext.orgId();
        Optional<OutcomeMetadataEntity> entity =
                outcomeMetadataRepository.findByOrgIdAndOutcomeId(orgId, outcomeId);

        return entity
                .<ResponseEntity<?>>map(e -> ResponseEntity.ok(OutcomeMetadataResponse.from(e)))
                .orElseGet(() -> ResponseEntity
                        .status(ErrorCode.NOT_FOUND.getHttpStatus())
                        .body(ApiErrorResponse.of(
                                ErrorCode.NOT_FOUND,
                                "Outcome metadata not found for outcomeId: " + outcomeId
                        )));
    }

    // ── PUT /api/v1/outcomes/{outcomeId}/metadata ─────────────────────────────

    /**
     * Creates or updates (upserts) the metadata for the given outcome.
     *
     * <p>Restricted to admins and managers. Returns {@code 403 Forbidden} for
     * any other authenticated role.
     *
     * <p>After persisting the upserted metadata, urgency recomputation is
     * triggered for the updated outcome so that urgency bands reflect the new
     * target date and progress configuration immediately.
     *
     * @param outcomeId the outcome UUID
     * @param request   the metadata to create or update
     * @return 200 with the saved {@link OutcomeMetadataResponse}, or 403 if
     *         the caller lacks the required role
     */
    @PutMapping("/{outcomeId}/metadata")
    @Transactional
    public ResponseEntity<?> upsertMetadata(
            @PathVariable UUID outcomeId,
            @Valid @RequestBody OutcomeMetadataRequest request
    ) {
        if (!authenticatedUserContext.isAdmin() && !authenticatedUserContext.isManager()) {
            return ResponseEntity
                    .status(ErrorCode.FORBIDDEN.getHttpStatus())
                    .body(ApiErrorResponse.of(
                            ErrorCode.FORBIDDEN,
                            "Admin or manager role required to manage outcome metadata"
                    ));
        }

        UUID orgId = authenticatedUserContext.orgId();

        OutcomeMetadataEntity entity = outcomeMetadataRepository
                .findByOrgIdAndOutcomeId(orgId, outcomeId)
                .orElseGet(() -> new OutcomeMetadataEntity(orgId, outcomeId));

        applyRequest(entity, request);
        outcomeMetadataRepository.save(entity);

        OutcomeMetadataEntity saved = urgencyComputeService
                .computeUrgencyForOutcome(orgId, outcomeId)
                .orElse(entity);

        return ResponseEntity.ok(OutcomeMetadataResponse.from(saved));
    }

    // ── PATCH /api/v1/outcomes/{outcomeId}/progress ───────────────────────────

    /**
     * Updates the progress fields ({@code currentValue} and/or {@code milestones})
     * of an existing outcome metadata record.
     *
     * <p>Restricted to admins and managers. Returns {@code 403 Forbidden} for
     * any other authenticated role. Returns {@code 404 Not Found} if no metadata
     * row exists for the given outcome.
     *
     * <p>After persisting the update, urgency recomputation is triggered for the
     * updated outcome so that urgency bands reflect the new progress values immediately.
     *
     * @param outcomeId the outcome UUID
     * @param request   the progress fields to update
     * @return 200 with the updated {@link OutcomeMetadataResponse}, 403 if the
     *         caller lacks the required role, or 404 if metadata does not exist
     */
    @PatchMapping("/{outcomeId}/progress")
    @Transactional
    public ResponseEntity<?> updateProgress(
            @PathVariable UUID outcomeId,
            @Valid @RequestBody ProgressUpdateRequest request
    ) {
        if (!authenticatedUserContext.isAdmin() && !authenticatedUserContext.isManager()) {
            return ResponseEntity
                    .status(ErrorCode.FORBIDDEN.getHttpStatus())
                    .body(ApiErrorResponse.of(
                            ErrorCode.FORBIDDEN,
                            "Admin or manager role required to update outcome progress"
                    ));
        }

        UUID orgId = authenticatedUserContext.orgId();

        Optional<OutcomeMetadataEntity> entityOpt =
                outcomeMetadataRepository.findByOrgIdAndOutcomeId(orgId, outcomeId);

        if (entityOpt.isEmpty()) {
            return ResponseEntity
                    .status(ErrorCode.NOT_FOUND.getHttpStatus())
                    .body(ApiErrorResponse.of(
                            ErrorCode.NOT_FOUND,
                            "Outcome metadata not found for outcomeId: " + outcomeId
                    ));
        }

        OutcomeMetadataEntity entity = entityOpt.get();

        if (request.currentValue() != null) {
            entity.setCurrentValue(request.currentValue());
        }
        if (request.milestones() != null) {
            entity.setMilestones(request.milestones());
        }

        outcomeMetadataRepository.save(entity);

        OutcomeMetadataEntity saved = urgencyComputeService
                .computeUrgencyForOutcome(orgId, outcomeId)
                .orElse(entity);

        return ResponseEntity.ok(OutcomeMetadataResponse.from(saved));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Applies all non-null fields from an {@link OutcomeMetadataRequest} to the
     * given entity. The {@code targetDate} field is always applied (even if null)
     * to allow removing a previously-set target date.
     *
     * @param entity  the entity to update
     * @param request the request containing the new values
     */
    private void applyRequest(OutcomeMetadataEntity entity, OutcomeMetadataRequest request) {
        // targetDate may be explicitly nulled to remove target-date tracking.
        entity.setTargetDate(request.targetDate());

        if (request.progressType() != null) {
            entity.setProgressType(request.progressType());
        }
        if (request.metricName() != null) {
            entity.setMetricName(request.metricName());
        }
        if (request.targetValue() != null) {
            entity.setTargetValue(request.targetValue());
        }
        if (request.currentValue() != null) {
            entity.setCurrentValue(request.currentValue());
        }
        if (request.unit() != null) {
            entity.setUnit(request.unit());
        }
        if (request.milestones() != null) {
            entity.setMilestones(request.milestones());
        }
    }
}
