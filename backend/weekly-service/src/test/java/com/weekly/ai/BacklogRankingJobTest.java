package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BacklogRankingJob}.
 *
 * <p>Verifies that the scheduled job:
 * <ul>
 *   <li>Calls {@link BacklogRankingService#rankTeamBacklog} for each team with open issues</li>
 *   <li>Skips gracefully when there are no open issues</li>
 *   <li>Isolates per-team errors (one failure does not abort other teams)</li>
 *   <li>Increments the Micrometer counter for each successfully processed team</li>
 * </ul>
 */
class BacklogRankingJobTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID TEAM_ID_A = UUID.randomUUID();
    private static final UUID TEAM_ID_B = UUID.randomUUID();

    private BacklogRankingService rankingService;
    private IssueRepository issueRepository;
    private TeamRepository teamRepository;
    private MeterRegistry meterRegistry;
    private BacklogRankingJob job;

    @BeforeEach
    void setUp() {
        rankingService = mock(BacklogRankingService.class);
        issueRepository = mock(IssueRepository.class);
        teamRepository = mock(TeamRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new BacklogRankingJob(rankingService, issueRepository, teamRepository, meterRegistry);
    }

    private TeamEntity buildTeam(UUID teamId) {
        return new TeamEntity(teamId, ORG_ID, "Team", "TST", UUID.randomUUID());
    }

    @Test
    void skipsWhenNoTeamsWithOpenIssues() {
        when(issueRepository.findDistinctTeamIdsByStatusIn(List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)))
                .thenReturn(List.of());

        job.rankAllTeams();

        verify(rankingService, never()).rankTeamBacklog(any(), any());
    }

    @Test
    void ranksEachTeamWithOpenIssues() {
        when(issueRepository.findDistinctTeamIdsByStatusIn(List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)))
                .thenReturn(List.of(TEAM_ID_A, TEAM_ID_B));
        when(teamRepository.findById(TEAM_ID_A))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_A)));
        when(teamRepository.findById(TEAM_ID_B))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_B)));
        when(rankingService.rankTeamBacklog(eq(ORG_ID), any()))
                .thenReturn(List.of());

        job.rankAllTeams();

        verify(rankingService, times(1)).rankTeamBacklog(ORG_ID, TEAM_ID_A);
        verify(rankingService, times(1)).rankTeamBacklog(ORG_ID, TEAM_ID_B);
    }

    @Test
    void skipsTeamWhenNotFoundInTeamRepository() {
        when(issueRepository.findDistinctTeamIdsByStatusIn(List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)))
                .thenReturn(List.of(TEAM_ID_A));
        when(teamRepository.findById(TEAM_ID_A)).thenReturn(Optional.empty());

        job.rankAllTeams(); // should not throw

        verify(rankingService, never()).rankTeamBacklog(any(), any());
    }

    @Test
    void continuesProcessingOtherTeamsWhenOneThrows() {
        when(issueRepository.findDistinctTeamIdsByStatusIn(List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)))
                .thenReturn(List.of(TEAM_ID_A, TEAM_ID_B));
        when(teamRepository.findById(TEAM_ID_A))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_A)));
        when(teamRepository.findById(TEAM_ID_B))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_B)));

        // Team A throws, Team B should still be processed
        when(rankingService.rankTeamBacklog(ORG_ID, TEAM_ID_A))
                .thenThrow(new RuntimeException("Simulated failure for team A"));
        when(rankingService.rankTeamBacklog(ORG_ID, TEAM_ID_B))
                .thenReturn(List.of());

        job.rankAllTeams(); // must not throw

        verify(rankingService, times(1)).rankTeamBacklog(ORG_ID, TEAM_ID_A);
        verify(rankingService, times(1)).rankTeamBacklog(ORG_ID, TEAM_ID_B);
    }

    @Test
    void incrementsCounterForEachSuccessfulTeam() {
        when(issueRepository.findDistinctTeamIdsByStatusIn(List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)))
                .thenReturn(List.of(TEAM_ID_A, TEAM_ID_B));
        when(teamRepository.findById(TEAM_ID_A))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_A)));
        when(teamRepository.findById(TEAM_ID_B))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_B)));
        when(rankingService.rankTeamBacklog(eq(ORG_ID), any()))
                .thenReturn(List.of());

        job.rankAllTeams();

        double count = meterRegistry.counter(BacklogRankingJob.COUNTER_RANKING_TOTAL).count();
        assertEquals(2.0, count);
    }

    @Test
    void doesNotIncrementCounterForFailedTeam() {
        when(issueRepository.findDistinctTeamIdsByStatusIn(List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)))
                .thenReturn(List.of(TEAM_ID_A, TEAM_ID_B));
        when(teamRepository.findById(TEAM_ID_A))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_A)));
        when(teamRepository.findById(TEAM_ID_B))
                .thenReturn(Optional.of(buildTeam(TEAM_ID_B)));

        when(rankingService.rankTeamBacklog(ORG_ID, TEAM_ID_A))
                .thenThrow(new RuntimeException("Failure"));
        when(rankingService.rankTeamBacklog(ORG_ID, TEAM_ID_B))
                .thenReturn(List.of());

        job.rankAllTeams();

        // Only team B succeeded
        double count = meterRegistry.counter(BacklogRankingJob.COUNTER_RANKING_TOTAL).count();
        assertEquals(1.0, count);
    }
}
