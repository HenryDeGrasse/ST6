package com.weekly.issues.service;

import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.audit.AuditService;
import com.weekly.issues.domain.EffortType;
import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueActivityType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.dto.AddCommentRequest;
import com.weekly.issues.dto.AssignIssueRequest;
import com.weekly.issues.dto.CommitIssueToWeekRequest;
import com.weekly.issues.dto.CreateIssueRequest;
import com.weekly.issues.dto.CreateWeeklyAssignmentRequest;
import com.weekly.issues.dto.IssueActivityResponse;
import com.weekly.issues.dto.IssueDetailResponse;
import com.weekly.issues.dto.IssueListResponse;
import com.weekly.issues.dto.IssueResponse;
import com.weekly.issues.dto.LogTimeEntryRequest;
import com.weekly.issues.dto.ReleaseIssueRequest;
import com.weekly.issues.dto.UpdateIssueRequest;
import com.weekly.issues.dto.WeeklyAssignmentResponse;
import com.weekly.issues.dto.WeeklyAssignmentsResponse;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamMemberRepository;
import com.weekly.team.repository.TeamRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for backlog issue lifecycle operations (Phase 6).
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Issue key generation is atomic: the service calls
 *       {@link IssueRepository#incrementAndGetIssueSequence(UUID)} inside a
 *       transaction, which acquires a row-level lock on the team row via
 *       {@code UPDATE … RETURNING}. This prevents duplicate keys under
 *       concurrent creation.</li>
 *   <li>All mutations log an {@link IssueActivityEntity} for a full audit trail.</li>
 *   <li>Archiving is a soft-delete: {@code status = ARCHIVED} + {@code archived_at}.</li>
 * </ul>
 */
@Service
public class IssueService {

    private final IssueRepository issueRepository;
    private final IssueActivityRepository activityRepository;
    private final WeeklyAssignmentRepository assignmentRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final WeeklyPlanRepository planRepository;
    private final AuditService auditService;

    public IssueService(
            IssueRepository issueRepository,
            IssueActivityRepository activityRepository,
            WeeklyAssignmentRepository assignmentRepository,
            TeamRepository teamRepository,
            TeamMemberRepository memberRepository,
            WeeklyPlanRepository planRepository,
            AuditService auditService
    ) {
        this.issueRepository = issueRepository;
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.auditService = auditService;
    }

    // ── Create ───────────────────────────────────────────────

    /**
     * Creates a new backlog issue with an atomically allocated key.
     *
     * <p>The sequence number is incremented with a row-locking UPDATE on the
     * team row, so concurrent creations cannot receive the same key.
     */
    @Transactional
    public IssueResponse createIssue(
            UUID orgId,
            UUID teamId,
            UUID creatorUserId,
            CreateIssueRequest request
    ) {
        TeamEntity team = requireTeam(orgId, teamId);
        requireTeamMember(teamId, creatorUserId);

        // Atomic sequence allocation — row lock via UPDATE … RETURNING
        int seq = issueRepository.incrementAndGetIssueSequence(teamId);
        String issueKey = team.getKeyPrefix() + "-" + seq;

        IssueEntity issue = new IssueEntity(
                UUID.randomUUID(), orgId, teamId, issueKey, seq,
                request.title(), creatorUserId
        );

        applyOptionalFields(issue, request);
        issueRepository.save(issue);

        // Log CREATED activity
        logActivity(issue.getId(), orgId, creatorUserId, IssueActivityType.CREATED, null, null, null, null);

        auditService.record(orgId, creatorUserId, "ISSUE_CREATED", "Issue",
                issue.getId(), null, IssueStatus.OPEN.name(), null, null, null);

        return IssueResponse.from(issue);
    }

    // ── Read ─────────────────────────────────────────────────

    /**
     * Returns issue detail with full activity log.
     */
    @Transactional(readOnly = true)
    public IssueDetailResponse getIssue(UUID orgId, UUID issueId, UUID actorUserId) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);
        List<IssueActivityResponse> activities = activityRepository
                .findAllByIssueIdOrderByCreatedAtAsc(issueId)
                .stream()
                .map(IssueActivityResponse::from)
                .toList();
        return new IssueDetailResponse(IssueResponse.from(issue), activities);
    }

    /**
     * Lists the backlog for a team, optionally filtered by status.
     *
     * @param status null means all non-archived issues
     */
    @Transactional(readOnly = true)
    public IssueListResponse listTeamBacklog(
            UUID orgId,
            UUID teamId,
            UUID actorUserId,
            IssueStatus status,
            int page,
            int size
    ) {
        requireTeam(orgId, teamId);
        requireTeamMember(teamId, actorUserId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<IssueEntity> result = (status != null)
                ? issueRepository.findAllByTeamIdAndStatus(teamId, status, pageable)
                : issueRepository.findAllByTeamIdAndStatusNot(teamId, IssueStatus.ARCHIVED, pageable);

        List<IssueResponse> content = result.getContent().stream()
                .map(IssueResponse::from)
                .toList();
        return new IssueListResponse(content, page, size, result.getTotalElements(),
                result.getTotalPages());
    }

    // ── Update ───────────────────────────────────────────────

    /**
     * Applies patch-style updates to an issue and logs changed fields.
     */
    @Transactional
    public IssueResponse updateIssue(
            UUID orgId, UUID issueId, UUID actorUserId, UpdateIssueRequest request
    ) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);

        if (request.title() != null && !request.title().equals(issue.getTitle())) {
            logActivity(issueId, orgId, actorUserId, IssueActivityType.TITLE_CHANGE,
                    issue.getTitle(), request.title(), null, null);
            issue.setTitle(request.title());
        }
        if (request.description() != null && !request.description().equals(issue.getDescription())) {
            logActivity(issueId, orgId, actorUserId, IssueActivityType.DESCRIPTION_CHANGE,
                    issue.getDescription(), request.description(), null, null);
            issue.setDescription(request.description());
        }
        if (request.effortType() != null) {
            EffortType newType = EffortType.valueOf(request.effortType().toUpperCase());
            String oldVal = issue.getEffortType() != null ? issue.getEffortType().name() : null;
            if (issue.getEffortType() != newType) {
                logActivity(issueId, orgId, actorUserId, IssueActivityType.EFFORT_TYPE_CHANGE,
                        oldVal, newType.name(), null, null);
                issue.setEffortType(newType);
            }
        }
        if (request.estimatedHours() != null) {
            BigDecimal newHours = BigDecimal.valueOf(request.estimatedHours());
            String oldVal = issue.getEstimatedHours() != null
                    ? issue.getEstimatedHours().toPlainString() : null;
            logActivity(issueId, orgId, actorUserId, IssueActivityType.ESTIMATE_CHANGE,
                    oldVal, newHours.toPlainString(), null, null);
            issue.setEstimatedHours(newHours);
        }
        if (request.chessPriority() != null) {
            ChessPriority newPriority = ChessPriority.valueOf(request.chessPriority().toUpperCase());
            String oldVal = issue.getChessPriority() != null
                    ? issue.getChessPriority().name() : null;
            if (issue.getChessPriority() != newPriority) {
                logActivity(issueId, orgId, actorUserId, IssueActivityType.PRIORITY_CHANGE,
                        oldVal, newPriority.name(), null, null);
                issue.setChessPriority(newPriority);
            }
        }
        if (request.outcomeId() != null) {
            String oldVal = issue.getOutcomeId() != null ? issue.getOutcomeId().toString() : null;
            logActivity(issueId, orgId, actorUserId, IssueActivityType.OUTCOME_CHANGE,
                    oldVal, request.outcomeId(), null, null);
            issue.setOutcomeId(UUID.fromString(request.outcomeId()));
        }
        if (request.nonStrategicReason() != null) {
            issue.setNonStrategicReason(request.nonStrategicReason());
        }
        if (request.assigneeUserId() != null) {
            String oldVal = issue.getAssigneeUserId() != null
                    ? issue.getAssigneeUserId().toString() : null;
            logActivity(issueId, orgId, actorUserId, IssueActivityType.ASSIGNMENT_CHANGE,
                    oldVal, request.assigneeUserId(), null, null);
            issue.setAssigneeUserId(UUID.fromString(request.assigneeUserId()));
        }
        if (request.blockedByIssueId() != null) {
            UUID newBlockedByIssueId = UUID.fromString(request.blockedByIssueId());
            String oldVal = issue.getBlockedByIssueId() != null
                    ? issue.getBlockedByIssueId().toString() : null;
            if (!newBlockedByIssueId.equals(issue.getBlockedByIssueId())) {
                logActivity(issueId, orgId, actorUserId, IssueActivityType.BLOCKED,
                        oldVal, request.blockedByIssueId(), null, null);
                issue.setBlockedByIssueId(newBlockedByIssueId);
            }
        }
        if (request.status() != null) {
            IssueStatus newStatus = IssueStatus.valueOf(request.status().toUpperCase());
            if (issue.getStatus() != newStatus) {
                logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                        issue.getStatus().name(), newStatus.name(), null, null);
                issue.setStatus(newStatus);
            }
        }

        issueRepository.save(issue);
        return IssueResponse.from(issue);
    }

    // ── Assign ───────────────────────────────────────────────

    /**
     * Assigns or unassigns an issue. A {@code null} assignee unassigns.
     */
    @Transactional
    public IssueResponse assignIssue(
            UUID orgId, UUID issueId, UUID actorUserId, AssignIssueRequest request
    ) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);

        String oldVal = issue.getAssigneeUserId() != null
                ? issue.getAssigneeUserId().toString() : null;
        String newVal = request.assigneeUserId();
        logActivity(issueId, orgId, actorUserId, IssueActivityType.ASSIGNMENT_CHANGE,
                oldVal, newVal, null, null);

        issue.setAssigneeUserId(newVal != null ? UUID.fromString(newVal) : null);
        issueRepository.save(issue);
        return IssueResponse.from(issue);
    }

    // ── Archive ──────────────────────────────────────────────

    /**
     * Soft-deletes the issue by setting {@code status = ARCHIVED} and recording the timestamp.
     */
    @Transactional
    public IssueResponse archiveIssue(UUID orgId, UUID issueId, UUID actorUserId) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);

        String previousStatus = issue.getStatus().name();
        logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                previousStatus, IssueStatus.ARCHIVED.name(), null, null);
        issue.archive();
        issueRepository.save(issue);

        auditService.record(orgId, actorUserId, "ISSUE_ARCHIVED", "Issue",
                issueId, previousStatus, IssueStatus.ARCHIVED.name(), null, null, null);

        return IssueResponse.from(issue);
    }

    // ── Comment ──────────────────────────────────────────────

    /**
     * Adds a comment activity to the issue.
     */
    @Transactional
    public IssueActivityResponse addComment(
            UUID orgId, UUID issueId, UUID actorUserId, AddCommentRequest request
    ) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);
        IssueActivityEntity activity = logActivity(
                issueId, orgId, actorUserId, IssueActivityType.COMMENT,
                null, null, request.commentText(), null
        );
        return IssueActivityResponse.from(activity);
    }

    // ── Time Entry ───────────────────────────────────────────

    /**
     * Logs time spent on an issue.
     */
    @Transactional
    public IssueActivityResponse logTimeEntry(
            UUID orgId, UUID issueId, UUID actorUserId, LogTimeEntryRequest request
    ) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);
        BigDecimal hours = BigDecimal.valueOf(request.hoursLogged());
        IssueActivityEntity activity = logActivity(
                issueId, orgId, actorUserId, IssueActivityType.TIME_ENTRY,
                null, null, request.note(), hours
        );
        return IssueActivityResponse.from(activity);
    }

    // ── Commit to Week ───────────────────────────────────────

    /**
     * Creates a weekly assignment for the issue in the specified week's plan.
     *
     * <p>If the issue is OPEN, transitions it to IN_PROGRESS.
     * Logs a COMMITTED_TO_WEEK activity.
     */
    @Transactional
    public WeeklyAssignmentResponse commitToWeek(
            UUID orgId, UUID issueId, UUID actorUserId, CommitIssueToWeekRequest request
    ) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);

        LocalDate weekStart = LocalDate.parse(request.weekStart());
        UUID planId = resolveOrCreatePlanId(orgId, actorUserId, weekStart);

        // Prevent duplicate assignment for same plan+issue
        assignmentRepository.findByWeeklyPlanIdAndIssueId(planId, issueId).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Issue already committed to this week's plan: " + issueId
            );
        });

        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), orgId, planId, issueId
        );
        if (request.chessPriorityOverride() != null) {
            assignment.setChessPriorityOverride(
                    request.chessPriorityOverride().toUpperCase()
            );
        }
        if (request.expectedResult() != null) {
            assignment.setExpectedResult(request.expectedResult());
        }
        if (request.confidence() != null) {
            assignment.setConfidence(BigDecimal.valueOf(request.confidence()));
        }
        assignmentRepository.save(assignment);

        // Transition status OPEN → IN_PROGRESS
        if (issue.getStatus() == IssueStatus.OPEN) {
            logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                    IssueStatus.OPEN.name(), IssueStatus.IN_PROGRESS.name(), null, null);
            issue.setStatus(IssueStatus.IN_PROGRESS);
        }
        logActivity(issueId, orgId, actorUserId, IssueActivityType.COMMITTED_TO_WEEK,
                null, planId.toString(), null, null);
        issueRepository.save(issue);

        return WeeklyAssignmentResponse.from(assignment);
    }

    // ── Release to Backlog ───────────────────────────────────

    /**
     * Removes the weekly assignment for the issue, returning it to the backlog.
     *
     * <p>Transitions IN_PROGRESS → OPEN if no other active assignments remain.
     * Logs a RELEASED_TO_BACKLOG activity.
     */
    @Transactional
    public IssueResponse releaseToBacklog(
            UUID orgId, UUID issueId, UUID actorUserId, ReleaseIssueRequest request
    ) {
        IssueEntity issue = requireIssue(orgId, issueId);
        requireTeamMember(issue.getTeamId(), actorUserId);

        requirePlanOwner(orgId, request.weeklyPlanId(), actorUserId);
        WeeklyAssignmentEntity assignment = assignmentRepository
                .findByWeeklyPlanIdAndIssueId(request.weeklyPlanId(), issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));

        assignmentRepository.delete(assignment);

        // Revert to OPEN if no other assignments remain
        List<WeeklyAssignmentEntity> remaining = assignmentRepository.findAllByIssueId(issueId);
        if (remaining.isEmpty() && issue.getStatus() == IssueStatus.IN_PROGRESS) {
            logActivity(issueId, orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                    IssueStatus.IN_PROGRESS.name(), IssueStatus.OPEN.name(), null, null);
            issue.setStatus(IssueStatus.OPEN);
        }
        logActivity(issueId, orgId, actorUserId, IssueActivityType.RELEASED_TO_BACKLOG,
                request.weeklyPlanId().toString(), null, null, null);
        issueRepository.save(issue);

        return IssueResponse.from(issue);
    }

    // ── Weekly Assignments (plan-scoped) ─────────────────────

    /**
     * Lists all assignments for a given weekly plan.
     */
    @Transactional(readOnly = true)
    public WeeklyAssignmentsResponse listPlanAssignments(UUID orgId, UUID planId, UUID actorUserId) {
        requirePlanOwner(orgId, planId, actorUserId);
        List<WeeklyAssignmentResponse> assignments = assignmentRepository
                .findAllByOrgIdAndWeeklyPlanId(orgId, planId)
                .stream()
                .map(WeeklyAssignmentResponse::from)
                .toList();
        return new WeeklyAssignmentsResponse(assignments);
    }

    /**
     * Creates a weekly assignment directly (used by the week-scoped endpoint).
     */
    @Transactional
    public WeeklyAssignmentResponse createWeeklyAssignment(
            UUID orgId, UUID planId, UUID actorUserId, CreateWeeklyAssignmentRequest request
    ) {
        requirePlanOwner(orgId, planId, actorUserId);
        IssueEntity issue = requireIssue(orgId, request.issueId());
        requireTeamMember(issue.getTeamId(), actorUserId);

        assignmentRepository.findByWeeklyPlanIdAndIssueId(planId, request.issueId())
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Issue already assigned to this plan: " + request.issueId()
                    );
                });

        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), orgId, planId, request.issueId()
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
        assignmentRepository.save(assignment);

        if (issue.getStatus() == IssueStatus.OPEN) {
            logActivity(issue.getId(), orgId, actorUserId, IssueActivityType.STATUS_CHANGE,
                    IssueStatus.OPEN.name(), IssueStatus.IN_PROGRESS.name(), null, null);
            issue.setStatus(IssueStatus.IN_PROGRESS);
            issueRepository.save(issue);
        }

        logActivity(issue.getId(), orgId, actorUserId, IssueActivityType.COMMITTED_TO_WEEK,
                null, planId.toString(), null, null);

        return WeeklyAssignmentResponse.from(assignment);
    }

    /**
     * Creates a weekly assignment for the user's plan in the given week.
     *
     * <p>Resolves the plan by orgId + userId + weekStart; throws if no plan exists.
     */
    @Transactional
    public WeeklyAssignmentResponse createWeeklyAssignmentForWeek(
            UUID orgId, UUID userId, String weekStart, CreateWeeklyAssignmentRequest request
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        UUID planId = resolveOrCreatePlanId(orgId, userId, weekStartDate);
        return createWeeklyAssignment(orgId, planId, userId, request);
    }

    /**
     * Removes a weekly assignment by ID.
     */
    @Transactional
    public void removeWeeklyAssignment(UUID orgId, UUID assignmentId, UUID actorUserId) {
        WeeklyAssignmentEntity assignment = assignmentRepository.findByOrgIdAndId(orgId, assignmentId)
                .orElseThrow(() -> new IssueNotFoundException(assignmentId));
        requirePlanOwner(orgId, assignment.getWeeklyPlanId(), actorUserId);

        IssueEntity issue = issueRepository.findByOrgIdAndId(orgId, assignment.getIssueId())
                .orElse(null);

        assignmentRepository.delete(assignment);

        if (issue != null) {
            List<WeeklyAssignmentEntity> remaining =
                    assignmentRepository.findAllByIssueId(issue.getId());
            if (remaining.isEmpty() && issue.getStatus() == IssueStatus.IN_PROGRESS) {
                issue.setStatus(IssueStatus.OPEN);
                issueRepository.save(issue);
            }
            logActivity(issue.getId(), orgId, actorUserId, IssueActivityType.RELEASED_TO_BACKLOG,
                    assignmentId.toString(), null, null, null);
        }
    }

    // ── Private helpers ──────────────────────────────────────

    private TeamEntity requireTeam(UUID orgId, UUID teamId) {
        return teamRepository.findByOrgIdAndId(orgId, teamId)
                .orElseThrow(() -> new IssueNotFoundException(teamId));
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

    private void requirePlanOwner(UUID orgId, UUID planId, UUID userId) {
        planRepository.findByOrgIdAndId(orgId, planId)
                .filter(plan -> plan.getOwnerUserId().equals(userId))
                .orElseThrow(() -> new IssueAccessDeniedException(
                        "User does not own the target weekly plan"
                ));
    }

    /** Finds an existing plan for the given user+week, or throws if none exists. */
    private UUID resolveOrCreatePlanId(UUID orgId, UUID userId, LocalDate weekStart) {
        return planRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, userId, weekStart)
                .map(p -> p.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No weekly plan found for week " + weekStart
                                + ". Create a plan first."
                ));
    }

    private void applyOptionalFields(IssueEntity issue, CreateIssueRequest request) {
        if (request.description() != null) {
            issue.setDescription(request.description());
        }
        if (request.effortType() != null) {
            issue.setEffortType(EffortType.valueOf(request.effortType().toUpperCase()));
        }
        if (request.estimatedHours() != null) {
            issue.setEstimatedHours(BigDecimal.valueOf(request.estimatedHours()));
        }
        if (request.chessPriority() != null) {
            issue.setChessPriority(ChessPriority.valueOf(request.chessPriority().toUpperCase()));
        }
        if (request.outcomeId() != null) {
            issue.setOutcomeId(UUID.fromString(request.outcomeId()));
        }
        if (request.nonStrategicReason() != null) {
            issue.setNonStrategicReason(request.nonStrategicReason());
        }
        if (request.assigneeUserId() != null) {
            issue.setAssigneeUserId(UUID.fromString(request.assigneeUserId()));
        }
        if (request.blockedByIssueId() != null) {
            issue.setBlockedByIssueId(UUID.fromString(request.blockedByIssueId()));
        }
    }

    private IssueActivityEntity logActivity(
            UUID issueId,
            UUID orgId,
            UUID actorUserId,
            IssueActivityType type,
            String oldValue,
            String newValue,
            String commentText,
            BigDecimal hoursLogged
    ) {
        IssueActivityEntity activity = new IssueActivityEntity(
                UUID.randomUUID(), orgId, issueId, actorUserId, type
        );
        if (oldValue != null || newValue != null) {
            activity.withChange(oldValue, newValue);
        }
        if (commentText != null) {
            activity.withComment(commentText);
        }
        if (hoursLogged != null) {
            activity.withHours(hoursLogged);
        }
        return activityRepository.save(activity);
    }
}
