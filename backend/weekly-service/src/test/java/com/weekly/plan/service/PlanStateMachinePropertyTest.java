package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditService;
import com.weekly.auth.OrgGraphClient;
import com.weekly.config.OrgPolicyService;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for the weekly plan lifecycle state machine (PRD §13.1).
 *
 * <p>The Gradle test configuration sets jqwik's {@code jqwik.tries.default} to
 * 100 for PR runs and allows nightly overrides via
 * {@code -PpropertyTries=10000}.
 *
 * <p>These properties prove four invariants:
 * <ol>
 *   <li><b>Invalid transitions</b> always throw {@link PlanStateException} and
 *       leave the plan unchanged.</li>
 *   <li><b>Every complete valid sequence</b> from DRAFT ends in either
 *       {@link PlanState#RECONCILED} or {@link PlanState#CARRY_FORWARD}.</li>
 *   <li><b>Random action sequences</b> only emit audit records for successful
 *       transitions, with the late-lock DRAFT→RECONCILING path emitting two
 *       records because it performs two state changes.</li>
 *   <li><b>Optimistic locking</b> rejects stale versions before any mutation.</li>
 * </ol>
 */
@Tag("property")
class PlanStateMachinePropertyTest {

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate WEEK_START = LocalDate.now().with(DayOfWeek.MONDAY);
    private static final Set<PlanState> RECONCILED_OR_TERMINAL = Set.of(
        PlanState.RECONCILED,
        PlanState.CARRY_FORWARD
    );

    /** Legal transition actions from each plan state. */
    private static final Map<PlanState, Set<TransitionAction>> VALID_FROM = Map.of(
        PlanState.DRAFT, Set.of(TransitionAction.LOCK, TransitionAction.START_RECONCILIATION),
        PlanState.LOCKED, Set.of(TransitionAction.START_RECONCILIATION),
        PlanState.RECONCILING, Set.of(TransitionAction.SUBMIT_RECONCILIATION),
        PlanState.RECONCILED, Set.of(TransitionAction.CARRY_FORWARD),
        PlanState.CARRY_FORWARD, Set.of()
    );

    enum TransitionAction {
        LOCK, START_RECONCILIATION, SUBMIT_RECONCILIATION, CARRY_FORWARD
    }

    record InvalidPair(PlanState state, TransitionAction action) {}

    @Property
    void invalidTransitionsAlwaysThrowPlanStateException(
            @ForAll("invalidPairs") InvalidPair pair) {
        TestContext ctx = newContext();
        ctx.plan().setState(pair.state());
        PlanState stateBefore = ctx.plan().getState();
        int versionBefore = ctx.plan().getVersion();

        assertThrows(
            PlanStateException.class,
            () -> executeAction(ctx, pair.action(), versionBefore),
            () -> "Expected PlanStateException for " + pair.state() + " + " + pair.action()
        );

        assertStateUnchanged(ctx, stateBefore, versionBefore);
    }

    @Property
    void completeValidSequencesAlwaysReachReconciledOrCarryForward(
            @ForAll("validTerminalSequences") List<TransitionAction> sequence) {
        TestContext ctx = newContext();
        PlanState expectedState = PlanState.DRAFT;

        for (TransitionAction action : sequence) {
            executeAction(ctx, action, ctx.plan().getVersion());
            expectedState = nextState(expectedState, action);
        }

        assertEquals(expectedState, ctx.plan().getState(),
            "Valid sequence should end in its expected lifecycle state");
        if (!RECONCILED_OR_TERMINAL.contains(ctx.plan().getState())) {
            throw new AssertionError("Complete valid sequence must end in RECONCILED or CARRY_FORWARD");
        }
    }

    @Property
    void randomActionSequencesOnlyAuditSuccessfulTransitions(
            @ForAll("actionSequences") List<TransitionAction> sequence) {
        TestContext ctx = newContext();
        int expectedAuditCalls = 0;

        for (TransitionAction action : sequence) {
            PlanState stateBefore = ctx.plan().getState();
            int versionBefore = ctx.plan().getVersion();

            if (VALID_FROM.get(stateBefore).contains(action)) {
                executeAction(ctx, action, versionBefore);
                expectedAuditCalls += expectedAuditCalls(stateBefore, action);
                assertEquals(nextState(stateBefore, action), ctx.plan().getState(),
                    "Successful transition should move to the expected state");
            } else {
                assertThrows(PlanStateException.class, () -> executeAction(ctx, action, versionBefore));
                assertStateUnchanged(ctx, stateBefore, versionBefore);
            }
        }

        verify(ctx.auditService(), times(expectedAuditCalls))
            .record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Property
    void staleVersionIsAlwaysRejected(
            @ForAll("allActions") TransitionAction action,
            @ForAll("staleVersions") int staleVersion) {
        TestContext ctx = newContext();
        PlanState stateBefore = ctx.plan().getState();
        int versionBefore = ctx.plan().getVersion();

        assertThrows(
            OptimisticLockException.class,
            () -> executeAction(ctx, action, staleVersion),
            () -> "Expected OptimisticLockException for stale version " + staleVersion
        );

        assertStateUnchanged(ctx, stateBefore, versionBefore);
    }

    @Provide
    Arbitrary<InvalidPair> invalidPairs() {
        List<InvalidPair> pairs = new ArrayList<>();
        for (PlanState state : PlanState.values()) {
            for (TransitionAction action : TransitionAction.values()) {
                if (!VALID_FROM.get(state).contains(action)) {
                    pairs.add(new InvalidPair(state, action));
                }
            }
        }
        return Arbitraries.of(pairs);
    }

    @Provide
    Arbitrary<List<TransitionAction>> validTerminalSequences() {
        return Arbitraries.of(
            List.of(
                TransitionAction.LOCK,
                TransitionAction.START_RECONCILIATION,
                TransitionAction.SUBMIT_RECONCILIATION
            ),
            List.of(
                TransitionAction.START_RECONCILIATION,
                TransitionAction.SUBMIT_RECONCILIATION
            ),
            List.of(
                TransitionAction.LOCK,
                TransitionAction.START_RECONCILIATION,
                TransitionAction.SUBMIT_RECONCILIATION,
                TransitionAction.CARRY_FORWARD
            ),
            List.of(
                TransitionAction.START_RECONCILIATION,
                TransitionAction.SUBMIT_RECONCILIATION,
                TransitionAction.CARRY_FORWARD
            )
        );
    }

    @Provide
    Arbitrary<List<TransitionAction>> actionSequences() {
        return Arbitraries.of(TransitionAction.values())
            .list()
            .ofMinSize(1)
            .ofMaxSize(8);
    }

    @Provide
    Arbitrary<TransitionAction> allActions() {
        return Arbitraries.of(TransitionAction.values());
    }

    @Provide
    Arbitrary<Integer> staleVersions() {
        return Arbitraries.oneOf(
            Arbitraries.integers().between(Integer.MIN_VALUE, 0),
            Arbitraries.integers().between(2, Integer.MAX_VALUE)
        );
    }

    private static int expectedAuditCalls(PlanState state, TransitionAction action) {
        if (state == PlanState.DRAFT && action == TransitionAction.START_RECONCILIATION) {
            return 2;
        }
        return 1;
    }

    private static PlanState nextState(PlanState state, TransitionAction action) {
        return switch (state) {
            case DRAFT -> switch (action) {
                case LOCK -> PlanState.LOCKED;
                case START_RECONCILIATION -> PlanState.RECONCILING;
                default -> throw new IllegalArgumentException("Invalid transition: " + state + " -> " + action);
            };
            case LOCKED -> switch (action) {
                case START_RECONCILIATION -> PlanState.RECONCILING;
                default -> throw new IllegalArgumentException("Invalid transition: " + state + " -> " + action);
            };
            case RECONCILING -> switch (action) {
                case SUBMIT_RECONCILIATION -> PlanState.RECONCILED;
                default -> throw new IllegalArgumentException("Invalid transition: " + state + " -> " + action);
            };
            case RECONCILED -> switch (action) {
                case CARRY_FORWARD -> PlanState.CARRY_FORWARD;
                default -> throw new IllegalArgumentException("Invalid transition: " + state + " -> " + action);
            };
            case CARRY_FORWARD -> throw new IllegalArgumentException("CARRY_FORWARD is terminal");
        };
    }

    private static void assertStateUnchanged(TestContext ctx, PlanState expectedState, int expectedVersion) {
        assertEquals(expectedState, ctx.plan().getState(),
            "Plan state must remain unchanged after a rejected transition");
        assertEquals(expectedVersion, ctx.plan().getVersion(),
            "Plan version must remain unchanged after a rejected transition");
    }

    private record TestContext(
        WeeklyPlanEntity plan,
        WeeklyPlanRepository planRepository,
        WeeklyCommitRepository commitRepository,
        WeeklyCommitActualRepository actualRepository,
        AuditService auditService,
        PlanService service,
        List<WeeklyCommitEntity> lockCommits,
        List<WeeklyCommitActualEntity> submitActuals
    ) {}

    private TestContext newContext() {
        UUID planId = UUID.randomUUID();
        WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, WEEK_START);

        WeeklyPlanRepository planRepo = mock(WeeklyPlanRepository.class);
        WeeklyCommitRepository commitRepo = mock(WeeklyCommitRepository.class);
        WeeklyCommitActualRepository actualRepo = mock(WeeklyCommitActualRepository.class);
        AuditService auditSvc = mock(AuditService.class);
        OutboxService outboxSvc = mock(OutboxService.class);
        OrgGraphClient orgGraph = mock(OrgGraphClient.class);
        OrgPolicyService orgPolicy = mock(OrgPolicyService.class);
        InMemoryRcdoClient rcdoClient = new InMemoryRcdoClient();

        when(planRepo.findByOrgIdAndId(ORG_ID, planId)).thenReturn(Optional.of(plan));
        when(planRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orgPolicy.getPolicy(any())).thenReturn(OrgPolicyService.defaultPolicy());

        UUID outcomeId = UUID.randomUUID();
        UUID objectiveId = UUID.randomUUID();
        UUID rallyCryId = UUID.randomUUID();
        RcdoTree.Outcome outcome = new RcdoTree.Outcome(
            outcomeId.toString(), "Test Outcome", objectiveId.toString()
        );
        RcdoTree.Objective objective = new RcdoTree.Objective(
            objectiveId.toString(), "Test Objective", rallyCryId.toString(), List.of(outcome)
        );
        RcdoTree.RallyCry rc = new RcdoTree.RallyCry(
            rallyCryId.toString(), "Test Rally Cry", List.of(objective)
        );
        rcdoClient.setTree(ORG_ID, new RcdoTree(List.of(rc)));

        WeeklyCommitEntity king = new WeeklyCommitEntity(
            UUID.randomUUID(), ORG_ID, planId, "King Commit"
        );
        king.setChessPriority(ChessPriority.KING);
        king.setOutcomeId(outcomeId);

        WeeklyCommitEntity queen = new WeeklyCommitEntity(
            UUID.randomUUID(), ORG_ID, planId, "Queen Commit"
        );
        queen.setChessPriority(ChessPriority.QUEEN);
        queen.setNonStrategicReason("Support work");

        List<WeeklyCommitEntity> lockCommits = List.of(king, queen);

        WeeklyCommitActualEntity kingActual = new WeeklyCommitActualEntity(king.getId(), ORG_ID);
        kingActual.setCompletionStatus(CompletionStatus.DONE);
        kingActual.setActualResult("Completed");

        WeeklyCommitActualEntity queenActual = new WeeklyCommitActualEntity(queen.getId(), ORG_ID);
        queenActual.setCompletionStatus(CompletionStatus.DONE);
        queenActual.setActualResult("Done");

        List<WeeklyCommitActualEntity> submitActuals = List.of(kingActual, queenActual);

        PlanService service = new PlanService(
            planRepo, commitRepo, actualRepo,
            new CommitValidator(), rcdoClient, auditSvc, outboxSvc,
            orgGraph, orgPolicy, mock(org.springframework.context.ApplicationEventPublisher.class),
            mock(com.weekly.compatibility.dualwrite.DualWriteService.class)
        );

        return new TestContext(
            plan,
            planRepo,
            commitRepo,
            actualRepo,
            auditSvc,
            service,
            lockCommits,
            submitActuals
        );
    }

    private void executeAction(TestContext ctx, TransitionAction action, int version) {
        UUID planId = ctx.plan().getId();
        switch (action) {
            case LOCK -> {
                when(ctx.commitRepository().findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(ctx.lockCommits());
                ctx.service().lockPlan(ORG_ID, planId, version, USER_ID);
            }
            case START_RECONCILIATION -> {
                when(ctx.commitRepository().findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(ctx.lockCommits());
                ctx.service().startReconciliation(ORG_ID, planId, version, USER_ID);
            }
            case SUBMIT_RECONCILIATION -> {
                when(ctx.commitRepository().findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(ctx.lockCommits());
                when(ctx.actualRepository().findByOrgIdAndCommitIdIn(eq(ORG_ID), anyList()))
                    .thenReturn(ctx.submitActuals());
                ctx.service().submitReconciliation(ORG_ID, planId, version, USER_ID);
            }
            case CARRY_FORWARD -> {
                when(ctx.commitRepository().findByOrgIdAndWeeklyPlanId(ORG_ID, planId))
                    .thenReturn(List.of());
                when(ctx.planRepository().findByOrgIdAndOwnerUserIdAndWeekStartDate(
                    eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(Optional.empty());
                ctx.service().carryForward(ORG_ID, planId, List.of(), version, USER_ID);
            }
        }
    }
}
