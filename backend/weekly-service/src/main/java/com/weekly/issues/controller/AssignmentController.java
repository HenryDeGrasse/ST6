package com.weekly.issues.controller;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.issues.dto.CreateWeeklyAssignmentRequest;
import com.weekly.issues.dto.WeeklyAssignmentResponse;
import com.weekly.issues.service.AssignmentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the week-scoped assignment endpoints (Phase 6).
 *
 * <p>These endpoints manage adding and removing issues from a weekly plan via
 * the new assignment-based workflow. They run in parallel with the legacy
 * commit-based endpoints in {@link com.weekly.plan.controller.CommitController}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/weeks/{weekStart}/plan/assignments} — Add an issue to
 *       this week's plan. Creates a {@code weekly_assignment} and dual-writes a legacy
 *       {@code weekly_commit} for backward compatibility.</li>
 *   <li>{@code DELETE /api/v1/weeks/{weekStart}/plan/assignments/{assignmentId}} —
 *       Remove an assignment from the plan (also removes the mirrored legacy commit).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public AssignmentController(
            AssignmentService assignmentService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.assignmentService = assignmentService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * Adds an issue to the user's plan for the given week.
     *
     * <p>The {@code weekStart} path variable is an ISO date (e.g. "2026-03-23").
     * The service resolves the plan by orgId + userId + weekStart; throws
     * {@code 409 Conflict} if the issue is already assigned this week, or
     * {@code 400 Bad Request} if no plan exists for the week.
     */
    @PostMapping("/weeks/{weekStart}/plan/assignments")
    public ResponseEntity<WeeklyAssignmentResponse> addToWeekPlan(
            @PathVariable String weekStart,
            @Valid @RequestBody CreateWeeklyAssignmentRequest request
    ) {
        WeeklyAssignmentResponse response = assignmentService.addToWeekPlan(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weekStart,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Removes an assignment from the week's plan.
     *
     * <p>The {@code weekStart} path variable is present for URL symmetry with the
     * POST endpoint; the actual removal is keyed by {@code assignmentId}. The caller
     * must own the plan that contains the assignment.
     */
    @DeleteMapping("/weeks/{weekStart}/plan/assignments/{assignmentId}")
    public ResponseEntity<Void> removeFromWeekPlan(
            @PathVariable String weekStart,
            @PathVariable UUID assignmentId
    ) {
        assignmentService.removeFromWeekPlan(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weekStart,
                assignmentId
        );
        return ResponseEntity.noContent().build();
    }
}
