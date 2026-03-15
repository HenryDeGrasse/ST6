package com.weekly.plan.service;

import com.weekly.auth.InMemoryOrgGraphClient;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.ReviewStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.RcdoRollupResponse;
import com.weekly.plan.dto.TeamMemberSummaryResponse;
import com.weekly.plan.dto.TeamSummaryResponseDto;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TeamDashboardService}: team summary, filters, RCDO roll-up.
 */
class TeamDashboardServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID USER_1 = UUID.randomUUID();
    private static final UUID USER_2 = UUID.randomUUID();
    private static final UUID USER_3 = UUID.randomUUID();

    private InMemoryOrgGraphClient orgGraphClient;
    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository commitActualRepository;
    private CommitValidator commitValidator;
    private TeamDashboardService service;

    @BeforeEach
    void setUp() {
        orgGraphClient = new InMemoryOrgGraphClient();
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        commitActualRepository = mock(WeeklyCommitActualRepository.class);
        // Use real CommitValidator — it's a lightweight, stateless component
        commitValidator = new CommitValidator();
        service = new TeamDashboardService(orgGraphClient, planRepository, commitRepository,
                commitActualRepository, commitValidator);
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private WeeklyPlanEntity makePlan(UUID userId, LocalDate weekStart) {
        return new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, userId, weekStart);
    }

    /**
     * Creates a commit that passes validation (has both chess priority and outcomeId).
     */
    private WeeklyCommitEntity makeCommit(UUID planId, ChessPriority priority) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, "Task " + priority
        );
        commit.setChessPriority(priority);
        commit.setOutcomeId(UUID.randomUUID());
        return commit;
    }

    @Nested
    class GetTeamSummary {

        @Test
        void returnsEmptyWhenNoDirectReports() {
            // manager has no direct reports
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of());

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            assertEquals(0, result.totalElements());
            assertTrue(result.users().isEmpty());
        }

        @Test
        void returnsUsersWithNoPlan() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of());
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            assertEquals(1, result.totalElements());
            TeamMemberSummaryResponse user = result.users().get(0);
            assertEquals(USER_1.toString(), user.userId());
            assertEquals(USER_1.toString(), user.displayName());
            assertNull(user.planId());
            assertEquals(0, user.commitCount());
        }

        @Test
        void returnsCorrectCountsForUserWithPlan() {
            orgGraphClient.registerUser(USER_1, "Alice Smith");
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            WeeklyPlanEntity plan = makePlan(USER_1, currentMonday());
            plan.lock(LockType.ON_TIME);
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan));

            WeeklyCommitEntity king = makeCommit(plan.getId(), ChessPriority.KING);
            WeeklyCommitEntity queen = makeCommit(plan.getId(), ChessPriority.QUEEN);
            WeeklyCommitEntity rook = makeCommit(plan.getId(), ChessPriority.ROOK);
            // Non-strategic commit (has chess priority and nonStrategicReason → passes validation)
            WeeklyCommitEntity nonStrategic = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Admin work"
            );
            nonStrategic.setChessPriority(ChessPriority.PAWN);
            nonStrategic.setNonStrategicReason("Administrative overhead");

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(king, queen, rook, nonStrategic));

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            assertEquals(1, result.totalElements());
            TeamMemberSummaryResponse user = result.users().get(0);
            assertEquals("Alice Smith", user.displayName());
            assertEquals(4, user.commitCount());
            assertEquals(1, user.kingCount());
            assertEquals(1, user.queenCount());
            assertEquals(1, user.nonStrategicCount());
            assertEquals(0, user.incompleteCount()); // LOCKED plan: no reconciliation data
            assertEquals(0, user.issueCount()); // all pass validation
            assertFalse(user.isStale());
            assertFalse(user.isLateLock());
        }

        @Test
        void detectsStalePlan() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            // Plan from last week, still in DRAFT
            LocalDate lastWeek = currentMonday().minusWeeks(1);
            WeeklyPlanEntity plan = makePlan(USER_1, lastWeek);
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), eq(lastWeek), any()
            )).thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, lastWeek, 0, 20,
                    null, null, null, null, null, null
            );

            assertTrue(result.users().get(0).isStale());
        }

        @Test
        void detectsLateLock() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            WeeklyPlanEntity plan = makePlan(USER_1, currentMonday());
            plan.lock(LockType.LATE_LOCK);
            plan.startReconciliation();
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            assertTrue(result.users().get(0).isLateLock());
        }

        @Test
        void paginatesResults() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1, USER_2, USER_3));
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of());
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            // Page 0, size 2
            TeamSummaryResponseDto page0 = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 2,
                    null, null, null, null, null, null
            );

            assertEquals(3, page0.totalElements());
            assertEquals(2, page0.totalPages());
            assertEquals(2, page0.users().size());
            assertEquals(0, page0.page());

            // Page 1, size 2
            TeamSummaryResponseDto page1 = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 1, 2,
                    null, null, null, null, null, null
            );

            assertEquals(1, page1.users().size());
            assertEquals(1, page1.page());
        }

        @Test
        void filtersbyState() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1, USER_2));

            WeeklyPlanEntity draftPlan = makePlan(USER_1, currentMonday());
            WeeklyPlanEntity lockedPlan = makePlan(USER_2, currentMonday());
            lockedPlan.lock(LockType.ON_TIME);

            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(draftPlan, lockedPlan));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    "LOCKED", null, null, null, null, null
            );

            assertEquals(1, result.totalElements());
            assertEquals("LOCKED", result.users().get(0).state());
        }

        @Test
        void computesReviewStatusCounts() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1, USER_2, USER_3));

            WeeklyPlanEntity plan1 = makePlan(USER_1, currentMonday());
            plan1.lock(LockType.ON_TIME);
            plan1.startReconciliation();
            plan1.submitReconciliation(); // REVIEW_PENDING

            WeeklyPlanEntity plan2 = makePlan(USER_2, currentMonday());
            plan2.lock(LockType.ON_TIME);
            plan2.startReconciliation();
            plan2.submitReconciliation();
            plan2.setReviewStatus(ReviewStatus.APPROVED);

            WeeklyPlanEntity plan3 = makePlan(USER_3, currentMonday());
            // DRAFT → REVIEW_NOT_APPLICABLE (not counted)

            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan1, plan2, plan3));
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            assertEquals(1, result.reviewStatusCounts().pending());
            assertEquals(1, result.reviewStatusCounts().approved());
            assertEquals(0, result.reviewStatusCounts().changesRequested());
        }

        @Test
        void rejectsNonMondayWeekStart() {
            assertThrows(PlanValidationException.class, () ->
                    service.getTeamSummary(
                            ORG_ID, MANAGER_ID,
                            LocalDate.of(2026, 3, 10), // Tuesday
                            0, 20,
                            null, null, null, null, null, null
                    )
            );
        }

        @Test
        void incompleteCountCountsOnlyNonDoneActualsForReconciledPlans() {
            orgGraphClient.registerUser(USER_1, "Bob Jones");
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            WeeklyPlanEntity plan = makePlan(USER_1, currentMonday());
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan));

            WeeklyCommitEntity c1 = makeCommit(plan.getId(), ChessPriority.KING);
            WeeklyCommitEntity c2 = makeCommit(plan.getId(), ChessPriority.QUEEN);
            WeeklyCommitEntity c3 = makeCommit(plan.getId(), ChessPriority.ROOK);

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(c1, c2, c3));

            // c1 DONE, c2 PARTIALLY, c3 has no saved actual
            WeeklyCommitActualEntity a1 = new WeeklyCommitActualEntity(c1.getId(), ORG_ID);
            a1.setCompletionStatus(CompletionStatus.DONE);
            WeeklyCommitActualEntity a2 = new WeeklyCommitActualEntity(c2.getId(), ORG_ID);
            a2.setCompletionStatus(CompletionStatus.PARTIALLY);

            when(commitActualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(a1, a2));

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            TeamMemberSummaryResponse user = result.users().get(0);
            assertEquals(1, user.incompleteCount()); // only c2 PARTIALLY counts
            assertEquals(0, user.issueCount()); // all pass validation
        }

        @Test
        void issueCountReflectsValidationErrors() {
            orgGraphClient.registerUser(USER_1, "Carol");
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            WeeklyPlanEntity plan = makePlan(USER_1, currentMonday());
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan));

            // Valid commit
            WeeklyCommitEntity valid = makeCommit(plan.getId(), ChessPriority.KING);
            // Invalid commit: no chess priority, no outcomeId → validation errors
            WeeklyCommitEntity invalid = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Unfinished"
            );

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(valid, invalid));

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            TeamMemberSummaryResponse user = result.users().get(0);
            assertEquals(1, user.issueCount()); // 'invalid' commit has validation errors
            assertEquals(0, user.incompleteCount()); // DRAFT plan → no reconciliation-based count
        }

        @Test
        void incompleteCountIsZeroWhilePlanIsStillReconciling() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            WeeklyPlanEntity plan = makePlan(USER_1, currentMonday());
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan));

            WeeklyCommitEntity commit = makeCommit(plan.getId(), ChessPriority.KING);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit));

            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            actual.setCompletionStatus(CompletionStatus.NOT_DONE);
            when(commitActualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            TeamMemberSummaryResponse user = result.users().get(0);
            assertEquals(0, user.incompleteCount()); // RECONCILING → not finalized yet
            assertEquals(0, user.issueCount());
        }

        @Test
        void incompleteCountIsZeroForDraftPlans() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            WeeklyPlanEntity plan = makePlan(USER_1, currentMonday());
            // Plan is in DRAFT (default state)
            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan));

            // Invalid commit (no priority, no outcomeId) → has validation errors
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan.getId(), "Some task"
            );

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit));

            TeamSummaryResponseDto result = service.getTeamSummary(
                    ORG_ID, MANAGER_ID, currentMonday(), 0, 20,
                    null, null, null, null, null, null
            );

            TeamMemberSummaryResponse user = result.users().get(0);
            assertEquals(0, user.incompleteCount()); // DRAFT → always 0
            assertEquals(1, user.issueCount()); // validation error
        }
    }

    @Nested
    class ManagerDrillDown {

        @Test
        void returnsPlanForDirectReport() {
            LocalDate weekStart = currentMonday();
            WeeklyPlanEntity plan = makePlan(USER_1, weekStart);
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_1, weekStart))
                    .thenReturn(java.util.Optional.of(plan));

            var result = service.getUserPlanForManager(ORG_ID, MANAGER_ID, USER_1, weekStart);

            assertTrue(result.isPresent());
            assertEquals(plan.getId().toString(), result.get().id());
        }

        @Test
        void rejectsPlanAccessForNonDirectReport() {
            LocalDate weekStart = currentMonday();
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> service.getUserPlanForManager(ORG_ID, MANAGER_ID, USER_2, weekStart)
            );

            assertEquals(com.weekly.shared.ErrorCode.FORBIDDEN, ex.getErrorCode());
        }

        @Test
        void returnsCommitsForAuthorizedPlan() {
            LocalDate weekStart = currentMonday();
            WeeklyPlanEntity plan = makePlan(USER_1, weekStart);
            WeeklyCommitEntity commit = makeCommit(plan.getId(), ChessPriority.KING);
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_1, weekStart))
                    .thenReturn(java.util.Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, plan.getId()))
                    .thenReturn(List.of(commit));

            var planResult = service.getUserPlanForManager(ORG_ID, MANAGER_ID, USER_1, weekStart);
            var commits = service.getPlanCommits(ORG_ID, java.util.UUID.fromString(planResult.orElseThrow().id()));

            assertEquals(1, commits.size());
            assertEquals(commit.getId().toString(), commits.get(0).id());
        }

        @Test
        void includesActualsForCarryForwardPlans() {
            LocalDate weekStart = currentMonday();
            WeeklyPlanEntity plan = makePlan(USER_1, weekStart);
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();
            plan.carryForward();

            WeeklyCommitEntity commit = makeCommit(plan.getId(), ChessPriority.KING);
            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            actual.setActualResult("Delivered the work");

            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_1, weekStart))
                    .thenReturn(java.util.Optional.of(plan));
            when(planRepository.findByOrgIdAndId(ORG_ID, plan.getId()))
                    .thenReturn(java.util.Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, plan.getId()))
                    .thenReturn(List.of(commit));
            when(commitActualRepository.findByOrgIdAndCommitIdIn(ORG_ID, List.of(commit.getId())))
                    .thenReturn(List.of(actual));

            var commits = service.getPlanCommits(ORG_ID, plan.getId());

            assertEquals(1, commits.size());
            assertEquals("Delivered the work", commits.get(0).actual().actualResult());
        }
    }

    @Nested
    class GetRcdoRollup {

        @Test
        void returnsEmptyWhenNoReports() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of());

            RcdoRollupResponse result = service.getRcdoRollup(
                    ORG_ID, MANAGER_ID, currentMonday()
            );

            assertTrue(result.items().isEmpty());
            assertEquals(0, result.nonStrategicCount());
        }

        @Test
        void groupsCommitsByOutcome() {
            orgGraphClient.setDirectReports(ORG_ID, MANAGER_ID, List.of(USER_1, USER_2));

            WeeklyPlanEntity plan1 = makePlan(USER_1, currentMonday());
            WeeklyPlanEntity plan2 = makePlan(USER_2, currentMonday());

            when(planRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    eq(ORG_ID), any(), any()
            )).thenReturn(List.of(plan1, plan2));

            UUID outcomeA = UUID.randomUUID();
            UUID outcomeB = UUID.randomUUID();

            WeeklyCommitEntity c1 = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan1.getId(), "Task A1"
            );
            c1.setOutcomeId(outcomeA);
            c1.setChessPriority(ChessPriority.KING);

            WeeklyCommitEntity c2 = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan2.getId(), "Task A2"
            );
            c2.setOutcomeId(outcomeA);
            c2.setChessPriority(ChessPriority.ROOK);

            WeeklyCommitEntity c3 = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan1.getId(), "Task B1"
            );
            c3.setOutcomeId(outcomeB);
            c3.setChessPriority(ChessPriority.QUEEN);

            WeeklyCommitEntity c4 = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, plan2.getId(), "Non-strategic"
            );
            c4.setNonStrategicReason("Support ticket");
            c4.setChessPriority(ChessPriority.PAWN);

            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(c1, c2, c3, c4));

            RcdoRollupResponse result = service.getRcdoRollup(
                    ORG_ID, MANAGER_ID, currentMonday()
            );

            assertEquals(2, result.items().size());
            assertEquals(1, result.nonStrategicCount());

            // Outcome A: 2 commits (1 KING, 1 ROOK)
            var itemA = result.items().stream()
                    .filter(i -> i.outcomeId().equals(outcomeA.toString()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(2, itemA.commitCount());
            assertEquals(1, itemA.kingCount());
            assertEquals(1, itemA.rookCount());

            // Outcome B: 1 commit (1 QUEEN)
            var itemB = result.items().stream()
                    .filter(i -> i.outcomeId().equals(outcomeB.toString()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(1, itemB.commitCount());
            assertEquals(1, itemB.queenCount());
        }
    }
}
