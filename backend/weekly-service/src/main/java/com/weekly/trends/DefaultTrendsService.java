package com.weekly.trends;

import com.weekly.capacity.CapacityProfileEntity;
import com.weekly.capacity.CapacityProfileService;
import com.weekly.issues.domain.EffortType;
import com.weekly.issues.domain.EffortTypeMapper;
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
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *   <li>Estimated-vs-actual hour trends</li>
 *   <li>Priority and category distributions</li>
 *   <li>Team average for strategic alignment comparison</li>
 * </ul>
 * Also generates structured {@link TrendInsight} objects for notable patterns.
 */
@Service
public class DefaultTrendsService implements TrendsService {

    static final int MIN_WEEKS = 1;
    static final int MAX_WEEKS = 26;

    private static final double HOURS_UNDERESTIMATION_THRESHOLD = 1.15;
    private static final double HOURS_ON_TARGET_LOW = 0.90;
    private static final double HOURS_ON_TARGET_HIGH = 1.10;

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository actualRepository;
    private final CapacityProfileService capacityProfileService;

    public DefaultTrendsService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository,
            CapacityProfileService capacityProfileService
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.actualRepository = actualRepository;
        this.capacityProfileService = capacityProfileService;
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

        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, windowEnd);

        Map<LocalDate, WeeklyPlanEntity> planByWeek = plans.stream()
                .collect(Collectors.toMap(WeeklyPlanEntity::getWeekStartDate, Function.identity()));

        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> allUserCommits = planIds.isEmpty()
                ? List.of()
                : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        Map<UUID, List<WeeklyCommitEntity>> commitsByPlan = allUserCommits.stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        List<UUID> commitIds = allUserCommits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsByCommitId = commitIds.isEmpty()
                ? Map.of()
                : actualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                        .collect(Collectors.toMap(
                                WeeklyCommitActualEntity::getCommitId, Function.identity()));

        List<WeekTrendPoint> weekPoints = buildWeekPoints(
                windowStart, windowEnd, planByWeek, commitsByPlan, actualsByCommitId);

        double strategicRate = computeStrategicRate(allUserCommits);
        double avgCarryForward = computeAvgCarryForward(weekPoints);
        int streak = computeCarryForwardStreak(weekPoints);
        double avgConfidence = computeAvgConfidence(allUserCommits);
        double completionAccuracy = computeCompletionAccuracy(weekPoints);
        double confidenceGap = computeConfidenceGap(avgConfidence, completionAccuracy);
        Double avgEstimatedHoursPerWeek = computeAverageEstimatedHoursPerWeek(weekPoints);
        Double avgActualHoursPerWeek = computeAverageActualHoursPerWeek(weekPoints);
        Double hoursAccuracyRatio = computeHoursAccuracyRatio(weekPoints);
        Map<String, Double> priorityDist = computePriorityDistribution(allUserCommits);
        Map<String, Double> categoryDist = computeCategoryDistribution(allUserCommits);
        Map<String, Double> effortTypeDist = computeEffortTypeDistribution(allUserCommits);

        double teamStrategicRate = computeTeamStrategicRate(orgId, windowStart, windowEnd);
        Optional<CapacityProfileEntity> capacityProfile = capacityProfileService.getProfile(orgId, userId);

        List<TrendInsight> insights = generateInsights(
                strategicRate,
                teamStrategicRate,
                streak,
                avgConfidence,
                completionAccuracy,
                confidenceGap,
                hoursAccuracyRatio,
                weekPoints,
                capacityProfile
        );

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
                avgEstimatedHoursPerWeek,
                avgActualHoursPerWeek,
                hoursAccuracyRatio,
                priorityDist,
                categoryDist,
                weekPoints,
                insights,
                effortTypeDist
        );
    }

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
        BigDecimal totalEstimatedHours = BigDecimal.ZERO;
        BigDecimal totalActualHours = BigDecimal.ZERO;
        boolean hasEstimatedHours = false;
        boolean hasActualHours = false;

        Map<String, Integer> priorityCounts = new LinkedHashMap<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        Map<String, Integer> effortTypeCounts = new LinkedHashMap<>();

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
            if (commit.getEstimatedHours() != null) {
                totalEstimatedHours = totalEstimatedHours.add(commit.getEstimatedHours());
                hasEstimatedHours = true;
            }
            if (commit.getChessPriority() != null) {
                priorityCounts.merge(commit.getChessPriority().name(), 1, Integer::sum);
            }
            if (commit.getCategory() != null) {
                categoryCounts.merge(commit.getCategory().name(), 1, Integer::sum);
                EffortType effortType = EffortTypeMapper.fromCommitCategory(commit.getCategory());
                if (effortType != null) {
                    effortTypeCounts.merge(effortType.name(), 1, Integer::sum);
                }
            }

            WeeklyCommitActualEntity actual = actualsByCommitId.get(commit.getId());
            if (actual != null && actual.getActualHours() != null) {
                totalActualHours = totalActualHours.add(actual.getActualHours());
                hasActualHours = true;
            }
            if (hasActuals && actual != null && actual.getCompletionStatus() == CompletionStatus.DONE) {
                doneCount++;
            }
        }

        double avgConfidence = confidenceCount > 0 ? sumConfidence / confidenceCount : 0.0;
        double completionRate = hasActuals && totalCommits > 0
                ? (double) doneCount / totalCommits
                : 0.0;
        Double estimatedHours = hasEstimatedHours ? totalEstimatedHours.doubleValue() : null;
        Double actualHours = hasActualHours ? totalActualHours.doubleValue() : null;
        Double hoursAccuracyRatio = hasEstimatedHours
                && hasActualHours
                && totalEstimatedHours.compareTo(BigDecimal.ZERO) > 0
                ? totalActualHours.divide(totalEstimatedHours, 4, java.math.RoundingMode.HALF_UP).doubleValue()
                : null;

        return new WeekTrendPoint(
                weekStart.toString(),
                totalCommits,
                strategicCommits,
                carryForwardCommits,
                avgConfidence,
                completionRate,
                hasActuals,
                priorityCounts,
                categoryCounts,
                estimatedHours,
                actualHours,
                hoursAccuracyRatio,
                effortTypeCounts
        );
    }

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

    Double computeAverageEstimatedHoursPerWeek(List<WeekTrendPoint> weekPoints) {
        return averageNullable(weekPoints.stream()
                .map(WeekTrendPoint::estimatedHours)
                .toList());
    }

    Double computeAverageActualHoursPerWeek(List<WeekTrendPoint> weekPoints) {
        return averageNullable(weekPoints.stream()
                .map(WeekTrendPoint::actualHours)
                .toList());
    }

    Double computeHoursAccuracyRatio(List<WeekTrendPoint> weekPoints) {
        BigDecimal totalEstimated = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        boolean hasComparableWeeks = false;

        for (WeekTrendPoint weekPoint : weekPoints) {
            if (weekPoint.estimatedHours() == null || weekPoint.actualHours() == null) {
                continue;
            }

            BigDecimal estimated = BigDecimal.valueOf(weekPoint.estimatedHours());
            if (estimated.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            totalEstimated = totalEstimated.add(estimated);
            totalActual = totalActual.add(BigDecimal.valueOf(weekPoint.actualHours()));
            hasComparableWeeks = true;
        }

        if (!hasComparableWeeks || totalEstimated.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalActual.divide(totalEstimated, 4, java.math.RoundingMode.HALF_UP).doubleValue();
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

    /**
     * Computes the fraction of commits per {@link EffortType} value
     * (BUILD / MAINTAIN / COLLABORATE / LEARN) mapped from {@link CommitCategory}
     * via {@link EffortTypeMapper}.
     *
     * <p>Only commits with a non-null category are included in the denominator.
     * Commits whose category maps to {@code null} (currently impossible but
     * defensive) are excluded from both numerator and denominator.
     *
     * @param commits all commits in the rolling window
     * @return map of effort-type name → fraction (0.0 when no categorised commits)
     */
    Map<String, Double> computeEffortTypeDistribution(List<WeeklyCommitEntity> commits) {
        Map<String, Double> result = new LinkedHashMap<>();
        // Build mapped counts first
        Map<EffortType, Long> rawCounts = new java.util.EnumMap<>(EffortType.class);
        for (EffortType et : EffortType.values()) {
            rawCounts.put(et, 0L);
        }
        long total = 0;
        for (WeeklyCommitEntity commit : commits) {
            if (commit.getCategory() == null) {
                continue;
            }
            EffortType effortType = EffortTypeMapper.fromCommitCategory(commit.getCategory());
            if (effortType != null) {
                rawCounts.merge(effortType, 1L, Long::sum);
                total++;
            }
        }
        for (EffortType et : EffortType.values()) {
            if (total == 0) {
                result.put(et.name(), 0.0);
            } else {
                result.put(et.name(), (double) rawCounts.get(et) / total);
            }
        }
        return result;
    }

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

    @SuppressWarnings("checkstyle:ParameterNumber")
    List<TrendInsight> generateInsights(
            double strategicRate,
            double teamStrategicRate,
            int carryForwardStreak,
            double avgConfidence,
            double completionAccuracy,
            double confidenceGap,
            Double hoursAccuracyRatio,
            List<WeekTrendPoint> weekPoints,
            Optional<CapacityProfileEntity> capacityProfile
    ) {
        List<TrendInsight> insights = new ArrayList<>();

        if (carryForwardStreak >= 3) {
            insights.add(new TrendInsight(
                    "CARRY_FORWARD_STREAK",
                    "You have carried forward commits for " + carryForwardStreak
                            + " consecutive weeks. Consider breaking work into smaller, completable tasks.",
                    "WARNING"
            ));
        }

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

        if (hoursAccuracyRatio != null && hoursAccuracyRatio > HOURS_UNDERESTIMATION_THRESHOLD) {
            insights.add(new TrendInsight(
                    "HOURS_UNDERESTIMATION",
                    String.format(
                            "Your actual hours are averaging %.0f%% of estimated hours."
                                    + " Consider adding more buffer to weekly plans.",
                            hoursAccuracyRatio * 100),
                    "WARNING"
            ));
        } else if (hoursAccuracyRatio != null
                && hoursAccuracyRatio >= HOURS_ON_TARGET_LOW
                && hoursAccuracyRatio <= HOURS_ON_TARGET_HIGH) {
            insights.add(new TrendInsight(
                    "HOURS_ESTIMATION_ON_TARGET",
                    String.format(
                            "Your estimated and actual hours are closely aligned at %.0f%% accuracy.",
                            hoursAccuracyRatio * 100),
                    "POSITIVE"
            ));
        }

        capacityProfile.flatMap(this::buildCapacityProfileSummaryMessage)
                .ifPresent(message -> insights.add(new TrendInsight(
                        "CAPACITY_PROFILE_SUMMARY",
                        message,
                        "INFO"
                )));

        boolean hasReconciledWeeks = weekPoints.stream().anyMatch(WeekTrendPoint::hasActuals);
        if (hasReconciledWeeks && completionAccuracy >= 0.85) {
            insights.add(new TrendInsight(
                    "HIGH_COMPLETION_RATE",
                    String.format("Great job! You completed %.0f%% of your committed work"
                            + " across reconciled weeks.", completionAccuracy * 100),
                    "POSITIVE"
            ));
        }

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

    private Optional<String> buildCapacityProfileSummaryMessage(CapacityProfileEntity profile) {
        List<String> parts = new ArrayList<>();
        if (profile.getEstimationBias() != null) {
            parts.add(String.format("historical bias is %.2fx", profile.getEstimationBias().doubleValue()));
        }
        if (profile.getRealisticWeeklyCap() != null) {
            parts.add(String.format("realistic weekly cap is %.1f hours", profile.getRealisticWeeklyCap().doubleValue()));
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String confidenceSuffix = profile.getConfidenceLevel() != null
                ? " (confidence: " + profile.getConfidenceLevel() + ")"
                : "";
        return Optional.of("Capacity profile summary: your "
                + String.join(" and ", parts)
                + confidenceSuffix
                + ".");
    }

    private Double averageNullable(List<Double> values) {
        DoubleSummary summary = new DoubleSummary();
        values.stream()
                .filter(value -> value != null)
                .forEach(summary::add);
        return summary.average();
    }

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

    private static final class DoubleSummary {
        private double sum;
        private int count;

        void add(double value) {
            sum += value;
            count++;
        }

        Double average() {
            return count > 0 ? sum / count : null;
        }
    }
}
