package com.weekly.plan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.audit.AuditService;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.CreateCommitRequest;
import com.weekly.plan.dto.UpdateCommitRequest;
import com.weekly.plan.dto.WeeklyCommitResponse;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import com.weekly.shared.EventType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for weekly commit CRUD operations with plan-state enforcement.
 */
@Service
public class CommitService {

    private static final ObjectMapper AUDIT_OBJECT_MAPPER = new ObjectMapper();

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository commitActualRepository;
    private final CommitValidator commitValidator;
    private final AuditService auditService;
    private final OutboxService outboxService;

    public CommitService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository commitActualRepository,
            CommitValidator commitValidator,
            AuditService auditService,
            OutboxService outboxService
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.commitActualRepository = commitActualRepository;
        this.commitValidator = commitValidator;
        this.auditService = auditService;
        this.outboxService = outboxService;
    }

    /**
     * Lists all commits for a plan, with inline validation errors.
     * When actuals exist (RECONCILING or later), they are embedded in the response.
     */
    @Transactional(readOnly = true)
    public List<WeeklyCommitResponse> listCommits(UUID orgId, UUID planId) {
        WeeklyPlanEntity plan = requirePlanAccess(orgId, planId);
        List<WeeklyCommitEntity> commits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);

        Map<UUID, WeeklyCommitActualEntity> actualsMap = loadActualsMap(orgId, plan, commits);

        return commits.stream()
                .map(c -> WeeklyCommitResponse.from(
                        c, commitValidator.validate(c), actualsMap.get(c.getId())))
                .toList();
    }

    /**
     * Creates a new commit in a DRAFT plan.
     *
     * @param orgId   the org from auth context
     * @param planId  the target plan
     * @param request commit data
     * @param userId  the authenticated user (must be plan owner)
     */
    @Transactional
    public WeeklyCommitResponse createCommit(UUID orgId, UUID planId, CreateCommitRequest request, UUID userId) {
        WeeklyPlanEntity plan = requireOwnedPlan(orgId, planId, userId);
        requirePlanInDraft(plan);
        rejectConflictingLink(request.outcomeId(), request.nonStrategicReason());

        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), orgId, planId, request.title()
        );

        if (request.description() != null) {
            commit.setDescription(request.description());
        }
        if (request.chessPriority() != null) {
            commit.setChessPriority(ChessPriority.valueOf(request.chessPriority()));
        }
        if (request.category() != null) {
            commit.setCategory(CommitCategory.valueOf(request.category()));
        }
        if (request.outcomeId() != null) {
            commit.setOutcomeId(parseOutcomeId(request.outcomeId()));
        }
        if (request.nonStrategicReason() != null) {
            commit.setNonStrategicReason(request.nonStrategicReason());
        }
        if (request.expectedResult() != null) {
            commit.setExpectedResult(request.expectedResult());
        }
        if (request.confidence() != null) {
            commit.setConfidence(BigDecimal.valueOf(request.confidence()));
        }
        if (request.estimatedHours() != null) {
            commit.setEstimatedHours(BigDecimal.valueOf(request.estimatedHours()));
        }
        if (request.tags() != null) {
            commit.setTagsFromArray(request.tags());
        }

        commitRepository.save(commit);

        // Touch plan updatedAt
        plan.setUpdatedAt(commit.getCreatedAt());
        planRepository.save(plan);

        Map<String, Object> auditPayload = buildCreateAuditPayload(planId, commit.getTitle());
        auditService.record(orgId, userId, EventType.COMMIT_CREATED.getValue(),
                "WeeklyCommit", commit.getId(), null, null,
                serializeAuditReason(auditPayload), null, null);

        outboxService.publish(EventType.COMMIT_CREATED, "WeeklyCommit", commit.getId(), orgId,
                auditPayload);

        return WeeklyCommitResponse.from(commit, commitValidator.validate(commit));
    }

    /**
     * Updates a commit. In DRAFT state, all planning fields may be updated.
     * In LOCKED/RECONCILING state, only progressNotes may be updated.
     *
     * @param orgId           org from auth context
     * @param commitId        the commit to update
     * @param expectedVersion If-Match version
     * @param request         update data
     * @param userId          the authenticated user (must be plan owner)
     */
    @Transactional
    public WeeklyCommitResponse updateCommit(
            UUID orgId, UUID commitId, int expectedVersion,
            UpdateCommitRequest request, UUID userId
    ) {
        WeeklyCommitEntity commit = findCommitForWrite(orgId, commitId);
        WeeklyPlanEntity plan = requireOwnedPlan(orgId, commit.getWeeklyPlanId(), userId);
        requireExpectedVersion(commitId, expectedVersion, commit.getVersion());

        PlanState state = plan.getState();
        CommitAuditSnapshot beforeUpdate = snapshot(commit);

        if (state == PlanState.DRAFT) {
            rejectConflictingLinkOnUpdate(commit, request);
            applyDraftUpdate(commit, request);
        } else if (state == PlanState.LOCKED || state == PlanState.RECONCILING) {
            applyLockedUpdate(commit, request);
        } else {
            throw new PlanStateException(
                    ErrorCode.FIELD_FROZEN,
                    "Plan is in " + state + " state; commits are read-only",
                    state.name()
            );
        }

        commitRepository.save(commit);

        Map<String, Object> changedFieldsPayload = buildChangedFieldsPayload(beforeUpdate, commit, state);
        auditService.record(orgId, userId, EventType.COMMIT_UPDATED.getValue(),
                "WeeklyCommit", commitId, null, null,
                serializeAuditReason(changedFieldsPayload), null, null);

        outboxService.publish(EventType.COMMIT_UPDATED, "WeeklyCommit", commitId, orgId,
                changedFieldsPayload);

        return WeeklyCommitResponse.from(commit, commitValidator.validate(commit));
    }

    /**
     * Deletes a commit. Only allowed in DRAFT state.
     *
     * @param orgId    org from auth context
     * @param commitId the commit to delete
     * @param userId   the authenticated user (must be plan owner)
     */
    @Transactional
    public void deleteCommit(UUID orgId, UUID commitId, UUID userId) {
        WeeklyCommitEntity commit = commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));

        WeeklyPlanEntity plan = requireOwnedPlan(orgId, commit.getWeeklyPlanId(), userId);

        if (plan.getState() != PlanState.DRAFT) {
            throw new PlanStateException(
                    ErrorCode.PLAN_NOT_IN_DRAFT,
                    "Commits can only be deleted in DRAFT state",
                    plan.getState().name()
            );
        }

        // Capture title before deletion for audit trail (PRD 'disappearing work' note)
        String deletedTitle = commit.getTitle();
        UUID deletedPlanId = commit.getWeeklyPlanId();

        commitRepository.delete(commit);

        Map<String, Object> deletePayload = buildDeleteAuditPayload(commitId, deletedPlanId, deletedTitle);
        auditService.record(orgId, userId, EventType.COMMIT_DELETED.getValue(),
                "WeeklyCommit", commitId, null, null,
                serializeAuditReason(deletePayload), null, null);

        outboxService.publish(EventType.COMMIT_DELETED, "WeeklyCommit", commitId, orgId,
                deletePayload);
    }

    /**
     * Gets a single commit by ID, with actuals if available.
     */
    @Transactional(readOnly = true)
    public WeeklyCommitResponse getCommit(UUID orgId, UUID commitId) {
        WeeklyCommitEntity commit = commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));

        WeeklyPlanEntity plan = requirePlanAccess(orgId, commit.getWeeklyPlanId());
        WeeklyCommitActualEntity actualEntity = null;
        if (isAtOrAfterReconciliation(plan.getState())) {
            actualEntity = commitActualRepository.findByOrgIdAndCommitId(orgId, commitId).orElse(null);
        }

        return WeeklyCommitResponse.from(commit, commitValidator.validate(commit), actualEntity);
    }

    // ── Internal helpers ─────────────────────────────────────

    /**
     * Eagerly rejects a create request that sets both outcomeId AND nonStrategicReason.
     * PRD §5: CONFLICTING_LINK — these fields are mutually exclusive.
     */
    private void rejectConflictingLink(String outcomeId, String nonStrategicReason) {
        boolean hasOutcome = outcomeId != null && !outcomeId.isBlank();
        boolean hasReason = nonStrategicReason != null && !nonStrategicReason.isBlank();
        if (hasOutcome && hasReason) {
            throw new PlanValidationException(
                    ErrorCode.CONFLICTING_LINK,
                    "A commit cannot have both an RCDO link and a non-strategic reason",
                    List.of(Map.of(
                            "field", "outcomeId / nonStrategicReason",
                            "message", "These fields are mutually exclusive"
                    ))
            );
        }
    }

    /**
     * Eagerly rejects an update request that would result in both outcomeId AND
     * nonStrategicReason being set on the commit. Considers both the request
     * fields and the existing entity state.
     */
    private void rejectConflictingLinkOnUpdate(WeeklyCommitEntity commit, UpdateCommitRequest request) {
        // Determine the effective values after the update would be applied
        String effectiveOutcomeId = request.outcomeId() != null
                ? request.outcomeId()
                : (commit.getOutcomeId() != null ? commit.getOutcomeId().toString() : null);
        String effectiveReason = request.nonStrategicReason() != null
                ? request.nonStrategicReason()
                : commit.getNonStrategicReason();

        rejectConflictingLink(effectiveOutcomeId, effectiveReason);
    }

    private void requirePlanOwnership(WeeklyPlanEntity plan, UUID userId) {
        if (!plan.getOwnerUserId().equals(userId)) {
            throw new PlanAccessForbiddenException(
                    "User " + userId + " is not the owner of plan " + plan.getId()
            );
        }
    }

    private WeeklyPlanEntity requirePlanAccess(UUID orgId, UUID planId) {
        return planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }

    private WeeklyPlanEntity requireOwnedPlan(UUID orgId, UUID planId, UUID userId) {
        WeeklyPlanEntity plan = requirePlanAccess(orgId, planId);
        requirePlanOwnership(plan, userId);
        return plan;
    }

    private void requirePlanInDraft(WeeklyPlanEntity plan) {
        if (plan.getState() != PlanState.DRAFT) {
            throw new PlanStateException(
                    ErrorCode.PLAN_NOT_IN_DRAFT,
                    "Commits can only be added in DRAFT state",
                    plan.getState().name()
            );
        }
    }

    private WeeklyCommitEntity findCommitForWrite(UUID orgId, UUID commitId) {
        return commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));
    }

    private void requireExpectedVersion(UUID commitId, int expectedVersion, int actualVersion) {
        if (actualVersion != expectedVersion) {
            throw new OptimisticLockException(commitId, expectedVersion, actualVersion);
        }
    }

    private void applyDraftUpdate(WeeklyCommitEntity commit, UpdateCommitRequest request) {
        if (request.title() != null) {
            commit.setTitle(request.title());
        }
        if (request.description() != null) {
            commit.setDescription(request.description());
        }
        if (request.chessPriority() != null) {
            commit.setChessPriority(ChessPriority.valueOf(request.chessPriority()));
        }
        if (request.category() != null) {
            commit.setCategory(CommitCategory.valueOf(request.category()));
        }
        if (request.outcomeId() != null) {
            commit.setOutcomeId(parseOutcomeId(request.outcomeId()));
        }
        if (request.nonStrategicReason() != null) {
            commit.setNonStrategicReason(request.nonStrategicReason());
        }
        if (request.expectedResult() != null) {
            commit.setExpectedResult(request.expectedResult());
        }
        if (request.confidence() != null) {
            commit.setConfidence(BigDecimal.valueOf(request.confidence()));
        }
        if (request.estimatedHours() != null) {
            commit.setEstimatedHours(BigDecimal.valueOf(request.estimatedHours()));
        }
        if (request.tags() != null) {
            commit.setTagsFromArray(request.tags());
        }
        if (request.progressNotes() != null) {
            commit.setProgressNotes(request.progressNotes());
        }
    }

    private void applyLockedUpdate(WeeklyCommitEntity commit, UpdateCommitRequest request) {
        // Only progressNotes is mutable after lock
        boolean hasFrozenField = request.title() != null
                || request.description() != null
                || request.chessPriority() != null
                || request.category() != null
                || request.outcomeId() != null
                || request.nonStrategicReason() != null
                || request.expectedResult() != null
                || request.confidence() != null
                || request.estimatedHours() != null
                || request.tags() != null;

        if (hasFrozenField) {
            throw new PlanStateException(
                    ErrorCode.FIELD_FROZEN,
                    "Only progressNotes may be updated after plan is locked",
                    "LOCKED"
            );
        }

        if (request.progressNotes() != null) {
            commit.setProgressNotes(request.progressNotes());
        }
    }

    private Map<String, Object> buildCreateAuditPayload(UUID planId, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", planId.toString());
        payload.put("title", title);
        return payload;
    }

    private Map<String, Object> buildChangedFieldsPayload(
            CommitAuditSnapshot before,
            WeeklyCommitEntity after,
            PlanState state
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planState", state.name());

        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(before.title(), after.getTitle())) {
            changedFields.add("title");
        }
        if (!Objects.equals(before.description(), after.getDescription())) {
            changedFields.add("description");
        }
        if (!Objects.equals(before.chessPriority(), after.getChessPriority())) {
            changedFields.add("chessPriority");
        }
        if (!Objects.equals(before.category(), after.getCategory())) {
            changedFields.add("category");
        }
        if (!Objects.equals(before.outcomeId(), after.getOutcomeId())) {
            changedFields.add("outcomeId");
        }
        if (!Objects.equals(before.nonStrategicReason(), after.getNonStrategicReason())) {
            changedFields.add("nonStrategicReason");
        }
        if (!Objects.equals(before.expectedResult(), after.getExpectedResult())) {
            changedFields.add("expectedResult");
        }
        if (!Objects.equals(before.confidence(), after.getConfidence())) {
            changedFields.add("confidence");
        }
        if (!Objects.equals(before.estimatedHours(), after.getEstimatedHours())) {
            changedFields.add("estimatedHours");
        }
        if (!Arrays.equals(before.tags(), after.getTags())) {
            changedFields.add("tags");
        }
        if (!Objects.equals(before.progressNotes(), after.getProgressNotes())) {
            changedFields.add("progressNotes");
        }

        payload.put("changedFields", changedFields);
        return payload;
    }

    private Map<String, Object> buildDeleteAuditPayload(UUID commitId, UUID planId, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commitId", commitId.toString());
        payload.put("planId", planId.toString());
        payload.put("title", title);
        return payload;
    }

    private CommitAuditSnapshot snapshot(WeeklyCommitEntity commit) {
        return new CommitAuditSnapshot(
                commit.getTitle(),
                commit.getDescription(),
                commit.getChessPriority(),
                commit.getCategory(),
                commit.getOutcomeId(),
                commit.getNonStrategicReason(),
                commit.getExpectedResult(),
                commit.getConfidence(),
                commit.getEstimatedHours(),
                commit.getTags(),
                commit.getProgressNotes()
        );
    }

    private String serializeAuditReason(Map<String, Object> payload) {
        try {
            return AUDIT_OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize commit audit payload", e);
        }
    }

    /**
     * Loads actuals for commits when the plan is in RECONCILING or later state.
     * Returns an empty map for plans not yet in reconciliation.
     */
    private Map<UUID, WeeklyCommitActualEntity> loadActualsMap(
            UUID orgId, WeeklyPlanEntity plan, List<WeeklyCommitEntity> commits
    ) {
        if (commits.isEmpty() || !isAtOrAfterReconciliation(plan.getState())) {
            return Map.of();
        }
        List<UUID> commitIds = commits.stream().map(WeeklyCommitEntity::getId).toList();
        return commitActualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                .collect(Collectors.toMap(WeeklyCommitActualEntity::getCommitId, Function.identity()));
    }

    private boolean isAtOrAfterReconciliation(PlanState state) {
        return state == PlanState.RECONCILING
                || state == PlanState.RECONCILED
                || state == PlanState.CARRY_FORWARD;
    }

    private UUID parseOutcomeId(String rawOutcomeId) {
        try {
            return UUID.fromString(rawOutcomeId);
        } catch (IllegalArgumentException e) {
            throw new PlanValidationException(
                    ErrorCode.MISSING_RCDO_OR_REASON,
                    "outcomeId must be a UUID string",
                    List.of(Map.of("field", "outcomeId", "provided", rawOutcomeId))
            );
        }
    }

    private record CommitAuditSnapshot(
            String title,
            String description,
            ChessPriority chessPriority,
            CommitCategory category,
            UUID outcomeId,
            String nonStrategicReason,
            String expectedResult,
            BigDecimal confidence,
            BigDecimal estimatedHours,
            String[] tags,
            String progressNotes
    ) {
    }
}
