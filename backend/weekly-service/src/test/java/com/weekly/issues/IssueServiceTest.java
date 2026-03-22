package com.weekly.issues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.audit.AuditService;
import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueActivityType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.dto.AddCommentRequest;
import com.weekly.issues.dto.AssignIssueRequest;
import com.weekly.issues.dto.CommitIssueToWeekRequest;
import com.weekly.issues.dto.CreateIssueRequest;
import com.weekly.issues.dto.CreateWeeklyAssignmentRequest;
import com.weekly.issues.dto.IssueDetailResponse;
import com.weekly.issues.dto.IssueListResponse;
import com.weekly.issues.dto.IssueResponse;
import com.weekly.issues.dto.LogTimeEntryRequest;
import com.weekly.issues.dto.ReleaseIssueRequest;
import com.weekly.issues.dto.UpdateIssueRequest;
import com.weekly.issues.dto.WeeklyAssignmentResponse;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.issues.service.IssueAccessDeniedException;
import com.weekly.issues.service.IssueNotFoundException;
import com.weekly.issues.service.IssueService;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamMemberRepository;
import com.weekly.team.repository.TeamRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for {@link IssueService} (Phase 6).
 *
 * <p>All dependencies are mocked; no Spring context or database is required.
 */
class IssueServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    private IssueRepository issueRepository;
    private IssueActivityRepository activityRepository;
    private WeeklyAssignmentRepository assignmentRepository;
    private TeamRepository teamRepository;
    private TeamMemberRepository memberRepository;
    private WeeklyPlanRepository planRepository;
    private AuditService auditService;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueRepository = mock(IssueRepository.class);
        activityRepository = mock(IssueActivityRepository.class);
        assignmentRepository = mock(WeeklyAssignmentRepository.class);
        teamRepository = mock(TeamRepository.class);
        memberRepository = mock(TeamMemberRepository.class);
        planRepository = mock(WeeklyPlanRepository.class);
        auditService = mock(AuditService.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        issueService = new IssueService(
                issueRepository, activityRepository, assignmentRepository,
                teamRepository, memberRepository, planRepository, auditService,
                eventPublisher
        );
    }

    // ── Helpers ──────────────────────────────────────────────

    private TeamEntity makeTeam() {
        return new TeamEntity(TEAM_ID, ORG_ID, "Platform", "PLAT", CREATOR_ID);
    }

    private IssueEntity makeIssue(UUID issueId) {
        IssueEntity issue = new IssueEntity(
                issueId, ORG_ID, TEAM_ID, "PLAT-1", 1, "Test issue", CREATOR_ID
        );
        return issue;
    }

    private void stubMember(UUID teamId, UUID userId) {
        when(memberRepository.existsByTeamIdAndUserId(teamId, userId)).thenReturn(true);
    }

    private void stubActivitySave() {
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── createIssue ──────────────────────────────────────────────────────────

    @Nested
    class CreateIssue {

        @Test
        void atomicallyAllocatesSequenceNumber() {
            TeamEntity team = makeTeam();
            stubMember(TEAM_ID, CREATOR_ID);
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(issueRepository.incrementAndGetIssueSequence(TEAM_ID)).thenReturn(1);
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubActivitySave();

            CreateIssueRequest req = new CreateIssueRequest(
                    "Ship auth", null, null, null, null, null, null, null, null
            );
            IssueResponse response = issueService.createIssue(ORG_ID, TEAM_ID, CREATOR_ID, req);

            assertEquals("PLAT-1", response.issueKey());
            assertEquals(1, response.sequenceNumber());
            verify(issueRepository).incrementAndGetIssueSequence(TEAM_ID);
        }

        @Test
        void keyUsesTeamPrefixAndSequence() {
            TeamEntity team = makeTeam(); // prefix = "PLAT"
            stubMember(TEAM_ID, CREATOR_ID);
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(issueRepository.incrementAndGetIssueSequence(TEAM_ID)).thenReturn(42);
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubActivitySave();

            IssueResponse response = issueService.createIssue(
                    ORG_ID, TEAM_ID, CREATOR_ID,
                    new CreateIssueRequest("Perf fix", null, null, null, null, null, null, null, null)
            );

            assertEquals("PLAT-42", response.issueKey());
        }

        @Test
        void appliesOptionalEffortType() {
            TeamEntity team = makeTeam();
            stubMember(TEAM_ID, CREATOR_ID);
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(issueRepository.incrementAndGetIssueSequence(TEAM_ID)).thenReturn(1);
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubActivitySave();

            IssueResponse response = issueService.createIssue(
                    ORG_ID, TEAM_ID, CREATOR_ID,
                    new CreateIssueRequest("Build X", null, "BUILD", 8.0, null, null, null, null, null)
            );

            assertEquals("BUILD", response.effortType());
            assertEquals(8.0, response.estimatedHours());
        }

        @Test
        void logsCreatedActivity() {
            TeamEntity team = makeTeam();
            stubMember(TEAM_ID, CREATOR_ID);
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(issueRepository.incrementAndGetIssueSequence(TEAM_ID)).thenReturn(1);
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubActivitySave();

            issueService.createIssue(ORG_ID, TEAM_ID, CREATOR_ID,
                    new CreateIssueRequest("T", null, null, null, null, null, null, null, null));

            ArgumentCaptor<IssueActivityEntity> captor =
                    ArgumentCaptor.forClass(IssueActivityEntity.class);
            verify(activityRepository).save(captor.capture());
            assertEquals(IssueActivityType.CREATED, captor.getValue().getActivityType());
        }

        @Test
        void throwsWhenTeamNotFound() {
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.empty());

            assertThrows(IssueNotFoundException.class, () ->
                    issueService.createIssue(ORG_ID, TEAM_ID, CREATOR_ID,
                            new CreateIssueRequest("T", null, null, null, null, null, null, null, null))
            );
            verify(issueRepository, never()).incrementAndGetIssueSequence(any());
        }

        @Test
        void rejectsCreateWhenUserIsNotTeamMember() {
            TeamEntity team = makeTeam();
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(TEAM_ID, OTHER_USER_ID)).thenReturn(false);

            assertThrows(IssueAccessDeniedException.class, () ->
                    issueService.createIssue(ORG_ID, TEAM_ID, OTHER_USER_ID,
                            new CreateIssueRequest("T", null, null, null, null, null, null, null, null))
            );
            verify(issueRepository, never()).incrementAndGetIssueSequence(any());
        }

        @Test
        void concurrentAllocationCallsIncrementEachTime() {
            // Verifies the service delegates all sequencing to DB (no in-memory counter)
            TeamEntity team = makeTeam();
            stubMember(TEAM_ID, CREATOR_ID);
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(issueRepository.incrementAndGetIssueSequence(TEAM_ID))
                    .thenReturn(1).thenReturn(2);
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            stubActivitySave();

            CreateIssueRequest req = new CreateIssueRequest(
                    "Issue", null, null, null, null, null, null, null, null
            );
            issueService.createIssue(ORG_ID, TEAM_ID, CREATOR_ID, req);
            issueService.createIssue(ORG_ID, TEAM_ID, CREATOR_ID, req);

            verify(issueRepository, times(2)).incrementAndGetIssueSequence(TEAM_ID);
        }
    }

    // ─── getIssue ────────────────────────────────────────────────────────────

    @Nested
    class GetIssue {

        @Test
        void returnsIssueWithActivities() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            IssueActivityEntity activity = new IssueActivityEntity(
                    UUID.randomUUID(), ORG_ID, issueId, CREATOR_ID, IssueActivityType.CREATED
            );

            stubMember(TEAM_ID, CREATOR_ID);
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(activityRepository.findAllByIssueIdOrderByCreatedAtAsc(issueId))
                    .thenReturn(List.of(activity));

            IssueDetailResponse response = issueService.getIssue(ORG_ID, issueId, CREATOR_ID);

            assertEquals("PLAT-1", response.issue().issueKey());
            assertEquals(1, response.activities().size());
            assertEquals("CREATED", response.activities().get(0).activityType());
        }

        @Test
        void throwsWhenIssueNotFound() {
            UUID issueId = UUID.randomUUID();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.empty());

            assertThrows(IssueNotFoundException.class,
                    () -> issueService.getIssue(ORG_ID, issueId, CREATOR_ID));
        }

        @Test
        void rejectsGetWhenUserIsNotTeamMember() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(memberRepository.existsByTeamIdAndUserId(TEAM_ID, OTHER_USER_ID)).thenReturn(false);

            assertThrows(IssueAccessDeniedException.class,
                    () -> issueService.getIssue(ORG_ID, issueId, OTHER_USER_ID));
        }
    }

    // ─── listTeamBacklog ─────────────────────────────────────────────────────

    @Nested
    class ListTeamBacklog {

        @Test
        void returnsAllIssuesWhenNoStatusFilter() {
            TeamEntity team = makeTeam();
            IssueEntity issue = makeIssue(UUID.randomUUID());
            stubMember(TEAM_ID, CREATOR_ID);
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(issueRepository.findAllByTeamIdAndStatusNot(eq(TEAM_ID), eq(IssueStatus.ARCHIVED), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(issue)));

            IssueListResponse response =
                    issueService.listTeamBacklog(ORG_ID, TEAM_ID, CREATOR_ID, null, 0, 20);

            assertEquals(1, response.content().size());
            assertEquals(1, response.totalElements());
        }

        @Test
        void filtersActiveByStatusWhenProvided() {
            TeamEntity team = makeTeam();
            IssueEntity openIssue = makeIssue(UUID.randomUUID());
            stubMember(TEAM_ID, CREATOR_ID);
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(issueRepository.findAllByTeamIdAndStatus(
                    eq(TEAM_ID), eq(IssueStatus.OPEN), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(openIssue)));

            IssueListResponse response =
                    issueService.listTeamBacklog(ORG_ID, TEAM_ID, CREATOR_ID, IssueStatus.OPEN, 0, 20);

            assertEquals(1, response.content().size());
        }

        @Test
        void rejectsListWhenUserIsNotTeamMember() {
            TeamEntity team = makeTeam();
            when(teamRepository.findByOrgIdAndId(ORG_ID, TEAM_ID)).thenReturn(Optional.of(team));
            when(memberRepository.existsByTeamIdAndUserId(TEAM_ID, OTHER_USER_ID)).thenReturn(false);

            assertThrows(IssueAccessDeniedException.class, () ->
                    issueService.listTeamBacklog(ORG_ID, TEAM_ID, OTHER_USER_ID, null, 0, 20)
            );
        }
    }

    // ─── updateIssue ─────────────────────────────────────────────────────────

    @Nested
    class UpdateIssue {

        @Test
        void updatesTitle() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueResponse response = issueService.updateIssue(
                    ORG_ID, issueId, CREATOR_ID,
                    new UpdateIssueRequest("New title", null, null, null, null, null, null, null, null, null)
            );

            assertEquals("New title", response.title());
            verify(activityRepository).save(
                    argOfType(IssueActivityType.TITLE_CHANGE)
            );
        }

        @Test
        void logsEffortTypeChange() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            issueService.updateIssue(ORG_ID, issueId, CREATOR_ID,
                    new UpdateIssueRequest(null, null, "MAINTAIN", null, null, null, null, null, null, null));

            verify(activityRepository).save(argOfType(IssueActivityType.EFFORT_TYPE_CHANGE));
        }

        @Test
        void rejectsNonMember() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(memberRepository.existsByTeamIdAndUserId(TEAM_ID, OTHER_USER_ID)).thenReturn(false);

            assertThrows(IssueAccessDeniedException.class, () ->
                    issueService.updateIssue(ORG_ID, issueId, OTHER_USER_ID,
                            new UpdateIssueRequest("X", null, null, null, null, null, null, null, null, null))
            );
        }
    }

    // ─── assignIssue ─────────────────────────────────────────────────────────

    @Nested
    class AssignIssue {

        @Test
        void assignsUserToIssue() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            UUID assignee = UUID.randomUUID();
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueResponse response = issueService.assignIssue(
                    ORG_ID, issueId, CREATOR_ID, new AssignIssueRequest(assignee.toString())
            );

            assertEquals(assignee.toString(), response.assigneeUserId());
        }

        @Test
        void unassignsWithNullAssignee() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            issue.setAssigneeUserId(UUID.randomUUID());
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueResponse response = issueService.assignIssue(
                    ORG_ID, issueId, CREATOR_ID, new AssignIssueRequest(null)
            );

            assertNull(response.assigneeUserId());
        }
    }

    // ─── archiveIssue ────────────────────────────────────────────────────────

    @Nested
    class ArchiveIssue {

        @Test
        void setsArchivedStatus() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueResponse response = issueService.archiveIssue(ORG_ID, issueId, CREATOR_ID);

            assertEquals("ARCHIVED", response.status());
            assertNotNull(response.archivedAt());
        }

        @Test
        void rejectsNonMember() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(memberRepository.existsByTeamIdAndUserId(TEAM_ID, OTHER_USER_ID)).thenReturn(false);

            assertThrows(IssueAccessDeniedException.class, () ->
                    issueService.archiveIssue(ORG_ID, issueId, OTHER_USER_ID)
            );
        }
    }

    // ─── addComment ──────────────────────────────────────────────────────────

    @Nested
    class AddComment {

        @Test
        void logsCommentActivity() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));

            var response = issueService.addComment(
                    ORG_ID, issueId, CREATOR_ID, new AddCommentRequest("Great progress!")
            );

            assertEquals("COMMENT", response.activityType());
            assertEquals("Great progress!", response.commentText());
        }
    }

    // ─── logTimeEntry ────────────────────────────────────────────────────────

    @Nested
    class LogTimeEntry {

        @Test
        void logsTimeEntryActivity() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));

            var response = issueService.logTimeEntry(
                    ORG_ID, issueId, CREATOR_ID, new LogTimeEntryRequest(3.5, "Deep work")
            );

            assertEquals("TIME_ENTRY", response.activityType());
            assertEquals(3.5, response.hoursLogged());
        }
    }

    // ─── commitToWeek ────────────────────────────────────────────────────────

    @Nested
    class CommitToWeek {

        @Test
        void createsAssignmentAndTransitionsStatus() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();

            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, CREATOR_ID, monday);

            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, CREATOR_ID, monday))
                    .thenReturn(Optional.of(plan));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(planId, issueId))
                    .thenReturn(Optional.empty());
            when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CommitIssueToWeekRequest req = new CommitIssueToWeekRequest(
                    monday.toString(), "QUEEN", "Done by Friday", 0.9
            );
            WeeklyAssignmentResponse response = issueService.commitToWeek(
                    ORG_ID, issueId, CREATOR_ID, req
            );

            assertNotNull(response.id());
            assertEquals(issueId.toString(), response.issueId());
            // Issue should transition to IN_PROGRESS
            assertEquals(IssueStatus.IN_PROGRESS, issue.getStatus());
            verify(eventPublisher).publishEvent(any(com.weekly.issues.events.IssueUpdatedEvent.class));
        }

        @Test
        void rejectsDuplicateAssignment() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);

            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, CREATOR_ID, monday);
            WeeklyAssignmentEntity existing =
                    new WeeklyAssignmentEntity(UUID.randomUUID(), ORG_ID, planId, issueId);

            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, CREATOR_ID, monday))
                    .thenReturn(Optional.of(plan));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(planId, issueId))
                    .thenReturn(Optional.of(existing));

            assertThrows(IllegalStateException.class, () ->
                    issueService.commitToWeek(ORG_ID, issueId, CREATOR_ID,
                            new CommitIssueToWeekRequest(monday.toString(), null, null, null))
            );
        }

        @Test
        void throwsWhenNoPlanExists() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            stubMember(TEAM_ID, CREATOR_ID);

            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, CREATOR_ID, monday))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class, () ->
                    issueService.commitToWeek(ORG_ID, issueId, CREATOR_ID,
                            new CommitIssueToWeekRequest(monday.toString(), null, null, null))
            );
        }
    }

    // ─── createWeeklyAssignmentForWeek ───────────────────────────────────────

    @Nested
    class CreateWeeklyAssignmentForWeek {

        @Test
        void transitionsOpenIssueToInProgress() {
            UUID issueId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, CREATOR_ID, monday);

            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, CREATOR_ID, monday))
                    .thenReturn(Optional.of(plan));
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(planId, issueId)).thenReturn(Optional.empty());
            when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var response = issueService.createWeeklyAssignmentForWeek(
                    ORG_ID,
                    CREATOR_ID,
                    monday.toString(),
                    new CreateWeeklyAssignmentRequest(issueId, null, "Done", 0.8)
            );

            assertNotNull(response.id());
            assertEquals(IssueStatus.IN_PROGRESS, issue.getStatus());
            verify(activityRepository).save(argOfType(IssueActivityType.COMMITTED_TO_WEEK));
            verify(activityRepository).save(argOfType(IssueActivityType.STATUS_CHANGE));
            verify(eventPublisher).publishEvent(any(com.weekly.issues.events.IssueUpdatedEvent.class));
        }
    }

    // ─── releaseToBacklog ────────────────────────────────────────────────────

    @Nested
    class ReleaseToBacklog {

        @Test
        void removesAssignmentAndReverts() {
            UUID issueId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyAssignmentEntity assignment =
                    new WeeklyAssignmentEntity(UUID.randomUUID(), ORG_ID, planId, issueId);

            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndId(ORG_ID, planId))
                    .thenReturn(Optional.of(new WeeklyPlanEntity(planId, ORG_ID, CREATOR_ID, LocalDate.now().with(DayOfWeek.MONDAY))));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(planId, issueId))
                    .thenReturn(Optional.of(assignment));
            when(assignmentRepository.findAllByIssueId(issueId)).thenReturn(List.of());
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueResponse response = issueService.releaseToBacklog(
                    ORG_ID, issueId, CREATOR_ID, new ReleaseIssueRequest(planId)
            );

            assertEquals("OPEN", response.status());
            verify(assignmentRepository).delete(assignment);
            verify(eventPublisher).publishEvent(any(com.weekly.issues.events.IssueUpdatedEvent.class));
        }

        @Test
        void keepsInProgressWhenOtherAssignmentsRemain() {
            UUID issueId = UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            UUID otherPlanId = UUID.randomUUID();
            IssueEntity issue = makeIssue(issueId);
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyAssignmentEntity toRemove =
                    new WeeklyAssignmentEntity(UUID.randomUUID(), ORG_ID, planId, issueId);
            WeeklyAssignmentEntity remaining =
                    new WeeklyAssignmentEntity(UUID.randomUUID(), ORG_ID, otherPlanId, issueId);

            stubMember(TEAM_ID, CREATOR_ID);
            stubActivitySave();
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndId(ORG_ID, planId))
                    .thenReturn(Optional.of(new WeeklyPlanEntity(planId, ORG_ID, CREATOR_ID, LocalDate.now().with(DayOfWeek.MONDAY))));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(planId, issueId))
                    .thenReturn(Optional.of(toRemove));
            when(assignmentRepository.findAllByIssueId(issueId)).thenReturn(List.of(remaining));
            when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            IssueResponse response = issueService.releaseToBacklog(
                    ORG_ID, issueId, CREATOR_ID, new ReleaseIssueRequest(planId)
            );

            assertEquals("IN_PROGRESS", response.status());
        }
    }

    // ─── Helper ──────────────────────────────────────────────

    /** Returns an argThat matcher that checks the activity type on a saved entity. */
    private IssueActivityEntity argOfType(IssueActivityType type) {
        return org.mockito.ArgumentMatchers.argThat(a -> a.getActivityType() == type);
    }
}
