package com.weekly.issues.controller;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.dto.AddCommentRequest;
import com.weekly.issues.dto.AssignIssueRequest;
import com.weekly.issues.dto.CommitIssueToWeekRequest;
import com.weekly.issues.dto.CreateIssueRequest;
import com.weekly.issues.dto.IssueActivityResponse;
import com.weekly.issues.dto.IssueDetailResponse;
import com.weekly.issues.dto.IssueListResponse;
import com.weekly.issues.dto.IssueResponse;
import com.weekly.issues.dto.LogTimeEntryRequest;
import com.weekly.issues.dto.ReleaseIssueRequest;
import com.weekly.issues.dto.UpdateIssueRequest;
import com.weekly.issues.dto.WeeklyAssignmentResponse;
import com.weekly.issues.dto.WeeklyAssignmentsResponse;
import com.weekly.issues.service.IssueService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for backlog issue CRUD and action endpoints (Phase 6).
 *
 * <p>Endpoints match the routes defined in {@code contracts/openapi.yaml}:
 * <ul>
 *   <li>{@code GET/POST /api/v1/teams/{teamId}/issues}</li>
 *   <li>{@code GET/PATCH/DELETE /api/v1/issues/{issueId}}</li>
 *   <li>{@code POST /api/v1/issues/{issueId}/assign}</li>
 *   <li>{@code POST /api/v1/issues/{issueId}/commit}</li>
 *   <li>{@code POST /api/v1/issues/{issueId}/release}</li>
 *   <li>{@code POST /api/v1/issues/{issueId}/comment}</li>
 *   <li>{@code POST /api/v1/issues/{issueId}/time-entry}</li>
 *   <li>{@code GET /api/v1/plans/{planId}/assignments}</li>
 * </ul>
 *
 * <p>Note: week-scoped assignment creation/removal is handled by
 * {@link com.weekly.issues.controller.AssignmentController}.
 */
@RestController
@RequestMapping("/api/v1")
public class IssueController {

    private final IssueService issueService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public IssueController(IssueService issueService, AuthenticatedUserContext ctx) {
        this.issueService = issueService;
        this.authenticatedUserContext = ctx;
    }

    // ── Team-scoped issue list / create ──────────────────────

    @GetMapping("/teams/{teamId}/issues")
    public ResponseEntity<IssueListResponse> listTeamIssues(
            @PathVariable UUID teamId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        IssueStatus statusEnum = status != null ? IssueStatus.valueOf(status.toUpperCase()) : null;
        IssueListResponse response = issueService.listTeamBacklog(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId(),
                statusEnum,
                page,
                size,
                sort
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/teams/{teamId}/issues")
    public ResponseEntity<IssueResponse> createIssue(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateIssueRequest request
    ) {
        IssueResponse response = issueService.createIssue(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Individual issue operations ──────────────────────────

    @GetMapping("/issues/{issueId}")
    public ResponseEntity<IssueDetailResponse> getIssue(@PathVariable UUID issueId) {
        IssueDetailResponse response = issueService.getIssue(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/issues/{issueId}")
    public ResponseEntity<IssueResponse> updateIssue(
            @PathVariable UUID issueId,
            @Valid @RequestBody UpdateIssueRequest request
    ) {
        IssueResponse response = issueService.updateIssue(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/issues/{issueId}")
    public ResponseEntity<IssueResponse> archiveIssue(@PathVariable UUID issueId) {
        IssueResponse response = issueService.archiveIssue(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }

    // ── Issue actions ────────────────────────────────────────

    @PostMapping("/issues/{issueId}/assign")
    public ResponseEntity<IssueResponse> assignIssue(
            @PathVariable UUID issueId,
            @RequestBody AssignIssueRequest request
    ) {
        IssueResponse response = issueService.assignIssue(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/issues/{issueId}/commit")
    public ResponseEntity<WeeklyAssignmentResponse> commitToWeek(
            @PathVariable UUID issueId,
            @Valid @RequestBody CommitIssueToWeekRequest request
    ) {
        WeeklyAssignmentResponse response = issueService.commitToWeek(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/issues/{issueId}/release")
    public ResponseEntity<IssueResponse> releaseToBacklog(
            @PathVariable UUID issueId,
            @Valid @RequestBody ReleaseIssueRequest request
    ) {
        IssueResponse response = issueService.releaseToBacklog(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/issues/{issueId}/comment")
    public ResponseEntity<IssueActivityResponse> addComment(
            @PathVariable UUID issueId,
            @Valid @RequestBody AddCommentRequest request
    ) {
        IssueActivityResponse response = issueService.addComment(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/issues/{issueId}/time-entry")
    public ResponseEntity<IssueActivityResponse> logTimeEntry(
            @PathVariable UUID issueId,
            @Valid @RequestBody LogTimeEntryRequest request
    ) {
        IssueActivityResponse response = issueService.logTimeEntry(
                authenticatedUserContext.orgId(),
                issueId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Plan-scoped assignment list ──────────────────────────

    @GetMapping("/plans/{planId}/assignments")
    public ResponseEntity<WeeklyAssignmentsResponse> listPlanAssignments(
            @PathVariable UUID planId
    ) {
        WeeklyAssignmentsResponse response = issueService.listPlanAssignments(
                authenticatedUserContext.orgId(),
                planId,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }
}
