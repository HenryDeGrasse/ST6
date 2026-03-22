package com.weekly.compatibility.dualwrite;

import com.weekly.assignment.domain.AssignmentCompletionStatus;
import com.weekly.assignment.domain.WeeklyAssignmentActualEntity;
import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentActualRepository;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.issues.domain.EffortType;
import com.weekly.issues.domain.IssueActivityEntity;
import com.weekly.issues.domain.IssueActivityType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.domain.TeamMemberEntity;
import com.weekly.team.domain.TeamRole;
import com.weekly.team.repository.TeamMemberRepository;
import com.weekly.team.repository.TeamRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dual-write compatibility layer for the Phase 6 two-phase migration (PRD §13.8).
 *
 * <p>Every mutation that writes to the legacy {@code weekly_commits} / {@code weekly_commit_actuals}
 * tables also writes the equivalent data to the new {@code issues} / {@code weekly_assignments} /
 * {@code weekly_assignment_actuals} tables within the <em>same</em> transaction.
 *
 * <p>Crosswalk invariants maintained by every dual-write operation:
 * <ul>
 *   <li>{@code weekly_commits.source_issue_id} → the UUID of the corresponding issue.</li>
 *   <li>{@code weekly_assignments.legacy_commit_id} → the UUID of the original commit.</li>
 * </ul>
 *
 * <p>Lookup helpers ({@link #findIssueByCommitId}, {@link #findAssignmentByCommitId}) allow
 * both code paths to navigate between the old and new models without resorting to title matching.
 */
@Service
public class DualWriteService {

    private static final Logger LOG = LoggerFactory.getLogger(DualWriteService.class);
    private static final String DEFAULT_TEAM_NAME = "General";
    private static final String DEFAULT_TEAM_PREFIX = "GEN";

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final IssueRepository issueRepository;
    private final IssueActivityRepository activityRepository;
    private final WeeklyAssignmentRepository assignmentRepository;
    private final WeeklyAssignmentActualRepository assignmentActualRepository;
    private final WeeklyCommitRepository commitRepository;

    public DualWriteService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            IssueRepository issueRepository,
            IssueActivityRepository activityRepository,
            WeeklyAssignmentRepository assignmentRepository,
            WeeklyAssignmentActualRepository assignmentActualRepository,
            WeeklyCommitRepository commitRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.issueRepository = issueRepository;
        this.activityRepository = activityRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentActualRepository = assignmentActualRepository;
        this.commitRepository = commitRepository;
    }

    // ── Crosswalk lookups ────────────────────────────────────

    /**
     * Finds the issue created for a given commit via the {@code source_issue_id} crosswalk.
     *
     * @param commit the source commit (must have {@code source_issue_id} populated)
     * @return the corresponding issue, or empty if the crosswalk is not populated
     */
    public Optional<IssueEntity> findIssueByCommit(WeeklyCommitEntity commit) {
        if (commit.getSourceIssueId() == null) {
            return Optional.empty();
        }
        return issueRepository.findById(commit.getSourceIssueId());
    }

    /**
     * Finds the issue for a legacy commit ID via {@code weekly_commits.source_issue_id}.
     *
     * @param commitId the legacy commit UUID
     * @return the corresponding issue, or empty if either side of the crosswalk is missing
     */
    public Optional<IssueEntity> findIssueByCommitId(UUID commitId) {
        return commitRepository.findById(commitId).flatMap(this::findIssueByCommit);
    }

    /**
     * Finds the weekly assignment for a commit via the {@code legacy_commit_id} crosswalk.
     *
     * @param commitId the commit UUID to look up
     * @return the corresponding assignment, or empty if none exists
     */
    public Optional<WeeklyAssignmentEntity> findAssignmentByCommitId(UUID commitId) {
        return assignmentRepository.findByLegacyCommitId(commitId);
    }

    /**
     * Resolves the legacy commit for an assignment via
     * {@code weekly_assignments.legacy_commit_id -> weekly_commits.id} and verifies
     * that the reverse crosswalk still points back to the same issue.
     *
     * @param assignmentId the assignment UUID
     * @return the corresponding commit when the bidirectional crosswalk is intact
     */
    public Optional<WeeklyCommitEntity> findCommitByAssignmentId(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .flatMap(assignment -> {
                    if (assignment.getLegacyCommitId() == null) {
                        return Optional.empty();
                    }
                    return commitRepository.findById(assignment.getLegacyCommitId())
                            .filter(commit -> assignment.getOrgId().equals(commit.getOrgId()))
                            .filter(commit -> assignment.getIssueId().equals(commit.getSourceIssueId()));
                });
    }

    // ── Commit create dual-write ─────────────────────────────

    /**
     * Creates an issue for a soon-to-be-saved commit and returns the issue ID.
     *
     * <p>Call this <em>before</em> {@code commitRepository.save(commit)} so that
     * {@code commit.source_issue_id} can be set in the same save, avoiding a
     * second save that would increment the JPA {@code @Version} counter.
     *
     * <p>After calling this method:
     * <ol>
     *   <li>Set {@code commit.setSourceIssueId(issueId)}.</li>
     *   <li>Call {@code commitRepository.save(commit)} (once).</li>
     *   <li>Call {@link #createAssignmentForCommit(UUID, UUID, WeeklyCommitEntity, UUID)}.</li>
     * </ol>
     *
     * @param commit  the not-yet-saved commit (read for title/category/etc.)
     * @param orgId   the org ID
     * @param userId  the creating user
     * @return the newly-created issue ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID createIssueForCommit(
            WeeklyCommitEntity commit, UUID orgId, UUID userId
    ) {
        TeamEntity team = findOrCreateDefaultTeam(orgId, userId);
        return createIssueFromCommit(commit, team, orgId, userId).getId();
    }

    /**
     * Creates the weekly assignment after the commit has been saved.
     *
     * <p>Must be called <em>after</em> {@code commitRepository.save(commit)} so the
     * {@code legacy_commit_id} FK constraint is satisfied.
     *
     * @param issueId the issue ID returned by {@link #createIssueForCommit}
     * @param planId the plan to assign to
     * @param commit the already-saved commit
     * @param orgId  the org ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createAssignmentForCommit(
            UUID issueId, UUID planId, WeeklyCommitEntity commit, UUID orgId
    ) {
        WeeklyAssignmentEntity assignment = createAssignment(issueId, planId, commit, orgId);
        LOG.debug("DualWrite: created assignment {} for commit {} / issue {}",
                assignment.getId(), commit.getId(), issueId);
    }

    // ── Commit update dual-write ─────────────────────────────

    /**
     * Propagates a commit update to the corresponding issue (if the crosswalk is set).
     *
     * <p>Updates title, description, effort type, estimated hours, chess priority,
     * and outcome when the corresponding commit fields change.
     *
     * @param commit the already-updated commit entity
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCommitUpdated(WeeklyCommitEntity commit) {
        if (commit.getSourceIssueId() == null) {
            return;
        }
        issueRepository.findById(commit.getSourceIssueId()).ifPresent(issue -> {
            issue.setTitle(commit.getTitle());
            if (commit.getDescription() != null) {
                issue.setDescription(commit.getDescription());
            }
            if (commit.getChessPriority() != null) {
                issue.setChessPriority(commit.getChessPriority());
            }
            if (commit.getOutcomeId() != null) {
                issue.setOutcomeId(commit.getOutcomeId());
            }
            if (commit.getNonStrategicReason() != null) {
                issue.setNonStrategicReason(commit.getNonStrategicReason());
            }
            if (commit.getEstimatedHours() != null) {
                issue.setEstimatedHours(commit.getEstimatedHours());
            }
            issue.setEffortType(categoryToEffortType(commit.getCategory()));
            issueRepository.save(issue);
        });
    }

    // ── Commit delete dual-write ─────────────────────────────

    /**
     * Archives the corresponding issue when a commit is deleted (soft-delete pattern).
     *
     * <p>If the issue has other assignments (from other weeks), only the assignment
     * for this specific plan is removed, and the issue remains active.
     *
     * @param commit  the commit being deleted
     * @param actorUserId the user performing the delete
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCommitDeleted(WeeklyCommitEntity commit, UUID actorUserId) {
        if (commit.getSourceIssueId() == null) {
            return;
        }
        // Remove the mirrored assignment via the commit→assignment crosswalk
        findAssignmentByCommitId(commit.getId()).ifPresent(assignmentRepository::delete);

        // Archive the issue if it has no other assignments
        issueRepository.findById(commit.getSourceIssueId()).ifPresent(issue -> {
            boolean hasOtherAssignments = !assignmentRepository
                    .findAllByIssueId(issue.getId()).isEmpty();
            if (!hasOtherAssignments) {
                logActivity(issue.getId(), issue.getOrgId(), actorUserId,
                        IssueActivityType.STATUS_CHANGE,
                        issue.getStatus().name(), IssueStatus.ARCHIVED.name());
                issue.archive();
                issueRepository.save(issue);
            }
        });
    }

    // ── Carry-forward dual-write ─────────────────────────────

    /**
     * Sets the carry-forward crosswalk on the clone commit BEFORE it is saved.
     *
     * <p>Call this before {@code commitRepository.save(cloneCommit)} to populate
     * {@code source_issue_id} in the same write without incrementing the version.
     *
     * @param cloneCommit  the not-yet-saved clone commit
     * @param sourceCommit the original commit being carried forward
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareCarryForward(
            WeeklyCommitEntity cloneCommit, WeeklyCommitEntity sourceCommit
    ) {
        if (sourceCommit.getSourceIssueId() != null) {
            cloneCommit.setSourceIssueId(sourceCommit.getSourceIssueId());
        }
    }

    /**
     * Creates the new weekly assignment for a carried-forward commit AFTER it is saved.
     *
     * <p>Must be called after {@code commitRepository.save(cloneCommit)}.
     *
     * @param cloneCommit  the already-saved clone commit
     * @param sourceCommit the original commit being carried forward
     * @param newPlanId    the plan for the next week
     * @param orgId        the org ID
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void finalizeCarryForward(
            WeeklyCommitEntity cloneCommit,
            WeeklyCommitEntity sourceCommit,
            UUID newPlanId,
            UUID orgId
    ) {
        if (sourceCommit.getSourceIssueId() == null) {
            return;
        }
        UUID issueId = sourceCommit.getSourceIssueId();
        issueRepository.findById(issueId).ifPresent(issue -> {
            WeeklyAssignmentEntity newAssignment = new WeeklyAssignmentEntity(
                    UUID.randomUUID(), orgId, newPlanId, issueId
            );
            newAssignment.setExpectedResult(
                    cloneCommit.getExpectedResult() != null ? cloneCommit.getExpectedResult() : "");
            newAssignment.setConfidence(cloneCommit.getConfidence());
            if (cloneCommit.getChessPriority() != null) {
                newAssignment.setChessPriorityOverride(cloneCommit.getChessPriority().name());
            }
            newAssignment.setLegacyCommitId(cloneCommit.getId());
            newAssignment.setTagsFromArray(cloneCommit.getTags());
            assignmentRepository.save(newAssignment);

            logActivity(issueId, orgId, null,
                    IssueActivityType.CARRIED_FORWARD,
                    sourceCommit.getWeeklyPlanId().toString(),
                    newPlanId.toString());
        });
    }

    // ── Lock dual-write ──────────────────────────────────────

    /**
     * Populates {@code chess_priority_override} on the weekly assignment when the plan is locked.
     *
     * @param commit the commit being locked (used to look up its assignment)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCommitLocked(WeeklyCommitEntity commit) {
        if (commit.getSourceIssueId() == null || commit.getChessPriority() == null) {
            return;
        }
        findAssignmentByCommitId(commit.getId()).ifPresent(assignment -> {
            assignment.setChessPriorityOverride(commit.getChessPriority().name());
            assignmentRepository.save(assignment);
        });
    }

    // ── Actual dual-write ────────────────────────────────────

    /**
     * Creates or updates the corresponding {@link WeeklyAssignmentActualEntity} when
     * a commit actual is written.
     *
     * <p>Looks up the assignment via the commit's {@code legacy_commit_id} crosswalk.
     *
     * @param commit  the commit whose actual was just saved
     * @param actual  the saved commit actual
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onActualWritten(WeeklyCommitEntity commit, WeeklyCommitActualEntity actual) {
        if (commit.getSourceIssueId() == null) {
            return;
        }
        findAssignmentByCommitId(commit.getId()).ifPresent(assignment -> {
            WeeklyAssignmentActualEntity assignmentActual =
                    assignmentActualRepository.findByAssignmentId(assignment.getId())
                            .orElseGet(() -> new WeeklyAssignmentActualEntity(
                                    assignment.getId(), assignment.getOrgId(),
                                    AssignmentCompletionStatus.NOT_DONE));
            assignmentActual.setActualResult(
                    actual.getActualResult() != null ? actual.getActualResult() : "");
            assignmentActual.setCompletionStatus(
                    toAssignmentStatus(actual.getCompletionStatus()));
            assignmentActual.setDeltaReason(actual.getDeltaReason());
            BigDecimal hours = actual.getActualHours() != null ? actual.getActualHours()
                    : (actual.getTimeSpent() != null
                            ? BigDecimal.valueOf(actual.getTimeSpent()).divide(
                                    BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
                            : null);
            assignmentActual.setHoursSpent(hours);
            assignmentActualRepository.save(assignmentActual);

            // Update issue status based on completion
            updateIssueStatusFromActual(assignment.getIssueId(), actual.getCompletionStatus());
        });
    }

    // ── Quick-update dual-write ──────────────────────────────

    /**
     * Creates a COMMENT-type issue activity when a quick-update note is written.
     *
     * <p>Only creates the activity when the commit has a note and a crosswalk to an issue.
     *
     * @param commit  the commit that received a check-in note
     * @param note    the note text
     * @param userId  the actor
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onQuickUpdateNote(WeeklyCommitEntity commit, String note, UUID userId) {
        if (commit.getSourceIssueId() == null || note == null || note.isBlank()) {
            return;
        }
        IssueActivityEntity activity = new IssueActivityEntity(
                UUID.randomUUID(), commit.getOrgId(), commit.getSourceIssueId(),
                userId != null ? userId : commit.getOrgId(), // fallback actor
                IssueActivityType.COMMENT
        );
        activity.withComment(note);
        activityRepository.save(activity);
    }

    // ── Private helpers ──────────────────────────────────────

    /**
     * Finds the default 'General' team for the org, creating it if it doesn't exist.
     *
     * <p>Uses an upsert-like approach: first tries to find the team, creates if absent.
     * The creating user becomes the OWNER member.
     */
    private TeamEntity findOrCreateDefaultTeam(UUID orgId, UUID userId) {
        return teamRepository.findAllByOrgId(orgId).stream()
                .filter(t -> DEFAULT_TEAM_NAME.equals(t.getName()))
                .findFirst()
                .orElseGet(() -> {
                    TeamEntity team = new TeamEntity(
                            UUID.randomUUID(), orgId,
                            DEFAULT_TEAM_NAME, DEFAULT_TEAM_PREFIX, userId
                    );
                    teamRepository.save(team);
                    TeamMemberEntity ownerMember =
                            new TeamMemberEntity(team.getId(), userId, orgId, TeamRole.OWNER);
                    teamMemberRepository.save(ownerMember);
                    LOG.info("DualWrite: created default team {} for org {}", team.getId(), orgId);
                    return team;
                });
    }

    /**
     * Allocates the next sequence number for the team and creates an issue.
     *
     * <p>Uses pessimistic locking (load + increment + save) for H2/Postgres compatibility.
     * In production the database-level row lock from the UPDATE still serialises concurrent
     * inserts; the Java increment is safe because this method runs inside a
     * {@code MANDATORY} transaction.
     */
    private IssueEntity createIssueFromCommit(
            WeeklyCommitEntity commit, TeamEntity team, UUID orgId, UUID userId
    ) {
        // Reload team with write lock to serialise sequence allocation
        TeamEntity lockedTeam = teamRepository.findById(team.getId()).orElse(team);
        int seq = lockedTeam.getIssueSequence() + 1;
        lockedTeam.setIssueSequence(seq);
        teamRepository.save(lockedTeam);

        String issueKey = lockedTeam.getKeyPrefix() + "-" + seq;
        IssueEntity issue = new IssueEntity(
                UUID.randomUUID(), orgId, lockedTeam.getId(),
                issueKey, seq, commit.getTitle(), userId
        );
        if (commit.getDescription() != null) {
            issue.setDescription(commit.getDescription());
        }
        if (commit.getChessPriority() != null) {
            issue.setChessPriority(commit.getChessPriority());
        }
        if (commit.getOutcomeId() != null) {
            issue.setOutcomeId(commit.getOutcomeId());
        }
        if (commit.getNonStrategicReason() != null) {
            issue.setNonStrategicReason(commit.getNonStrategicReason());
        }
        if (commit.getEstimatedHours() != null) {
            issue.setEstimatedHours(commit.getEstimatedHours());
        }
        issue.setEffortType(categoryToEffortType(commit.getCategory()));
        issueRepository.save(issue);

        // Log CREATED activity
        IssueActivityEntity created = new IssueActivityEntity(
                UUID.randomUUID(), orgId, issue.getId(), userId, IssueActivityType.CREATED
        );
        activityRepository.save(created);

        return issue;
    }

    private WeeklyAssignmentEntity createAssignment(
            UUID issueId, UUID planId, WeeklyCommitEntity commit, UUID orgId
    ) {
        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), orgId, planId, issueId
        );
        assignment.setExpectedResult(
                commit.getExpectedResult() != null ? commit.getExpectedResult() : "");
        assignment.setConfidence(commit.getConfidence());
        if (commit.getChessPriority() != null) {
            assignment.setChessPriorityOverride(commit.getChessPriority().name());
        }
        assignment.setTagsFromArray(commit.getTags());
        // Crosswalk: assignment → commit
        assignment.setLegacyCommitId(commit.getId());
        assignmentRepository.save(assignment);
        return assignment;
    }

    static AssignmentCompletionStatus toAssignmentStatus(CompletionStatus status) {
        if (status == null) {
            return AssignmentCompletionStatus.NOT_DONE;
        }
        return switch (status) {
            case DONE -> AssignmentCompletionStatus.DONE;
            case PARTIALLY -> AssignmentCompletionStatus.PARTIALLY;
            case DROPPED -> AssignmentCompletionStatus.DROPPED;
            default -> AssignmentCompletionStatus.NOT_DONE;
        };
    }

    private void updateIssueStatusFromActual(UUID issueId, CompletionStatus completionStatus) {
        issueRepository.findById(issueId).ifPresent(issue -> {
            IssueStatus newStatus = completionStatus == CompletionStatus.DONE
                    ? IssueStatus.DONE
                    : IssueStatus.IN_PROGRESS;
            if (issue.getStatus() != newStatus) {
                issue.setStatus(newStatus);
                issueRepository.save(issue);
            }
        });
    }

    private void logActivity(
            UUID issueId, UUID orgId, UUID actorUserId,
            IssueActivityType type, String oldValue, String newValue
    ) {
        UUID actor = actorUserId != null ? actorUserId : orgId;
        IssueActivityEntity activity = new IssueActivityEntity(
                UUID.randomUUID(), orgId, issueId, actor, type
        );
        activity.withChange(oldValue, newValue);
        activityRepository.save(activity);
    }

    /**
     * Maps a {@link CommitCategory} to the closest {@link EffortType}.
     *
     * <p>Mapping:
     * <ul>
     *   <li>DELIVERY / GTM → BUILD</li>
     *   <li>OPERATIONS / TECH_DEBT → MAINTAIN</li>
     *   <li>CUSTOMER / PEOPLE → COLLABORATE</li>
     *   <li>LEARNING → LEARN</li>
     *   <li>null → null</li>
     * </ul>
     */
    static EffortType categoryToEffortType(CommitCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case DELIVERY, GTM -> EffortType.BUILD;
            case OPERATIONS, TECH_DEBT -> EffortType.MAINTAIN;
            case CUSTOMER, PEOPLE -> EffortType.COLLABORATE;
            case LEARNING -> EffortType.LEARN;
        };
    }
}
