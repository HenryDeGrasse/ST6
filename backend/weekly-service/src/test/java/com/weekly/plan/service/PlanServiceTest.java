package com.weekly.plan.service;

import com.weekly.audit.AuditService;
import com.weekly.auth.OrgGraphClient;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.WeeklyPlanResponse;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import com.weekly.shared.ErrorCode;
import com.weekly.shared.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlanService}: plan creation, retrieval, locking,
 * reconciliation, and carry-forward.
 */
class PlanServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private CommitValidator commitValidator;
    private InMemoryRcdoClient rcdoClient;
    private AuditService auditService;
    private OutboxService outboxService;
    private OrgGraphClient orgGraphClient;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        commitValidator = new CommitValidator();
        rcdoClient = new InMemoryRcdoClient();
        auditService = mock(AuditService.class);
        outboxService = mock(OutboxService.class);
        orgGraphClient = mock(OrgGraphClient.class);
        planService = new PlanService(
                planRepository, commitRepository, actualRepository,
                commitValidator, rcdoClient, auditService, outboxService,
                orgGraphClient
        );
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private LocalDate nextMonday() {
        return currentMonday().plusWeeks(1);
    }

    // ── Plan Creation ────────────────────────────────────────

    @Nested
    class CreatePlan {

        @Test
        void createsNewPlanForCurrentWeek() {
            LocalDate weekStart = currentMonday();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanService.CreatePlanResult result = planService.createPlan(ORG_ID, USER_ID, weekStart);

            assertTrue(result.created());
            assertEquals("DRAFT", result.plan().state());
            assertEquals("REVIEW_NOT_APPLICABLE", result.plan().reviewStatus());
            assertEquals(weekStart.toString(), result.plan().weekStartDate());
            verify(planRepository).save(any(WeeklyPlanEntity.class));
        }

        @Test
        void createsNewPlanForNextWeek() {
            LocalDate weekStart = nextMonday();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanService.CreatePlanResult result = planService.createPlan(ORG_ID, USER_ID, weekStart);

            assertTrue(result.created());
        }

        @Test
        void returnsExistingPlanIdempotently() {
            LocalDate weekStart = currentMonday();
            WeeklyPlanEntity existing = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.of(existing));

            PlanService.CreatePlanResult result = planService.createPlan(ORG_ID, USER_ID, weekStart);

            assertFalse(result.created());
            assertEquals(existing.getId().toString(), result.plan().id());
            verify(planRepository, never()).save(any());
        }

        @Test
        void rejectsNonMondayWeekStart() {
            LocalDate tuesday = currentMonday().plusDays(1);

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.createPlan(ORG_ID, USER_ID, tuesday)
            );
            assertEquals(ErrorCode.INVALID_WEEK_START, ex.getErrorCode());
        }

        @Test
        void rejectsPastWeekCreation() {
            LocalDate pastMonday = currentMonday().minusWeeks(1);

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.createPlan(ORG_ID, USER_ID, pastMonday)
            );
            assertEquals(ErrorCode.PAST_WEEK_CREATION_BLOCKED, ex.getErrorCode());
        }

        @Test
        void rejectsFarFutureWeekCreation() {
            LocalDate farFuture = currentMonday().plusWeeks(2);

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.createPlan(ORG_ID, USER_ID, farFuture)
            );
            assertEquals(ErrorCode.INVALID_WEEK_START, ex.getErrorCode());
        }
    }

    // ── Plan Retrieval ───────────────────────────────────────

    @Nested
    class GetPlan {

        @Test
        void getMyPlanReturnsExisting() {
            LocalDate weekStart = currentMonday();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, weekStart);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.of(plan));

            Optional<WeeklyPlanResponse> result = planService.getMyPlan(ORG_ID, USER_ID, weekStart);

            assertTrue(result.isPresent());
            assertEquals(plan.getId().toString(), result.get().id());
        }

        @Test
        void getMyPlanReturnsEmptyWhenNotFound() {
            LocalDate weekStart = currentMonday();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, weekStart))
                    .thenReturn(Optional.empty());

            Optional<WeeklyPlanResponse> result = planService.getMyPlan(ORG_ID, USER_ID, weekStart);

            assertTrue(result.isEmpty());
        }

        @Test
        void getPlanByIdReturnsExistingForOwner() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            // USER_ID is the plan owner — access is allowed
            Optional<WeeklyPlanResponse> result = planService.getPlan(ORG_ID, planId, USER_ID);

            assertTrue(result.isPresent());
        }

        @Test
        void getPlanByIdAllowsManagerAccess() {
            UUID planId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(orgGraphClient.isDirectReport(ORG_ID, managerId, USER_ID)).thenReturn(true);

            // managerId is a manager of USER_ID — access is allowed
            Optional<WeeklyPlanResponse> result = planService.getPlan(ORG_ID, planId, managerId);

            assertTrue(result.isPresent());
        }

        @Test
        void getPlanByIdForbidsThirdParty() {
            UUID planId = UUID.randomUUID();
            UUID thirdParty = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(orgGraphClient.isDirectReport(ORG_ID, thirdParty, USER_ID)).thenReturn(false);

            // thirdParty is neither owner nor manager — access is denied
            assertThrows(PlanAccessForbiddenException.class,
                    () -> planService.getPlan(ORG_ID, planId, thirdParty));
        }

        @Test
        void getPlanByIdRejectsWrongOrg() {
            UUID planId = UUID.randomUUID();
            UUID otherOrg = UUID.randomUUID();
            // findByOrgIdAndId with otherOrg returns empty (plan not in that org)

            Optional<WeeklyPlanResponse> result = planService.getPlan(otherOrg, planId, USER_ID);

            assertTrue(result.isEmpty());
        }
    }

    // ── Plan Locking ─────────────────────────────────────────

    @Nested
    class LockPlan {

        private UUID planId;
        private WeeklyPlanEntity plan;

        @BeforeEach
        void setUpPlan() {
            planId = UUID.randomUUID();
            plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Setup RCDO tree with a valid outcome
            setupRcdoTree();
        }

        private UUID outcomeId;

        private void setupRcdoTree() {
            outcomeId = UUID.randomUUID();
            RcdoTree.Outcome outcome = new RcdoTree.Outcome(
                    outcomeId.toString(), "Revenue Growth", UUID.randomUUID().toString()
            );
            UUID objId = UUID.fromString(outcome.objectiveId());
            RcdoTree.Objective objective = new RcdoTree.Objective(
                    objId.toString(), "Increase ARR",
                    UUID.randomUUID().toString(), List.of(outcome)
            );
            UUID rcId = UUID.fromString(objective.rallyCryId());
            RcdoTree.RallyCry rallyCry = new RcdoTree.RallyCry(
                    rcId.toString(), "Scale to $500M", List.of(objective)
            );
            rcdoClient.setTree(ORG_ID, new RcdoTree(List.of(rallyCry)));
        }

        @Test
        void locksValidPlanWithCorrectSnapshots() {
            WeeklyCommitEntity king = createCommit(planId, "Deliver feature X", ChessPriority.KING, outcomeId, null);
            WeeklyCommitEntity queen = createCommit(planId, "Review PRs", ChessPriority.QUEEN, null, "Admin work");

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king, queen));

            WeeklyPlanResponse result = planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID);

            assertEquals("LOCKED", result.state());
            assertNotNull(result.lockedAt());
            assertEquals("ON_TIME", result.lockType());

            // Verify snapshot was populated on the outcome-linked commit
            assertNotNull(king.getSnapshotRallyCryName());
            assertEquals("Scale to $500M", king.getSnapshotRallyCryName());
            assertEquals("Increase ARR", king.getSnapshotObjectiveName());
            assertEquals("Revenue Growth", king.getSnapshotOutcomeName());

            // Non-strategic commit should not have snapshot
            assertNull(queen.getSnapshotRallyCryName());
        }

        @Test
        void rejectsLockWhenMissingChessPriority() {
            WeeklyCommitEntity commit = createCommit(planId, "Task", null, outcomeId, null);
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertTrue(ex.getMessage().contains("validation failed"));
        }

        @Test
        void rejectsLockWhenMissingRcdoAndReason() {
            WeeklyCommitEntity commit = createCommit(planId, "Task", ChessPriority.KING, null, null);
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertFalse(ex.getDetails().isEmpty());
        }

        @Test
        void rejectsLockWhenBothOutcomeAndReasonPresent() {
            WeeklyCommitEntity commit = createCommit(planId, "Task", ChessPriority.KING, outcomeId, "Also non-strategic");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertTrue(ex.getDetails().stream()
                    .anyMatch(d -> "CONFLICTING_LINK".equals(d.get("code"))));
        }

        @Test
        void rejectsLockWithNoKing() {
            WeeklyCommitEntity queen = createCommit(planId, "Task", ChessPriority.QUEEN, outcomeId, null);
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(queen));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.CHESS_RULE_VIOLATION, ex.getErrorCode());
        }

        @Test
        void rejectsLockWithTwoKings() {
            WeeklyCommitEntity king1 = createCommit(planId, "Task 1", ChessPriority.KING, outcomeId, null);
            WeeklyCommitEntity king2 = createCommit(planId, "Task 2", ChessPriority.KING, null, "Non-strategic");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king1, king2));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.CHESS_RULE_VIOLATION, ex.getErrorCode());
        }

        @Test
        void rejectsLockWithThreeQueens() {
            WeeklyCommitEntity king = createCommit(planId, "King", ChessPriority.KING, outcomeId, null);
            WeeklyCommitEntity q1 = createCommit(planId, "Queen 1", ChessPriority.QUEEN, null, "Admin");
            WeeklyCommitEntity q2 = createCommit(planId, "Queen 2", ChessPriority.QUEEN, null, "Support");
            WeeklyCommitEntity q3 = createCommit(planId, "Queen 3", ChessPriority.QUEEN, null, "Other");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king, q1, q2, q3));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.CHESS_RULE_VIOLATION, ex.getErrorCode());
        }

        @Test
        void rejectsLockOnNonDraftPlan() {
            plan.lock(LockType.ON_TIME);

            PlanStateException stateEx = assertThrows(
                    PlanStateException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );

            assertEquals(ErrorCode.PLAN_NOT_IN_DRAFT, stateEx.getErrorCode());
        }

        @Test
        void rejectsLockOnVersionMismatch() {
            WeeklyCommitEntity king = createCommit(planId, "Task", ChessPriority.KING, outcomeId, null);
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king));

            OptimisticLockException ex = assertThrows(
                    OptimisticLockException.class,
                    () -> planService.lockPlan(ORG_ID, planId, 999, USER_ID)
            );
            assertEquals(999, ex.getExpectedVersion());
        }

        @Test
        void rejectsLockWhenRcdoStale() {
            rcdoClient.clear(); // Clear forces isCacheFresh to return true by default

            // Create a custom RCDO client mock to test staleness
            var mockRcdoClient = mock(com.weekly.rcdo.RcdoClient.class);
            when(mockRcdoClient.isCacheFresh(eq(ORG_ID), eq(60))).thenReturn(false);

            PlanService serviceWithStaleRcdo = new PlanService(
                    planRepository, commitRepository, actualRepository,
                    commitValidator, mockRcdoClient, auditService, outboxService,
                    orgGraphClient
            );

            WeeklyCommitEntity king = createCommit(planId, "Task", ChessPriority.KING, outcomeId, null);
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> serviceWithStaleRcdo.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.RCDO_VALIDATION_STALE, ex.getErrorCode());
        }

        @Test
        void rejectsLockWhenOutcomeCannotBeResolvedForSnapshotting() {
            WeeklyCommitEntity king = createCommit(planId, "Task", ChessPriority.KING, UUID.randomUUID(), null);
            WeeklyCommitEntity queen = createCommit(planId, "Admin", ChessPriority.QUEEN, null, "Support work");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king, queen));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );

            assertEquals(ErrorCode.MISSING_RCDO_OR_REASON, ex.getErrorCode());
            assertTrue(ex.getDetails().stream()
                    .anyMatch(detail -> king.getId().toString().equals(detail.get("commitId"))));
            verify(planRepository, never()).save(any(WeeklyPlanEntity.class));
        }

        @Test
        void rejectsLockWhenRcdoSnapshotIdsAreNotUuids() {
            UUID validOutcomeId = UUID.randomUUID();
            RcdoTree.Outcome outcome = new RcdoTree.Outcome(
                    validOutcomeId.toString(), "Revenue Growth", "obj-not-a-uuid"
            );
            RcdoTree.Objective objective = new RcdoTree.Objective(
                    "obj-not-a-uuid", "Increase ARR", "rc-not-a-uuid", List.of(outcome)
            );
            RcdoTree.RallyCry rallyCry = new RcdoTree.RallyCry(
                    "rc-not-a-uuid", "Scale to $500M", List.of(objective)
            );
            rcdoClient.setTree(ORG_ID, new RcdoTree(List.of(rallyCry)));

            WeeklyCommitEntity king = createCommit(planId, "Task", ChessPriority.KING, validOutcomeId, null);
            WeeklyCommitEntity queen = createCommit(planId, "Admin", ChessPriority.QUEEN, null, "Support work");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king, queen));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), USER_ID)
            );

            assertEquals(ErrorCode.MISSING_RCDO_OR_REASON, ex.getErrorCode());
            assertTrue(ex.getDetails().stream()
                    .anyMatch(detail -> king.getId().toString().equals(detail.get("commitId"))));
            verify(planRepository, never()).save(any(WeeklyPlanEntity.class));
        }
    }

    // ── Start Reconciliation ─────────────────────────────────

    @Nested
    class StartReconciliation {

        private UUID planId;
        private UUID outcomeId;
        private WeeklyPlanEntity plan;

        @BeforeEach
        void setUpLockedPlan() {
            planId = UUID.randomUUID();
            plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            setupRcdoTree();
        }

        private void setupRcdoTree() {
            outcomeId = UUID.randomUUID();
            RcdoTree.Outcome outcome = new RcdoTree.Outcome(
                    outcomeId.toString(), "Revenue Growth", UUID.randomUUID().toString()
            );
            UUID objId = UUID.fromString(outcome.objectiveId());
            RcdoTree.Objective objective = new RcdoTree.Objective(
                    objId.toString(), "Increase ARR",
                    UUID.randomUUID().toString(), List.of(outcome)
            );
            UUID rcId = UUID.fromString(objective.rallyCryId());
            RcdoTree.RallyCry rallyCry = new RcdoTree.RallyCry(
                    rcId.toString(), "Scale to $500M", List.of(objective)
            );
            rcdoClient.setTree(ORG_ID, new RcdoTree(List.of(rallyCry)));
        }

        @Test
        void transitionsLockedToReconciling() {
            plan.lock(LockType.ON_TIME);

            WeeklyPlanResponse result = planService.startReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID);

            assertEquals("RECONCILING", result.state());
        }

        @Test
        void lateLockPathTransitionsDraftToReconciling() {
            WeeklyCommitEntity king = createCommit(planId, "Task", ChessPriority.KING, outcomeId, null);
            WeeklyCommitEntity queen = createCommit(planId, "Admin", ChessPriority.QUEEN, null, "Support");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(king, queen));

            WeeklyPlanResponse result = planService.startReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID);

            assertEquals("RECONCILING", result.state());
            assertEquals("LATE_LOCK", result.lockType());
            assertNotNull(result.lockedAt());
            verify(outboxService).publish(eq(EventType.PLAN_LOCKED), eq("WeeklyPlan"), eq(planId), eq(ORG_ID), any());
            verify(outboxService).publish(eq(EventType.PLAN_RECONCILIATION_STARTED),
                    eq("WeeklyPlan"), eq(planId), eq(ORG_ID), any());
        }

        @Test
        void lateLockPathRejectsInvalidCommits() {
            // Missing chess priority
            WeeklyCommitEntity commit = createCommit(planId, "Task", null, outcomeId, null);
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            assertThrows(PlanValidationException.class,
                    () -> planService.startReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID));
        }

        @Test
        void rejectsReconciliationOnReconciledPlan() {
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> planService.startReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }

        @Test
        void rejectsVersionMismatch() {
            plan.lock(LockType.ON_TIME);

            assertThrows(OptimisticLockException.class,
                    () -> planService.startReconciliation(ORG_ID, planId, 999, USER_ID));
        }
    }

    // ── Submit Reconciliation ────────────────────────────────

    @Nested
    class SubmitReconciliation {

        private UUID planId;
        private WeeklyPlanEntity plan;

        @BeforeEach
        void setUpReconcilingPlan() {
            planId = UUID.randomUUID();
            plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void submitsWhenAllCommitsHaveActuals() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = createCommit(planId, "Task", ChessPriority.KING, null, "Non-strategic");
            // Override commit ID via reflection-free approach: use the commit with its random ID
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            actual.setCompletionStatus(CompletionStatus.DONE);
            actual.setActualResult("Completed");
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), anyList()))
                    .thenReturn(List.of(actual));

            WeeklyPlanResponse result = planService.submitReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID);

            assertEquals("RECONCILED", result.state());
            assertEquals("REVIEW_PENDING", result.reviewStatus());
        }

        @Test
        void rejectsWhenCommitIsMissingActuals() {
            WeeklyCommitEntity commit = createCommit(planId, "Task", ChessPriority.KING, null, "Non-strategic");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), anyList()))
                    .thenReturn(List.of());

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.submitReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID)
            );

            assertEquals(ErrorCode.MISSING_COMPLETION_STATUS, ex.getErrorCode());
            assertTrue(ex.getDetails().stream()
                    .anyMatch(d -> commit.getId().toString().equals(d.get("commitId"))
                            && "MISSING_COMPLETION_STATUS".equals(d.get("code"))));
            verify(actualRepository, never()).save(any());
        }

        @Test
        void rejectsWhenSomeCommitsAreMissingActuals() {
            WeeklyCommitEntity firstCommit = createCommit(planId, "Task 1", ChessPriority.KING, null, "Non-strategic");
            WeeklyCommitEntity secondCommit = createCommit(planId, "Task 2", ChessPriority.QUEEN, null, "Non-strategic");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(firstCommit, secondCommit));

            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(firstCommit.getId(), ORG_ID);
            actual.setCompletionStatus(CompletionStatus.DONE);
            actual.setActualResult("Completed");
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), anyList()))
                    .thenReturn(List.of(actual));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.submitReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID)
            );

            assertEquals(ErrorCode.MISSING_COMPLETION_STATUS, ex.getErrorCode());
            assertTrue(ex.getDetails().stream()
                    .anyMatch(d -> secondCommit.getId().toString().equals(d.get("commitId"))
                            && "MISSING_COMPLETION_STATUS".equals(d.get("code"))));
        }

        @Test
        void rejectsWhenNonDoneCommitMissingDeltaReason() {
            WeeklyCommitEntity commit = createCommit(planId, "Task", ChessPriority.KING, null, "Non-strategic");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(commit.getId(), ORG_ID);
            actual.setCompletionStatus(CompletionStatus.PARTIALLY);
            actual.setActualResult("Halfway");
            // delta reason is null/empty — should fail
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), anyList()))
                    .thenReturn(List.of(actual));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.submitReconciliation(ORG_ID, planId, plan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.MISSING_DELTA_REASON, ex.getErrorCode());
            assertTrue(ex.getDetails().stream()
                    .anyMatch(d -> "MISSING_DELTA_REASON".equals(d.get("code"))));
        }

        @Test
        void rejectsSubmitOnNonReconcilingPlan() {
            // Reset to LOCKED state
            WeeklyPlanEntity lockedPlan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            lockedPlan.lock(LockType.ON_TIME);
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(lockedPlan));

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> planService.submitReconciliation(ORG_ID, planId, lockedPlan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }
    }

    // ── Carry Forward ────────────────────────────────────────

    @Nested
    class CarryForward {

        private UUID planId;
        private WeeklyPlanEntity plan;
        private WeeklyCommitEntity commit;

        @BeforeEach
        void setUpReconciledPlan() {
            planId = UUID.randomUUID();
            plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            commit = createCommit(planId, "Incomplete task", ChessPriority.ROOK, null, "Support");
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void carriesForwardSelectedCommits() {
            // No next week plan yet
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, currentMonday().plusWeeks(1)))
                    .thenReturn(Optional.empty());

            WeeklyPlanResponse result = planService.carryForward(
                    ORG_ID, planId, List.of(commit.getId()),
                    plan.getVersion(), USER_ID
            );

            assertEquals("CARRY_FORWARD", result.state());
            assertNotNull(result.carryForwardExecutedAt());
        }

        @Test
        void rejectsCarryForwardOnNonReconciledPlan() {
            WeeklyPlanEntity draftPlan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, currentMonday());
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(draftPlan));

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> planService.carryForward(
                            ORG_ID, planId, List.of(commit.getId()),
                            draftPlan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }

        @Test
        void rejectsInvalidCommitId() {
            UUID bogusId = UUID.randomUUID();

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.carryForward(
                            ORG_ID, planId, List.of(bogusId),
                            plan.getVersion(), USER_ID)
            );
            assertTrue(ex.getMessage().contains(bogusId.toString()));
        }

        @Test
        void rejectsCarryForwardWhenNextPlanIsNotDraft() {
            // Next week plan exists but is LOCKED
            LocalDate nextWeek = currentMonday().plusWeeks(1);
            WeeklyPlanEntity nextPlan = new WeeklyPlanEntity(UUID.randomUUID(), ORG_ID, USER_ID, nextWeek);
            nextPlan.lock(LockType.ON_TIME);
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(ORG_ID, USER_ID, nextWeek))
                    .thenReturn(Optional.of(nextPlan));

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> planService.carryForward(
                            ORG_ID, planId, List.of(commit.getId()),
                            plan.getVersion(), USER_ID)
            );
            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        }
    }

    // ── Owner Authorization ──────────────────────────────────

    @Nested
    class OwnerAuthorization {

        private UUID planId;
        private WeeklyPlanEntity plan;
        private static final UUID OTHER_USER = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            planId = UUID.randomUUID();
            // Plan is owned by USER_ID
            plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, LocalDate.now().with(DayOfWeek.MONDAY));
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void lockPlanRejectsDifferentUser() {
            PlanAccessForbiddenException ex = assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.lockPlan(ORG_ID, planId, plan.getVersion(), OTHER_USER)
            );
            assertTrue(ex.getMessage().contains(OTHER_USER.toString()));
        }

        @Test
        void lockPlanRejectsDifferentUserBeforeVersionChecks() {
            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.lockPlan(ORG_ID, planId, 999, OTHER_USER)
            );
        }

        @Test
        void startReconciliationRejectsDifferentUser() {
            plan.lock(LockType.ON_TIME);

            PlanAccessForbiddenException ex = assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.startReconciliation(ORG_ID, planId, plan.getVersion(), OTHER_USER)
            );
            assertTrue(ex.getMessage().contains(OTHER_USER.toString()));
        }

        @Test
        void startReconciliationRejectsDifferentUserBeforeVersionChecks() {
            plan.lock(LockType.ON_TIME);

            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.startReconciliation(ORG_ID, planId, 999, OTHER_USER)
            );
        }

        @Test
        void submitReconciliationRejectsDifferentUser() {
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();

            PlanAccessForbiddenException ex = assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.submitReconciliation(ORG_ID, planId, plan.getVersion(), OTHER_USER)
            );
            assertTrue(ex.getMessage().contains(OTHER_USER.toString()));
        }

        @Test
        void submitReconciliationRejectsDifferentUserBeforeVersionChecks() {
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();

            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.submitReconciliation(ORG_ID, planId, 999, OTHER_USER)
            );
        }

        @Test
        void carryForwardRejectsDifferentUser() {
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId)).thenReturn(List.of());

            PlanAccessForbiddenException ex = assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.carryForward(ORG_ID, planId, List.of(), plan.getVersion(), OTHER_USER)
            );
            assertTrue(ex.getMessage().contains(OTHER_USER.toString()));
        }

        @Test
        void carryForwardRejectsDifferentUserBeforeVersionChecks() {
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.carryForward(ORG_ID, planId, List.of(), 999, OTHER_USER)
            );
        }

        @Test
        void requirePlanOwnershipPassesForOwner() {
            // Should not throw
            planService.requirePlanOwnership(plan, USER_ID);
        }

        @Test
        void requirePlanOwnershipThrowsForNonOwner() {
            assertThrows(
                    PlanAccessForbiddenException.class,
                    () -> planService.requirePlanOwnership(plan, OTHER_USER)
            );
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private WeeklyCommitEntity createCommit(
            UUID planId, String title, ChessPriority priority,
            UUID outcomeIdVal, String nonStrategicReason
    ) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, title
        );
        commit.setChessPriority(priority);
        if (outcomeIdVal != null) {
            commit.setOutcomeId(outcomeIdVal);
        }
        if (nonStrategicReason != null) {
            commit.setNonStrategicReason(nonStrategicReason);
        }
        return commit;
    }
}
