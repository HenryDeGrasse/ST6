package com.weekly.plan.service;

import com.weekly.audit.AuditService;
import com.weekly.auth.OrgGraphClient;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.WeeklyPlanResponse;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoOutcomeDetail;
import com.weekly.shared.ErrorCode;
import com.weekly.shared.EventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for weekly plan lifecycle operations: create, get, lock,
 * start-reconciliation, submit-reconciliation, and carry-forward.
 */
@Service
public class PlanService {

    private static final int DEFAULT_STALENESS_THRESHOLD_MIN = 60;
    private static final int DEFAULT_MAX_KING = 1;
    private static final int DEFAULT_MAX_QUEEN = 2;

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository actualRepository;
    private final CommitValidator commitValidator;
    private final RcdoClient rcdoClient;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final OrgGraphClient orgGraphClient;

    public PlanService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository,
            CommitValidator commitValidator,
            RcdoClient rcdoClient,
            AuditService auditService,
            OutboxService outboxService,
            OrgGraphClient orgGraphClient
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.actualRepository = actualRepository;
        this.commitValidator = commitValidator;
        this.rcdoClient = rcdoClient;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.orgGraphClient = orgGraphClient;
    }

    /**
     * Creates or retrieves a weekly plan for the given user and week.
     *
     * @return the plan and whether it was newly created
     */
    @Transactional
    public CreatePlanResult createPlan(UUID orgId, UUID userId, LocalDate weekStart) {
        validateWeekStart(weekStart);

        Optional<WeeklyPlanEntity> existing = planRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, userId, weekStart);

        if (existing.isPresent()) {
            return new CreatePlanResult(WeeklyPlanResponse.from(existing.get()), false);
        }

        // Block past-week creation
        LocalDate currentWeekStart = currentWeekMonday();
        LocalDate nextWeekStart = currentWeekStart.plusWeeks(1);
        if (weekStart.isBefore(currentWeekStart)) {
            throw new PlanValidationException(
                    ErrorCode.PAST_WEEK_CREATION_BLOCKED,
                    "Cannot create plans for past weeks",
                    List.of(Map.of("weekStart", weekStart.toString()))
            );
        }
        if (weekStart.isAfter(nextWeekStart)) {
            throw new PlanValidationException(
                    ErrorCode.INVALID_WEEK_START,
                    "Cannot create plans more than one week in advance",
                    List.of(Map.of("weekStart", weekStart.toString()))
            );
        }

        WeeklyPlanEntity plan = new WeeklyPlanEntity(
                UUID.randomUUID(), orgId, userId, weekStart
        );
        planRepository.save(plan);

        auditService.record(orgId, userId, EventType.PLAN_CREATED.getValue(),
                "WeeklyPlan", plan.getId(), null, PlanState.DRAFT.name(),
                null, null, null);

        outboxService.publish(EventType.PLAN_CREATED, "WeeklyPlan", plan.getId(), orgId,
                Map.of("ownerUserId", userId.toString(),
                        "weekStartDate", weekStart.toString()));

        return new CreatePlanResult(WeeklyPlanResponse.from(plan), true);
    }

    /**
     * Gets the current user's plan for the given week.
     */
    @Transactional(readOnly = true)
    public Optional<WeeklyPlanResponse> getMyPlan(UUID orgId, UUID userId, LocalDate weekStart) {
        validateWeekStart(weekStart);
        return planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, userId, weekStart)
                .map(WeeklyPlanResponse::from);
    }

    /**
     * Gets a plan by its ID, enforcing access control.
     *
     * <p>The requesting user must be either the plan owner or a manager whose
     * org-graph entry shows the plan owner as a direct report. Returns empty
     * if the plan is not found or belongs to a different org.
     *
     * @param orgId            the org ID from JWT
     * @param planId           the plan to retrieve
     * @param requestingUserId the authenticated user requesting access
     * @return the plan response, or empty if not found
     * @throws PlanAccessForbiddenException if the user is neither owner nor manager
     */
    @Transactional(readOnly = true)
    public Optional<WeeklyPlanResponse> getPlan(UUID orgId, UUID planId, UUID requestingUserId) {
        return planRepository.findByOrgIdAndId(orgId, planId)
                .map(plan -> {
                    boolean isOwner = plan.getOwnerUserId().equals(requestingUserId);
                    boolean isManager = !isOwner
                            && orgGraphClient.isDirectReport(orgId, requestingUserId, plan.getOwnerUserId());
                    if (!isOwner && !isManager) {
                        throw new PlanAccessForbiddenException(
                                "User " + requestingUserId + " is not authorized to view plan " + planId
                        );
                    }
                    return WeeklyPlanResponse.from(plan);
                });
    }

    /**
     * Gets a specific user's plan for a given week (manager drill-down).
     */
    @Transactional(readOnly = true)
    public Optional<WeeklyPlanResponse> getUserPlan(UUID orgId, UUID userId, LocalDate weekStart) {
        validateWeekStart(weekStart);
        return planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, userId, weekStart)
                .map(WeeklyPlanResponse::from);
    }

    /**
     * Locks a plan (DRAFT → LOCKED), enforcing all commit validations,
     * chess priority rules, and RCDO freshness. Populates RCDO snapshots.
     *
     * @param orgId          the org ID from JWT
     * @param planId         the plan to lock
     * @param expectedVersion the If-Match version for optimistic locking
     * @param userId         the authenticated user (must be plan owner)
     * @return the locked plan
     */
    @Transactional
    public WeeklyPlanResponse lockPlan(UUID orgId, UUID planId, int expectedVersion, UUID userId) {
        WeeklyPlanEntity plan = findPlanForWrite(orgId, planId);
        requirePlanOwnership(plan, userId);
        requireExpectedVersion(planId, expectedVersion, plan.getVersion());

        if (plan.getState() != PlanState.DRAFT) {
            throw new PlanStateException(
                    ErrorCode.PLAN_NOT_IN_DRAFT,
                    "Plan must be in DRAFT state to lock",
                    plan.getState().name()
            );
        }

        List<WeeklyCommitEntity> commits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);

        performLockValidationAndSnapshots(orgId, commits);

        String previousState = plan.getState().name();
        plan.lock(LockType.ON_TIME);
        planRepository.save(plan);

        auditService.record(orgId, plan.getOwnerUserId(), EventType.PLAN_LOCKED.getValue(),
                "WeeklyPlan", planId, previousState, PlanState.LOCKED.name(),
                "ON_TIME", null, null);

        outboxService.publish(EventType.PLAN_LOCKED, "WeeklyPlan", planId, orgId,
                Map.of("ownerUserId", plan.getOwnerUserId().toString(),
                        "lockType", "ON_TIME",
                        "weekStartDate", plan.getWeekStartDate().toString()));

        return WeeklyPlanResponse.from(plan);
    }

    /**
     * Starts reconciliation on a plan.
     *
     * <p>If LOCKED → RECONCILING (normal path).
     * <p>If DRAFT → RECONCILING (late lock path): performs implicit baseline
     * snapshot, validation, and sets lockType = LATE_LOCK.
     *
     * @param orgId          the org ID
     * @param planId         the plan to reconcile
     * @param expectedVersion optimistic lock version
     * @param userId         the user initiating reconciliation
     * @return the updated plan
     */
    @Transactional
    public WeeklyPlanResponse startReconciliation(UUID orgId, UUID planId, int expectedVersion, UUID userId) {
        WeeklyPlanEntity plan = findPlanForWrite(orgId, planId);
        requirePlanOwnership(plan, userId);
        requireExpectedVersion(planId, expectedVersion, plan.getVersion());
        PlanState currentState = plan.getState();

        if (currentState == PlanState.DRAFT) {
            // Late lock path: DRAFT → RECONCILING
            List<WeeklyCommitEntity> commits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
            performLockValidationAndSnapshots(orgId, commits);

            plan.lock(LockType.LATE_LOCK);
            plan.startReconciliation();
            planRepository.save(plan);

            auditService.record(orgId, userId, EventType.PLAN_LOCKED.getValue(),
                    "WeeklyPlan", planId, PlanState.DRAFT.name(), PlanState.LOCKED.name(),
                    "LATE_LOCK", null, null);
            auditService.record(orgId, userId, EventType.PLAN_RECONCILIATION_STARTED.getValue(),
                    "WeeklyPlan", planId, PlanState.LOCKED.name(), PlanState.RECONCILING.name(),
                    "Late lock path", null, null);

            outboxService.publish(EventType.PLAN_LOCKED, "WeeklyPlan", planId, orgId,
                    Map.of("ownerUserId", plan.getOwnerUserId().toString(),
                            "lockType", "LATE_LOCK",
                            "weekStartDate", plan.getWeekStartDate().toString(),
                            "commitCount", commits.size(),
                            "incompleteCount", 0));

            outboxService.publish(EventType.PLAN_RECONCILIATION_STARTED, "WeeklyPlan", planId, orgId,
                    Map.of("ownerUserId", plan.getOwnerUserId().toString(),
                            "lockType", "LATE_LOCK",
                            "weekStartDate", plan.getWeekStartDate().toString()));

        } else if (currentState == PlanState.LOCKED) {
            // Normal path: LOCKED → RECONCILING
            plan.startReconciliation();
            planRepository.save(plan);

            auditService.record(orgId, userId, EventType.PLAN_RECONCILIATION_STARTED.getValue(),
                    "WeeklyPlan", planId, PlanState.LOCKED.name(), PlanState.RECONCILING.name(),
                    null, null, null);

            outboxService.publish(EventType.PLAN_RECONCILIATION_STARTED, "WeeklyPlan", planId, orgId,
                    Map.of("ownerUserId", plan.getOwnerUserId().toString(),
                            "weekStartDate", plan.getWeekStartDate().toString()));

        } else {
            throw new PlanStateException(
                    ErrorCode.CONFLICT,
                    "Plan must be in DRAFT or LOCKED state to start reconciliation",
                    currentState.name()
            );
        }

        return WeeklyPlanResponse.from(plan);
    }

    /**
     * Submits reconciliation (RECONCILING → RECONCILED).
     *
     * <p>Requires every commit to have a completionStatus and, if not DONE,
     * a non-empty deltaReason.
     *
     * @param orgId          the org ID
     * @param planId         the plan
     * @param expectedVersion optimistic lock version
     * @param userId         the user submitting
     * @return the updated plan
     */
    @Transactional
    public WeeklyPlanResponse submitReconciliation(UUID orgId, UUID planId, int expectedVersion, UUID userId) {
        WeeklyPlanEntity plan = findPlanForWrite(orgId, planId);
        requirePlanOwnership(plan, userId);
        requireExpectedVersion(planId, expectedVersion, plan.getVersion());

        if (plan.getState() != PlanState.RECONCILING) {
            throw new PlanStateException(
                    ErrorCode.CONFLICT,
                    "Plan must be in RECONCILING state to submit reconciliation",
                    plan.getState().name()
            );
        }

        List<WeeklyCommitEntity> commits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
        List<UUID> commitIds = commits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsMap = new HashMap<>();
        if (!commitIds.isEmpty()) {
            actualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds)
                    .forEach(a -> actualsMap.put(a.getCommitId(), a));
        }

        // Validate reconciliation completeness
        List<Map<String, Object>> missingCompletionErrors = new ArrayList<>();
        List<Map<String, Object>> missingDeltaReasonErrors = new ArrayList<>();
        for (WeeklyCommitEntity commit : commits) {
            WeeklyCommitActualEntity actual = actualsMap.get(commit.getId());
            if (actual == null || actual.getCompletionStatus() == null) {
                missingCompletionErrors.add(Map.of(
                        "commitId", commit.getId().toString(),
                        "code", ErrorCode.MISSING_COMPLETION_STATUS.name(),
                        "message", "Completion status is required for reconciliation"
                ));
                continue;
            }
            if (actual.getCompletionStatus() != CompletionStatus.DONE
                    && (actual.getDeltaReason() == null || actual.getDeltaReason().isBlank())) {
                missingDeltaReasonErrors.add(Map.of(
                        "commitId", commit.getId().toString(),
                        "code", ErrorCode.MISSING_DELTA_REASON.name(),
                        "message", "Delta reason is required for non-DONE commits"
                ));
            }
        }

        if (!missingCompletionErrors.isEmpty()) {
            throw new PlanValidationException(
                    ErrorCode.MISSING_COMPLETION_STATUS,
                    "Reconciliation incomplete: some commits are missing a completion status",
                    missingCompletionErrors
            );
        }

        if (!missingDeltaReasonErrors.isEmpty()) {
            throw new PlanValidationException(
                    ErrorCode.MISSING_DELTA_REASON,
                    "Reconciliation incomplete: some commits need a delta reason",
                    missingDeltaReasonErrors
            );
        }

        long doneCount = actualsMap.values().stream()
                .filter(actual -> actual.getCompletionStatus() == CompletionStatus.DONE)
                .count();
        long partialCount = actualsMap.values().stream()
                .filter(actual -> actual.getCompletionStatus() == CompletionStatus.PARTIALLY)
                .count();
        long droppedCount = actualsMap.values().stream()
                .filter(actual -> actual.getCompletionStatus() == CompletionStatus.DROPPED)
                .count();

        plan.submitReconciliation();
        planRepository.save(plan);

        auditService.record(orgId, userId, EventType.PLAN_RECONCILED.getValue(),
                "WeeklyPlan", planId, PlanState.RECONCILING.name(), PlanState.RECONCILED.name(),
                null, null, null);

        outboxService.publish(EventType.PLAN_RECONCILED, "WeeklyPlan", planId, orgId,
                Map.of("ownerUserId", plan.getOwnerUserId().toString(),
                        "weekStartDate", plan.getWeekStartDate().toString(),
                        "doneCount", doneCount,
                        "partialCount", partialCount,
                        "droppedCount", droppedCount));

        return WeeklyPlanResponse.from(plan);
    }

    /**
     * Carries forward selected commits from a RECONCILED plan to the next week's plan.
     *
     * <p>Transitions the current plan to CARRY_FORWARD. Creates a new DRAFT plan
     * for the next week (if it doesn't exist) and clones commits with lineage.
     *
     * @param orgId          the org ID
     * @param planId         the current plan (must be RECONCILED)
     * @param commitIds      IDs of commits to carry forward
     * @param expectedVersion optimistic lock version
     * @param userId         the user performing carry-forward
     * @return the current plan (now in CARRY_FORWARD state)
     */
    @Transactional
    public WeeklyPlanResponse carryForward(
            UUID orgId, UUID planId, List<UUID> commitIds,
            int expectedVersion, UUID userId
    ) {
        WeeklyPlanEntity plan = findPlanForWrite(orgId, planId);
        requirePlanOwnership(plan, userId);
        requireExpectedVersion(planId, expectedVersion, plan.getVersion());

        if (plan.getState() != PlanState.RECONCILED) {
            throw new PlanStateException(
                    ErrorCode.CONFLICT,
                    "Plan must be in RECONCILED state to carry forward",
                    plan.getState().name()
            );
        }

        // Validate that all commit IDs belong to this plan
        List<WeeklyCommitEntity> allCommits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
        Set<UUID> validIds = allCommits.stream().map(WeeklyCommitEntity::getId).collect(Collectors.toSet());
        for (UUID cid : commitIds) {
            if (!validIds.contains(cid)) {
                throw new PlanValidationException(
                        ErrorCode.NOT_FOUND,
                        "Commit " + cid + " does not belong to this plan",
                        List.of(Map.of("commitId", cid.toString()))
                );
            }
        }

        // Find or create next week's plan
        LocalDate nextWeekStart = plan.getWeekStartDate().plusWeeks(1);
        WeeklyPlanEntity nextPlan = planRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, plan.getOwnerUserId(), nextWeekStart)
                .orElseGet(() -> {
                    WeeklyPlanEntity newPlan = new WeeklyPlanEntity(
                            UUID.randomUUID(), orgId, plan.getOwnerUserId(), nextWeekStart
                    );
                    return planRepository.save(newPlan);
                });

        if (nextPlan.getState() != PlanState.DRAFT) {
            throw new PlanStateException(
                    ErrorCode.CONFLICT,
                    "Next week's plan is not in DRAFT state; cannot carry forward",
                    nextPlan.getState().name()
            );
        }

        // Clone commits with lineage
        Map<UUID, WeeklyCommitEntity> commitMap = allCommits.stream()
                .collect(Collectors.toMap(WeeklyCommitEntity::getId, c -> c));
        for (UUID cid : commitIds) {
            WeeklyCommitEntity source = commitMap.get(cid);
            WeeklyCommitEntity clone = new WeeklyCommitEntity(
                    UUID.randomUUID(), orgId, nextPlan.getId(), source.getTitle()
            );
            clone.setDescription(source.getDescription());
            clone.setChessPriority(source.getChessPriority());
            clone.setCategory(source.getCategory());
            clone.setOutcomeId(source.getOutcomeId());
            clone.setNonStrategicReason(source.getNonStrategicReason());
            clone.setExpectedResult(source.getExpectedResult());
            clone.setConfidence(source.getConfidence());
            clone.setTagsFromArray(source.getTags());
            clone.setCarriedFromCommitId(source.getId());
            commitRepository.save(clone);
        }

        // Transition plan to CARRY_FORWARD
        plan.carryForward();
        planRepository.save(plan);

        auditService.record(orgId, userId, EventType.PLAN_CARRY_FORWARD.getValue(),
                "WeeklyPlan", planId, PlanState.RECONCILED.name(), PlanState.CARRY_FORWARD.name(),
                "Carried " + commitIds.size() + " commits", null, null);

        outboxService.publish(EventType.PLAN_CARRY_FORWARD, "WeeklyPlan", planId, orgId,
                Map.of("ownerUserId", plan.getOwnerUserId().toString(),
                        "weekStartDate", plan.getWeekStartDate().toString(),
                        "carriedCommitCount", commitIds.size(),
                        "nextWeekPlanId", nextPlan.getId().toString()));

        return WeeklyPlanResponse.from(plan);
    }

    // ── Internal helpers ─────────────────────────────────────

    /**
     * Throws {@link PlanAccessForbiddenException} if the authenticated user is
     * not the owner of the plan.
     */
    void requirePlanOwnership(WeeklyPlanEntity plan, UUID userId) {
        if (!plan.getOwnerUserId().equals(userId)) {
            throw new PlanAccessForbiddenException(
                    "User " + userId + " is not the owner of plan " + plan.getId()
            );
        }
    }

    WeeklyPlanEntity findPlanForWrite(UUID orgId, UUID planId) {
        return planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }

    void requireExpectedVersion(UUID resourceId, int expectedVersion, int actualVersion) {
        if (actualVersion != expectedVersion) {
            throw new OptimisticLockException(resourceId, expectedVersion, actualVersion);
        }
    }

    /**
     * Performs lock-time validation (commit validation, chess rules, RCDO freshness,
     * and RCDO snapshot population). Shared between lockPlan and late-lock path.
     */
    void performLockValidationAndSnapshots(UUID orgId, List<WeeklyCommitEntity> commits) {
        // Validate all commits
        LockValidationResult validation = validateForLock(commits);
        if (!validation.valid()) {
            throw new PlanValidationException(
                    ErrorCode.MISSING_RCDO_OR_REASON,
                    "Lock validation failed: commits have errors",
                    validation.details()
            );
        }

        // Validate chess rules
        LockValidationResult chessValidation = validateChessRules(commits);
        if (!chessValidation.valid()) {
            throw new PlanValidationException(
                    ErrorCode.CHESS_RULE_VIOLATION,
                    "Chess priority rules violated",
                    chessValidation.details()
            );
        }

        // Validate RCDO freshness
        if (!rcdoClient.isCacheFresh(orgId, DEFAULT_STALENESS_THRESHOLD_MIN)) {
            java.time.Instant lastRefreshedAt = rcdoClient.getLastRefreshedAt(orgId);
            throw new PlanValidationException(
                    ErrorCode.RCDO_VALIDATION_STALE,
                    "RCDO data is stale; cannot lock plan",
                    List.of(Map.of(
                            "lastRefreshedAt", lastRefreshedAt != null ? lastRefreshedAt.toString() : "never",
                            "stalenessThresholdMinutes", DEFAULT_STALENESS_THRESHOLD_MIN
                    ))
            );
        }

        // Populate RCDO snapshots
        LockValidationResult rcdoSnapshotValidation = populateRcdoSnapshots(orgId, commits);
        if (!rcdoSnapshotValidation.valid()) {
            throw new PlanValidationException(
                    ErrorCode.MISSING_RCDO_OR_REASON,
                    "Lock validation failed: linked RCDO outcomes could not be resolved",
                    rcdoSnapshotValidation.details()
            );
        }
    }

    LockValidationResult validateForLock(List<WeeklyCommitEntity> commits) {
        List<Map<String, Object>> details = new ArrayList<>();

        for (WeeklyCommitEntity commit : commits) {
            List<CommitValidationError> errors = commitValidator.validate(commit);
            for (CommitValidationError error : errors) {
                details.add(LockValidationResult.commitError(
                        commit.getId(), error.code(), error.message()
                ));
            }
        }

        if (details.isEmpty()) {
            return LockValidationResult.success();
        }
        return LockValidationResult.failure(details);
    }

    LockValidationResult validateChessRules(List<WeeklyCommitEntity> commits) {
        List<Map<String, Object>> details = new ArrayList<>();

        long kingCount = commits.stream()
                .filter(c -> c.getChessPriority() == ChessPriority.KING)
                .count();
        long queenCount = commits.stream()
                .filter(c -> c.getChessPriority() == ChessPriority.QUEEN)
                .count();

        if (kingCount != DEFAULT_MAX_KING) {
            details.add(LockValidationResult.planError(
                    "CHESS_RULE_VIOLATION",
                    "Exactly " + DEFAULT_MAX_KING + " KING required",
                    "exactlyOneKing",
                    DEFAULT_MAX_KING,
                    (int) kingCount
            ));
        }

        if (queenCount > DEFAULT_MAX_QUEEN) {
            details.add(LockValidationResult.planError(
                    "CHESS_RULE_VIOLATION",
                    "At most " + DEFAULT_MAX_QUEEN + " QUEEN allowed",
                    "maxQueen",
                    DEFAULT_MAX_QUEEN,
                    (int) queenCount
            ));
        }

        if (details.isEmpty()) {
            return LockValidationResult.success();
        }
        return LockValidationResult.failure(details);
    }

    LockValidationResult populateRcdoSnapshots(UUID orgId, List<WeeklyCommitEntity> commits) {
        Map<UUID, RcdoOutcomeDetail> detailsByCommitId = new HashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (WeeklyCommitEntity commit : commits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }

            Optional<RcdoOutcomeDetail> detail = rcdoClient.getOutcome(orgId, commit.getOutcomeId());
            if (detail.isEmpty()) {
                details.add(LockValidationResult.commitError(
                        commit.getId(),
                        ErrorCode.MISSING_RCDO_OR_REASON.name(),
                        "Linked outcome could not be resolved from the cached RCDO tree"
                ));
                continue;
            }

            detailsByCommitId.put(commit.getId(), detail.get());
        }

        if (!details.isEmpty()) {
            return LockValidationResult.failure(details);
        }

        for (WeeklyCommitEntity commit : commits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }

            RcdoOutcomeDetail detail = detailsByCommitId.get(commit.getId());
            try {
                commit.populateSnapshot(
                        UUID.fromString(detail.rallyCryId()),
                        detail.rallyCryName(),
                        UUID.fromString(detail.objectiveId()),
                        detail.objectiveName(),
                        UUID.fromString(detail.outcomeId()),
                        detail.outcomeName()
                );
            } catch (IllegalArgumentException e) {
                details.add(LockValidationResult.commitError(
                        commit.getId(),
                        ErrorCode.MISSING_RCDO_OR_REASON.name(),
                        "Resolved RCDO identifiers must be UUID strings"
                ));
            }
        }

        if (!details.isEmpty()) {
            return LockValidationResult.failure(details);
        }

        return LockValidationResult.success();
    }

    private void validateWeekStart(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new PlanValidationException(
                    ErrorCode.INVALID_WEEK_START,
                    "weekStart must be a Monday",
                    List.of(Map.of("provided", weekStart.toString()))
            );
        }
    }

    static LocalDate currentWeekMonday() {
        LocalDate today = LocalDate.now();
        return today.with(DayOfWeek.MONDAY);
    }

    // ── Result types ─────────────────────────────────────────

    public record CreatePlanResult(WeeklyPlanResponse plan, boolean created) {}
}
