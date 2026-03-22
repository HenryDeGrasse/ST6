package com.weekly.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditService;
import com.weekly.auth.OrgGraphClient;
import com.weekly.config.OrgPolicyService;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.ReviewStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.CreateReviewRequest;
import com.weekly.plan.dto.UpdateCommitRequest;
import com.weekly.plan.dto.WeeklyCommitResponse;
import com.weekly.plan.dto.WeeklyPlanResponse;
import com.weekly.plan.repository.ManagerReviewRepository;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitService;
import com.weekly.plan.service.CommitValidator;
import com.weekly.plan.service.OptimisticLockException;
import com.weekly.plan.service.PlanService;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.plan.service.ReviewService;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import com.weekly.shared.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Acceptance criteria tests mapped directly to PRD §18.
 *
 * <p>Each nested class references the acceptance criterion number.
 * These tests verify domain logic at the service layer using mocked
 * repositories and the in-memory RCDO client.
 */
class AcceptanceCriteriaTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();

    // RCDO hierarchy IDs
    private static final UUID RALLY_CRY_ID = UUID.randomUUID();
    private static final UUID OBJECTIVE_ID = UUID.randomUUID();
    private static final UUID OUTCOME_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyCommitActualRepository actualRepository;
    private ManagerReviewRepository reviewRepository;
    private InMemoryRcdoClient rcdoClient;
    private AuditService auditService;
    private OutboxService outboxService;
    private OrgGraphClient orgGraphClient;
    private PlanService planService;
    private CommitService commitService;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        actualRepository = mock(WeeklyCommitActualRepository.class);
        reviewRepository = mock(ManagerReviewRepository.class);
        rcdoClient = new InMemoryRcdoClient();
        auditService = mock(AuditService.class);
        outboxService = mock(OutboxService.class);
        orgGraphClient = mock(OrgGraphClient.class);

        CommitValidator commitValidator = new CommitValidator();
        OrgPolicyService orgPolicyService = mock(OrgPolicyService.class);
        when(orgPolicyService.getPolicy(any())).thenReturn(OrgPolicyService.defaultPolicy());

        planService = new PlanService(
                planRepository, commitRepository, actualRepository,
                commitValidator, rcdoClient, auditService, outboxService,
                orgGraphClient, orgPolicyService,
                mock(org.springframework.context.ApplicationEventPublisher.class),
                mock(com.weekly.compatibility.dualwrite.DualWriteService.class)
        );
        commitService = new CommitService(
                planRepository, commitRepository, actualRepository,
                commitValidator, auditService, outboxService,
                mock(com.weekly.compatibility.dualwrite.DualWriteService.class)
        );
        reviewService = new ReviewService(
                planRepository, reviewRepository,
                auditService, outboxService, orgGraphClient
        );

        // Set up org graph: MANAGER_ID manages USER_ID
        when(orgGraphClient.getDirectReports(ORG_ID, MANAGER_ID))
                .thenReturn(List.of(USER_ID));
        when(orgGraphClient.isDirectReport(ORG_ID, MANAGER_ID, USER_ID))
                .thenReturn(true);

        // Set up RCDO hierarchy using the setTree method
        rcdoClient.setTree(ORG_ID, new RcdoTree(List.of(
                new RcdoTree.RallyCry(
                        RALLY_CRY_ID.toString(), "Scale Revenue",
                        List.of(new RcdoTree.Objective(
                                OBJECTIVE_ID.toString(), "Improve Conversion",
                                RALLY_CRY_ID.toString(),
                                List.of(new RcdoTree.Outcome(
                                        OUTCOME_ID.toString(),
                                        "Increase trial-to-paid by 20%",
                                        OBJECTIVE_ID.toString()
                                ))
                        ))
                )
        )));
    }

    private LocalDate currentMonday() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC1: Plan creation
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC1: Plan creation")
    class Ac1PlanCreation {

        @Test
        @DisplayName("IC creates a plan for the current week → 201")
        void createsPlanForCurrentWeek() {
            LocalDate weekStart = currentMonday();
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, weekStart
            )).thenReturn(Optional.empty());
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanService.CreatePlanResult result = planService.createPlan(
                    ORG_ID, USER_ID, weekStart
            );

            assertTrue(result.created());
            assertEquals("DRAFT", result.plan().state());
        }

        @Test
        @DisplayName("Creating a second plan for the same week → 200 (idempotent)")
        void idempotentCreation() {
            LocalDate weekStart = currentMonday();
            WeeklyPlanEntity existing = new WeeklyPlanEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, weekStart
            );
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, weekStart
            )).thenReturn(Optional.of(existing));

            PlanService.CreatePlanResult result = planService.createPlan(
                    ORG_ID, USER_ID, weekStart
            );

            assertFalse(result.created());
            assertEquals(existing.getId().toString(), result.plan().id());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC2: Lock validation — happy path
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC2: Lock validation – happy path")
    class Ac2LockHappyPath {

        @Test
        @DisplayName("Lock with valid commits (1 KING, 1 QUEEN, 1 ROOK) succeeds")
        void lockSucceedsWithValidCommits() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );

            List<WeeklyCommitEntity> commits = List.of(
                    makeCommit(planId, "Must ship feature", ChessPriority.KING, OUTCOME_ID),
                    makeCommit(planId, "Important review", ChessPriority.QUEEN, OUTCOME_ID),
                    makeCommit(planId, "Bug fixes", ChessPriority.ROOK, OUTCOME_ID)
            );

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(commits);

            WeeklyPlanResponse result = planService.lockPlan(ORG_ID, planId, 1, USER_ID);

            assertEquals("LOCKED", result.state());
            assertNotNull(result.lockedAt());
            assertEquals("ON_TIME", result.lockType());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC3: Lock validation — rejection cases
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC3: Lock validation – rejection cases")
    class Ac3LockRejection {

        @Test
        @DisplayName("Lock with missing chessPriority → 422")
        void rejectsMissingChessPriority() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "Test"
            );
            commit.setOutcomeId(OUTCOME_ID);

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, 1, USER_ID)
            );
        }

        @Test
        @DisplayName("Lock with missing RCDO and no reason → 422 MISSING_RCDO_OR_REASON")
        void rejectsMissingRcdoAndReason() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "Test"
            );
            commit.setChessPriority(ChessPriority.KING);

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, 1, USER_ID)
            );
            assertEquals(ErrorCode.MISSING_RCDO_OR_REASON, ex.getErrorCode());
        }

        @Test
        @DisplayName("Lock with 2 KING commits → 422 CHESS_RULE_VIOLATION")
        void rejectsTwoKings() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            List<WeeklyCommitEntity> commits = List.of(
                    makeCommit(planId, "King 1", ChessPriority.KING, OUTCOME_ID),
                    makeCommit(planId, "King 2", ChessPriority.KING, OUTCOME_ID)
            );

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(commits);

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, 1, USER_ID)
            );
            assertEquals(ErrorCode.CHESS_RULE_VIOLATION, ex.getErrorCode());
        }

        @Test
        @DisplayName("Lock with 3 QUEEN commits → 422 CHESS_RULE_VIOLATION")
        void rejectsThreeQueens() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            List<WeeklyCommitEntity> commits = List.of(
                    makeCommit(planId, "King", ChessPriority.KING, OUTCOME_ID),
                    makeCommit(planId, "Queen 1", ChessPriority.QUEEN, OUTCOME_ID),
                    makeCommit(planId, "Queen 2", ChessPriority.QUEEN, OUTCOME_ID),
                    makeCommit(planId, "Queen 3", ChessPriority.QUEEN, OUTCOME_ID)
            );

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(commits);

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.lockPlan(ORG_ID, planId, 1, USER_ID)
            );
            assertEquals(ErrorCode.CHESS_RULE_VIOLATION, ex.getErrorCode());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC4: Locked plan immutability
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC4: Locked plan immutability")
    class Ac4LockedImmutability {

        @Test
        @DisplayName("PATCH chessPriority on locked commit → 409 FIELD_FROZEN")
        void rejectsFrozenFieldEdit() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    commitId, ORG_ID, planId, "Test"
            );
            commit.setChessPriority(ChessPriority.KING);

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    null, null, "QUEEN", null, null, null, null, null, null, null, null
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> commitService.updateCommit(ORG_ID, commitId, 1, request, USER_ID)
            );
            assertEquals(ErrorCode.FIELD_FROZEN, ex.getErrorCode());
        }

        @Test
        @DisplayName("PATCH progressNotes on locked commit → 200 OK")
        void allowsProgressNotesOnLockedPlan() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    commitId, ORG_ID, planId, "Test"
            );

            when(commitRepository.findById(commitId)).thenReturn(Optional.of(commit));
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateCommitRequest request = new UpdateCommitRequest(
                    null, null, null, null, null, null, null, null, null, null,
                    "Making progress"
            );

            WeeklyCommitResponse result = commitService.updateCommit(
                    ORG_ID, commitId, 1, request, USER_ID
            );

            assertEquals("Making progress", result.progressNotes());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC5 & AC6: Reconciliation
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC5/AC6: Reconciliation")
    class Ac5Reconciliation {

        @Test
        @DisplayName("Submit reconciliation with all actuals → RECONCILED + REVIEW_PENDING")
        void submitReconciliationSucceeds() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    commitId, ORG_ID, planId, "Test"
            );
            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(
                    commitId, ORG_ID
            );
            actual.setCompletionStatus(CompletionStatus.DONE);

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            WeeklyPlanResponse result = planService.submitReconciliation(
                    ORG_ID, planId, plan.getVersion(), USER_ID
            );

            assertEquals("RECONCILED", result.state());
            assertEquals("REVIEW_PENDING", result.reviewStatus());
        }

        @Test
        @DisplayName("Submit without actuals → 422 MISSING_COMPLETION_STATUS")
        void rejectsMissingCompletionStatus() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    commitId, ORG_ID, planId, "Test"
            );

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of());

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.submitReconciliation(
                            ORG_ID, planId, plan.getVersion(), USER_ID
                    )
            );
            assertEquals(ErrorCode.MISSING_COMPLETION_STATUS, ex.getErrorCode());
            assertTrue(ex.getDetails().stream()
                    .anyMatch(d -> commitId.toString().equals(d.get("commitId"))
                            && "MISSING_COMPLETION_STATUS".equals(d.get("code"))));
        }

        @Test
        @DisplayName("Submit with DROPPED commit missing deltaReason → 422")
        void rejectsMissingDeltaReason() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    commitId, ORG_ID, planId, "Test"
            );
            WeeklyCommitActualEntity actual = new WeeklyCommitActualEntity(
                    commitId, ORG_ID
            );
            actual.setCompletionStatus(CompletionStatus.DROPPED);

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));
            when(actualRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(actual));

            PlanValidationException ex = assertThrows(
                    PlanValidationException.class,
                    () -> planService.submitReconciliation(
                            ORG_ID, planId, plan.getVersion(), USER_ID
                    )
            );
            assertEquals(ErrorCode.MISSING_DELTA_REASON, ex.getErrorCode());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC7: Carry-forward
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC7: Carry-forward")
    class Ac7CarryForward {

        @Test
        @DisplayName("Carry forward creates commit in next week with lineage")
        void carryForwardCreatesLineage() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    commitId, ORG_ID, planId, "Carry me"
            );
            commit.setChessPriority(ChessPriority.ROOK);
            commit.setOutcomeId(OUTCOME_ID);

            LocalDate nextWeekStart = currentMonday().plusWeeks(1);
            WeeklyPlanEntity nextPlan = new WeeklyPlanEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, nextWeekStart
            );

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    ORG_ID, USER_ID, nextWeekStart
            )).thenReturn(Optional.of(nextPlan));
            when(commitRepository.save(any())).thenAnswer(inv -> {
                WeeklyCommitEntity saved = inv.getArgument(0);
                assertEquals(commitId, saved.getCarriedFromCommitId());
                return saved;
            });

            WeeklyPlanResponse result = planService.carryForward(
                    ORG_ID, planId, List.of(commitId),
                    plan.getVersion(), USER_ID
            );

            assertEquals("CARRY_FORWARD", result.state());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC8: Optimistic lock conflict
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC8: Optimistic lock conflict")
    class Ac8OptimisticLock {

        @Test
        @DisplayName("Stale version causes 409 Conflict")
        void staleVersionCausesConflict() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            OptimisticLockException ex = assertThrows(
                    OptimisticLockException.class,
                    () -> planService.lockPlan(ORG_ID, planId, 0, USER_ID)
            );
            assertEquals(planId, ex.getEntityId());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC11: Review flow
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC11: Review flow")
    class Ac11ReviewFlow {

        @Test
        @DisplayName("Manager approves plan → reviewStatus = APPROVED")
        void approvesPlan() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateReviewRequest request = new CreateReviewRequest(
                    "APPROVED", "Great work"
            );

            reviewService.submitReview(ORG_ID, planId, MANAGER_ID, request);

            assertEquals(ReviewStatus.APPROVED, plan.getReviewStatus());
        }

        @Test
        @DisplayName("Manager requests changes → plan reverts to RECONCILING")
        void requestsChanges() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CreateReviewRequest request = new CreateReviewRequest(
                    "CHANGES_REQUESTED", "Please add more detail"
            );

            reviewService.submitReview(ORG_ID, planId, MANAGER_ID, request);

            assertEquals(PlanState.RECONCILING, plan.getState());
            assertEquals(ReviewStatus.CHANGES_REQUESTED, plan.getReviewStatus());
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC15: Late lock path
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC15: Late lock path")
    class Ac15LateLock {

        @Test
        @DisplayName("DRAFT plan starts reconciliation → implicit late lock")
        void lateLockPath() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );

            List<WeeklyCommitEntity> commits = List.of(
                    makeCommit(planId, "Task", ChessPriority.KING, OUTCOME_ID)
            );

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(commits);

            WeeklyPlanResponse result = planService.startReconciliation(
                    ORG_ID, planId, 1, USER_ID
            );

            assertEquals("RECONCILING", result.state());
            assertEquals("LATE_LOCK", result.lockType());
            assertNotNull(result.lockedAt());
        }

        @Test
        @DisplayName("Late lock with missing chess priority → 422")
        void lateLockValidationStillApplies() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );

            WeeklyCommitEntity commit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "No priority"
            );
            commit.setOutcomeId(OUTCOME_ID);

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of(commit));

            assertThrows(
                    PlanValidationException.class,
                    () -> planService.startReconciliation(
                            ORG_ID, planId, 1, USER_ID
                    )
            );
        }
    }

    // ────────────────────────────────────────────────────────────
    // §18 AC17: Changes requested after carry-forward
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC17: Changes requested after carry-forward")
    class Ac17ChangesAfterCarryForward {

        @Test
        @DisplayName("Request changes on plan with carryForwardExecutedAt → 409")
        void blocksChangesAfterCarryForward() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(
                    planId, ORG_ID, USER_ID, currentMonday()
            );
            plan.lock(LockType.ON_TIME);
            plan.startReconciliation();
            plan.submitReconciliation();
            plan.carryForward();

            when(planRepository.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));

            CreateReviewRequest request = new CreateReviewRequest(
                    "CHANGES_REQUESTED", "Too late"
            );

            PlanStateException ex = assertThrows(
                    PlanStateException.class,
                    () -> reviewService.submitReview(
                            ORG_ID, planId, MANAGER_ID, request
                    )
            );
            assertEquals(ErrorCode.CARRY_FORWARD_ALREADY_EXECUTED, ex.getErrorCode());
        }
    }

    // ── Helper methods ─────────────────────────────────────────

    private WeeklyCommitEntity makeCommit(
            UUID planId, String title, ChessPriority priority, UUID outcomeId
    ) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), ORG_ID, planId, title
        );
        commit.setChessPriority(priority);
        commit.setOutcomeId(outcomeId);
        return commit;
    }
}
