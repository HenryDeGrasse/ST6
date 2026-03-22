package com.weekly.team.service;

import com.weekly.audit.AuditService;
import com.weekly.auth.OrgGraphClient;
import com.weekly.team.domain.AccessRequestStatus;
import com.weekly.team.domain.TeamAccessRequestEntity;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.domain.TeamMemberEntity;
import com.weekly.team.domain.TeamRole;
import com.weekly.team.dto.CreateTeamRequest;
import com.weekly.team.dto.TeamAccessRequestListResponse;
import com.weekly.team.dto.TeamAccessRequestResponse;
import com.weekly.team.dto.TeamDetailResponse;
import com.weekly.team.dto.TeamListResponse;
import com.weekly.team.dto.TeamMemberResponse;
import com.weekly.team.dto.TeamResponse;
import com.weekly.team.dto.UpdateTeamRequest;
import com.weekly.team.repository.TeamAccessRequestRepository;
import com.weekly.team.repository.TeamMemberRepository;
import com.weekly.team.repository.TeamRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for team lifecycle operations (Phase 6).
 *
 * <p>Visibility rules:
 * <ul>
 *   <li>IC: sees teams they are a member of.</li>
 *   <li>Manager: sees all teams containing their direct reports.</li>
 *   <li>Admin: sees all teams in the org.</li>
 * </ul>
 *
 * <p>Mutation rules:
 * <ul>
 *   <li>Team creation: managers (validated via OrgGraphClient) or admins only.</li>
 *   <li>Update team / add member / remove member / decide access request: team OWNER only.</li>
 *   <li>Request access: any authenticated user not already a member.</li>
 * </ul>
 */
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final TeamAccessRequestRepository accessRequestRepository;
    private final OrgGraphClient orgGraphClient;
    private final AuditService auditService;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository memberRepository,
            TeamAccessRequestRepository accessRequestRepository,
            OrgGraphClient orgGraphClient,
            AuditService auditService
    ) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.orgGraphClient = orgGraphClient;
        this.auditService = auditService;
    }

    // ── Create ───────────────────────────────────────────────

    /**
     * Creates a new team in the organisation.
     *
     * <p>If {@code keyPrefix} is null or blank, a prefix is auto-derived from
     * the first four uppercase characters of {@code name}, deduplicating by
     * appending incrementing digits when collisions exist.
     */
    @Transactional
    public TeamResponse createTeam(
            UUID orgId,
            UUID requestingUserId,
            boolean isAdmin,
            CreateTeamRequest request
    ) {
        validateCanCreateTeam(orgId, requestingUserId, isAdmin);
        String resolvedPrefix = resolvePrefix(orgId, request.keyPrefix(), request.name());
        String description = request.description() != null ? request.description() : "";

        TeamEntity team = new TeamEntity(
                UUID.randomUUID(),
                orgId,
                request.name(),
                resolvedPrefix,
                requestingUserId
        );
        team.setDescription(description);
        teamRepository.save(team);

        // Creator automatically becomes an OWNER member
        TeamMemberEntity ownerMembership = new TeamMemberEntity(
                team.getId(), requestingUserId, orgId, TeamRole.OWNER
        );
        memberRepository.save(ownerMembership);

        auditService.record(
                orgId, requestingUserId,
                "TEAM_CREATED", "Team", team.getId(),
                null, "ACTIVE", null, null, null
        );

        return TeamResponse.from(team);
    }

    // ── Read ─────────────────────────────────────────────────

    /**
     * Returns team detail (team + members) for a visible team.
     *
     * @throws TeamNotFoundException if the team does not exist.
     */
    @Transactional(readOnly = true)
    public TeamDetailResponse getTeam(UUID orgId, UUID teamId, UUID requestingUserId, boolean isAdmin) {
        TeamEntity team = requireVisibleTeam(orgId, teamId, requestingUserId, isAdmin);
        List<TeamMemberEntity> members = memberRepository.findAllByTeamId(teamId);
        return new TeamDetailResponse(TeamResponse.from(team), toMemberResponses(members));
    }

    /**
     * Lists teams visible to the requesting user.
     *
     * <ul>
     *   <li>Admin: all teams in the org.</li>
     *   <li>Manager: own teams + teams containing direct reports.</li>
     *   <li>IC: teams they are a member of.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public TeamListResponse listTeams(UUID orgId, UUID requestingUserId, boolean isManager, boolean isAdmin) {
        if (isAdmin) {
            List<TeamResponse> all = teamRepository.findAllByOrgId(orgId).stream()
                    .map(TeamResponse::from)
                    .sorted(java.util.Comparator.comparing(TeamResponse::name))
                    .toList();
            return new TeamListResponse(all);
        }

        // Collect team IDs the user has access to
        Set<UUID> visibleTeamIds = new HashSet<>();

        // Teams the user is a direct member of
        memberRepository.findAllByOrgIdAndUserId(orgId, requestingUserId)
                .forEach(m -> visibleTeamIds.add(m.getTeamId()));

        // Managers also see teams of their direct reports
        if (isManager) {
            List<UUID> directReports = orgGraphClient.getDirectReports(orgId, requestingUserId);
            for (UUID reportId : directReports) {
                memberRepository.findAllByOrgIdAndUserId(orgId, reportId)
                        .forEach(m -> visibleTeamIds.add(m.getTeamId()));
            }
        }

        List<TeamResponse> teams = new ArrayList<>();
        for (UUID teamId : visibleTeamIds) {
            teamRepository.findByOrgIdAndId(orgId, teamId).map(TeamResponse::from).ifPresent(teams::add);
        }
        teams.sort(java.util.Comparator.comparing(TeamResponse::name));
        return new TeamListResponse(teams);
    }

    // ── Update ───────────────────────────────────────────────

    /**
     * Updates mutable team fields (name and/or description). Owner-only.
     *
     * @throws TeamNotFoundException     if the team does not exist.
     * @throws TeamAccessDeniedException if the requesting user is not the team owner.
     */
    @Transactional
    public TeamResponse updateTeam(
            UUID orgId, UUID teamId, UUID requestingUserId, UpdateTeamRequest request
    ) {
        TeamEntity team = requireTeam(orgId, teamId);
        requireOwner(teamId, requestingUserId);

        if (request.name() != null) {
            team.setName(request.name());
        }
        if (request.description() != null) {
            team.setDescription(request.description());
        }
        teamRepository.save(team);

        auditService.record(
                orgId, requestingUserId,
                "TEAM_UPDATED", "Team", teamId,
                null, null, null, null, null
        );

        return TeamResponse.from(team);
    }

    // ── Members ──────────────────────────────────────────────

    /**
     * Lists all members of the specified team.
     *
     * @throws TeamNotFoundException if the team does not exist.
     */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(
            UUID orgId,
            UUID teamId,
            UUID requestingUserId,
            boolean isAdmin
    ) {
        requireVisibleTeam(orgId, teamId, requestingUserId, isAdmin);
        return toMemberResponses(memberRepository.findAllByTeamId(teamId));
    }

    /**
     * Adds a user as a member of the team. Owner-only.
     *
     * @throws TeamAccessDeniedException if the requesting user is not the team owner.
     * @throws IllegalStateException     if the target user is already a member.
     */
    @Transactional
    public TeamMemberResponse addMember(
            UUID orgId, UUID teamId, UUID requestingUserId, UUID targetUserId, TeamRole role
    ) {
        requireTeam(orgId, teamId);
        requireOwner(teamId, requestingUserId);

        if (memberRepository.existsByTeamIdAndUserId(teamId, targetUserId)) {
            throw new IllegalStateException("User is already a member of this team");
        }

        TeamMemberEntity member = new TeamMemberEntity(teamId, targetUserId, orgId, role);
        memberRepository.save(member);

        auditService.record(
                orgId, requestingUserId,
                "TEAM_MEMBER_ADDED", "Team", teamId,
                null, role.name(), null, null, null
        );

        return TeamMemberResponse.from(member);
    }

    /**
     * Removes a member from the team. Owner-only. The owner cannot remove themselves.
     *
     * @throws TeamAccessDeniedException if the requesting user is not the owner,
     *                                   or if they attempt to remove themselves.
     * @throws TeamNotFoundException     if the member is not on the team.
     */
    @Transactional
    public void removeMember(UUID orgId, UUID teamId, UUID requestingUserId, UUID targetUserId) {
        requireTeam(orgId, teamId);
        requireOwner(teamId, requestingUserId);

        if (requestingUserId.equals(targetUserId)) {
            throw new TeamAccessDeniedException("Team owner cannot remove themselves");
        }

        TeamMemberEntity.TeamMemberId memberId =
                new TeamMemberEntity.TeamMemberId(teamId, targetUserId);
        if (!memberRepository.existsById(memberId)) {
            throw new TeamNotFoundException(teamId);
        }
        memberRepository.deleteById(memberId);

        auditService.record(
                orgId, requestingUserId,
                "TEAM_MEMBER_REMOVED", "Team", teamId,
                null, null, null, null, null
        );
    }

    // ── Access Requests ──────────────────────────────────────

    /**
     * Submits a request to join the specified team.
     *
     * @throws IllegalStateException if the user is already a member, or if a
     *                               pending request already exists.
     */
    @Transactional
    public TeamAccessRequestResponse requestAccess(UUID orgId, UUID teamId, UUID requestingUserId) {
        requireTeam(orgId, teamId);

        if (memberRepository.existsByTeamIdAndUserId(teamId, requestingUserId)) {
            throw new IllegalStateException("Already a member of this team");
        }

        Optional<TeamAccessRequestEntity> existing =
                accessRequestRepository.findByTeamIdAndRequesterUserId(teamId, requestingUserId);
        if (existing.isPresent()
                && existing.get().getStatus() == AccessRequestStatus.PENDING) {
            throw new IllegalStateException("A pending access request already exists");
        }

        TeamAccessRequestEntity request = new TeamAccessRequestEntity(
                UUID.randomUUID(), teamId, requestingUserId, orgId
        );
        accessRequestRepository.save(request);

        return TeamAccessRequestResponse.from(request);
    }

    /**
     * Lists pending access requests for a team. Owner-only.
     *
     * @throws TeamAccessDeniedException if the requesting user is not the team owner.
     */
    @Transactional(readOnly = true)
    public TeamAccessRequestListResponse listAccessRequests(
            UUID orgId, UUID teamId, UUID requestingUserId
    ) {
        requireTeam(orgId, teamId);
        requireOwner(teamId, requestingUserId);

        List<TeamAccessRequestResponse> responses =
                accessRequestRepository
                        .findAllByTeamIdAndStatus(teamId, AccessRequestStatus.PENDING)
                        .stream()
                        .map(TeamAccessRequestResponse::from)
                        .toList();
        return new TeamAccessRequestListResponse(responses);
    }

    /**
     * Approves or denies a team access request. Owner-only.
     *
     * <p>Approval automatically creates a MEMBER membership for the requester.
     *
     * @throws TeamAccessDeniedException if the requesting user is not the team owner.
     * @throws TeamNotFoundException     if the access request does not exist.
     */
    @Transactional
    public TeamAccessRequestResponse decideAccessRequest(
            UUID orgId,
            UUID teamId,
            UUID requestId,
            UUID requestingUserId,
            AccessRequestStatus decision
    ) {
        requireTeam(orgId, teamId);
        requireOwner(teamId, requestingUserId);

        TeamAccessRequestEntity request = accessRequestRepository.findById(requestId)
                .filter(existing -> existing.getTeamId().equals(teamId) && existing.getOrgId().equals(orgId))
                .orElseThrow(() -> new TeamAccessRequestNotFoundException(requestId));

        request.decide(decision, requestingUserId);
        accessRequestRepository.save(request);

        if (decision == AccessRequestStatus.APPROVED) {
            if (!memberRepository.existsByTeamIdAndUserId(teamId, request.getRequesterUserId())) {
                TeamMemberEntity member = new TeamMemberEntity(
                        teamId, request.getRequesterUserId(), orgId, TeamRole.MEMBER
                );
                memberRepository.save(member);
            }
        }

        auditService.record(
                orgId, requestingUserId,
                "TEAM_ACCESS_REQUEST_" + decision.name(),
                "Team", teamId,
                "PENDING", decision.name(), null, null, null
        );

        return TeamAccessRequestResponse.from(request);
    }

    // ── Private helpers ──────────────────────────────────────

    private TeamEntity requireTeam(UUID orgId, UUID teamId) {
        return teamRepository.findByOrgIdAndId(orgId, teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));
    }

    private TeamEntity requireVisibleTeam(UUID orgId, UUID teamId, UUID userId, boolean isAdmin) {
        TeamEntity team = requireTeam(orgId, teamId);
        if (!canViewTeam(orgId, teamId, userId, isAdmin)) {
            throw new TeamAccessDeniedException("You do not have access to this team");
        }
        return team;
    }

    private boolean canViewTeam(UUID orgId, UUID teamId, UUID userId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        if (memberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            return true;
        }
        List<UUID> directReports = orgGraphClient.getDirectReports(orgId, userId);
        return !directReports.isEmpty() && memberRepository.existsByTeamIdAndUserIdIn(teamId, directReports);
    }

    private void validateCanCreateTeam(UUID orgId, UUID requestingUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (orgGraphClient.getDirectReports(orgId, requestingUserId).isEmpty()) {
            throw new TeamAccessDeniedException("Only managers may create teams");
        }
    }

    /**
     * Enforces that the requesting user is the team owner.
     *
     * @throws TeamAccessDeniedException if the user does not own the team.
     */
    private void requireOwner(UUID teamId, UUID userId) {
        memberRepository.findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getRole() == TeamRole.OWNER)
                .orElseThrow(() -> new TeamAccessDeniedException(
                        "Only the team owner may perform this action"
                ));
    }

    /**
     * Resolves the key prefix, auto-deriving from name if not supplied.
     *
     * <p>Auto-derived prefix: first 4 uppercase alphanumeric characters of the name.
     * When a collision exists, a digit is appended (PLAT → PLAT2, PLAT3, …).
     */
    private String resolvePrefix(UUID orgId, String requestedPrefix, String name) {
        List<TeamEntity> orgTeams = teamRepository.findAllByOrgId(orgId);
        Set<String> taken = new HashSet<>();
        for (TeamEntity t : orgTeams) {
            taken.add(t.getKeyPrefix());
        }

        if (requestedPrefix != null && !requestedPrefix.isBlank()) {
            String normalizedPrefix = requestedPrefix.toUpperCase();
            if (taken.contains(normalizedPrefix)) {
                throw new IllegalStateException("A team with key prefix '" + normalizedPrefix + "' already exists");
            }
            return normalizedPrefix;
        }
        // Derive from name: strip non-alphanumeric, uppercase, take first 4 chars
        String base = name.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (base.isEmpty()) {
            base = "TEAM";
        }
        String candidate = base.substring(0, Math.min(4, base.length()));

        if (!taken.contains(candidate)) {
            return candidate;
        }
        for (int i = 2; i <= 99; i++) {
            String suffixed = candidate + i;
            if (!taken.contains(suffixed)) {
                return suffixed;
            }
        }
        // Fallback: UUID-based unique prefix
        return candidate + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private List<TeamMemberResponse> toMemberResponses(List<TeamMemberEntity> members) {
        return members.stream().map(TeamMemberResponse::from).toList();
    }
}
