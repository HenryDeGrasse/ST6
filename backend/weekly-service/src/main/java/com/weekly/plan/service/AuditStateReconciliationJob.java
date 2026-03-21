package com.weekly.plan.service;

import com.weekly.audit.AuditEventEntity;
import com.weekly.audit.AuditEventRepository;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyPlanRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that verifies every weekly plan has a complete audit trail (§14.1).
 *
 * <p>PRD §14.1 mandates a 100% SLO for state machine integrity: every state transition
 * must produce an audit event. This job runs daily at 03:30 UTC (after the hash chain
 * verification job at 03:00) and detects <em>missing</em> audit events — a different
 * failure mode from hash-chain tampering.
 *
 * <p>For each plan the job:
 * <ol>
 *   <li>Fetches all {@code WeeklyPlan} audit events ordered by {@code created_at}.</li>
 *   <li>Filters to real plan-state transitions (ignoring review events and no-op events
 *       that also happen to populate {@code new_state}).</li>
 *   <li>Reconstructs the state history from {@code previous_state -> new_state} pairs and
 *       verifies the chain is internally consistent.</li>
 *   <li>Verifies that the final reconstructed state matches {@code plan.state}.</li>
 *   <li>Verifies that the number of state-transition audit events is at least the minimum
 *       required for the plan's current state:
 *       DRAFT=1, LOCKED=2, RECONCILING=3, RECONCILED=4, CARRY_FORWARD=5.</li>
 * </ol>
 *
 * <p>Any detected mismatch is logged at ERROR level and increments the
 * {@code audit_state_reconciliation_mismatches_total} Micrometer counter so that
 * alerting can be wired against it.
 *
 * <p>Plans are paginated (page size {@link #PAGE_SIZE}) to avoid OOM, following the
 * same pattern as {@link com.weekly.audit.AuditHashChainVerificationJob}.
 *
 * <p>Enabled via {@code weekly.audit.state-reconciliation.enabled=true}.
 */
@Component
@ConditionalOnProperty(
        name = "weekly.audit.state-reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AuditStateReconciliationJob {

    private static final Logger LOG =
            LoggerFactory.getLogger(AuditStateReconciliationJob.class);

    /** Number of plans fetched per database page to bound heap usage. */
    static final int PAGE_SIZE = 1000;

    /** Stable pagination order prevents duplicates/skips across pages. */
    static final Sort PLAN_PAGE_SORT = Sort.by(
            Sort.Order.asc("createdAt"),
            Sort.Order.asc("id")
    );

    private final WeeklyPlanRepository planRepository;
    private final AuditEventRepository auditEventRepository;
    private final Counter mismatchCounter;
    private final Clock clock;

    @Autowired
    public AuditStateReconciliationJob(
            WeeklyPlanRepository planRepository,
            AuditEventRepository auditEventRepository,
            MeterRegistry meterRegistry
    ) {
        this(planRepository, auditEventRepository, meterRegistry, Clock.systemUTC());
    }

    AuditStateReconciliationJob(
            WeeklyPlanRepository planRepository,
            AuditEventRepository auditEventRepository,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.auditEventRepository = auditEventRepository;
        this.mismatchCounter = Counter.builder("audit_state_reconciliation_mismatches_total")
                .description("Number of plan/audit-trail state mismatches detected")
                .register(meterRegistry);
        this.clock = clock;
    }

    /**
     * Iterates all organisations and verifies the audit trail for each plan.
     * Runs daily at 03:30 UTC.
     *
     * <p>Deliberately avoids a single long-lived transaction so pagination does
     * not accumulate every loaded entity in the persistence context.
     */
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void reconcileAuditStates() {
        List<UUID> orgIds = planRepository.findDistinctOrgIds();
        if (orgIds.isEmpty()) {
            LOG.debug("Audit state reconciliation: no organisations found, nothing to reconcile");
            return;
        }

        LOG.info("Audit state reconciliation: starting for {} organisation(s)", orgIds.size());
        long totalMismatches = 0;
        for (UUID orgId : orgIds) {
            totalMismatches += reconcileOrg(orgId);
        }

        if (totalMismatches > 0) {
            LOG.error("Audit state reconciliation: detected {} mismatch(es) across all "
                    + "organisations", totalMismatches);
        } else {
            LOG.info("Audit state reconciliation: all plan states match audit trails");
        }
    }

    /**
     * Paginates all plans for a single organisation and checks each one's audit trail.
     *
     * @param orgId the organisation to reconcile
     * @return the total number of mismatches found for this organisation
     */
    private long reconcileOrg(UUID orgId) {
        long mismatches = 0;
        int pageNumber = 0;
        Page<WeeklyPlanEntity> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE, PLAN_PAGE_SORT);
            page = planRepository.findByOrgId(orgId, pageable);

            for (WeeklyPlanEntity plan : page.getContent()) {
                mismatches += verifyPlanAuditTrail(plan);
            }
            pageNumber++;
        } while (page.hasNext());

        return mismatches;
    }

    /**
     * Verifies that the audit trail for a single plan is consistent with its current state.
     *
     * <p>Three checks are performed:
     * <ol>
     *   <li>The state-transition history must form a coherent chain.
     *   <li>The final {@code new_state} in the chain must match {@code plan.state}.</li>
     *   <li>The number of state-transition events must be at least the minimum expected
     *       for the plan's current state.</li>
     * </ol>
     *
     * @param plan the plan to verify
     * @return the number of mismatches found
     */
    private long verifyPlanAuditTrail(WeeklyPlanEntity plan) {
        List<AuditEventEntity> allEvents =
                auditEventRepository.findByOrgIdAndAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                        plan.getOrgId(), "WeeklyPlan", plan.getId());

        List<StateTransitionEvent> stateEvents = allEvents.stream()
                .map(AuditStateReconciliationJob::toStateTransitionEvent)
                .filter(java.util.Objects::nonNull)
                .toList();

        long mismatches = 0;

        if (stateEvents.isEmpty()) {
            LOG.error("Audit state reconciliation mismatch: planId={}, orgId={}, "
                    + "plan.state={} — no state-transition audit events found",
                    plan.getId(), plan.getOrgId(), plan.getState());
            mismatchCounter.increment();
            return 1;
        }

        if (!hasCoherentStateHistory(stateEvents)) {
            LOG.error("Audit state reconciliation mismatch: planId={}, orgId={}, plan.state={} "
                    + "— state-transition history is not coherent",
                    plan.getId(), plan.getOrgId(), plan.getState());
            mismatchCounter.increment();
            mismatches++;
        }

        PlanState lastNewState = stateEvents.get(stateEvents.size() - 1).newState();
        if (!plan.getState().equals(lastNewState)) {
            LOG.error("Audit state reconciliation mismatch: planId={}, orgId={}, "
                    + "plan.state={}, lastAuditNewState={}",
                    plan.getId(), plan.getOrgId(), plan.getState(), lastNewState);
            mismatchCounter.increment();
            mismatches++;
        }

        int minRequired = minimumEventsForState(plan.getState());
        if (stateEvents.size() < minRequired) {
            LOG.error("Audit state reconciliation mismatch: planId={}, orgId={}, "
                    + "plan.state={}, stateEventCount={}, minimumRequired={}",
                    plan.getId(), plan.getOrgId(), plan.getState(),
                    stateEvents.size(), minRequired);
            mismatchCounter.increment();
            mismatches++;
        }

        return mismatches;
    }

    private static StateTransitionEvent toStateTransitionEvent(AuditEventEntity event) {
        PlanState newState = parsePlanState(event.getNewState());
        if (newState == null) {
            return null;
        }

        PlanState previousState = parsePlanState(event.getPreviousState());
        if (event.getPreviousState() != null && previousState == null) {
            return null;
        }

        if (previousState != null && previousState == newState) {
            return null;
        }

        return new StateTransitionEvent(previousState, newState);
    }

    private static PlanState parsePlanState(String rawState) {
        if (rawState == null || rawState.isBlank()) {
            return null;
        }
        try {
            return PlanState.valueOf(rawState);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean hasCoherentStateHistory(List<StateTransitionEvent> stateEvents) {
        StateTransitionEvent firstEvent = stateEvents.get(0);
        if (firstEvent.previousState() != null || firstEvent.newState() != PlanState.DRAFT) {
            return false;
        }

        for (int i = 1; i < stateEvents.size(); i++) {
            StateTransitionEvent previousEvent = stateEvents.get(i - 1);
            StateTransitionEvent currentEvent = stateEvents.get(i);
            if (currentEvent.previousState() == null
                    || currentEvent.previousState() != previousEvent.newState()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the minimum number of state-transition audit events expected for a plan
     * in the given state, based on the normal lifecycle path.
     *
     * <p>Each state requires the events from all preceding transitions plus its own:
     * <ul>
     *   <li>DRAFT: 1 (PLAN_CREATED: null → DRAFT)</li>
     *   <li>LOCKED: 2 (+ PLAN_LOCKED: DRAFT → LOCKED)</li>
     *   <li>RECONCILING: 3 (+ PLAN_RECONCILIATION_STARTED: LOCKED → RECONCILING)</li>
     *   <li>RECONCILED: 4 (+ PLAN_RECONCILED: RECONCILING → RECONCILED)</li>
     *   <li>CARRY_FORWARD: 5 (+ PLAN_CARRY_FORWARD: RECONCILED → CARRY_FORWARD)</li>
     * </ul>
     *
     * @param state the current plan state
     * @return the minimum required number of state-transition audit events
     */
    static int minimumEventsForState(PlanState state) {
        return switch (state) {
            case DRAFT -> 1;
            case LOCKED -> 2;
            case RECONCILING -> 3;
            case RECONCILED -> 4;
            case CARRY_FORWARD -> 5;
        };
    }

    /** Visible for testing. */
    Clock getClock() {
        return clock;
    }

    private record StateTransitionEvent(PlanState previousState, PlanState newState) {
    }
}
