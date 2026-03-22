package com.weekly.compatibility.dualwrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentActualRepository;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.issues.domain.EffortType;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.repository.IssueActivityRepository;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamMemberRepository;
import com.weekly.team.repository.TeamRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Unit tests for {@link DualWriteService}. */
class DualWriteServiceTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    private TeamRepository teamRepository;
    private TeamMemberRepository teamMemberRepository;
    private IssueRepository issueRepository;
    private IssueActivityRepository activityRepository;
    private WeeklyAssignmentRepository assignmentRepository;
    private WeeklyAssignmentActualRepository assignmentActualRepository;
    private WeeklyCommitRepository commitRepository;
    private DualWriteService dualWriteService;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TeamRepository.class);
        teamMemberRepository = mock(TeamMemberRepository.class);
        issueRepository = mock(IssueRepository.class);
        activityRepository = mock(IssueActivityRepository.class);
        assignmentRepository = mock(WeeklyAssignmentRepository.class);
        assignmentActualRepository = mock(WeeklyAssignmentActualRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        dualWriteService = new DualWriteService(
                teamRepository,
                teamMemberRepository,
                issueRepository,
                activityRepository,
                assignmentRepository,
                assignmentActualRepository,
                commitRepository
        );
    }

    @Test
    void createIssueForCommitUsesDefaultTeamAndAdvancesSequence() {
        TeamEntity generalTeam = new TeamEntity(UUID.randomUUID(), orgId, "General", "GEN", userId);
        when(teamRepository.findAllByOrgId(orgId)).thenReturn(List.of(generalTeam));
        when(teamRepository.findById(generalTeam.getId())).thenReturn(Optional.of(generalTeam));
        when(teamRepository.save(any(TeamEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(issueRepository.save(any(IssueEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), orgId, planId, "Ship APIs");
        commit.setCategory(CommitCategory.DELIVERY);

        UUID issueId = dualWriteService.createIssueForCommit(commit, orgId, userId);

        ArgumentCaptor<IssueEntity> issueCaptor = ArgumentCaptor.forClass(IssueEntity.class);
        verify(issueRepository).save(issueCaptor.capture());
        assertEquals(issueId, issueCaptor.getValue().getId());
        assertEquals("GEN-1", issueCaptor.getValue().getIssueKey());
        assertEquals(1, issueCaptor.getValue().getSequenceNumber());
        assertEquals(EffortType.BUILD, issueCaptor.getValue().getEffortType());
        assertEquals(1, generalTeam.getIssueSequence());
    }

    @Test
    void createAssignmentForCommitPersistsLegacyCommitCrosswalk() {
        IssueEntity issue = new IssueEntity(
                UUID.randomUUID(), orgId, UUID.randomUUID(), "GEN-7", 7, "Ship APIs", userId
        );
        WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), orgId, planId, "Ship APIs");
        when(assignmentRepository.save(any(WeeklyAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        dualWriteService.createAssignmentForCommit(issue.getId(), planId, commit, orgId);

        ArgumentCaptor<WeeklyAssignmentEntity> captor = ArgumentCaptor.forClass(WeeklyAssignmentEntity.class);
        verify(assignmentRepository).save(captor.capture());
        assertEquals(commit.getId(), captor.getValue().getLegacyCommitId());
        assertEquals(issue.getId(), captor.getValue().getIssueId());
    }

    @Test
    void findIssueByCommitIdResolvesThroughSourceIssueCrosswalk() {
        UUID issueId = UUID.randomUUID();
        WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), orgId, planId, "Ship APIs");
        commit.setSourceIssueId(issueId);
        IssueEntity issue = new IssueEntity(issueId, orgId, UUID.randomUUID(), "GEN-3", 3, "Ship APIs", userId);

        when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        assertTrue(dualWriteService.findIssueByCommitId(commit.getId()).isPresent());
        assertSame(issue, dualWriteService.findIssueByCommitId(commit.getId()).orElseThrow());
    }

    @Test
    void findCommitByAssignmentIdRequiresBidirectionalCrosswalkMatch() {
        UUID issueId = UUID.randomUUID();
        WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), orgId, planId, "Ship APIs");
        commit.setSourceIssueId(issueId);

        WeeklyAssignmentEntity assignment = new WeeklyAssignmentEntity(UUID.randomUUID(), orgId, planId, issueId);
        assignment.setLegacyCommitId(commit.getId());

        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(commitRepository.findById(commit.getId())).thenReturn(Optional.of(commit));

        assertSame(commit, dualWriteService.findCommitByAssignmentId(assignment.getId()).orElseThrow());

        WeeklyCommitEntity mismatchedCommit = new WeeklyCommitEntity(UUID.randomUUID(), orgId, planId, "Other");
        mismatchedCommit.setSourceIssueId(UUID.randomUUID());
        WeeklyAssignmentEntity mismatchedAssignment = new WeeklyAssignmentEntity(
                UUID.randomUUID(), orgId, planId, issueId
        );
        mismatchedAssignment.setLegacyCommitId(mismatchedCommit.getId());
        when(assignmentRepository.findById(mismatchedAssignment.getId())).thenReturn(Optional.of(mismatchedAssignment));
        when(commitRepository.findById(mismatchedCommit.getId())).thenReturn(Optional.of(mismatchedCommit));

        assertTrue(dualWriteService.findCommitByAssignmentId(mismatchedAssignment.getId()).isEmpty());
    }
}
