package com.weekly.ai;

import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.team.domain.TeamEntity;
import com.weekly.team.repository.TeamRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that re-ranks team backlogs using the deterministic ranking formula.
 *
 * <p>Runs every 2 hours by default (configurable via {@code backlog.ranking.cron}).
 * For each team that has open issues, calls {@link BacklogRankingService#rankTeamBacklog}
 * to recompute {@code ai_recommended_rank} and {@code ai_rank_rationale} on each issue row.
 *
 * <p>Enabled via {@code backlog.ranking.enabled=true} (worker profile only).
 * Defaults to {@code false} so the job is never active in local/test environments.
 *
 * <p>Follows the {@link com.weekly.urgency.UrgencyComputeJob} pattern:
 * per-team error isolation, a Micrometer counter, and structured logging.
 */
@Component
@ConditionalOnProperty(name = "backlog.ranking.enabled", havingValue = "true")
public class BacklogRankingJob {

    private static final Logger LOG = LoggerFactory.getLogger(BacklogRankingJob.class);

    /** Micrometer counter name incremented once per successfully processed team. */
    static final String COUNTER_RANKING_TOTAL = "backlog_ranking_total";

    private final BacklogRankingService rankingService;
    private final IssueRepository issueRepository;
    private final TeamRepository teamRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public BacklogRankingJob(
            BacklogRankingService rankingService,
            IssueRepository issueRepository,
            TeamRepository teamRepository,
            MeterRegistry meterRegistry
    ) {
        this.rankingService = rankingService;
        this.issueRepository = issueRepository;
        this.teamRepository = teamRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Recomputes backlog ranks for every team that has at least one open issue.
     *
     * <p>Scheduled to run every 2 hours by default; configurable via
     * {@code backlog.ranking.cron}.
     *
     * <p>Per-team failures are caught, logged, and skipped so a bad team does not
     * abort the entire batch run.  A Micrometer counter
     * ({@value #COUNTER_RANKING_TOTAL}) is incremented for each team processed
     * successfully.
     */
    @Scheduled(cron = "${backlog.ranking.cron:0 0 */2 * * *}")
    public void rankAllTeams() {
        List<UUID> teamIds = issueRepository.findDistinctTeamIdsByStatusIn(
                List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)
        );

        if (teamIds.isEmpty()) {
            LOG.debug("BacklogRankingJob: no teams with open issues, skipping");
            return;
        }

        LOG.info("BacklogRankingJob: starting backlog ranking for {} team(s)", teamIds.size());
        int successCount = 0;

        for (UUID teamId : teamIds) {
            try {
                Optional<TeamEntity> teamOpt = teamRepository.findById(teamId);
                if (teamOpt.isEmpty()) {
                    LOG.warn("BacklogRankingJob: team {} not found, skipping", teamId);
                    continue;
                }
                UUID orgId = teamOpt.get().getOrgId();
                List<BacklogRankingService.RankedIssue> ranked =
                        rankingService.rankTeamBacklog(orgId, teamId);
                meterRegistry.counter(COUNTER_RANKING_TOTAL).increment();
                successCount++;
                LOG.debug("BacklogRankingJob: ranked {} issues for team {}", ranked.size(), teamId);
            } catch (Exception e) {
                LOG.warn("BacklogRankingJob: error ranking team {}: {}",
                        teamId, e.getMessage(), e);
            }
        }

        LOG.info("BacklogRankingJob: ranking complete — {}/{} team(s) succeeded",
                successCount, teamIds.size());
    }
}
