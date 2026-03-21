package com.weekly.trends;

import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link TrendsService}.
 *
 * <p>Aggregation service for rolling cross-week trend analysis (Wave 1).
 * Computes per-user planning metrics over a configurable rolling window:
 * <ul>
 *   <li>Strategic alignment rate (% RCDO-linked commits)</li>
 *   <li>Carry-forward velocity (avg per week + consecutive streak)</li>
 *   <li>Completion accuracy (avg confidence vs actual completion rate)</li>
 *   <li>Priority and category distributions</li>
 *   <li>Team average for strategic alignment comparison</li>
 * </ul>
 * Also generates structured {@link TrendInsight} objects for notable patterns.
 */
@Service
public class DefaultTrendsService implements TrendsService {

    static final int MIN_WEEKS = 1;
    static final int MAX_WEEKS = 26;

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository actualRepository;

    public DefaultTrendsService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.actualRepository = actualRepository;
    }

    /**
     * Computes the rolling-window trend metrics for the given user.
     *
     * @param orgId  the organisation ID from the authenticated JWT
     * @param userId the user whose trends to compute
     * @param weeks  size of the rolling window (clamped to [1, 26])
     * @return aggregated trend data with per-week breakdown and insights
     */
    @Override
    @Transactional(readOnly = true)
    public TrendsResponse computeTrends(UUID orgId, UUID userId, int weeks) {
        int clampedWeeks = Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, weeks));

        LocalDate windowEnd = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate windowStart = windowEnd.minusWeeks(clampedWeeks - 1);

        // ── 1. Fetch user's plans in window ──────────────────────────────────
        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, windowEnd);

        Map<LocalDate, WeeklyPlanEntity> planByWeek = plans.stream()
                .collect(Collectors.toMap(WeeklyPlanEntity::getWeekStartDate, Function.identity()));

        // ── 2. Fetch commits for all plans ───────────────────────────────────
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> allUserCommits = planIds.isEmpty()
                ? List.of()
                : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        Map<UUID, List<WeeklyCommitEntity>> commitsByPlan = allUserCommits.stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        // ── 3. Fetch actuals for all commits ─────────────────────────────────
        List<UUID> commitIds = allUserCommits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsByCommitId = commitIds.isEmpty()
                ? Map.of()
                : actualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                        .collect(Collectors.toMap(
                                WeeklyCommitActualEntity::getCommitId, Function.identity()));

        // ── 4. Build per-week trend points ───────────────────────────────────
        List<WeekTrendPoint> weekPoints = buildWeekPoints(
                windowStart, windowEnd, planByWeek, commitsByPlan, actualsByCommitId);

        // ── 5. Aggregate user-level metrics ──────────────────────────────────
        double strategicRate = computeStrategicRate(allUserCommits);
        double avgCarryForward = computeAvgCarryForward(weekPoints);
        int streak = computeCarryForwardStreak(weekPoints);
        double avgConfidence = computeAvgConfidence(allUserCommits);
        double completionAccuracy = computeCompletionAccuracy(weekPoints);
        double confidenceGap = computeConfidenceGap(avgConfidence, completionAccuracy);
        Map<String, Double> priorityDist = computePriorityDistribution(allUserCommits);
        Map<String, Double> categoryDist = computeCategoryDistribution(allUserCommits);

        // ── 6. Compute team-wide strategic alignment ──────────────────────────
        double teamStrategicRate = computeTeamStrategicRate(orgId, windowStart, windowEnd);

        // ── 7. Generate insights ──────────────────────────────────────────────
        List<TrendInsight> insights = generateInsights(
                strategicRate, teamStrategicRate,
                streak, avgConfidence, completionAccuracy, confidenceGap, weekPoints);

        int weeksAnalyzed = (int) weekPoints.stream()
                .filter(wp -> wp.totalCommits() > 0)
                .count();

        return new TrendsResponse(
                weeksAnalyzed,
                windowStart.toString(),
                windowEnd.toString(),
                strategicRate,
                teamStrategicRate,
                avgCarryForward,
                streak,
                avgConfidence,
                completionAccuracy,
                confidenceGap,
                priorityDist,
                categoryDist,
                weekPoints,
                insights
        );
    }

    // ── Per-week breakdown ───────────────────────────────────────────────────

    private List<WeekTrendPoint> buildWeekPoints(
            LocalDate windowStart,
            LocalDate windowEnd,
            Map<LocalDate, WeeklyPlanEntity> planByWeek,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlan,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId
    ) {
        List<WeekTrendPoint> points = new ArrayList<>();
        LocalDate week = windowStart;
        while (!week.isAfter(windowEnd)) {
            WeeklyPlanEntity plan = planByWeek.get(week);
            List<WeeklyCommitEntity> commits = plan != null
                    ? commitsByPlan.getOrDefault(plan.getId(), List.of())
                    : List.of();

            boolean hasActuals = plan != null && isFinalizedReconciliation(plan.getState());

            points.add(buildWeekPoint(week, commits, actualsByCommitId, hasActuals));
            week = week.plusWeeks(1);
        }
        return points;
    }

    private WeekTrendPoint buildWeekPoint(
            LocalDate weekStart,
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId,
            boolean hasActuals
    ) {
        int totalCommits = commits.size();
        int strategicCommits = 0;
        int carryForwardCommits = 0;
        double sumConfidence = 0.0;
        int confidenceCount = 0;
        int doneCount = 0;

        Map<String, Integer> priorityCounts = new LinkedHashMap<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        for (WeeklyCommitEntity commit : commits) {
            if (commit.getOutcomeId() != null) {
                strategicCommits++;
            }
            if (commit.getCarriedFromCommitId() != null) {
                carryForwardCommits++;
            }
            if (commit.getConfidence() != null) {
                sumConfidence += commit.getConfidence().doubleValue();
                confidenceCount++;
            }
            if (commit.getChessPriority() != null) {
                priorityCounts.merge(commit.getChessPriority().name(), 1, Integer::sum);
            }
            if (commit.getCategory() != null) {
                categoryCounts.merge(commit.getCategory().name(), 1, Integer::sum);
            }
            if (hasActuals) {
                WeeklyCommitActualEntity actual = actualsByCommitId.get(commit.getId());
                if (actual != null && actual.getCompletionStatus() == CompletionStatus.DONE) {
                    doneCount++;
                }
            }
        }

        double avgConfidence = confidenceCount > 0 ? sumConfidence / confidenceCount : 0.0;
        double completionRate = hasActuals && totalCommits > 0
                ? (double) doneCount / totalCommits
                : 0.0;

        return new WeekTrendPoint(
                weekStart.toString(),
                totalCommits,
                strategicCommits,
                carryForwardCommits,
                avgConfidence,
                completionRate,
                hasActuals,
                priorityCounts,
                categoryCounts
        );
    }

    // ── Aggregate metrics ────────────────────────────────────────────────────

    double computeStrategicRate(List<WeeklyCommitEntity> commits) {
        if (commits.isEmpty()) {
            return 0.0;
        }
        long strategic = commits.stream()
                .filter(c -> c.getOutcomeId() != null)
                .count();
        return (double) strategic / commits.size();
    }

    double computeAvgCarryForward(List<WeekTrendPoint> weekPoints) {
        List<WeekTrendPoint> weeksWithData = weekPoints.stream()
                .filter(wp -> wp.totalCommits() > 0)
                .toList();
        if (weeksWithData.isEmpty()) {
            return 0.0;
        }
        int total = weeksWithData.stream()
                .mapToInt(WeekTrendPoint::carryForwardCommits)
                .sum();
        return (double) total / weeksWithData.size();
    }

    /**
     * Counts consecutive recent active weeks (from most recent backward) with at least one
     * carry-forward commit.
     *
     * <p>Weeks with no commits are skipped so an unplanned current week does not erase the
     * user's latest carry-forward streak.
     */
    int computeCarryForwardStreak(List<WeekTrendPoint> weekPoints) {
        return computeTrailingActiveWeekStreak(weekPoints, point -> point.carryForwardCommits() > 0);
    }

    double computeAvgConfidence(List<WeeklyCommitEntity> commits) {
        OptionalDouble avg = commits.stream()
                .filter(c -> c.getConfidence() != null)
                .mapToDouble(c -> c.getConfidence().doubleValue())
                .average();
        return avg.orElse(0.0);
    }

    double computeCompletionAccuracy(List<WeekTrendPoint> weekPoints) {
        List<WeekTrendPoint> reconciled = weekPoints.stream()
                .filter(WeekTrendPoint::hasActuals)
                .filter(wp -> wp.totalCommits() > 0)
                .toList();
        if (reconciled.isEmpty()) {
            return 0.0;
        }
        OptionalDouble avg = reconciled.stream()
                .mapToDouble(WeekTrendPoint::completionRate)
                .average();
        return avg.orElse(0.0);
    }

    double computeConfidenceGap(double avgConfidence, double completionAccuracy) {
        return avgConfidence - completionAccuracy;
    }

    Map<String, Double> computePriorityDistribution(List<WeeklyCommitEntity> commits) {
        Map<String, Double> result = new LinkedHashMap<>();
        long withPriority = commits.stream()
                .filter(c -> c.getChessPriority() != null)
                .count();
        for (ChessPriority priority : ChessPriority.values()) {
            if (withPriority == 0) {
                result.put(priority.name(), 0.0);
            } else {
                long count = commits.stream()
                        .filter(c -> c.getChessPriority() == priority)
                        .count();
                result.put(priority.name(), (double) count / withPriority);
            }
        }
        return result;
    }

    Map<String, Double> computeCategoryDistribution(List<WeeklyCommitEntity> commits) {
        Map<String, Double> result = new LinkedHashMap<>();
        long withCategory = commits.stream()
                .filter(c -> c.getCategory() != null)
                .count();
        for (CommitCategory category : CommitCategory.values()) {
            if (withCategory == 0) {
                result.put(category.name(), 0.0);
            } else {
                long count = commits.stream()
                        .filter(c -> c.getCategory() == category)
                        .count();
                result.put(category.name(), (double) count / withCategory);
            }
        }
        return result;
    }

    // ── Team average ─────────────────────────────────────────────────────────

    double computeTeamStrategicRate(UUID orgId, LocalDate windowStart, LocalDate windowEnd) {
        List<WeeklyPlanEntity> orgPlans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, windowStart, windowEnd);
        if (orgPlans.isEmpty()) {
            return 0.0;
        }
        List<UUID> orgPlanIds = orgPlans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> orgCommits =
                commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, orgPlanIds);
        if (orgCommits.isEmpty()) {
            return 0.0;
        }
        long strategic = orgCommits.stream()
                .filter(c -> c.getOutcomeId() != null)
                .count();
        return (double) strategic / orgCommits.size();
    }

    // ── Insight generation ───────────────────────────────────────────────────

    @SuppressWarnings("checkstyle:ParameterNumber")
    List<TrendInsight> generateInsights(
            double strategicRate,
            double teamStrategicRate,
            int carryForwardStreak,
            double avgConfidence,
            double completionAccuracy,
            double confidenceGap,
            List<WeekTrendPoint> weekPoints
    ) {
        List<TrendInsight> insights = new ArrayList<>();

        // Carry-forward streak warning (3+ consecutive weeks)
        if (carryForwardStreak >= 3) {
            insights.add(new TrendInsight(
                    "CARRY_FORWARD_STREAK",
                    "You have carried forward commits for " + carryForwardStreak
                            + " consecutive weeks. Consider breaking work into smaller, completable tasks.",
                    "WARNING"
            ));
        }

        // Confidence-accuracy gap warning (overconfident by more than 20 pp)
        if (confidenceGap > 0.20) {
            String gap = String.format("%.0f%%", confidenceGap * 100);
            insights.add(new TrendInsight(
                    "CONFIDENCE_ACCURACY_GAP",
                    "Your confidence estimates are " + gap
                            + " higher than your actual completion rate."
                            + " Consider setting more conservative confidence values.",
                    "WARNING"
            ));
        }

        // Recent uncategorized work streak (3+ active weeks)
        int zeroCategoryStreak = computeTrailingActiveWeekStreak(
                weekPoints,
                point -> point.categoryCounts().values().stream().mapToInt(Integer::intValue).sum() == 0
        );
        if (zeroCategoryStreak >= 3) {
            insights.add(new TrendInsight(
                    "ZERO_CATEGORY_STREAK",
                    "Your last " + zeroCategoryStreak
                            + " active weeks have no commit categories assigned."
                            + " Categorizing work will improve signal quality in weekly reviews.",
                    "WARNING"
            ));
        }

        // Persistent zero strategic alignment (3+ active weeks with no RCDO links)
        long zeroStrategicWeeks = weekPoints.stream()
                .filter(wp -> wp.totalCommits() > 0 && wp.strategicCommits() == 0)
                .count();
        if (zeroStrategicWeeks >= 3) {
            insights.add(new TrendInsight(
                    "ZERO_STRATEGIC_WEEKS",
                    "You have had " + zeroStrategicWeeks
                            + " weeks with no RCDO-linked commits."
                            + " Review your strategic alignment.",
                    "WARNING"
            ));
        }

        // Below team average by more than 20 pp
        if (teamStrategicRate > 0 && (teamStrategicRate - strategicRate) > 0.20) {
            insights.add(new TrendInsight(
                    "BELOW_TEAM_STRATEGIC_AVERAGE",
                    String.format(
                            "Your strategic alignment (%.0f%%) is notably below the team"
                                    + " average (%.0f%%). Consider linking more commits to RCDO outcomes.",
                            strategicRate * 100, teamStrategicRate * 100),
                    "WARNING"
            ));
        }

        // High completion rate — positive signal
        boolean hasReconciledWeeks = weekPoints.stream().anyMatch(WeekTrendPoint::hasActuals);
        if (hasReconciledWeeks && completionAccuracy >= 0.85) {
            insights.add(new TrendInsight(
                    "HIGH_COMPLETION_RATE",
                    String.format("Great job! You completed %.0f%% of your committed work"
                            + " across reconciled weeks.", completionAccuracy * 100),
                    "POSITIVE"
            ));
        }

        // High strategic alignment — positive signal
        if (strategicRate >= 0.80) {
            insights.add(new TrendInsight(
                    "HIGH_STRATEGIC_ALIGNMENT",
                    String.format("%.0f%% of your commits are linked to RCDO outcomes"
                            + " — excellent strategic focus!", strategicRate * 100),
                    "POSITIVE"
            ));
        }

        return insights;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int computeTrailingActiveWeekStreak(
            List<WeekTrendPoint> weekPoints,
            Predicate<WeekTrendPoint> matcher
    ) {
        int streak = 0;
        for (int i = weekPoints.size() - 1; i >= 0; i--) {
            WeekTrendPoint point = weekPoints.get(i);
            if (point.totalCommits() == 0) {
                continue;
            }
            if (matcher.test(point)) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private boolean isFinalizedReconciliation(PlanState state) {
        return state == PlanState.RECONCILED || state == PlanState.CARRY_FORWARD;
    }
}
