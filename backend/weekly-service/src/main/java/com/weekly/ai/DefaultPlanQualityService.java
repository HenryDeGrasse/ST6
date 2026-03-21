package com.weekly.ai;

import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoTree;
import com.weekly.shared.PlanQualityDataProvider;
import com.weekly.shared.PlanQualityDataProvider.CommitQualitySummary;
import com.weekly.shared.TeamRcdoUsageProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link PlanQualityService}.
 *
 * <p>Runs four data-driven quality checks without any LLM call:
 * <ol>
 *   <li>Coverage gaps — user has no commits in any of the team's top rally cries</li>
 *   <li>Category imbalance — one category exceeds {@value #CATEGORY_SKEW_THRESHOLD} of commits</li>
 *   <li>Chess distribution — missing KING or PAWN-heavy distribution</li>
 *   <li>RCDO alignment — user rate vs team average gap exceeds {@value #RCDO_BELOW_TEAM_THRESHOLD}</li>
 * </ol>
 *
 * <p>On any unexpected error the method degrades gracefully and returns
 * {@link PlanQualityService.QualityCheckResult#unavailable()}.
 */
@Service
public class DefaultPlanQualityService implements PlanQualityService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPlanQualityService.class);

    /** Minimum commits required before most distribution checks fire. */
    static final int MIN_COMMITS_FOR_CHECKS = 2;

    /** Category fraction above which we fire a CATEGORY_IMBALANCE nudge. */
    static final double CATEGORY_SKEW_THRESHOLD = 0.70;

    /** PAWN fraction above which we fire a CHESS_PAWN_HEAVY nudge. */
    static final double CHESS_PAWN_THRESHOLD = 0.75;

    /** How many percentage points below team average triggers RCDO alignment warning. */
    static final double RCDO_BELOW_TEAM_THRESHOLD = 0.20;

    /** Number of top team rally cries to check coverage against. */
    static final int TOP_RALLY_CRY_COUNT = 3;

    private final PlanQualityDataProvider qualityDataProvider;
    private final TeamRcdoUsageProvider teamRcdoUsageProvider;
    private final RcdoClient rcdoClient;

    public DefaultPlanQualityService(
            PlanQualityDataProvider qualityDataProvider,
            TeamRcdoUsageProvider teamRcdoUsageProvider,
            RcdoClient rcdoClient
    ) {
        this.qualityDataProvider = qualityDataProvider;
        this.teamRcdoUsageProvider = teamRcdoUsageProvider;
        this.rcdoClient = rcdoClient;
    }

    @Override
    public QualityCheckResult checkPlanQuality(UUID orgId, UUID planId, UUID userId) {
        try {
            PlanQualityDataProvider.PlanQualityContext context =
                    qualityDataProvider.getPlanQualityContext(orgId, planId, userId);
            if (!context.planFound()) {
                return new QualityCheckResult("ok", List.of());
            }

            LocalDate weekStart = LocalDate.parse(context.weekStart());
            List<CommitQualitySummary> commits = context.commits();

            // Previous week for category comparison
            PlanQualityDataProvider.PlanQualityContext prevContext =
                    qualityDataProvider.getPreviousWeekQualityContext(orgId, userId, weekStart);

            // RCDO tree for outcome → rally-cry resolution
            RcdoTree tree = rcdoClient.getTree(orgId);

            // Team RCDO usage for the current week
            TeamRcdoUsageProvider.TeamRcdoUsageResult teamUsage =
                    teamRcdoUsageProvider.getTeamRcdoUsage(orgId, weekStart);

            // Team strategic alignment rate
            double teamStrategicRate =
                    qualityDataProvider.getTeamStrategicAlignmentRate(orgId, weekStart);

            List<QualityNudge> nudges = new ArrayList<>();
            nudges.addAll(checkCoverageGaps(commits, teamUsage, tree));
            nudges.addAll(checkCategoryImbalance(commits, prevContext.commits()));
            nudges.addAll(checkChessDistribution(commits));
            nudges.addAll(checkRcdoAlignment(commits, teamStrategicRate));

            return new QualityCheckResult("ok", nudges);
        } catch (Exception e) {
            LOG.error("Unexpected error in plan quality check for plan {}", planId, e);
            return QualityCheckResult.unavailable();
        }
    }

    // ── Coverage gap check ────────────────────────────────────────────────────

    /**
     * Fires a WARNING when the user's plan covers none of the team's top rally cries.
     *
     * <p>Rally cry activity is derived by mapping each team outcome usage entry
     * to its parent rally cry via the RCDO tree, then summing commit counts per rally cry.
     */
    List<QualityNudge> checkCoverageGaps(
            List<CommitQualitySummary> commits,
            TeamRcdoUsageProvider.TeamRcdoUsageResult teamUsage,
            RcdoTree tree
    ) {
        if (commits.size() < MIN_COMMITS_FOR_CHECKS || teamUsage.outcomes().isEmpty()) {
            return List.of();
        }

        Map<String, String> outcomeToRallyCry = buildOutcomeToRallyCryMap(tree);
        Map<String, String> rallyCryIdToName = buildRallyCryNameMap(tree);

        // Aggregate team commit counts by rally cry
        Map<String, Integer> teamRallyCryCounts = new LinkedHashMap<>();
        for (TeamRcdoUsageProvider.OutcomeUsage usage : teamUsage.outcomes()) {
            String rcId = outcomeToRallyCry.get(usage.outcomeId());
            if (rcId != null) {
                teamRallyCryCounts.merge(rcId, usage.commitCount(), Integer::sum);
            }
        }

        if (teamRallyCryCounts.isEmpty()) {
            return List.of();
        }

        // Find top-N rally cries by commit count
        List<String> topRallyCryIds = teamRallyCryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_RALLY_CRY_COUNT)
                .map(Map.Entry::getKey)
                .toList();

        // Determine which rally cries the user's plan covers
        Set<String> userRallyCryIds = resolveUserRallyCryIds(commits, outcomeToRallyCry);

        boolean coversAny = topRallyCryIds.stream().anyMatch(userRallyCryIds::contains);
        if (!coversAny) {
            List<String> topNames = topRallyCryIds.stream()
                    .map(id -> rallyCryIdToName.getOrDefault(id, id))
                    .toList();
            String cryOrCries = topNames.size() == 1 ? "cry" : "cries";
            String nameList = topNames.stream()
                    .limit(3)
                    .collect(Collectors.joining(", "));
            return List.of(new QualityNudge(
                    "COVERAGE_GAP",
                    "Your plan has no commits linked to the team's top rally "
                            + cryOrCries + ": " + nameList + ".",
                    "WARNING"
            ));
        }

        return List.of();
    }

    // ── Category imbalance check ──────────────────────────────────────────────

    /**
     * Fires a WARNING (or INFO if it is the same dominant category as last week)
     * when one category accounts for {@value #CATEGORY_SKEW_THRESHOLD} or more of commits.
     */
    List<QualityNudge> checkCategoryImbalance(
            List<CommitQualitySummary> commits,
            List<CommitQualitySummary> prevCommits
    ) {
        List<CommitQualitySummary> categorized = commits.stream()
                .filter(c -> c.category() != null)
                .toList();

        if (categorized.size() < MIN_COMMITS_FOR_CHECKS) {
            return List.of();
        }

        Map<String, Long> categoryCounts = categorized.stream()
                .collect(Collectors.groupingBy(CommitQualitySummary::category, Collectors.counting()));

        String dominant = categoryCounts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);

        if (dominant == null) {
            return List.of();
        }

        double dominantPct = (double) categoryCounts.get(dominant) / categorized.size();
        if (dominantPct < CATEGORY_SKEW_THRESHOLD) {
            return List.of();
        }

        // Determine if this is a new dominance or the same as last week
        String prevDominant = findDominantCategory(prevCommits);
        boolean isNewDominance = prevDominant == null || !prevDominant.equals(dominant);
        String severity = isNewDominance ? "WARNING" : "INFO";

        String pctStr = String.format("%.0f%%", dominantPct * 100);
        return List.of(new QualityNudge(
                "CATEGORY_IMBALANCE",
                pctStr + " of your commits are categorised as " + dominant
                        + ". Consider spreading work across more categories for a balanced week.",
                severity
        ));
    }

    // ── Chess distribution check ──────────────────────────────────────────────

    /**
     * Fires nudges for unbalanced chess distributions:
     * <ul>
     *   <li>WARNING when there is no KING commit</li>
     *   <li>WARNING when PAWNs exceed {@value #CHESS_PAWN_THRESHOLD} of commits</li>
     *   <li>POSITIVE when distribution looks healthy (at least one KING, mixed priorities)</li>
     * </ul>
     */
    List<QualityNudge> checkChessDistribution(List<CommitQualitySummary> commits) {
        List<CommitQualitySummary> prioritized = commits.stream()
                .filter(c -> c.chessPriority() != null)
                .toList();

        if (prioritized.size() < MIN_COMMITS_FOR_CHECKS) {
            return List.of();
        }

        Map<String, Long> priorityCounts = prioritized.stream()
                .collect(Collectors.groupingBy(
                        CommitQualitySummary::chessPriority, Collectors.counting()));

        long kingCount = priorityCounts.getOrDefault("KING", 0L);
        long pawnCount = priorityCounts.getOrDefault("PAWN", 0L);
        double pawnPct = (double) pawnCount / prioritized.size();

        List<QualityNudge> nudges = new ArrayList<>();

        if (kingCount == 0) {
            nudges.add(new QualityNudge(
                    "CHESS_NO_KING",
                    "Your plan has no KING priority commit. Identify your most critical task"
                            + " and elevate it to KING so your top priority is clear.",
                    "WARNING"
            ));
        }

        if (pawnPct >= CHESS_PAWN_THRESHOLD) {
            String pctStr = String.format("%.0f%%", pawnPct * 100);
            nudges.add(new QualityNudge(
                    "CHESS_PAWN_HEAVY",
                    pctStr + " of your commits are PAWN priority. Consider whether some"
                            + " of these deserve a higher priority.",
                    "WARNING"
            ));
        }

        // Positive signal: at least one KING and a healthy mix with 3+ commits
        if (nudges.isEmpty() && kingCount >= 1 && prioritized.size() >= 3) {
            nudges.add(new QualityNudge(
                    "CHESS_BALANCED",
                    "Good chess priority distribution — you have a clear top priority"
                            + " with a healthy mix of supporting tasks.",
                    "POSITIVE"
            ));
        }

        return nudges;
    }

    // ── RCDO alignment check ──────────────────────────────────────────────────

    /**
     * Compares the user's strategic alignment rate against the team average.
     *
     * <ul>
     *   <li>WARNING when zero commits are RCDO-linked</li>
     *   <li>WARNING when user rate is more than {@value #RCDO_BELOW_TEAM_THRESHOLD} below team</li>
     *   <li>POSITIVE when user rate is 80% or above</li>
     * </ul>
     */
    List<QualityNudge> checkRcdoAlignment(
            List<CommitQualitySummary> commits, double teamStrategicRate) {
        if (commits.size() < MIN_COMMITS_FOR_CHECKS) {
            return List.of();
        }

        long strategic = commits.stream()
                .filter(c -> c.outcomeId() != null)
                .count();
        double userRate = (double) strategic / commits.size();

        if (userRate == 0.0) {
            return List.of(new QualityNudge(
                    "ZERO_RCDO_ALIGNMENT",
                    "None of your commits are linked to RCDO outcomes. Link at least one commit"
                            + " to a Rally Cry outcome to signal strategic focus.",
                    "WARNING"
            ));
        }

        if (teamStrategicRate > 0.0
                && (teamStrategicRate - userRate) > RCDO_BELOW_TEAM_THRESHOLD) {
            String userPct = String.format("%.0f%%", userRate * 100);
            String teamPct = String.format("%.0f%%", teamStrategicRate * 100);
            return List.of(new QualityNudge(
                    "BELOW_TEAM_RCDO_ALIGNMENT",
                    "Your RCDO alignment (" + userPct + ") is below the team average ("
                            + teamPct + "). Consider linking more commits to outcomes.",
                    "WARNING"
            ));
        }

        if (userRate >= 0.80) {
            return List.of(new QualityNudge(
                    "HIGH_RCDO_ALIGNMENT",
                    String.format("%.0f%% of your commits are linked to RCDO outcomes"
                            + " — excellent strategic focus!", userRate * 100),
                    "POSITIVE"
            ));
        }

        return List.of();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, String> buildOutcomeToRallyCryMap(RcdoTree tree) {
        Map<String, String> map = new HashMap<>();
        for (RcdoTree.RallyCry rc : tree.rallyCries()) {
            for (RcdoTree.Objective obj : rc.objectives()) {
                for (RcdoTree.Outcome outcome : obj.outcomes()) {
                    map.put(outcome.id(), rc.id());
                }
            }
        }
        return map;
    }

    private Map<String, String> buildRallyCryNameMap(RcdoTree tree) {
        Map<String, String> map = new HashMap<>();
        for (RcdoTree.RallyCry rc : tree.rallyCries()) {
            map.put(rc.id(), rc.name());
        }
        return map;
    }

    private Set<String> resolveUserRallyCryIds(
            List<CommitQualitySummary> commits, Map<String, String> outcomeToRallyCry) {
        Set<String> rallyCryIds = new HashSet<>();
        for (CommitQualitySummary commit : commits) {
            if (commit.rallyCryId() != null) {
                // Populated at lock time — fastest path
                rallyCryIds.add(commit.rallyCryId());
            } else if (commit.outcomeId() != null) {
                // Pre-lock or snapshot absent: resolve via tree
                String rcId = outcomeToRallyCry.get(commit.outcomeId());
                if (rcId != null) {
                    rallyCryIds.add(rcId);
                }
            }
        }
        return rallyCryIds;
    }

    private String findDominantCategory(List<CommitQualitySummary> commits) {
        List<CommitQualitySummary> categorized = commits.stream()
                .filter(c -> c.category() != null)
                .toList();
        if (categorized.isEmpty()) {
            return null;
        }
        Map<String, Long> counts = categorized.stream()
                .collect(Collectors.groupingBy(CommitQualitySummary::category, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

}
