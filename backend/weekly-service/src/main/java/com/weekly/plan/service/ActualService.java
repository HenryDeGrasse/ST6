package com.weekly.plan.service;

import com.weekly.audit.AuditService;
import com.weekly.compatibility.dualwrite.DualWriteService;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.UpdateActualRequest;
import com.weekly.plan.dto.WeeklyCommitActualResponse;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import com.weekly.shared.EventType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing commit actuals (reconciliation data).
 *
 * <p>Actuals can only be edited when the plan is in RECONCILING state.
 * The commit entity serves as the aggregate root — writing actuals
 * increments the commit's version (optimistic locking on the commit).
 */
@Service
public class ActualService {

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository actualRepository;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final DualWriteService dualWriteService;

    public ActualService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository,
            AuditService auditService,
            OutboxService outboxService,
            DualWriteService dualWriteService
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.actualRepository = actualRepository;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.dualWriteService = dualWriteService;
    }

    /**
     * Updates the actuals for a commit. Creates the actual entity if it doesn't exist.
     *
     * <p>The plan must be in RECONCILING state. The commit version (If-Match)
     * is used for optimistic locking since the commit is the aggregate root.
     *
     * @param orgId           the org ID
     * @param commitId        the commit ID
     * @param expectedVersion the commit version for optimistic locking
     * @param request         the actuals data
     * @param userId          the user performing the update
     * @return the updated actuals
     */
    @Transactional
    public WeeklyCommitActualResponse updateActual(
            UUID orgId, UUID commitId, int expectedVersion,
            UpdateActualRequest request, UUID userId
    ) {
        WeeklyCommitEntity commit = commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));

        WeeklyPlanEntity plan = planRepository.findByOrgIdAndId(orgId, commit.getWeeklyPlanId())
                .orElseThrow(() -> new PlanNotFoundException(commit.getWeeklyPlanId()));

        requirePlanOwnership(plan, userId);
        requireExpectedVersion(commitId, expectedVersion, commit.getVersion());

        if (plan.getState() != PlanState.RECONCILING) {
            throw new PlanStateException(
                    ErrorCode.CONFLICT,
                    "Actuals can only be edited when the plan is in RECONCILING state",
                    plan.getState().name()
            );
        }

        CompletionStatus status = parseCompletionStatus(request.completionStatus());

        WeeklyCommitActualEntity actual = actualRepository.findById(commitId)
                .orElseGet(() -> new WeeklyCommitActualEntity(commitId, orgId));

        actual.setActualResult(request.actualResult());
        actual.setCompletionStatus(status);
        actual.setDeltaReason(request.deltaReason());
        actual.setTimeSpent(request.timeSpent());
        if (request.actualHours() != null) {
            actual.setActualHours(BigDecimal.valueOf(request.actualHours()));
        }
        actualRepository.save(actual);

        // Phase 6 dual-write: mirror actual to weekly_assignment_actuals
        dualWriteService.onActualWritten(commit, actual);

        // Bump commit version (aggregate root)
        commit.setUpdatedAt(actual.getUpdatedAt());
        commitRepository.save(commit);

        auditService.record(orgId, userId, EventType.COMMIT_ACTUAL_UPDATED.getValue(),
                "WeeklyCommit", commitId, null, status.name(),
                null, null, null);

        outboxService.publish(EventType.COMMIT_ACTUAL_UPDATED, "WeeklyCommit", commitId, orgId,
                Map.of("planId", plan.getId().toString(),
                        "completionStatus", status.name()));

        return WeeklyCommitActualResponse.from(actual);
    }

    // ── Internal helpers ─────────────────────────────────────

    private CompletionStatus parseCompletionStatus(String rawCompletionStatus) {
        if (rawCompletionStatus == null || rawCompletionStatus.isBlank()) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request validation failed",
                    java.util.List.of(Map.of(
                            "field", "completionStatus",
                            "message", "must not be blank"
                    ))
            );
        }

        try {
            return CompletionStatus.valueOf(rawCompletionStatus);
        } catch (IllegalArgumentException ex) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Request validation failed",
                    java.util.List.of(Map.of(
                            "field", "completionStatus",
                            "message", "must be one of DONE, PARTIALLY, NOT_DONE, DROPPED"
                    ))
            );
        }
    }

    private void requirePlanOwnership(WeeklyPlanEntity plan, UUID userId) {
        if (!plan.getOwnerUserId().equals(userId)) {
            throw new PlanAccessForbiddenException(
                    "User " + userId + " is not the owner of plan " + plan.getId()
            );
        }
    }

    private void requireExpectedVersion(UUID commitId, int expectedVersion, int actualVersion) {
        if (actualVersion != expectedVersion) {
            throw new OptimisticLockException(commitId, expectedVersion, actualVersion);
        }
    }
}
