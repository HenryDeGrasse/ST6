package com.weekly.ai;

import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic backlog ranking service (Phase 6, Step 10).
 *
 * <p>Computes an {@code ai_recommended_rank} for each open issue in a team's
 * backlog using the formula:
 * <pre>
 *   score = urgency_weight × time_pressure × effort_fit × dependency_bonus
 * </pre>
 *
 * <ul>
 *   <li><b>urgency_weight</b> — CRITICAL=4, AT_RISK=3, NEEDS_ATTENTION=2, ON_TRACK/other=1.</li>
 *   <li><b>time_pressure</b> — {@code max(1, 5 - weeks_until_target) / 4}.
 *       Defaults to 1.0 when no target date is set.</li>
 *   <li><b>effort_fit</b> — 1.0 when estimated_hours ≤ assignee's realisticWeeklyCap,
 *       otherwise 0.5. Defaults to 1.0 when no estimate or no capacity profile.</li>
 *   <li><b>dependency_bonus</b> — 1.5 when this issue unblocks at least one other
 *       open issue (i.e. another issue has {@code blocked_by_issue_id} pointing here),
 *       otherwise 1.0.</li>
 * </ul>
 *
 * <p>Issues are ranked 1 (highest priority) → N (lowest priority) by descending score.
 * Ranks and human-readable rationale strings are persisted on the issue rows.
 */
@Service
public class BacklogRankingService {

    private static final Logger LOG = LoggerFactory.getLogger(BacklogRankingService.class);

    /** Default urgency weight when no urgency info is available. */
    static final double DEFAULT_URGENCY_WEIGHT = 1.0;

    /** Default time pressure when no target date has been set. */
    static final double DEFAULT_TIME_PRESSURE = 1.0;

    /** Effort-fit score when the issue fits within the user's weekly capacity. */
    static final double EFFORT_FIT_OK = 1.0;

    /** Effort-fit score when the issue exceeds the user's weekly capacity. */
    static final double EFFORT_FIT_OVER = 0.5;

    /** Multiplier applied when an issue unblocks at least one other open issue. */
    static final double DEPENDENCY_BONUS = 1.5;

    private final IssueRepository issueRepository;
    private final UrgencyDataProvider urgencyDataProvider;
    private final CapacityProfileProvider capacityProfileProvider;

    public BacklogRankingService(
            IssueRepository issueRepository,
            UrgencyDataProvider urgencyDataProvider,
            CapacityProfileProvider capacityProfileProvider
    ) {
        this.issueRepository = issueRepository;
        this.urgencyDataProvider = urgencyDataProvider;
        this.capacityProfileProvider = capacityProfileProvider;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Ranks all open issues for the given team, persists updated ranks, and
     * returns the ranked list ordered by ascending rank (rank 1 = highest priority).
     *
     * @param orgId  organisation ID (used for urgency and capacity lookups)
     * @param teamId the team whose backlog should be ranked
     * @return list of {@link RankedIssue}, ordered rank ascending (1 = highest priority)
     */
    @Transactional
    public List<RankedIssue> rankTeamBacklog(UUID orgId, UUID teamId) {
        List<IssueEntity> openIssues = issueRepository.findAllByTeamIdAndStatusIn(
                teamId, List.of(IssueStatus.OPEN, IssueStatus.IN_PROGRESS)
        );

        if (openIssues.isEmpty()) {
            LOG.debug("BacklogRankingService: no open issues for team {}, skipping", teamId);
            return List.of();
        }

        // Build reverse-dependency map: set of issue IDs that are referenced by
        // another issue's blocked_by_issue_id, i.e. issues that unblock others.
        Set<UUID> unblockingIssueIds = openIssues.stream()
                .map(IssueEntity::getBlockedByIssueId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Score each issue
        List<ScoredIssue> scored = openIssues.stream()
                .map(issue -> scoreIssue(issue, orgId, unblockingIssueIds))
                .sorted(Comparator.comparingDouble(ScoredIssue::score)
                        .reversed()
                        .thenComparing(si -> si.issue().getId()))
                .toList();

        // Assign integer ranks and update entities
        List<RankedIssue> result = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            ScoredIssue si = scored.get(i);
            int rank = i + 1;
            si.issue().setAiRecommendedRank(rank);
            si.issue().setAiRankRationale(si.rationale());
            result.add(new RankedIssue(si.issue().getId(), rank, si.score(), si.rationale()));
        }

        issueRepository.saveAll(openIssues);
        LOG.debug("BacklogRankingService: ranked {} open issues for team {}",
                openIssues.size(), teamId);
        return result;
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private ScoredIssue scoreIssue(IssueEntity issue, UUID orgId, Set<UUID> unblockingIssueIds) {
        double urgencyWeight = computeUrgencyWeight(issue, orgId);
        double timePressure = computeTimePressure(issue, orgId);
        double effortFit = computeEffortFit(issue, orgId);
        double dependencyBonus = unblockingIssueIds.contains(issue.getId()) ? DEPENDENCY_BONUS : 1.0;

        double score = urgencyWeight * timePressure * effortFit * dependencyBonus;
        String rationale = buildRationale(urgencyWeight, timePressure, effortFit, dependencyBonus, score);
        return new ScoredIssue(issue, score, rationale);
    }

    /**
     * Urgency weight: CRITICAL=4, AT_RISK=3, NEEDS_ATTENTION=2, ON_TRACK=1, other=1.
     */
    double computeUrgencyWeight(IssueEntity issue, UUID orgId) {
        if (issue.getOutcomeId() == null) {
            return DEFAULT_URGENCY_WEIGHT;
        }
        UrgencyInfo urgencyInfo = urgencyDataProvider.getOutcomeUrgency(orgId, issue.getOutcomeId());
        if (urgencyInfo == null) {
            return DEFAULT_URGENCY_WEIGHT;
        }
        return switch (urgencyInfo.urgencyBand()) {
            case "CRITICAL" -> 4.0;
            case "AT_RISK" -> 3.0;
            case "NEEDS_ATTENTION" -> 2.0;
            default -> 1.0; // ON_TRACK, NO_TARGET, unknown
        };
    }

    /**
     * Time pressure: {@code max(1, 5 - weeks_until_target) / 4}.
     * Returns {@value #DEFAULT_TIME_PRESSURE} when no target date has been set.
     */
    double computeTimePressure(IssueEntity issue, UUID orgId) {
        if (issue.getOutcomeId() == null) {
            return DEFAULT_TIME_PRESSURE;
        }
        UrgencyInfo urgencyInfo = urgencyDataProvider.getOutcomeUrgency(orgId, issue.getOutcomeId());
        if (urgencyInfo == null || urgencyInfo.daysRemaining() == Long.MIN_VALUE) {
            return DEFAULT_TIME_PRESSURE;
        }
        double weeksUntilTarget = urgencyInfo.daysRemaining() / 7.0;
        return Math.max(1.0, 5.0 - weeksUntilTarget) / 4.0;
    }

    /**
     * Effort fit: 1.0 when estimated_hours ≤ assignee's realisticWeeklyCap, otherwise 0.5.
     * Defaults to 1.0 when no estimated hours or no capacity profile is available.
     */
    double computeEffortFit(IssueEntity issue, UUID orgId) {
        BigDecimal estimatedHours = issue.getEstimatedHours();
        if (estimatedHours == null) {
            return EFFORT_FIT_OK;
        }
        UUID assigneeId = issue.getAssigneeUserId();
        if (assigneeId == null) {
            return EFFORT_FIT_OK;
        }
        return capacityProfileProvider.getLatestProfile(orgId, assigneeId)
                .map(profile -> {
                    BigDecimal cap = profile.realisticWeeklyCap();
                    if (cap == null) {
                        return EFFORT_FIT_OK;
                    }
                    return estimatedHours.compareTo(cap) <= 0 ? EFFORT_FIT_OK : EFFORT_FIT_OVER;
                })
                .orElse(EFFORT_FIT_OK);
    }

    /**
     * Builds a human-readable rationale string for the computed score.
     */
    private static String buildRationale(
            double urgencyWeight,
            double timePressure,
            double effortFit,
            double dependencyBonus,
            double score
    ) {
        String urgencyLabel = urgencyWeightLabel(urgencyWeight);
        String dependencyLabel = dependencyBonus > 1.0 ? "unblocks-others" : "no-blockers-freed";
        return String.format(
                "urgency=%s(%.0f) × time_pressure=%.2f × effort_fit=%.1f × dependency_bonus=%.1f → score=%.3f",
                urgencyLabel, urgencyWeight, timePressure, effortFit, dependencyBonus, score
        );
    }

    private static String urgencyWeightLabel(double weight) {
        if (weight >= 4.0) {
            return "CRITICAL";
        }
        if (weight >= 3.0) {
            return "AT_RISK";
        }
        if (weight >= 2.0) {
            return "NEEDS_ATTENTION";
        }
        return "ON_TRACK";
    }

    // ── Internal record types ─────────────────────────────────────────────────

    private record ScoredIssue(IssueEntity issue, double score, String rationale) {}

    // ── Public result type ────────────────────────────────────────────────────

    /**
     * Ranked issue result returned by {@link #rankTeamBacklog}.
     *
     * @param issueId  the issue UUID
     * @param rank     persisted backlog rank (1 = highest priority)
     * @param score    the computed ranking score (higher = higher priority)
     * @param rationale human-readable explanation of the score components
     */
    public record RankedIssue(UUID issueId, int rank, double score, String rationale) {}
}
