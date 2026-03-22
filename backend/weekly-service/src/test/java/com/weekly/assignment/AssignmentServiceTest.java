package com.weekly.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.assignment.domain.AssignmentCompletionStatus;
import com.weekly.assignment.domain.WeeklyAssignmentActualEntity;
import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentActualRepository;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.issues.service.AssignmentService;
import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueActivityType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.dto.CreateWeeklyAssignmentRequest;
import com.weekly.issues.dto.IssueResponse;
import com.weekly.issues.dto.WeeklyAssignmentResponse;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.issues.service.IssueAccessDeniedException;
import com.weekly.issues.service.IssueNotFoundException;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.team.repository.TeamMemberRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link AssignmentService} (Phase 6, Step 8).
 *
 * <p>All dependencies are mocked; no Spring context or database is required.
 */
class AssignmentServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ISSUE_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.now().with(DayOfWeek.MONDAY);

    private WeeklyAssignmentRepository assignmentRepository;
    private WeeklyAssignmentActualRepository assignmentActualRepository;
    private IssueRepository issueRepository;
    private IssueActivityRepository activityRepository;
    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private TeamMemberRepository memberRepository;
    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(WeeklyAssignmentRepository.class);
        assignmentActualRepository = mock(WeeklyAssignmentActualRepository.class);
        issueRepository = mock(IssueRepository.class);
        activityRepository = mock(IssueActivityRepository.class);
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        memberRepository = mock(TeamMemberRepository.class);
        assignmentService = new AssignmentService(
                assignmentRepository, assignmentActualRepository,
                issueRepository, activityRepository,
                planRepository, commitRepository, memberRepository
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IssueEntity makeIssue() {
        return new IssueEntity(ISSUE_ID, ORG_ID, TEAM_ID, "PLAT-1", 1, "Fix login", USER_ID);
    }

    private WeeklyPlanEntity makePlan() {
        return new WeeklyPlanEntity(PLAN_ID, ORG_ID, USER_ID, WEEK_START);
    }

    private WeeklyAssignmentEntity makeAssignment() {
        WeeklyAssignmentEntity a = new WeeklyAssignmentEntity(
                ASSIGNMENT_ID, ORG_ID, PLAN_ID, ISSUE_ID
        );
        return a;
    }

    private void stubMember() {
        when(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(true);
    }

    private void stubActivitySave() {
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubIssueSave() {
        when(issueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubCommitSave() {
        when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubAssignmentSave() {
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── addToWeekPlan ─────────────────────────────────────────────────────────

    @Nested
    class AddToWeekPlan {

        @Test
        void createsAssignmentAndDualWritesCommit() {
            IssueEntity issue = makeIssue();
            WeeklyPlanEntity plan = makePlan();

            stubMember();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START)).thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(PLAN_ID, ISSUE_ID))
                    .thenReturn(Optional.empty());
            stubCommitSave();
            stubAssignmentSave();
            stubActivitySave();
            stubIssueSave();

            CreateWeeklyAssignmentRequest req = new CreateWeeklyAssignmentRequest(
                    ISSUE_ID, "KING", "Ship auth integration", 0.9
            );

            WeeklyAssignmentResponse response = assignmentService.addToWeekPlan(
                    ORG_ID, USER_ID, WEEK_START.toString(), req
            );

            assertNotNull(response);
            assertEquals(ISSUE_ID.toString(), response.issueId());
            assertEquals(PLAN_ID.toString(), response.weeklyPlanId());

            // Verify dual-write commit was created
            ArgumentCaptor<WeeklyCommitEntity> commitCaptor =
                    ArgumentCaptor.forClass(WeeklyCommitEntity.class);
            verify(commitRepository).save(commitCaptor.capture());
            WeeklyCommitEntity savedCommit = commitCaptor.getValue();
            assertEquals(ISSUE_ID, savedCommit.getSourceIssueId());
            assertEquals("Fix login", savedCommit.getTitle());
            assertEquals("Ship auth integration", savedCommit.getExpectedResult());
        }

        @Test
        void transitionsIssueFromOpenToInProgress() {
            IssueEntity issue = makeIssue(); // status = OPEN
            WeeklyPlanEntity plan = makePlan();

            stubMember();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START)).thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(PLAN_ID, ISSUE_ID))
                    .thenReturn(Optional.empty());
            stubCommitSave();
            stubAssignmentSave();
            stubActivitySave();
            stubIssueSave();

            assignmentService.addToWeekPlan(
                    ORG_ID, USER_ID, WEEK_START.toString(),
                    new CreateWeeklyAssignmentRequest(ISSUE_ID, null, null, null)
            );

            ArgumentCaptor<IssueEntity> issueCaptor = ArgumentCaptor.forClass(IssueEntity.class);
            verify(issueRepository).save(issueCaptor.capture());
            assertEquals(IssueStatus.IN_PROGRESS, issueCaptor.getValue().getStatus());

            // STATUS_CHANGE + COMMITTED_TO_WEEK activities
            verify(activityRepository, times(2)).save(any(IssueActivityEntity.class));
        }

        @Test
        void doesNotTransitionIfAlreadyInProgress() {
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyPlanEntity plan = makePlan();

            stubMember();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START)).thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(PLAN_ID, ISSUE_ID))
                    .thenReturn(Optional.empty());
            stubCommitSave();
            stubAssignmentSave();
            stubActivitySave();
            stubIssueSave();

            assignmentService.addToWeekPlan(
                    ORG_ID, USER_ID, WEEK_START.toString(),
                    new CreateWeeklyAssignmentRequest(ISSUE_ID, null, null, null)
            );

            // Only COMMITTED_TO_WEEK activity (no STATUS_CHANGE)
            verify(activityRepository, times(1)).save(any(IssueActivityEntity.class));
        }

        @Test
        void throwsWhenDuplicateAssignment() {
            IssueEntity issue = makeIssue();
            WeeklyPlanEntity plan = makePlan();

            stubMember();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START)).thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(PLAN_ID, ISSUE_ID))
                    .thenReturn(Optional.of(makeAssignment()));

            assertThrows(IllegalStateException.class, () ->
                    assignmentService.addToWeekPlan(
                            ORG_ID, USER_ID, WEEK_START.toString(),
                            new CreateWeeklyAssignmentRequest(ISSUE_ID, null, null, null)
                    )
            );
        }

        @Test
        void throwsWhenNotTeamMember() {
            IssueEntity issue = makeIssue();
            WeeklyPlanEntity plan = makePlan();

            when(memberRepository.existsByTeamIdAndUserId(TEAM_ID, USER_ID)).thenReturn(false);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START)).thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(PLAN_ID, ISSUE_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IssueAccessDeniedException.class, () ->
                    assignmentService.addToWeekPlan(
                            ORG_ID, USER_ID, WEEK_START.toString(),
                            new CreateWeeklyAssignmentRequest(ISSUE_ID, null, null, null)
                    )
            );
        }

        @Test
        void throwsWhenNoPlanForWeek() {
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START)).thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class, () ->
                    assignmentService.addToWeekPlan(
                            ORG_ID, USER_ID, WEEK_START.toString(),
                            new CreateWeeklyAssignmentRequest(ISSUE_ID, null, null, null)
                    )
            );
        }

        @Test
        void throwsWhenPlanIsNotDraft() {
            IssueEntity issue = makeIssue();
            WeeklyPlanEntity plan = makePlan();
            plan.lock(com.weekly.plan.domain.LockType.ON_TIME);

            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, WEEK_START)).thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            stubMember();

            assertThrows(IllegalStateException.class, () ->
                    assignmentService.addToWeekPlan(
                            ORG_ID, USER_ID, WEEK_START.toString(),
                            new CreateWeeklyAssignmentRequest(ISSUE_ID, null, null, null)
                    )
            );
        }
    }

    // ── removeFromWeekPlan ────────────────────────────────────────────────────

    @Nested
    class RemoveFromWeekPlan {

        @Test
        void removesAssignmentAndRevertsIssueToOpen() {
            WeeklyAssignmentEntity assignment = makeAssignment();
            UUID commitId = UUID.randomUUID();
            assignment.setLegacyCommitId(commitId);

            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);

            WeeklyPlanEntity plan = makePlan();

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, PLAN_ID, "Fix login");
            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findAllByIssueId(ISSUE_ID)).thenReturn(List.of());
            stubActivitySave();
            stubIssueSave();

            assignmentService.removeFromWeekPlan(ORG_ID, USER_ID, WEEK_START.toString(), ASSIGNMENT_ID);

            verify(commitRepository).delete(commit);
            verify(assignmentRepository).delete(assignment);

            ArgumentCaptor<IssueEntity> issueCaptor = ArgumentCaptor.forClass(IssueEntity.class);
            verify(issueRepository).save(issueCaptor.capture());
            assertEquals(IssueStatus.OPEN, issueCaptor.getValue().getStatus());

            // STATUS_CHANGE + RELEASED_TO_BACKLOG activities
            verify(activityRepository, times(2)).save(any(IssueActivityEntity.class));
        }

        @Test
        void keepsIssueInProgressWhenOtherAssignmentsRemain() {
            WeeklyAssignmentEntity assignment = makeAssignment();

            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);

            WeeklyPlanEntity plan = makePlan();
            UUID otherPlanId = UUID.randomUUID();
            WeeklyAssignmentEntity otherAssignment = new WeeklyAssignmentEntity(
                    UUID.randomUUID(), ORG_ID, otherPlanId, ISSUE_ID
            );

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findAllByIssueId(ISSUE_ID))
                    .thenReturn(List.of(otherAssignment));
            stubActivitySave();
            stubIssueSave();

            assignmentService.removeFromWeekPlan(ORG_ID, USER_ID, WEEK_START.toString(), ASSIGNMENT_ID);

            // Issue should remain IN_PROGRESS; only RELEASED_TO_BACKLOG logged
            verify(activityRepository, times(1)).save(any(IssueActivityEntity.class));
            ArgumentCaptor<IssueEntity> issueCaptor = ArgumentCaptor.forClass(IssueEntity.class);
            verify(issueRepository).save(issueCaptor.capture());
            assertEquals(IssueStatus.IN_PROGRESS, issueCaptor.getValue().getStatus());
        }

        @Test
        void throwsWhenAssignmentNotFound() {
            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IssueNotFoundException.class, () ->
                    assignmentService.removeFromWeekPlan(ORG_ID, USER_ID, WEEK_START.toString(), ASSIGNMENT_ID)
            );
        }

        @Test
        void throwsWhenNotPlanOwner() {
            WeeklyAssignmentEntity assignment = makeAssignment();
            UUID otherUser = UUID.randomUUID();

            WeeklyPlanEntity plan = new WeeklyPlanEntity(PLAN_ID, ORG_ID, otherUser, WEEK_START);

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));

            assertThrows(IssueAccessDeniedException.class, () ->
                    assignmentService.removeFromWeekPlan(ORG_ID, USER_ID, WEEK_START.toString(), ASSIGNMENT_ID)
            );
        }

        @Test
        void throwsWhenWeekPathDoesNotMatchAssignmentPlan() {
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlan();

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));

            assertThrows(IllegalArgumentException.class, () ->
                    assignmentService.removeFromWeekPlan(
                            ORG_ID, USER_ID, WEEK_START.plusWeeks(1).toString(), ASSIGNMENT_ID
                    )
            );
        }

        @Test
        void throwsWhenPlanIsNotDraft() {
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlan();
            plan.lock(com.weekly.plan.domain.LockType.ON_TIME);

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));

            assertThrows(IllegalStateException.class, () ->
                    assignmentService.removeFromWeekPlan(ORG_ID, USER_ID, WEEK_START.toString(), ASSIGNMENT_ID)
            );
        }
    }

    // ── carryForwardAssignment ────────────────────────────────────────────────

    @Nested
    class CarryForwardAssignment {

        @Test
        void createsNewAssignmentInTargetPlan() {
            UUID toPlanId = UUID.randomUUID();
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyAssignmentEntity sourceAssignment = makeAssignment();
            WeeklyPlanEntity sourcePlan = makePlan();
            sourcePlan.lock(com.weekly.plan.domain.LockType.ON_TIME);
            sourcePlan.startReconciliation();
            sourcePlan.submitReconciliation();
            WeeklyPlanEntity targetPlan = new WeeklyPlanEntity(toPlanId, ORG_ID, USER_ID, WEEK_START.plusWeeks(1));

            stubMember();
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(sourcePlan));
            when(planRepository.findByOrgIdAndId(ORG_ID, toPlanId)).thenReturn(Optional.of(targetPlan));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(toPlanId, ISSUE_ID))
                    .thenReturn(Optional.empty());
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(PLAN_ID, ISSUE_ID))
                    .thenReturn(Optional.of(sourceAssignment));
            stubCommitSave();
            stubAssignmentSave();
            stubActivitySave();
            stubIssueSave();

            WeeklyAssignmentResponse response = assignmentService.carryForwardAssignment(
                    ORG_ID, USER_ID, ISSUE_ID, PLAN_ID, toPlanId
            );

            assertNotNull(response);
            assertEquals(toPlanId.toString(), response.weeklyPlanId());
            assertEquals(ISSUE_ID.toString(), response.issueId());

            // Verify a new commit was created in the target plan
            ArgumentCaptor<WeeklyCommitEntity> commitCaptor =
                    ArgumentCaptor.forClass(WeeklyCommitEntity.class);
            verify(commitRepository).save(commitCaptor.capture());
            assertEquals(toPlanId, commitCaptor.getValue().getWeeklyPlanId());
            assertEquals(ISSUE_ID, commitCaptor.getValue().getSourceIssueId());

            // Verify CARRIED_FORWARD activity was logged
            ArgumentCaptor<IssueActivityEntity> activityCaptor =
                    ArgumentCaptor.forClass(IssueActivityEntity.class);
            verify(activityRepository, times(1)).save(activityCaptor.capture());
            assertEquals(IssueActivityType.CARRIED_FORWARD,
                    activityCaptor.getValue().getActivityType());
        }

        @Test
        void setsCarriedFromCommitIdLineage() {
            UUID toPlanId = UUID.randomUUID();
            UUID sourceCommitId = UUID.randomUUID();
            IssueEntity issue = makeIssue();
            WeeklyAssignmentEntity sourceAssignment = makeAssignment();
            sourceAssignment.setLegacyCommitId(sourceCommitId);
            WeeklyPlanEntity sourcePlan = makePlan();
            sourcePlan.lock(com.weekly.plan.domain.LockType.ON_TIME);
            sourcePlan.startReconciliation();
            sourcePlan.submitReconciliation();
            WeeklyPlanEntity targetPlan = new WeeklyPlanEntity(toPlanId, ORG_ID, USER_ID, WEEK_START.plusWeeks(1));

            stubMember();
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(sourcePlan));
            when(planRepository.findByOrgIdAndId(ORG_ID, toPlanId)).thenReturn(Optional.of(targetPlan));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(toPlanId, ISSUE_ID))
                    .thenReturn(Optional.empty());
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(PLAN_ID, ISSUE_ID))
                    .thenReturn(Optional.of(sourceAssignment));
            stubCommitSave();
            stubAssignmentSave();
            stubActivitySave();
            stubIssueSave();

            assignmentService.carryForwardAssignment(ORG_ID, USER_ID, ISSUE_ID, PLAN_ID, toPlanId);

            ArgumentCaptor<WeeklyCommitEntity> commitCaptor =
                    ArgumentCaptor.forClass(WeeklyCommitEntity.class);
            verify(commitRepository).save(commitCaptor.capture());
            assertEquals(sourceCommitId, commitCaptor.getValue().getCarriedFromCommitId());
        }

        @Test
        void throwsWhenAlreadyAssignedToTargetPlan() {
            UUID toPlanId = UUID.randomUUID();
            IssueEntity issue = makeIssue();
            WeeklyPlanEntity sourcePlan = makePlan();
            sourcePlan.lock(com.weekly.plan.domain.LockType.ON_TIME);
            sourcePlan.startReconciliation();
            sourcePlan.submitReconciliation();
            WeeklyPlanEntity targetPlan = new WeeklyPlanEntity(toPlanId, ORG_ID, USER_ID, WEEK_START.plusWeeks(1));
            stubMember();
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(sourcePlan));
            when(planRepository.findByOrgIdAndId(ORG_ID, toPlanId)).thenReturn(Optional.of(targetPlan));
            when(assignmentRepository.findByWeeklyPlanIdAndIssueId(toPlanId, ISSUE_ID))
                    .thenReturn(Optional.of(makeAssignment()));

            assertThrows(IllegalStateException.class, () ->
                    assignmentService.carryForwardAssignment(
                            ORG_ID, USER_ID, ISSUE_ID, PLAN_ID, toPlanId
                    )
            );
        }

        @Test
        void throwsWhenSourcePlanIsNotReconciled() {
            UUID toPlanId = UUID.randomUUID();
            IssueEntity issue = makeIssue();
            WeeklyPlanEntity sourcePlan = makePlan();
            WeeklyPlanEntity targetPlan = new WeeklyPlanEntity(toPlanId, ORG_ID, USER_ID, WEEK_START.plusWeeks(1));

            stubMember();
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(sourcePlan));
            when(planRepository.findByOrgIdAndId(ORG_ID, toPlanId)).thenReturn(Optional.of(targetPlan));

            assertThrows(IllegalStateException.class, () ->
                    assignmentService.carryForwardAssignment(ORG_ID, USER_ID, ISSUE_ID, PLAN_ID, toPlanId)
            );
        }
    }

    // ── releaseToBacklog ──────────────────────────────────────────────────────

    @Nested
    class ReleaseToBacklog {

        @Test
        void revertsIssueToOpenAndLogsActivity() {
            WeeklyAssignmentEntity assignment = makeAssignment();
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyPlanEntity plan = makePlan();

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findAllByIssueId(ISSUE_ID)).thenReturn(List.of());
            stubActivitySave();
            stubIssueSave();

            IssueResponse response = assignmentService.releaseToBacklog(
                    ORG_ID, USER_ID, ASSIGNMENT_ID, false
            );

            assertNotNull(response);
            assertEquals(IssueStatus.OPEN.name(), response.status());
            verify(assignmentRepository).delete(assignment);

            // STATUS_CHANGE + RELEASED_TO_BACKLOG
            verify(activityRepository, times(2)).save(any(IssueActivityEntity.class));
        }

        @Test
        void clearsAssigneeWhenFlagIsTrue() {
            UUID assigneeId = UUID.randomUUID();
            WeeklyAssignmentEntity assignment = makeAssignment();
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            issue.setAssigneeUserId(assigneeId);
            WeeklyPlanEntity plan = makePlan();

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findAllByIssueId(ISSUE_ID)).thenReturn(List.of());
            stubActivitySave();
            stubIssueSave();

            IssueResponse response = assignmentService.releaseToBacklog(
                    ORG_ID, USER_ID, ASSIGNMENT_ID, true
            );

            assertNull(response.assigneeUserId());

            // STATUS_CHANGE + ASSIGNMENT_CHANGE + RELEASED_TO_BACKLOG
            verify(activityRepository, times(3)).save(any(IssueActivityEntity.class));
        }

        @Test
        void doesNotClearAssigneeWhenFlagIsFalse() {
            UUID assigneeId = UUID.randomUUID();
            WeeklyAssignmentEntity assignment = makeAssignment();
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            issue.setAssigneeUserId(assigneeId);
            WeeklyPlanEntity plan = makePlan();

            when(assignmentRepository.findByOrgIdAndId(ORG_ID, ASSIGNMENT_ID))
                    .thenReturn(Optional.of(assignment));
            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID))
                    .thenReturn(Optional.of(plan));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            when(assignmentRepository.findAllByIssueId(ISSUE_ID)).thenReturn(List.of());
            stubActivitySave();
            stubIssueSave();

            IssueResponse response = assignmentService.releaseToBacklog(
                    ORG_ID, USER_ID, ASSIGNMENT_ID, false
            );

            assertEquals(assigneeId.toString(), response.assigneeUserId());
        }
    }

    // ── reconcileAssignmentStatus ─────────────────────────────────────────────

    @Nested
    class ReconcileAssignmentStatus {

        private WeeklyPlanEntity makePlanWithWeekStart(LocalDate weekStart) {
            return new WeeklyPlanEntity(PLAN_ID, ORG_ID, USER_ID, weekStart);
        }

        private WeeklyAssignmentActualEntity makeActual(AssignmentCompletionStatus status) {
            return new WeeklyAssignmentActualEntity(ASSIGNMENT_ID, ORG_ID, status);
        }

        @Test
        void doneStatusTransitionsIssueToIssueStatusDone() {
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlanWithWeekStart(WEEK_START); // recent week — no archive

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(assignment));
            when(assignmentActualRepository.findByAssignmentId(ASSIGNMENT_ID))
                    .thenReturn(Optional.of(makeActual(AssignmentCompletionStatus.DONE)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            stubActivitySave();
            stubIssueSave();

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);
            verify(issueRepository).save(captor.capture());
            assertEquals(IssueStatus.DONE, captor.getValue().getStatus());
        }

        @Test
        void doneStatusArchivesIssueAfter8Weeks() {
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyAssignmentEntity assignment = makeAssignment();
            // Plan week that is exactly 8 weeks old (should trigger archive)
            LocalDate oldWeek = LocalDate.now().minusWeeks(AssignmentService.ARCHIVE_AFTER_WEEKS);
            WeeklyPlanEntity plan = makePlanWithWeekStart(oldWeek);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(assignment));
            when(assignmentActualRepository.findByAssignmentId(ASSIGNMENT_ID))
                    .thenReturn(Optional.of(makeActual(AssignmentCompletionStatus.DONE)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            stubActivitySave();
            stubIssueSave();

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);
            verify(issueRepository).save(captor.capture());
            assertEquals(IssueStatus.ARCHIVED, captor.getValue().getStatus());
            assertNotNull(captor.getValue().getArchivedAt());
        }

        @Test
        void partiallyKeepsIssueInProgress() {
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlanWithWeekStart(WEEK_START);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(assignment));
            when(assignmentActualRepository.findByAssignmentId(ASSIGNMENT_ID))
                    .thenReturn(Optional.of(makeActual(AssignmentCompletionStatus.PARTIALLY)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            stubActivitySave();

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            // No status change — issue remains IN_PROGRESS
            verify(issueRepository, never()).save(any());
        }

        @Test
        void notDoneKeepsIssueInProgress() {
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlanWithWeekStart(WEEK_START);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(assignment));
            when(assignmentActualRepository.findByAssignmentId(ASSIGNMENT_ID))
                    .thenReturn(Optional.of(makeActual(AssignmentCompletionStatus.NOT_DONE)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            stubActivitySave();

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            verify(issueRepository, never()).save(any());
        }

        @Test
        void partiallyTransitionsOpenIssueBackToInProgress() {
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.OPEN);
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlanWithWeekStart(WEEK_START);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(assignment));
            when(assignmentActualRepository.findByAssignmentId(ASSIGNMENT_ID))
                    .thenReturn(Optional.of(makeActual(AssignmentCompletionStatus.PARTIALLY)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            stubActivitySave();
            stubIssueSave();

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);
            verify(issueRepository).save(captor.capture());
            assertEquals(IssueStatus.IN_PROGRESS, captor.getValue().getStatus());
        }

        @Test
        void droppedRevertsIssueToOpenAndClearsAssignee() {
            UUID assigneeId = UUID.randomUUID();
            IssueEntity issue = makeIssue();
            issue.setStatus(IssueStatus.IN_PROGRESS);
            issue.setAssigneeUserId(assigneeId);
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlanWithWeekStart(WEEK_START);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(assignment));
            when(assignmentActualRepository.findByAssignmentId(ASSIGNMENT_ID))
                    .thenReturn(Optional.of(makeActual(AssignmentCompletionStatus.DROPPED)));
            when(issueRepository.findByOrgIdAndId(ORG_ID, ISSUE_ID)).thenReturn(Optional.of(issue));
            stubActivitySave();
            stubIssueSave();

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            ArgumentCaptor<IssueEntity> captor = ArgumentCaptor.forClass(IssueEntity.class);
            verify(issueRepository).save(captor.capture());
            IssueEntity savedIssue = captor.getValue();
            assertEquals(IssueStatus.OPEN, savedIssue.getStatus());
            assertNull(savedIssue.getAssigneeUserId());

            // STATUS_CHANGE + ASSIGNMENT_CHANGE activities
            verify(activityRepository, times(2)).save(any(IssueActivityEntity.class));
        }

        @Test
        void skipsAssignmentsWithNoActual() {
            WeeklyAssignmentEntity assignment = makeAssignment();
            WeeklyPlanEntity plan = makePlanWithWeekStart(WEEK_START);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(assignment));
            when(assignmentActualRepository.findByAssignmentId(ASSIGNMENT_ID))
                    .thenReturn(Optional.empty());

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            verify(issueRepository, never()).save(any());
            verify(activityRepository, never()).save(any());
        }

        @Test
        void handlesEmptyAssignmentList() {
            WeeklyPlanEntity plan = makePlanWithWeekStart(WEEK_START);

            when(planRepository.findByOrgIdAndId(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan));
            when(assignmentRepository.findAllByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(Collections.emptyList());

            assignmentService.reconcileAssignmentStatus(ORG_ID, PLAN_ID, USER_ID);

            verify(issueRepository, never()).save(any());
        }
    }
}
