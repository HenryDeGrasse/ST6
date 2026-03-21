package com.weekly.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditEventEntity;
import com.weekly.audit.AuditEventRepository;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyPlanRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AuditStateReconciliationJob}.
 *
 * <p>Covers:
 * <ul>
 *   <li>No mismatches when plan has a correct, complete audit trail.</li>
 *   <li>Detection of a wrong final state (state mismatch).</li>
 *   <li>Detection of a plan with too few state-transition events.</li>
 *   <li>Detection of a plan with no audit events at all.</li>
 *   <li>Graceful handling when no organisations exist.</li>
 *   <li>Multi-org scanning independence.</li>
 *   <li>Minimum event counts for each {@link PlanState}.</li>
 *   <li>Pagination: all pages are scanned.</li>
 * </ul>
 */
class AuditStateReconciliationJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-18T03:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private static final UUID ORG_ID = UUID.randomUUID();

    private WeeklyPlanRepository planRepository;
    private AuditEventRepository auditEventRepository;
    private SimpleMeterRegistry meterRegistry;
    private AuditStateReconciliationJob job;

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        auditEventRepository = mock(AuditEventRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new AuditStateReconciliationJob(
                planRepository, auditEventRepository, meterRegistry, FIXED_CLOCK);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WeeklyPlanEntity buildPlan(UUID orgId, PlanState state) {
        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                UUID.randomUUID(), orgId, UUID.randomUUID(),
                LocalDate.of(2026, 3, 16));
        plan.setState(state);
        return plan;
    }

    /**
     * Builds an audit event for a WeeklyPlan aggregate recording the given state
     * transition. The {@code createdAt} is set via reflection so tests fully control
     * the chronological ordering of events.
     */
    private AuditEventEntity buildStateEvent(
            UUID orgId, UUID planId,
            String previousState, String newState,
            Instant createdAt) {
        AuditEventEntity event = new AuditEventEntity(
                orgId, UUID.randomUUID(), "plan.action",
                "WeeklyPlan", planId,
                previousState, newState,
                null, null, null);
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        return event;
    }

    /** Minimal valid audit trail for a DRAFT plan — 1 event. */
    private List<AuditEventEntity> draftTrail(UUID orgId, UUID planId) {
        return List.of(
                buildStateEvent(orgId, planId, null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z"))
        );
    }

    /** Minimal valid audit trail for a LOCKED plan — 2 events. */
    private List<AuditEventEntity> lockedTrail(UUID orgId, UUID planId) {
        return List.of(
                buildStateEvent(orgId, planId, null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z")),
                buildStateEvent(orgId, planId, "DRAFT", "LOCKED",
                        Instant.parse("2026-03-16T10:00:00Z"))
        );
    }

    /** Minimal valid audit trail for a RECONCILING plan — 3 events. */
    private List<AuditEventEntity> reconcilingTrail(UUID orgId, UUID planId) {
        return List.of(
                buildStateEvent(orgId, planId, null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z")),
                buildStateEvent(orgId, planId, "DRAFT", "LOCKED",
                        Instant.parse("2026-03-16T10:00:00Z")),
                buildStateEvent(orgId, planId, "LOCKED", "RECONCILING",
                        Instant.parse("2026-03-17T09:00:00Z"))
        );
    }

    /** Minimal valid audit trail for a RECONCILED plan — 4 events. */
    private List<AuditEventEntity> reconciledTrail(UUID orgId, UUID planId) {
        return List.of(
                buildStateEvent(orgId, planId, null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z")),
                buildStateEvent(orgId, planId, "DRAFT", "LOCKED",
                        Instant.parse("2026-03-16T10:00:00Z")),
                buildStateEvent(orgId, planId, "LOCKED", "RECONCILING",
                        Instant.parse("2026-03-17T09:00:00Z")),
                buildStateEvent(orgId, planId, "RECONCILING", "RECONCILED",
                        Instant.parse("2026-03-17T15:00:00Z"))
        );
    }

    /** Minimal valid audit trail for a CARRY_FORWARD plan — 5 events. */
    private List<AuditEventEntity> carryForwardTrail(UUID orgId, UUID planId) {
        return List.of(
                buildStateEvent(orgId, planId, null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z")),
                buildStateEvent(orgId, planId, "DRAFT", "LOCKED",
                        Instant.parse("2026-03-16T10:00:00Z")),
                buildStateEvent(orgId, planId, "LOCKED", "RECONCILING",
                        Instant.parse("2026-03-17T09:00:00Z")),
                buildStateEvent(orgId, planId, "RECONCILING", "RECONCILED",
                        Instant.parse("2026-03-17T15:00:00Z")),
                buildStateEvent(orgId, planId, "RECONCILED", "CARRY_FORWARD",
                        Instant.parse("2026-03-18T09:00:00Z"))
        );
    }

    /** Review event uses newState for decision, not plan lifecycle state. */
    private AuditEventEntity reviewSubmittedEvent(UUID orgId, UUID planId, String decision) {
        return buildStateEvent(
                orgId,
                planId,
                null,
                decision,
                Instant.parse("2026-03-18T10:00:00Z")
        );
    }

    /** Same-state event should not count as a lifecycle transition. */
    private AuditEventEntity sameStateEvent(UUID orgId, UUID planId, String state) {
        return buildStateEvent(
                orgId,
                planId,
                state,
                state,
                Instant.parse("2026-03-18T11:00:00Z")
        );
    }

    private double mismatchCount() {
        Counter counter = meterRegistry
                .find("audit_state_reconciliation_mismatches_total").counter();
        return counter == null ? 0.0 : counter.count();
    }

    // ── Empty organisation list ───────────────────────────────────────────────

    @Test
    void emptyOrgListIsHandledGracefully() {
        when(planRepository.findDistinctOrgIds()).thenReturn(Collections.emptyList());

        job.reconcileAuditStates();

        verify(planRepository).findDistinctOrgIds();
        assertEquals(0.0, mismatchCount(), "No mismatches expected when no orgs exist");
    }

    // ── No mismatches — correct audit trails ─────────────────────────────────

    @Test
    void draftPlanWithCorrectAuditTrailProducesNoMismatches() {
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.DRAFT);
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(draftTrail(ORG_ID, plan.getId()));

        job.reconcileAuditStates();

        assertEquals(0.0, mismatchCount(),
                "DRAFT plan with correct 1-event trail must produce no mismatches");
    }

    @Test
    void reconciledPlanWithFourEventsProducesNoMismatches() {
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.RECONCILED);
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(reconciledTrail(ORG_ID, plan.getId()));

        job.reconcileAuditStates();

        assertEquals(0.0, mismatchCount(),
                "RECONCILED plan with correct 4-event trail must produce no mismatches");
    }

    @Test
    void carryForwardPlanWithFiveEventsProducesNoMismatches() {
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.CARRY_FORWARD);
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(carryForwardTrail(ORG_ID, plan.getId()));

        job.reconcileAuditStates();

        assertEquals(0.0, mismatchCount(),
                "CARRY_FORWARD plan with correct 5-event trail must produce no mismatches");
    }

    @Test
    void reviewEventsAreIgnoredWhenReconstructingPlanStateHistory() {
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.RECONCILED);
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));
        List<AuditEventEntity> events = new java.util.ArrayList<>(reconciledTrail(ORG_ID, plan.getId()));
        events.add(reviewSubmittedEvent(ORG_ID, plan.getId(), "APPROVED"));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(events);

        job.reconcileAuditStates();

        assertEquals(0.0, mismatchCount(),
                "Review decision events must not be mistaken for plan-state transitions");
    }

    @Test
    void sameStateEventsAreIgnoredWhenReconstructingPlanStateHistory() {
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.CARRY_FORWARD);
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));
        List<AuditEventEntity> events = new java.util.ArrayList<>(carryForwardTrail(ORG_ID, plan.getId()));
        events.add(sameStateEvent(ORG_ID, plan.getId(), "CARRY_FORWARD"));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(events);

        job.reconcileAuditStates();

        assertEquals(0.0, mismatchCount(),
                "Same-state audit rows must not be mistaken for lifecycle transitions");
    }

    // ── Mismatch detection: wrong final state ─────────────────────────────────

    @Test
    void planWithWrongFinalStateIsDetected() {
        // Plan is RECONCILED but the audit trail only goes to LOCKED.
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.RECONCILED);
        List<AuditEventEntity> truncatedTrail = List.of(
                buildStateEvent(ORG_ID, plan.getId(), null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z")),
                buildStateEvent(ORG_ID, plan.getId(), "DRAFT", "LOCKED",
                        Instant.parse("2026-03-16T10:00:00Z"))
        );
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(truncatedTrail);

        job.reconcileAuditStates();

        // Both checks fire: wrong final state AND insufficient event count.
        assertEquals(2.0, mismatchCount(),
                "RECONCILED plan with trail ending at LOCKED must produce 2 counter increments "
                        + "(wrong final state + insufficient event count)");
    }

    // ── Mismatch detection: missing audit events ──────────────────────────────

    @Test
    void reconciledPlanWithTooFewEventsIsDetected() {
        // RECONCILED requires ≥4 events. Provide 2 but with the correct final state.
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.RECONCILED);
        List<AuditEventEntity> skimpyTrail = List.of(
                buildStateEvent(ORG_ID, plan.getId(), null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z")),
                // LOCKED and RECONCILING events are missing; jump straight to RECONCILED.
                buildStateEvent(ORG_ID, plan.getId(), "RECONCILING", "RECONCILED",
                        Instant.parse("2026-03-17T15:00:00Z"))
        );
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(skimpyTrail);

        job.reconcileAuditStates();

        assertEquals(2.0, mismatchCount(),
                "RECONCILED plan with a broken 2-event trail must be detected as both an "
                        + "incoherent history and missing events");
    }

    @Test
    void brokenPreviousStateChainIsDetected() {
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.RECONCILED);
        List<AuditEventEntity> brokenTrail = List.of(
                buildStateEvent(ORG_ID, plan.getId(), null, "DRAFT",
                        Instant.parse("2026-03-16T09:00:00Z")),
                buildStateEvent(ORG_ID, plan.getId(), "DRAFT", "LOCKED",
                        Instant.parse("2026-03-16T10:00:00Z")),
                buildStateEvent(ORG_ID, plan.getId(), "DRAFT", "RECONCILING",
                        Instant.parse("2026-03-17T09:00:00Z")),
                buildStateEvent(ORG_ID, plan.getId(), "RECONCILING", "RECONCILED",
                        Instant.parse("2026-03-17T15:00:00Z"))
        );
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(brokenTrail);

        job.reconcileAuditStates();

        assertEquals(1.0, mismatchCount(),
                "A broken previous_state → new_state chain must be detected even when "
                        + "the final state and event count look correct");
    }

    // ── Mismatch detection: no audit events ──────────────────────────────────

    @Test
    void planWithNoAuditEventsIsDetected() {
        WeeklyPlanEntity plan = buildPlan(ORG_ID, PlanState.DRAFT);
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan.getId())))
                .thenReturn(Collections.emptyList());

        job.reconcileAuditStates();

        assertEquals(1.0, mismatchCount(),
                "Plan with no audit events must be detected as a mismatch");
    }

    // ── Multiple plans ────────────────────────────────────────────────────────

    @Test
    void multiplePlansWithCorrectTrailsProduceNoMismatches() {
        WeeklyPlanEntity plan1 = buildPlan(ORG_ID, PlanState.DRAFT);
        WeeklyPlanEntity plan2 = buildPlan(ORG_ID, PlanState.RECONCILED);
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan1, plan2));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan1.getId())))
                .thenReturn(draftTrail(ORG_ID, plan1.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan2.getId())))
                .thenReturn(reconciledTrail(ORG_ID, plan2.getId()));

        job.reconcileAuditStates();

        assertEquals(0.0, mismatchCount(),
                "Multiple plans with correct trails must produce no mismatches");
    }

    @Test
    void multiplePlansWhereOneHasNoEventsDetectsSingleMismatch() {
        WeeklyPlanEntity plan1 = buildPlan(ORG_ID, PlanState.DRAFT);
        WeeklyPlanEntity plan2 = buildPlan(ORG_ID, PlanState.LOCKED);
        // plan2 has no audit events at all.
        Page<WeeklyPlanEntity> page = new PageImpl<>(List.of(plan1, plan2));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan1.getId())))
                .thenReturn(draftTrail(ORG_ID, plan1.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan2.getId())))
                .thenReturn(Collections.emptyList());

        job.reconcileAuditStates();

        assertEquals(1.0, mismatchCount(),
                "Only the plan missing audit events should produce a mismatch");
    }

    // ── Multi-org scanning ────────────────────────────────────────────────────

    @Test
    void multipleOrgsAreScannedIndependently() {
        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();

        WeeklyPlanEntity plan1 = buildPlan(org1, PlanState.DRAFT);
        WeeklyPlanEntity plan2 = buildPlan(org2, PlanState.LOCKED);

        Page<WeeklyPlanEntity> page1 = new PageImpl<>(List.of(plan1));
        Page<WeeklyPlanEntity> page2 = new PageImpl<>(List.of(plan2));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(org1, org2));
        when(planRepository.findByOrgId(eq(org1), any(Pageable.class))).thenReturn(page1);
        when(planRepository.findByOrgId(eq(org2), any(Pageable.class))).thenReturn(page2);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(org1), eq("WeeklyPlan"), eq(plan1.getId())))
                .thenReturn(draftTrail(org1, plan1.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(org2), eq("WeeklyPlan"), eq(plan2.getId())))
                .thenReturn(lockedTrail(org2, plan2.getId()));

        job.reconcileAuditStates();

        verify(planRepository).findByOrgId(eq(org1), any(Pageable.class));
        verify(planRepository).findByOrgId(eq(org2), any(Pageable.class));
        assertEquals(0.0, mismatchCount(),
                "Both orgs with correct trails must produce no mismatches");
    }

    // ── Minimum event counts per state ───────────────────────────────────────

    @Test
    void minimumEventsForDraftIsOne() {
        assertEquals(1, AuditStateReconciliationJob.minimumEventsForState(PlanState.DRAFT));
    }

    @Test
    void minimumEventsForLockedIsTwo() {
        assertEquals(2, AuditStateReconciliationJob.minimumEventsForState(PlanState.LOCKED));
    }

    @Test
    void minimumEventsForReconcilingIsThree() {
        assertEquals(3,
                AuditStateReconciliationJob.minimumEventsForState(PlanState.RECONCILING));
    }

    @Test
    void minimumEventsForReconciledIsFour() {
        assertEquals(4,
                AuditStateReconciliationJob.minimumEventsForState(PlanState.RECONCILED));
    }

    @Test
    void minimumEventsForCarryForwardIsFive() {
        assertEquals(5,
                AuditStateReconciliationJob.minimumEventsForState(PlanState.CARRY_FORWARD));
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    void paginationFetchesAllPagesForOrg() {
        WeeklyPlanEntity plan1 = buildPlan(ORG_ID, PlanState.DRAFT);
        WeeklyPlanEntity plan2 = buildPlan(ORG_ID, PlanState.LOCKED);

        // Two pages: total > PAGE_SIZE so a second fetch is required.
        Page<WeeklyPlanEntity> firstPage = new PageImpl<>(
                List.of(plan1),
                PageRequest.of(0, AuditStateReconciliationJob.PAGE_SIZE),
                AuditStateReconciliationJob.PAGE_SIZE + 1L);
        Page<WeeklyPlanEntity> secondPage = new PageImpl<>(
                List.of(plan2),
                PageRequest.of(1, AuditStateReconciliationJob.PAGE_SIZE),
                AuditStateReconciliationJob.PAGE_SIZE + 1L);

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class)))
                .thenReturn(firstPage)
                .thenReturn(secondPage);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan1.getId())))
                .thenReturn(draftTrail(ORG_ID, plan1.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(plan2.getId())))
                .thenReturn(lockedTrail(ORG_ID, plan2.getId()));

        job.reconcileAuditStates();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(planRepository, times(2)).findByOrgId(eq(ORG_ID), pageableCaptor.capture());
        assertEquals(AuditStateReconciliationJob.PLAN_PAGE_SORT,
                pageableCaptor.getAllValues().get(0).getSort(),
                "Plan pagination must use a stable sort to avoid skips/duplicates across pages");
        assertEquals(0.0, mismatchCount(),
                "Plans across two pages with correct trails must produce no mismatches");
    }

    // ── All state trails produce no mismatches ────────────────────────────────

    @Test
    void allPlanStatesWithCorrectTrailsProduceNoMismatches() {
        WeeklyPlanEntity draftPlan = buildPlan(ORG_ID, PlanState.DRAFT);
        WeeklyPlanEntity lockedPlan = buildPlan(ORG_ID, PlanState.LOCKED);
        WeeklyPlanEntity reconcilingPlan = buildPlan(ORG_ID, PlanState.RECONCILING);
        WeeklyPlanEntity reconciledPlan = buildPlan(ORG_ID, PlanState.RECONCILED);
        WeeklyPlanEntity carryForwardPlan = buildPlan(ORG_ID, PlanState.CARRY_FORWARD);

        Page<WeeklyPlanEntity> page = new PageImpl<>(
                List.of(draftPlan, lockedPlan, reconcilingPlan,
                        reconciledPlan, carryForwardPlan));

        when(planRepository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(planRepository.findByOrgId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(draftPlan.getId())))
                .thenReturn(draftTrail(ORG_ID, draftPlan.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(lockedPlan.getId())))
                .thenReturn(lockedTrail(ORG_ID, lockedPlan.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(reconcilingPlan.getId())))
                .thenReturn(reconcilingTrail(ORG_ID, reconcilingPlan.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(reconciledPlan.getId())))
                .thenReturn(reconciledTrail(ORG_ID, reconciledPlan.getId()));
        when(auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                eq(ORG_ID), eq("WeeklyPlan"), eq(carryForwardPlan.getId())))
                .thenReturn(carryForwardTrail(ORG_ID, carryForwardPlan.getId()));

        job.reconcileAuditStates();

        assertEquals(0.0, mismatchCount(),
                "All plan states with correct audit trails must produce no mismatches");
    }
}
