package com.weekly.issues.service;

import com.weekly.assignment.domain.AssignmentCompletionStatus;
import com.weekly.assignment.domain.WeeklyAssignmentActualEntity;
import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentActualRepository;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueActivityType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.dto.CreateWeeklyAssignmentRequest;
import com.weekly.issues.dto.IssueResponse;
import com.weekly.issues.dto.WeeklyAssignmentResponse;
import com.weekly.issues.events.AssignmentReconciledEvent;
import com.weekly.issues.events.IssueUpdatedEvent;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.team.repository.TeamMemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing weekly assignments as the primary work unit (Phase 6).
 *
 * <p>This service orchestrates:
 * <ul>
 *   <li>Adding issues to a week's plan (creates {@link WeeklyAssignmentEntity} and
 *       dual-writes a legacy {@link WeeklyCommitEntity} for backward compat).</li>
 *   <li>Removing assignments from a plan (also removes the mirrored legacy commit).</li>
 *   <li>Carrying assignments forward from one week's plan to another, preserving the
 *       issue and logging a CARRIED_FORWARD activity.</li>
 *   <li>Releasing assignments back to the backlog, reverting issue status and
 *       optionally clearing the assignee.</li>
 *   <li>Reconciling issue statuses based on assignment actuals after a plan is
 *       submitted for reconciliation (DONE/PARTIALLY/NOT_DONE/DROPPED transitions).</li>
 * </ul>
 *
 * <p>This service lives in the {@code issues} package (rather than {@code assignment})
 * to prevent a cyclic dependency: {@code issues} already depends on {@code assignment}
 * (e.g. {@link IssueService} uses {@code WeeklyAssignmentRepository}), so placing
 * this service in {@code assignment} and having it import from {@code issues} would
 * create an {@code assignment} ↔ {@code issues} cycle that violates the module
 * boundary rules.
 */
@Service
public class AssignmentService {

    /** After this many weeks, a DONE issue is automatically archived. */
    public static final int ARCHIVE_AFTER_WEEKS = 8;

    private final WeeklyAssignmentRepository assignmentRepository;
    private final WeeklyAssignmentActualRepository assignmentActualRepository;
    private final IssueRepository issueRepository;
    private final IssueActivityRepository activityRepository;
    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final TeamMemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AssignmentService(
            WeeklyAssignmentRepository assignmentRepository,
            WeeklyAssignmentActualRepository assignmentActualRepository,
            IssueRepository issueRepository,
            IssueActivityRepository activityRepository,
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            TeamMemberRepository memberRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentActualRepository = assignmentActualRepository;
        this.issueRepository = issueRepository;
        this.activityRepository = activityRepository;
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    // ── Add to week plan ─────────────────────────────────────

    /**
     * Adds an issue to the user's plan for a given week.
     *
     * <p>Creates a {@link WeeklyAssignmentEntity} and dual-writes a legacy
     * {@link WeeklyCommitEntity} for backward compatibility. The crosswalk columns
     * ({@code source_issue_id} on the commit, {@code legacy_commit_id} on the assignment)
     * are populated in the same transaction.
     *
     * <p>If the issue is OPEN, transitions it to IN_PROGRESS. Logs a
     * COMMITTED_TO_WEEK activity.
     *
     * @param orgId     the org ID from auth context
     * @param userId    the authenticated user (must own the plan)
     * @param weekStart the ISO week start date (e.g. "2026-03-23")
     * @param request   the assignment request
     * @return the created assignment
     */
    @Transactional
    public WeeklyAssignmentResponse addToWeekPlan(
            UUID orgId, UUID userId, String weekStart, CreateWeeklyAssignmentRequest request
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        WeeklyPlanEntity plan = requireOwnedPlan(orgId, userId, weekStartDate);
        requirePlanInDraft(plan, "Issues can only be added while the weekly plan is in DRAFT");
        IssueEntity issue = requireIssue(orgId, request.issueId());
        requireTeamMember(issue.getTeamId(), userId);

        // Prevent duplicate assignment for same plan + issue
        assignmentRepository.findByWeeklyPlanIdAndIssueId(plan.getId(), issue.getId())
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Issue already assigned to this week's plan: " + issue.getId()
                    );
                });

        // Create assignment
        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), orgId, plan.getId(), issue.getId()
        );
        if (request.chessPriorityOverride() != null) {
            assignment.setChessPriorityOverride(request.chessPriorityOverride().toUpperCase());
        }
        if (request.expectedResult() != null) {
            assignment.setExpectedResult(request.expectedResult());
        }
        if (request.confidence() != null) {
            assignment.setConfidence(BigDecimal.valueOf(request.confidence()));
        }

        // Dual-write: create a mirrored legacy commit for backward compat
        WeeklyCommitEntity commit = new WeeklyCommitEntity(
                UUID.randomUUID(), orgId, plan.getId(), issue.getTitle()
        );
        if (issue.getDescription() != null && !issue.getDescription().isBlank()) {
            commit.setDescription(issue.getDescription());
        }
        if (issue.getChessPriority() != null) {
            commit.setChessPriority(issue.getChessPriority());
        }
        if (issue.getOutcomeId() != null) {
            commit.setOutcomeId(issue.getOutcomeId());
        }
        if (issue.getNonStrategicReason() != null) {
            commit.setNonStrategicReason(issue.getNonStrategicReason());
        }
        if (issue.getEstimatedHours() != null) {
            commit.setEstimatedHours(issue.getEstimatedHours());
        }
        if (request.expectedResult() != null) {
            commit.setExpectedResult(request.expectedResult());
        }
        if (request.confidence() != null) {
            commit.setConfidence(BigDecimal.valueOf(request.confidence()));
        }
        // Crosswalk: commit.source_issue_id → issue
        commit.setSourceIssueId(issue.getId());
        commitRepository.save(commit);

        // Crosswalk: assignment.legacy_commit_id → commit (saved after commit FK is satisfied)
        assignment.setLegacyCommitId(commit.getId());
        assignmentRepository.save(assignment);

        // Transition OPEN → IN_PROGRESS
        if (issue.getStatus() == IssueStatus.OPEN) {
            logActivity(issue.getId(), orgId, userId, IssueActivityType.STATUS_CHANGE,
                    IssueStatus.OPEN.name(), IssueStatus.IN_PROGRESS.name());
            issue.setStatus(IssueStatus.IN_PROGRESS);
        }
        logActivity(issue.getId(), orgId, userId, IssueActivityType.COMMITTED_TO_WEEK,
                null, plan.getId().toString());
        issueRepository.save(issue);
        eventPublisher.publishEvent(new IssueUpdatedEvent(this, orgId, issue.getId()));

        return WeeklyAssignmentResponse.from(assignment);
    }

    // ── Remove from week plan ────────────────────────────────

    /**
     * Removes an assignment from the plan.
     *
     * <p>Also removes the mirrored legacy commit (if the crosswalk is intact).
     * Reverts the issue to OPEN if no other assignments remain, and logs a
     * RELEASED_TO_BACKLOG activity.
     *
     * @param orgId        the org ID
     * @param userId       the authenticated user (must own the plan)
     * @param assignmentId the assignment to remove
     */
    @Transactional
    public void removeFromWeekPlan(UUID orgId, UUID userId, String weekStart, UUID assignmentId) {
        WeeklyAssignmentEntity assignment = requireAssignment(orgId, assignmentId);
        WeeklyPlanEntity plan = requirePlanOwner(orgId, assignment.getWeeklyPlanId(), userId);
        requireWeekMatch(plan, LocalDate.parse(weekStart));
        requirePlanInDraft(plan, "Assignments can only be removed while the weekly plan is in DRAFT");

        UUID issueId = assignment.getIssueId();

        // Remove mirrored commit via crosswalk
        if (assignment.getLegacyCommitId() != null) {
            commitRepository.findById(assignment.getLegacyCommitId())
                    .ifPresent(commitRepository::delete);
        }

        assignmentRepository.delete(assignment);

        // Revert issue to OPEN if no other assignments remain
        issueRepository.findByOrgIdAndId(orgId, issueId).ifPresent(issue -> {
            List<WeeklyAssignmentEntity> remaining =
                    assignmentRepository.findAllByIssueId(issueId);
            if (remaining.isEmpty() && issue.getStatus() == IssueStatus.IN_PROGRESS) {
                logActivity(issueId, orgId, userId, IssueActivityType.STATUS_CHANGE,
                        IssueStatus.IN_PROGRESS.name(), IssueStatus.OPEN.name());
                issue.setStatus(IssueStatus.OPEN);
            }
            logActivity(issueId, orgId, userId, IssueActivityType.RELEASED_TO_BACKLOG,
                    assignmentId.toString(), null);
            issueRepository.save(issue);
            eventPublisher.publishEvent(new IssueUpdatedEvent(this, orgId, issueId));
        });
    }

    // ── Carry-forward via assignments ────────────────────────

    /**
     * Carries an assignment forward from one week's plan to another.
     *
     * <p>Creates a new {@link WeeklyAssignmentEntity} for the same issue in the target
     * plan, copying metadata from the source assignment. Also dual-writes a legacy
     * commit in the target plan.
     *
     * <p>Logs a CARRIED_FORWARD activity on the issue.
     *
     * @param orgId        the org ID
     * @param actorUserId  the user performing the carry-forward
     * @param issueId      the issue to carry forward
     * @param fromPlanId   the source plan (e.g. current week, must be RECONCILED)
     * @param toPlanId     the target plan (next week's DRAFT plan)
     * @return the newly created assignment
     */
    @Transactional
    public WeeklyAssignmentResponse carryForwardAssignment(
            UUID orgId, UUID actorUserId, UUID issueId, UUID fromPlanId, UUID toPlanId
    ) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);
        WeeklyPlanEntity sourcePlan = requirePlanOwner(orgId, fromPlanId, actorUserId);
        WeeklyPlanEntity targetPlan = requirePlanOwner(orgId, toPlanId, actorUserId);
        requirePlanState(sourcePlan, PlanState.RECONCILED,
                "Assignments can only be carried forward from a RECONCILED weekly plan");
        requirePlanState(targetPlan, PlanState.DRAFT,
                "Assignments can only be carried into a DRAFT weekly plan");

        // Prevent duplicate in target plan
        assignmentRepository.findByWeeklyPlanIdAndIssueId(toPlanId, issueId)
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Issue already assigned to the target plan: " + issueId
                    );
                });

        // Source assignment (optional — may not exist if this is a pure issue carry-forward)
        Optional<WeeklyAssignmentEntity> sourceAssignmentOpt =
                assignmentRepository.findByWeeklyPlanIdAndIssueId(fromPlanId, issueId);

        // Create new assignment in target plan
        WeeklyAssignmentEntity newAssignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), orgId, toPlanId, issueId
        );
        sourceAssignmentOpt.ifPresent(src -> {
            if (src.getChessPriorityOverride() != null) {
                newAssignment.setChessPriorityOverride(src.getChessPriorityOverride());
            }
            if (src.getExpectedResult() != null && !src.getExpectedResult().isBlank()) {
                newAssignment.setExpectedResult(src.getExpectedResult());
            }
            if (src.getConfidence() != null) {
                newAssignment.setConfidence(src.getConfidence());
            }
        });

        // Dual-write: create a mirrored legacy commit in the target plan
        WeeklyCommitEntity newCommit = new WeeklyCommitEntity(
                UUID.randomUUID(), orgId, toPlanId, issue.getTitle()
        );
        if (issue.getDescription() != null && !issue.getDescription().isBlank()) {
            newCommit.setDescription(issue.getDescription());
        }
        if (issue.getChessPriority() != null) {
            newCommit.setChessPriority(issue.getChessPriority());
        }
        if (issue.getOutcomeId() != null) {
            newCommit.setOutcomeId(issue.getOutcomeId());
        }
        if (issue.getEstimatedHours() != null) {
            newCommit.setEstimatedHours(issue.getEstimatedHours());
        }
        if (!newAssignment.getExpectedResult().isBlank()) {
            newCommit.setExpectedResult(newAssignment.getExpectedResult());
        }
        // Set carry-forward lineage: link to source commit if available
        sourceAssignmentOpt.flatMap(src -> src.getLegacyCommitId() != null
                ? Optional.of(src.getLegacyCommitId())
                : Optional.empty()
        ).ifPresent(newCommit::setCarriedFromCommitId);

        newCommit.setSourceIssueId(issueId);
        commitRepository.save(newCommit);

        newAssignment.setLegacyCommitId(newCommit.getId());
        assignmentRepository.save(newAssignment);

        // Ensure issue remains IN_PROGRESS
        if (issue.getStatus() == IssueStatus.OPEN) {
            logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                    IssueStatus.OPEN.name(), IssueStatus.IN_PROGRESS.name());
            issue.setStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(issue);
        }

        logActivity(issueId, orgId, actorUserId, IssueActivityType.CARRIED_FORWARD,
                fromPlanId.toString(), toPlanId.toString());
        eventPublisher.publishEvent(new IssueUpdatedEvent(this, orgId, issueId));

        return WeeklyAssignmentResponse.from(newAssignment);
    }

    // ── Release to backlog ───────────────────────────────────

    /**
     * Releases an assignment back to the backlog.
     *
     * <p>Removes the assignment (and its mirrored commit), reverts the issue to OPEN,
     * and optionally clears the assignee. Logs a RELEASED_TO_BACKLOG activity.
     *
     * @param orgId         the org ID
     * @param actorUserId   the authenticated user (must own the plan)
     * @param assignmentId  the assignment to release
     * @param clearAssignee if {@code true}, clears {@code issue.assigneeUserId}
     * @return the updated issue
     */
    @Transactional
    public IssueResponse releaseToBacklog(
            UUID orgId, UUID actorUserId, UUID assignmentId, boolean clearAssignee
    ) {
        WeeklyAssignmentEntity assignment = requireAssignment(orgId, assignmentId);
        WeeklyPlanEntity plan = requirePlanOwner(orgId, assignment.getWeeklyPlanId(), actorUserId);
        requirePlanInDraft(plan, "Assignments can only be released while the weekly plan is in DRAFT");

        UUID issueId = assignment.getIssueId();
        IssueEntity issue = issueRepository.findByOrgIdAndId(orgId, issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));

        // Remove mirrored commit
        if (assignment.getLegacyCommitId() != null) {
            commitRepository.findById(assignment.getLegacyCommitId())
                    .ifPresent(commitRepository::delete);
        }

        assignmentRepository.delete(assignment);

        // Revert to OPEN if no other assignments remain
        List<WeeklyAssignmentEntity> remaining = assignmentRepository.findAllByIssueId(issueId);
        if (remaining.isEmpty()) {
            String oldStatus = issue.getStatus().name();
            issue.setStatus(IssueStatus.OPEN);
            if (!oldStatus.equals(IssueStatus.OPEN.name())) {
                logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                        oldStatus, IssueStatus.OPEN.name());
            }
            if (clearAssignee && issue.getAssigneeUserId() != null) {
                logActivity(issueId, orgId, actorUserId, IssueActivityType.ASSIGNMENT_CHANGE,
                        issue.getAssigneeUserId().toString(), null);
                issue.setAssigneeUserId(null);
            }
        }

        logActivity(issueId, orgId, actorUserId, IssueActivityType.RELEASED_TO_BACKLOG,
                assignmentId.toString(), null);
        issueRepository.save(issue);
        eventPublisher.publishEvent(new IssueUpdatedEvent(this, orgId, issueId));

        return IssueResponse.from(issue);
    }

    // ── Reconciliation ───────────────────────────────────────

    /**
     * Applies assignment-based reconciliation status transitions to issues.
     *
     * <p>Called by {@link AssignmentReconciliationListener} after the
     * {@link com.weekly.plan.service.ReconciliationSubmittedEvent} is received. For each
     * assignment in the plan that has a corresponding actual, the issue status is updated:
     *
     * <ul>
     *   <li><b>DONE</b> → set issue to DONE; if the plan's week started 8+ weeks ago,
     *       also archive the issue (auto-archive after quiet period).</li>
     *   <li><b>PARTIALLY / NOT_DONE</b> → keep issue as IN_PROGRESS so it remains
     *       available for carry-forward or re-planning.</li>
     *   <li><b>DROPPED</b> → revert issue to OPEN and clear the assignee (the work
     *       was explicitly dropped and returns to the un-assigned backlog).</li>
     * </ul>
     *
     * <p>Activities are logged for every status change to maintain a full audit trail.
     *
     * @param orgId       the org ID
     * @param planId      the plan being reconciled
     * @param actorUserId the user submitting reconciliation
     */
    @Transactional
    public void reconcileAssignmentStatus(UUID orgId, UUID planId, UUID actorUserId) {
        WeeklyPlanEntity plan = planRepository.findByOrgIdAndId(orgId, planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));

        LocalDate planWeekStart = plan.getWeekStartDate();
        LocalDate archiveThreshold = LocalDate.now().minusWeeks(ARCHIVE_AFTER_WEEKS);

        List<WeeklyAssignmentEntity> assignments =
                assignmentRepository.findAllByOrgIdAndWeeklyPlanId(orgId, planId);

        List<UUID> reconciledIssueIds = new ArrayList<>();
        for (WeeklyAssignmentEntity assignment : assignments) {
            Optional<WeeklyAssignmentActualEntity> actualOpt =
                    assignmentActualRepository.findByAssignmentId(assignment.getId());

            if (actualOpt.isEmpty()) {
                continue; // no actual recorded — skip (commit path may handle it)
            }

            WeeklyAssignmentActualEntity actual = actualOpt.get();
            issueRepository.findByOrgIdAndId(orgId, assignment.getIssueId()).ifPresent(issue -> {
                applyReconciliationTransition(
                        issue, actual.getCompletionStatus(),
                        planWeekStart, archiveThreshold, planId, actorUserId
                );
                reconciledIssueIds.add(issue.getId());
            });
        }

        if (!reconciledIssueIds.isEmpty()) {
            eventPublisher.publishEvent(
                    new AssignmentReconciledEvent(this, orgId, planId, reconciledIssueIds));
        }
    }

    // ── Private helpers ──────────────────────────────────────

    private void applyReconciliationTransition(
            IssueEntity issue,
            AssignmentCompletionStatus completionStatus,
            LocalDate planWeekStart,
            LocalDate archiveThreshold,
            UUID planId,
            UUID actorUserId
    ) {
        UUID issueId = issue.getId();
        UUID orgId = issue.getOrgId();

        switch (completionStatus) {
            case DONE -> {
                if (issue.getStatus() != IssueStatus.DONE
                        && issue.getStatus() != IssueStatus.ARCHIVED) {
                    logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                            issue.getStatus().name(), IssueStatus.DONE.name());
                    issue.setStatus(IssueStatus.DONE);
                }
                // Auto-archive if the plan week is old enough
                if (!planWeekStart.isAfter(archiveThreshold)
                        && issue.getStatus() != IssueStatus.ARCHIVED) {
                    logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                            issue.getStatus().name(), IssueStatus.ARCHIVED.name());
                    issue.archive();
                }
                issueRepository.save(issue);
            }
            case PARTIALLY, NOT_DONE -> {
                // Keep IN_PROGRESS so it's available for carry-forward.
                // OPEN issues should also move back to IN_PROGRESS because the assignment
                // is still active after reconciliation.
                if (issue.getStatus() == IssueStatus.DONE || issue.getStatus() == IssueStatus.OPEN) {
                    logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                            issue.getStatus().name(), IssueStatus.IN_PROGRESS.name());
                    issue.setStatus(IssueStatus.IN_PROGRESS);
                    issueRepository.save(issue);
                }
                // If already IN_PROGRESS, no change needed.
            }
            case DROPPED -> {
                String oldStatus = issue.getStatus().name();
                logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                        oldStatus, IssueStatus.OPEN.name());
                issue.setStatus(IssueStatus.OPEN);
                if (issue.getAssigneeUserId() != null) {
                    logActivity(issueId, orgId, actorUserId, IssueActivityType.ASSIGNMENT_CHANGE,
                            issue.getAssigneeUserId().toString(), null);
                    issue.setAssigneeUserId(null);
                }
                issueRepository.save(issue);
            }
        }
    }

    private WeeklyAssignmentEntity requireAssignment(UUID orgId, UUID assignmentId) {
        return assignmentRepository.findByOrgIdAndId(orgId, assignmentId)
                .orElseThrow(() -> new IssueNotFoundException(assignmentId));
    }

    private IssueEntity requireIssue(UUID orgId, UUID issueId) {
        return issueRepository.findByOrgIdAndId(orgId, issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));
    }

    private void requireTeamMember(UUID teamId, UUID userId) {
        if (!memberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new IssueAccessDeniedException(
                    "User is not a member of the team that owns this issue"
            );
        }
    }

    private WeeklyPlanEntity requireOwnedPlan(UUID orgId, UUID userId, LocalDate weekStart) {
        return planRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, userId, weekStart)
                .orElseThrow(() -> new IllegalStateException(
                        "No weekly plan found for week " + weekStart
                                + ". Create a plan first."
                ));
    }

    private WeeklyPlanEntity requirePlanOwner(UUID orgId, UUID planId, UUID userId) {
        return planRepository.findByOrgIdAndId(orgId, planId)
                .filter(plan -> plan.getOwnerUserId().equals(userId))
                .orElseThrow(() -> new IssueAccessDeniedException(
                        "User does not own the target weekly plan"
                ));
    }

    private void requirePlanInDraft(WeeklyPlanEntity plan, String message) {
        requirePlanState(plan, PlanState.DRAFT, message);
    }

    private void requirePlanState(WeeklyPlanEntity plan, PlanState expectedState, String message) {
        if (plan.getState() != expectedState) {
            throw new IllegalStateException(message + ": " + plan.getState());
        }
    }

    private void requireWeekMatch(WeeklyPlanEntity plan, LocalDate expectedWeekStart) {
        if (!plan.getWeekStartDate().equals(expectedWeekStart)) {
            throw new IllegalArgumentException(
                    "Assignment does not belong to weekly plan for week " + expectedWeekStart
            );
        }
    }

    private IssueActivityEntity logActivity(
            UUID issueId, UUID orgId, UUID actorUserId,
            IssueActivityType type, String oldValue, String newValue
    ) {
        UUID actor = actorUserId != null ? actorUserId : orgId;
        IssueActivityEntity activity = new IssueActivityEntity(
                UUID.randomUUID(), orgId, issueId, actor, type
        );
        if (oldValue != null || newValue != null) {
            activity.withChange(oldValue, newValue);
        }
        return activityRepository.save(activity);
    }
}
