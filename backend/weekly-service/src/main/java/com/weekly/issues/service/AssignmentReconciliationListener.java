package com.weekly.issues.service;

import com.weekly.plan.service.ReconciliationSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link ReconciliationSubmittedEvent} and applies assignment-based
 * issue status transitions (Phase 6, Step 8).
 *
 * <p>When a plan is submitted for reconciliation, this listener calls
 * {@link AssignmentService#reconcileAssignmentStatus} to:
 * <ul>
 *   <li><b>DONE</b> → set issue to DONE; auto-archive if plan is ≥ 8 weeks old.</li>
 *   <li><b>PARTIALLY / NOT_DONE</b> → keep issue as IN_PROGRESS for carry-forward.</li>
 *   <li><b>DROPPED</b> → revert issue to OPEN and clear the assignee.</li>
 * </ul>
 *
 * <p>Using Spring's {@link TransactionalEventListener} ensures this runs after
 * the reconciliation transaction commits, so assignment status failures do not
 * roll back the plan state change. Using the event bus also avoids a cyclic
 * dependency between the {@code plan} and {@code issues} modules.
 */
@Component
public class AssignmentReconciliationListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(AssignmentReconciliationListener.class);

    private final AssignmentService assignmentService;

    public AssignmentReconciliationListener(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Triggered after the reconciliation transaction commits.
     *
     * <p>Calls {@link AssignmentService#reconcileAssignmentStatus} with the plan and
     * actor from the event. The actor is the user who submitted the reconciliation and
     * will be recorded on any issue activity log entries created during this process.
     */
    @TransactionalEventListener
    public void onReconciliationSubmitted(ReconciliationSubmittedEvent event) {
        LOG.debug("AssignmentReconciliationListener: reconciling assignments for plan {}",
                event.planId());
        try {
            assignmentService.reconcileAssignmentStatus(
                    event.orgId(), event.planId(), event.actorUserId()
            );
        } catch (Exception e) {
            // Best-effort: log and continue. A failure here must not affect external
            // systems or the overall reconciliation outcome.
            LOG.warn("AssignmentReconciliationListener: error reconciling plan {} — {}",
                    event.planId(), e.getMessage(), e);
        }
    }
}
