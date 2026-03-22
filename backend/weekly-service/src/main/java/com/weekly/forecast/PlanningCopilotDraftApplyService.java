package com.weekly.forecast;

import com.weekly.audit.AuditService;
import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.WeeklyCommitResponse;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitValidator;
import com.weekly.plan.service.PlanAccessForbiddenException;
import com.weekly.plan.service.PlanService;
import com.weekly.plan.service.PlanStateException;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ErrorCode;
import com.weekly.shared.EventType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager-authorized helper for applying planning-copilot suggestions to direct-report draft plans.
 */
@Service
public class PlanningCopilotDraftApplyService {

    static final String AI_PLANNED_TAG = "draft_source:AI_PLANNED";

    private final OrgGraphClient orgGraphClient;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyCommitRepository weeklyCommitRepository;
    private final CommitValidator commitValidator;
    private final PlanService planService;
    private final AuditService auditService;
    private final OutboxService outboxService;

    public PlanningCopilotDraftApplyService(
            OrgGraphClient orgGraphClient,
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            CommitValidator commitValidator,
            PlanService planService,
            AuditService auditService,
            OutboxService outboxService) {
        this.orgGraphClient = orgGraphClient;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.weeklyCommitRepository = weeklyCommitRepository;
        this.commitValidator = commitValidator;
        this.planService = planService;
        this.auditService = auditService;
        this.outboxService = outboxService;
    }

    @Transactional
    public ApplyTeamPlanSuggestionResult apply(
            UUID orgId,
            UUID managerId,
            LocalDate weekStart,
            List<PlanningCopilotController.TeamMemberApplyRequest> members) {
        List<DirectReport> directReports = orgGraphClient.getDirectReportsWithNames(orgId, managerId);
        Map<UUID, DirectReport> directReportsById = new LinkedHashMap<>();
        for (DirectReport directReport : directReports) {
            directReportsById.put(directReport.userId(), directReport);
        }

        validateUniqueMemberIds(members);

        List<MemberDraftApplyResult> appliedMembers = new ArrayList<>();
        for (PlanningCopilotController.TeamMemberApplyRequest memberRequest : members) {
            UUID memberId = parseUserId(memberRequest.userId());
            DirectReport directReport = directReportsById.get(memberId);
            if (directReport == null) {
                throw new PlanAccessForbiddenException(
                        "User " + managerId + " is not authorized to apply AI drafts for user " + memberId);
            }

            PlanService.CreatePlanResult planResult = planService.createPlan(orgId, memberId, weekStart);
            WeeklyPlanEntity plan = weeklyPlanRepository.findByOrgIdAndId(orgId, UUID.fromString(planResult.plan().id()))
                    .orElseThrow(() -> new IllegalStateException("Applied draft plan was not found after createPlan"));
            if (plan.getState() != PlanState.DRAFT) {
                throw new PlanStateException(
                        ErrorCode.PLAN_NOT_IN_DRAFT,
                        "Managers may only apply AI suggestions to DRAFT plans",
                        plan.getState().name());
            }

            replaceAiDraftCommits(
                    orgId,
                    managerId,
                    plan,
                    Optional.ofNullable(memberRequest.suggestedCommits()).orElse(List.of()));
            List<WeeklyCommitResponse> persistedCommits = weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(orgId, plan.getId())
                    .stream()
                    .filter(this::isAiPlannedCommit)
                    .map(commit -> WeeklyCommitResponse.from(commit, commitValidator.validate(commit)))
                    .toList();

            appliedMembers.add(new MemberDraftApplyResult(
                    memberId.toString(),
                    directReport.displayName(),
                    plan.getId().toString(),
                    planResult.created(),
                    persistedCommits));
        }

        return new ApplyTeamPlanSuggestionResult("ok", weekStart, appliedMembers);
    }

    private void replaceAiDraftCommits(
            UUID orgId,
            UUID managerId,
            WeeklyPlanEntity plan,
            List<PlanningCopilotController.SuggestedCommitApplyRequest> suggestedCommits) {
        List<WeeklyCommitEntity> existingCommits = weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(orgId, plan.getId());
        for (WeeklyCommitEntity existingCommit : existingCommits) {
            if (isAiPlannedCommit(existingCommit)) {
                weeklyCommitRepository.delete(existingCommit);
                auditService.record(orgId, managerId, EventType.COMMIT_DELETED.getValue(),
                        "WeeklyCommit", existingCommit.getId(), null, null,
                        "manager_ai_reapply", null, null);
                outboxService.publish(EventType.COMMIT_DELETED, "WeeklyCommit", existingCommit.getId(), orgId,
                        Map.of(
                                "planId", plan.getId().toString(),
                                "title", existingCommit.getTitle(),
                                "source", "AI_PLANNED",
                                "ownerUserId", plan.getOwnerUserId().toString()));
            }
        }

        for (PlanningCopilotController.SuggestedCommitApplyRequest suggestedCommit : suggestedCommits) {
            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), orgId, plan.getId(), suggestedCommit.title());
            commit.setDescription(Optional.ofNullable(suggestedCommit.rationale()).orElse(""));
            commit.setChessPriority(ChessPriority.valueOf(suggestedCommit.chessPriority()));
            if (suggestedCommit.outcomeId() != null && !suggestedCommit.outcomeId().isBlank()) {
                commit.setOutcomeId(parseOutcomeId(suggestedCommit.outcomeId()));
            }
            if (suggestedCommit.estimatedHours() != null) {
                commit.setEstimatedHours(BigDecimal.valueOf(suggestedCommit.estimatedHours()));
            }
            commit.setTagsFromArray(new String[]{AI_PLANNED_TAG});
            weeklyCommitRepository.save(commit);
            auditService.record(orgId, managerId, EventType.COMMIT_CREATED.getValue(),
                    "WeeklyCommit", commit.getId(), null, null,
                    "manager_ai_apply", null, null);
            outboxService.publish(EventType.COMMIT_CREATED, "WeeklyCommit", commit.getId(), orgId,
                    Map.of(
                            "planId", plan.getId().toString(),
                            "title", commit.getTitle(),
                            "source", "AI_PLANNED",
                            "ownerUserId", plan.getOwnerUserId().toString()));
        }

        plan.setUpdatedAt(java.time.Instant.now());
        weeklyPlanRepository.save(plan);
    }

    private boolean isAiPlannedCommit(WeeklyCommitEntity commit) {
        return Arrays.asList(commit.getTags()).contains(AI_PLANNED_TAG);
    }

    private void validateUniqueMemberIds(List<PlanningCopilotController.TeamMemberApplyRequest> members) {
        Set<UUID> seenMemberIds = new LinkedHashSet<>();
        for (PlanningCopilotController.TeamMemberApplyRequest memberRequest : members) {
            UUID memberId = parseUserId(memberRequest.userId());
            if (!seenMemberIds.add(memberId)) {
                throw new PlanValidationException(
                        ErrorCode.VALIDATION_ERROR,
                        "members must not contain duplicate userId values",
                        List.of(Map.of("field", "members.userId", "provided", memberId.toString())));
            }
        }
    }

    private UUID parseUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "userId must be a UUID string",
                    List.of(Map.of("field", "userId", "provided", String.valueOf(rawUserId))));
        }

        try {
            return UUID.fromString(rawUserId);
        } catch (IllegalArgumentException e) {
            throw new PlanValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "userId must be a UUID string",
                    List.of(Map.of("field", "userId", "provided", rawUserId)));
        }
    }

    private UUID parseOutcomeId(String rawOutcomeId) {
        try {
            return UUID.fromString(rawOutcomeId);
        } catch (IllegalArgumentException e) {
            throw new PlanValidationException(
                    ErrorCode.MISSING_RCDO_OR_REASON,
                    "outcomeId must be a UUID string",
                    List.of(Map.of("field", "outcomeId", "provided", rawOutcomeId)));
        }
    }

    public record ApplyTeamPlanSuggestionResult(
            String status,
            LocalDate weekStart,
            List<MemberDraftApplyResult> members) {
    }

    public record MemberDraftApplyResult(
            String userId,
            String displayName,
            String planId,
            boolean createdPlan,
            List<WeeklyCommitResponse> appliedCommits) {
    }
}
