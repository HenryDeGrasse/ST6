package com.weekly.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditService;
import com.weekly.auth.OrgGraphClient;
import com.weekly.team.domain.AccessRequestStatus;
import com.weekly.team.domain.TeamAccessRequestEntity;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.domain.TeamMemberEntity;
import com.weekly.team.domain.TeamMemberEntity.TeamMemberId;
import com.weekly.team.domain.TeamRole;
import com.weekly.team.dto.CreateTeamRequest;
import com.weekly.team.dto.TeamDetailResponse;
import com.weekly.team.dto.TeamListResponse;
import com.weekly.team.dto.TeamResponse;
import com.weekly.team.dto.UpdateTeamRequest;
import com.weekly.team.repository.TeamAccessRequestRepository;
import com.weekly.team.repository.TeamMemberRepository;
import com.weekly.team.repository.TeamRepository;
import com.weekly.team.service.TeamAccessDeniedException;
import com.weekly.team.service.TeamAccessRequestNotFoundException;
import com.weekly.team.service.TeamNotFoundException;
import com.weekly.team.service.TeamService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TeamService} (Phase 6).
 *
 * <p>All dependencies are mocked; no Spring context or database is required.
 */
class TeamServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();

    private TeamRepository teamRepository;
    private TeamMemberRepository memberRepository;
    private TeamAccessRequestRepository accessRequestRepository;
    private OrgGraphClient orgGraphClient;
    private AuditService auditService;
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TeamRepository.class);
        memberRepository = mock(TeamMemberRepository.class);
        accessRequestRepository = mock(TeamAccessRequestRepository.class);
        orgGraphClient = mock(OrgGraphClient.class);
        auditService = mock(AuditService.class);
        teamService = new TeamService(
                teamRepository, memberRepository, accessRequestRepository,
                orgGraphClient, auditService
        );
        when(orgGraphClient.getDirectReports(any(), any())).thenReturn(List.of());
    }

    // ─── createTeam ───────────────────────────────────────────────────────────

    @Nested
    class CreateTeam {

        @Test
        void createsTeamWithExplicitPrefix() {
            CreateTeamRequest request = new CreateTeamRequest("Platform", "PLAT", null);
            when(orgGraphClient.getDirectReports(ORG_ID, OWNER_ID)).thenReturn(List.of(MEMBER_ID));
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TeamResponse response = teamService.createTeam(ORG_ID, OWNER_ID, false, request);

            assertNotNull(response.id());
            assertEquals("Platform", response.name());
            assertEquals("PLAT", response.keyPrefix());
            verify(memberRepository).save(any(TeamMemberEntity.class));
            verify(auditService).record(eq(ORG_ID), eq(OWNER_ID),
                    eq("TEAM_CREATED"), eq("Team"), any(), any(), any(), any(), any(), any());
        }

        @Test
        void autoDerivesPrefix() {
            CreateTeamRequest request = new CreateTeamRequest("Platform Engineering", null, "desc");
            when(orgGraphClient.getDirectReports(ORG_ID, OWNER_ID)).thenReturn(List.of(MEMBER_ID));
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TeamResponse response = teamService.createTeam(ORG_ID, OWNER_ID, false, request);

            // First 4 chars of "PLATFORMENGINEERING" = "PLAT"
            assertEquals("PLAT", response.keyPrefix());
        }

        @Test
        void deduplicatesPrefixWhenCollisionExists() {
            // Existing teams with PLAT and PLAT2
            TeamEntity existing1 = new TeamEntity(UUID.randomUUID(), ORG_ID, "Old1", "PLAT", OWNER_ID);
            TeamEntity existing2 = new TeamEntity(UUID.randomUUID(), ORG_ID, "Old2", "PLAT2", OWNER_ID);
            when(orgGraphClient.getDirectReports(ORG_ID, OWNER_ID)).thenReturn(List.of(MEMBER_ID));
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of(existing1, existing2));
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateTeamRequest request = new CreateTeamRequest("Platform Ops", null, null);
            TeamResponse response = teamService.createTeam(ORG_ID, OWNER_ID, false, request);

            assertEquals("PLAT3", response.keyPrefix());
        }

        @Test
        void upperCasesExplicitPrefix() {
            when(orgGraphClient.getDirectReports(ORG_ID, OWNER_ID)).thenReturn(List.of(MEMBER_ID));
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TeamResponse response = teamService.createTeam(
                    ORG_ID, OWNER_ID, false, new CreateTeamRequest("Eng", "eng", null)
            );

            assertEquals("ENG", response.keyPrefix());
        }

        @Test
        void rejectsNonManagerWhenCreatingTeam() {
            when(orgGraphClient.getDirectReports(ORG_ID, OWNER_ID)).thenReturn(List.of());

            assertThrows(TeamAccessDeniedException.class, () ->
                    teamService.createTeam(ORG_ID, OWNER_ID, false, new CreateTeamRequest("Platform", "PLAT", null))
            );
            verify(teamRepository, never()).save(any());
        }

        @Test
        void adminCanCreateTeamWithoutDirectReports() {
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TeamResponse response = teamService.createTeam(
                    ORG_ID, OWNER_ID, true, new CreateTeamRequest("Platform", "PLAT", null)
            );

            assertEquals("PLAT", response.keyPrefix());
        }
    }

    // ─── getTeam ──────────────────────────────────────────────────────────────

    @Nested
    class GetTeam {

        @Test
        void returnsTeamWithMembers() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "Platform", "PLAT", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(teamId, OWNER_ID)).thenReturn(true);
            when(memberRepository.findAllByTeamId(teamId)).thenReturn(List.of(ownerMember));

            TeamDetailResponse response = teamService.getTeam(ORG_ID, teamId, OWNER_ID, false);

            assertEquals("Platform", response.team().name());
            assertEquals(1, response.members().size());
            assertEquals("OWNER", response.members().get(0).role());
        }

        @Test
        void throwsWhenTeamNotFound() {
            UUID teamId = UUID.randomUUID();
            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.empty());

            assertThrows(TeamNotFoundException.class,
                    () -> teamService.getTeam(ORG_ID, teamId, OWNER_ID, false));
        }

        @Test
        void deniesInvisibleTeamToIc() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "Platform", "PLAT", OWNER_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(teamId, OUTSIDER_ID)).thenReturn(false);
            when(orgGraphClient.getDirectReports(ORG_ID, OUTSIDER_ID)).thenReturn(List.of());

            assertThrows(TeamAccessDeniedException.class,
                    () -> teamService.getTeam(ORG_ID, teamId, OUTSIDER_ID, false));
        }
    }

    // ─── listTeams ────────────────────────────────────────────────────────────

    @Nested
    class ListTeams {

        @Test
        void adminSeesAllTeams() {
            TeamEntity t1 = new TeamEntity(UUID.randomUUID(), ORG_ID, "T1", "T1", OWNER_ID);
            TeamEntity t2 = new TeamEntity(UUID.randomUUID(), ORG_ID, "T2", "T2", OWNER_ID);
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of(t1, t2));

            TeamListResponse response = teamService.listTeams(ORG_ID, OWNER_ID, false, true);

            assertEquals(2, response.teams().size());
        }

        @Test
        void icSeesOnlyOwnTeams() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "Team", "TEAM", OWNER_ID);
            TeamMemberEntity membership = new TeamMemberEntity(teamId, MEMBER_ID, ORG_ID, TeamRole.MEMBER);

            when(memberRepository.findAllByOrgIdAndUserId(ORG_ID, MEMBER_ID))
                    .thenReturn(List.of(membership));
            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));

            TeamListResponse response = teamService.listTeams(ORG_ID, MEMBER_ID, false, false);

            assertEquals(1, response.teams().size());
            assertEquals("Team", response.teams().get(0).name());
        }

        @Test
        void managerSeesDirectReportTeams() {
            UUID managerId = UUID.randomUUID();
            UUID reportId = UUID.randomUUID();
            UUID reportTeamId = UUID.randomUUID();
            TeamEntity reportTeam = new TeamEntity(reportTeamId, ORG_ID, "Report Team", "RPT", reportId);
            TeamMemberEntity reportMembership =
                    new TeamMemberEntity(reportTeamId, reportId, ORG_ID, TeamRole.MEMBER);

            when(memberRepository.findAllByOrgIdAndUserId(ORG_ID, managerId)).thenReturn(List.of());
            when(orgGraphClient.getDirectReports(ORG_ID, managerId)).thenReturn(List.of(reportId));
            when(memberRepository.findAllByOrgIdAndUserId(ORG_ID, reportId))
                    .thenReturn(List.of(reportMembership));
            when(teamRepository.findByOrgIdAndId(ORG_ID, reportTeamId)).thenReturn(Optional.of(reportTeam));

            TeamListResponse response = teamService.listTeams(ORG_ID, managerId, true, false);

            assertEquals(1, response.teams().size());
            assertEquals("Report Team", response.teams().get(0).name());
        }
    }

    // ─── listMembers ─────────────────────────────────────────────────────────

    @Nested
    class ListMembers {

        @Test
        void visibleMemberCanListTeamMembers() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "Team", "TEAM", OWNER_ID);
            TeamMemberEntity membership = new TeamMemberEntity(teamId, MEMBER_ID, ORG_ID, TeamRole.MEMBER);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(teamId, MEMBER_ID)).thenReturn(true);
            when(memberRepository.findAllByTeamId(teamId)).thenReturn(List.of(membership));

            var response = teamService.listMembers(ORG_ID, teamId, MEMBER_ID, false);

            assertEquals(1, response.size());
            assertEquals(MEMBER_ID.toString(), response.get(0).userId());
        }

        @Test
        void outsiderCannotListInvisibleTeamMembers() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "Team", "TEAM", OWNER_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(teamId, OUTSIDER_ID)).thenReturn(false);
            when(orgGraphClient.getDirectReports(ORG_ID, OUTSIDER_ID)).thenReturn(List.of());

            assertThrows(TeamAccessDeniedException.class, () ->
                    teamService.listMembers(ORG_ID, teamId, OUTSIDER_ID, false)
            );
        }
    }

    // ─── updateTeam ───────────────────────────────────────────────────────────

    @Nested
    class UpdateTeam {

        @Test
        void ownerCanUpdateName() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "Old Name", "OLD", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TeamResponse response = teamService.updateTeam(
                    ORG_ID, teamId, OWNER_ID, new UpdateTeamRequest("New Name", null)
            );

            assertEquals("New Name", response.name());
        }

        @Test
        void nonOwnerCannotUpdate() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "Team", "T", OWNER_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OUTSIDER_ID))
                    .thenReturn(Optional.empty());

            assertThrows(TeamAccessDeniedException.class, () ->
                    teamService.updateTeam(ORG_ID, teamId, OUTSIDER_ID, new UpdateTeamRequest("X", null))
            );
        }
    }

    // ─── addMember ────────────────────────────────────────────────────────────

    @Nested
    class AddMember {

        @Test
        void ownerCanAddMember() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.existsByTeamIdAndUserId(teamId, MEMBER_ID)).thenReturn(false);
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            teamService.addMember(ORG_ID, teamId, OWNER_ID, MEMBER_ID, TeamRole.MEMBER);

            verify(memberRepository).save(any(TeamMemberEntity.class));
        }

        @Test
        void rejectsAddingExistingMember() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.existsByTeamIdAndUserId(teamId, MEMBER_ID)).thenReturn(true);

            assertThrows(IllegalStateException.class, () ->
                    teamService.addMember(ORG_ID, teamId, OWNER_ID, MEMBER_ID, TeamRole.MEMBER)
            );
            verify(memberRepository, never()).save(any());
        }

        @Test
        void nonOwnerCannotAddMember() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OUTSIDER_ID))
                    .thenReturn(Optional.empty());

            assertThrows(TeamAccessDeniedException.class, () ->
                    teamService.addMember(ORG_ID, teamId, OUTSIDER_ID, MEMBER_ID, TeamRole.MEMBER)
            );
        }
    }

    // ─── removeMember ────────────────────────────────────────────────────────

    @Nested
    class RemoveMember {

        @Test
        void ownerCanRemoveMember() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));
            when(memberRepository.existsById(new TeamMemberId(teamId, MEMBER_ID))).thenReturn(true);

            teamService.removeMember(ORG_ID, teamId, OWNER_ID, MEMBER_ID);

            verify(memberRepository).deleteById(new TeamMemberId(teamId, MEMBER_ID));
        }

        @Test
        void ownerCannotRemoveSelf() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));

            assertThrows(TeamAccessDeniedException.class, () ->
                    teamService.removeMember(ORG_ID, teamId, OWNER_ID, OWNER_ID)
            );
        }
    }

    // ─── requestAccess ────────────────────────────────────────────────────────

    @Nested
    class RequestAccess {

        @Test
        void submitsAccessRequest() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(teamId, OUTSIDER_ID)).thenReturn(false);
            when(accessRequestRepository.findByTeamIdAndRequesterUserId(teamId, OUTSIDER_ID))
                    .thenReturn(Optional.empty());
            when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var response = teamService.requestAccess(ORG_ID, teamId, OUTSIDER_ID);

            assertNotNull(response.id());
            assertEquals("PENDING", response.status());
            verify(accessRequestRepository).save(any(TeamAccessRequestEntity.class));
        }

        @Test
        void rejectsIfAlreadyMember() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(teamId, MEMBER_ID)).thenReturn(true);

            assertThrows(IllegalStateException.class, () ->
                    teamService.requestAccess(ORG_ID, teamId, MEMBER_ID)
            );
        }

        @Test
        void rejectsIfPendingRequestExists() {
            UUID teamId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamAccessRequestEntity pending =
                    new TeamAccessRequestEntity(UUID.randomUUID(), teamId, OUTSIDER_ID, ORG_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(teamId, OUTSIDER_ID)).thenReturn(false);
            when(accessRequestRepository.findByTeamIdAndRequesterUserId(teamId, OUTSIDER_ID))
                    .thenReturn(Optional.of(pending));

            assertThrows(IllegalStateException.class, () ->
                    teamService.requestAccess(ORG_ID, teamId, OUTSIDER_ID)
            );
        }
    }

    // ─── decideAccessRequest ──────────────────────────────────────────────────

    @Nested
    class DecideAccessRequest {

        @Test
        void approvingCreatesNewMembership() {
            UUID teamId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);
            TeamAccessRequestEntity request =
                    new TeamAccessRequestEntity(requestId, teamId, OUTSIDER_ID, ORG_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));
            when(accessRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.existsByTeamIdAndUserId(teamId, OUTSIDER_ID)).thenReturn(false);
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var response = teamService.decideAccessRequest(
                    ORG_ID, teamId, requestId, OWNER_ID, AccessRequestStatus.APPROVED
            );

            assertEquals("APPROVED", response.status());
            verify(memberRepository).save(any(TeamMemberEntity.class));
        }

        @Test
        void denyingDoesNotCreateMembership() {
            UUID teamId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);
            TeamAccessRequestEntity request =
                    new TeamAccessRequestEntity(requestId, teamId, OUTSIDER_ID, ORG_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));
            when(accessRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
            when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var response = teamService.decideAccessRequest(
                    ORG_ID, teamId, requestId, OWNER_ID, AccessRequestStatus.DENIED
            );

            assertEquals("DENIED", response.status());
            verify(memberRepository, never()).save(any(TeamMemberEntity.class));
        }

        @Test
        void nonOwnerCannotDecide() {
            UUID teamId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OUTSIDER_ID))
                    .thenReturn(Optional.empty());

            assertThrows(TeamAccessDeniedException.class, () ->
                    teamService.decideAccessRequest(
                            ORG_ID, teamId, requestId, OUTSIDER_ID, AccessRequestStatus.APPROVED
                    )
            );
            verify(accessRequestRepository, never()).findById(any());
        }

        @Test
        void rejectsAccessRequestForDifferentTeam() {
            UUID teamId = UUID.randomUUID();
            UUID otherTeamId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            TeamEntity team = new TeamEntity(teamId, ORG_ID, "T", "T", OWNER_ID);
            TeamMemberEntity ownerMember = new TeamMemberEntity(teamId, OWNER_ID, ORG_ID, TeamRole.OWNER);
            TeamAccessRequestEntity request =
                    new TeamAccessRequestEntity(requestId, otherTeamId, OUTSIDER_ID, ORG_ID);

            when(teamRepository.findByOrgIdAndId(ORG_ID, teamId)).thenReturn(Optional.of(team));
            when(memberRepository.findByTeamIdAndUserId(teamId, OWNER_ID))
                    .thenReturn(Optional.of(ownerMember));
            when(accessRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

            assertThrows(TeamAccessRequestNotFoundException.class, () ->
                    teamService.decideAccessRequest(
                            ORG_ID, teamId, requestId, OWNER_ID, AccessRequestStatus.APPROVED
                    )
            );
        }
    }

    // ─── prefixGeneration edge cases ──────────────────────────────────────────

    @Nested
    class PrefixGeneration {

        @Test
        void handlesSingleCharName() {
            when(orgGraphClient.getDirectReports(ORG_ID, OWNER_ID)).thenReturn(List.of(MEMBER_ID));
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TeamResponse response = teamService.createTeam(
                    ORG_ID, OWNER_ID, false, new CreateTeamRequest("A", null, null)
            );

            assertFalse(response.keyPrefix().isBlank());
        }

        @Test
        void handlesNameWithSpecialCharsOnly() {
            when(orgGraphClient.getDirectReports(ORG_ID, OWNER_ID)).thenReturn(List.of(MEMBER_ID));
            when(teamRepository.findAllByOrgId(ORG_ID)).thenReturn(List.of());
            when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Name with only special chars → fallback prefix "TEAM"
            TeamResponse response = teamService.createTeam(
                    ORG_ID, OWNER_ID, false, new CreateTeamRequest("---", null, null)
            );

            assertNotNull(response.keyPrefix());
            assertFalse(response.keyPrefix().isBlank());
        }
    }
}
