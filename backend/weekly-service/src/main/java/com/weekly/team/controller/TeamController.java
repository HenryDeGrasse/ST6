package com.weekly.team.controller;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.team.domain.AccessRequestStatus;
import com.weekly.team.domain.TeamRole;
import com.weekly.team.dto.AddTeamMemberRequest;
import com.weekly.team.dto.CreateTeamRequest;
import com.weekly.team.dto.TeamAccessRequestActionRequest;
import com.weekly.team.dto.TeamAccessRequestListResponse;
import com.weekly.team.dto.TeamAccessRequestResponse;
import com.weekly.team.dto.TeamDetailResponse;
import com.weekly.team.dto.TeamListResponse;
import com.weekly.team.dto.TeamMemberResponse;
import com.weekly.team.dto.TeamResponse;
import com.weekly.team.dto.UpdateTeamRequest;
import com.weekly.team.service.TeamService;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for team management operations (Phase 6).
 *
 * <p>All endpoints are under {@code /api/v1/teams/**}. User identity and
 * org context come from the validated {@link AuthenticatedUserContext} —
 * never from raw request headers (§9.1).
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public TeamController(
            TeamService teamService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.teamService = teamService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    // ── Team CRUD ────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<TeamListResponse> listTeams() {
        TeamListResponse response = teamService.listTeams(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                authenticatedUserContext.isManager(),
                authenticatedUserContext.isAdmin()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(
            @Valid @RequestBody CreateTeamRequest request
    ) {
        TeamResponse response = teamService.createTeam(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                authenticatedUserContext.isAdmin(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamDetailResponse> getTeam(@PathVariable UUID teamId) {
        TeamDetailResponse response = teamService.getTeam(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId(),
                authenticatedUserContext.isAdmin()
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<TeamResponse> updateTeam(
            @PathVariable UUID teamId,
            @Valid @RequestBody UpdateTeamRequest request
    ) {
        TeamResponse response = teamService.updateTeam(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId(),
                request
        );
        return ResponseEntity.ok(response);
    }

    // ── Members ──────────────────────────────────────────────

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<TeamMemberResponse>> listMembers(@PathVariable UUID teamId) {
        List<TeamMemberResponse> members = teamService.listMembers(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId(),
                authenticatedUserContext.isAdmin()
        );
        return ResponseEntity.ok(members);
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberResponse> addMember(
            @PathVariable UUID teamId,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        TeamRole role = parseRole(request.role());
        TeamMemberResponse response = teamService.addMember(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId(),
                request.userId(),
                role
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID teamId,
            @PathVariable UUID userId
    ) {
        teamService.removeMember(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId(),
                userId
        );
        return ResponseEntity.noContent().build();
    }

    // ── Access Requests ──────────────────────────────────────

    @GetMapping("/{teamId}/access-requests")
    public ResponseEntity<TeamAccessRequestListResponse> listAccessRequests(
            @PathVariable UUID teamId
    ) {
        TeamAccessRequestListResponse response = teamService.listAccessRequests(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{teamId}/access-requests")
    public ResponseEntity<TeamAccessRequestResponse> requestAccess(
            @PathVariable UUID teamId
    ) {
        TeamAccessRequestResponse response = teamService.requestAccess(
                authenticatedUserContext.orgId(),
                teamId,
                authenticatedUserContext.userId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{teamId}/access-requests/{requestId}")
    public ResponseEntity<TeamAccessRequestResponse> decideAccessRequest(
            @PathVariable UUID teamId,
            @PathVariable UUID requestId,
            @Valid @RequestBody TeamAccessRequestActionRequest request
    ) {
        AccessRequestStatus decision = AccessRequestStatus.valueOf(request.status().toUpperCase());
        TeamAccessRequestResponse response = teamService.decideAccessRequest(
                authenticatedUserContext.orgId(),
                teamId,
                requestId,
                authenticatedUserContext.userId(),
                decision
        );
        return ResponseEntity.ok(response);
    }

    // ── Private helpers ──────────────────────────────────────

    private static TeamRole parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return TeamRole.MEMBER;
        }
        try {
            return TeamRole.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TeamRole.MEMBER;
        }
    }
}
