package com.weekly.plan.service;

import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.ManagerReviewEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.RcdoRollupResponse;
import com.weekly.plan.dto.TeamSummaryResponseDto;
import com.weekly.plan.repository.ManagerReviewRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ManagerInsightDataProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Plan-module implementation of {@link ManagerInsightDataProvider}.
 *
 * <p>Builds the manager-week summary that the AI module uses for beta
 * insight generation while keeping the AI module decoupled from plan internals.
 *
 * <p>In addition to the current-week snapshot, this provider queries up to
 * {@code windowWeeks} weeks of history to populate:
 * <ul>
 *   <li>carry-forward streaks per team member</li>
 *   <li>outcome-coverage trends (commit counts per outcome per week)</li>
 *   <li>late-lock frequency per team member</li>
 *   <li>aggregate review-turnaround statistics</li>
 * </ul>
 */
@Component
public class PlanManagerInsightDataProvider implements ManagerInsightDataProvider {

    private final TeamDashboardService teamDashboardService;
    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final ManagerReviewRepository reviewRepository;

    public PlanManagerInsightDataProvider(
            TeamDashboardService teamDashboardService,
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            ManagerReviewRepository reviewRepository
    ) {
        this.teamDashboardService = teamDashboardService;
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.reviewRepository = reviewRepository;
    }

    @Override
    public ManagerWeekContext getManagerWeekContext(
            UUID orgId, UUID managerId, LocalDate weekStart, int windowWeeks) {

        if (windowWeeks < 1) {
            throw new IllegalArgumentException("windowWeeks must be >= 1");
        }

        // ── Current-week snapshot (existing logic) ──────────────────────────
        TeamSummaryResponseDto summary = teamDashboardService.getTeamSummary(
                orgId, managerId, weekStart,
                0, Integer.MAX_VALUE,
                null, null, null, null, null, null
        );
        RcdoRollupResponse rollup = teamDashboardService.getRcdoRollup(orgId, managerId, weekStart);

        // Extract direct-report user IDs from the team summary so we don't
        // need a separate OrgGraphClient call.
        Set<UUID> directReportIds = summary.users().stream()
                .map(u -> UUID.fromString(u.userId()))
                .collect(Collectors.toSet());

        // ── Historical context ───────────────────────────────────────────────
        List<CarryForwardStreak> carryForwardStreaks;
        List<OutcomeCoverageTrend> outcomeCoverageTrends;
        List<LateLockPattern> lateLockPatterns;
        ReviewTurnaroundStats reviewTurnaroundStats;

        if (windowWeeks > 1 && !directReportIds.isEmpty()) {
            // Load all plans and commits once, then derive all metrics in-memory
            LocalDate windowStart = weekStart.minusWeeks(windowWeeks - 1);

            List<WeeklyPlanEntity> windowPlans = planRepository
                    .findByOrgIdAndWeekStartDateBetween(orgId, windowStart, weekStart)
                    .stream()
                    .filter(p -> directReportIds.contains(p.getOwnerUserId()))
                    .toList();

            List<UUID> planIds = windowPlans.stream().map(WeeklyPlanEntity::getId).toList();

            List<WeeklyCommitEntity> windowCommits = planIds.isEmpty()
                    ? List.of()
                    : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

            List<ManagerReviewEntity> windowReviews = planIds.isEmpty()
                    ? List.of()
                    : reviewRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

            carryForwardStreaks = computeCarryForwardStreaks(
                    directReportIds, weekStart, windowWeeks, windowPlans, windowCommits);
            outcomeCoverageTrends = computeOutcomeCoverageTrends(
                    weekStart, windowWeeks, windowPlans, windowCommits);
            lateLockPatterns = computeLateLockPatterns(
                    directReportIds, windowWeeks, windowPlans);
            reviewTurnaroundStats = computeReviewTurnaround(
                    windowPlans, windowReviews);
        } else {
            carryForwardStreaks = List.of();
            outcomeCoverageTrends = List.of();
            lateLockPatterns = List.of();
            reviewTurnaroundStats = null;
        }

        // ── Assemble result ──────────────────────────────────────────────────
        return new ManagerWeekContext(
                summary.weekStart(),
                new ReviewCounts(
                        summary.reviewStatusCounts().pending(),
                        summary.reviewStatusCounts().approved(),
                        summary.reviewStatusCounts().changesRequested()
                ),
                summary.users().stream()
                        .map(user -> new TeamMemberContext(
                                user.userId(),
                                user.state(),
                                user.reviewStatus(),
                                user.commitCount(),
                                user.incompleteCount(),
                                user.issueCount(),
                                user.nonStrategicCount(),
                                user.kingCount(),
                                user.queenCount(),
                                user.isStale(),
                                user.isLateLock()
                        ))
                        .toList(),
                rollup.items().stream()
                        .map(item -> new RcdoFocusContext(
                                item.outcomeId(),
                                item.outcomeName(),
                                item.objectiveName(),
                                item.rallyCryName(),
                                item.commitCount(),
                                item.kingCount(),
                                item.queenCount()
                        ))
                        .toList(),
                carryForwardStreaks,
                outcomeCoverageTrends,
                lateLockPatterns,
                reviewTurnaroundStats
        );
    }

    // ── Historical metric helpers ────────────────────────────────────────────

    /**
     * Computes carry-forward streaks for each direct report.
     *
     * <p>A streak counts consecutive weeks (most-recent-first) where the member
     * carried 2 or more items from the previous week.
     */
    private List<CarryForwardStreak> computeCarryForwardStreaks(
            Set<UUID> directReportIds,
            LocalDate weekStart,
            int windowWeeks,
            List<WeeklyPlanEntity> windowPlans,
            List<WeeklyCommitEntity> windowCommits
    ) {
        // Index commits by plan ID
        Map<UUID, List<WeeklyCommitEntity>> commitsByPlan = windowCommits.stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        // Group plans by user and week
        Map<UUID, Map<LocalDate, List<WeeklyPlanEntity>>> plansByUserAndWeek = windowPlans.stream()
                .collect(Collectors.groupingBy(
                        WeeklyPlanEntity::getOwnerUserId,
                        Collectors.groupingBy(WeeklyPlanEntity::getWeekStartDate)
                ));

        // Weeks ordered from most-recent to oldest
        List<LocalDate> weeks = new ArrayList<>();
        for (int i = 0; i < windowWeeks; i++) {
            weeks.add(weekStart.minusWeeks(i));
        }

        List<CarryForwardStreak> streaks = new ArrayList<>();

        for (UUID userId : directReportIds) {
            Map<LocalDate, List<WeeklyPlanEntity>> plansByWeek =
                    plansByUserAndWeek.getOrDefault(userId, Map.of());

            // Collect carry titles for each week (null = streak broken for that week)
            Map<LocalDate, List<String>> carriedItemsByWeek = new LinkedHashMap<>();
            for (LocalDate week : weeks) {
                List<String> carriedTitles = new ArrayList<>();
                for (WeeklyPlanEntity plan : plansByWeek.getOrDefault(week, List.of())) {
                    for (WeeklyCommitEntity commit
                            : commitsByPlan.getOrDefault(plan.getId(), List.of())) {
                        if (commit.getCarriedFromCommitId() != null) {
                            carriedTitles.add(commit.getTitle());
                        }
                    }
                }
                carriedItemsByWeek.put(week, carriedTitles.size() >= 2 ? carriedTitles : null);
            }

            // Count consecutive carry weeks starting from the most recent
            int streakWeeks = 0;
            List<String> streakItems = List.of();
            for (LocalDate week : weeks) {
                List<String> items = carriedItemsByWeek.get(week);
                if (items != null) {
                    streakWeeks++;
                    if (streakWeeks == 1) {
                        streakItems = items;
                    }
                } else {
                    break;
                }
            }

            if (streakWeeks > 0) {
                streaks.add(new CarryForwardStreak(userId.toString(), streakWeeks, streakItems));
            }
        }

        return streaks.stream()
                .sorted(Comparator.comparing(CarryForwardStreak::userId))
                .toList();
    }

    /**
     * Computes per-outcome commit counts across each week in the rolling window.
     * Weeks are ordered oldest-to-most-recent in the returned trend lists.
     */
    private List<OutcomeCoverageTrend> computeOutcomeCoverageTrends(
            LocalDate weekStart,
            int windowWeeks,
            List<WeeklyPlanEntity> windowPlans,
            List<WeeklyCommitEntity> windowCommits
    ) {
        // Map plan ID → week start date
        Map<UUID, LocalDate> weekByPlanId = windowPlans.stream()
                .collect(Collectors.toMap(WeeklyPlanEntity::getId, WeeklyPlanEntity::getWeekStartDate));

        // Accumulate commit counts: outcomeId → weekStart → count
        Map<UUID, Map<LocalDate, Integer>> countsByOutcomeAndWeek = new LinkedHashMap<>();
        Map<UUID, String> outcomeNames = new LinkedHashMap<>();

        for (WeeklyCommitEntity commit : windowCommits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }
            LocalDate planWeek = weekByPlanId.get(commit.getWeeklyPlanId());
            if (planWeek == null) {
                continue;
            }
            countsByOutcomeAndWeek
                    .computeIfAbsent(commit.getOutcomeId(), k -> new LinkedHashMap<>())
                    .merge(planWeek, 1, Integer::sum);
            if (commit.getSnapshotOutcomeName() != null) {
                outcomeNames.putIfAbsent(commit.getOutcomeId(), commit.getSnapshotOutcomeName());
            }
        }

        // Build the ordered week list (oldest first)
        List<LocalDate> orderedWeeks = new ArrayList<>();
        for (int i = windowWeeks - 1; i >= 0; i--) {
            orderedWeeks.add(weekStart.minusWeeks(i));
        }

        List<OutcomeCoverageTrend> trends = new ArrayList<>();
        for (Map.Entry<UUID, Map<LocalDate, Integer>> entry : countsByOutcomeAndWeek.entrySet()) {
            UUID outcomeId = entry.getKey();
            Map<LocalDate, Integer> countsByWeek = entry.getValue();

            List<WeeklyCommitCount> weekCounts = orderedWeeks.stream()
                    .map(w -> new WeeklyCommitCount(
                            w.toString(),
                            countsByWeek.getOrDefault(w, 0)))
                    .toList();

            trends.add(new OutcomeCoverageTrend(
                    outcomeId.toString(),
                    outcomeNames.getOrDefault(outcomeId, outcomeId.toString()),
                    weekCounts
            ));
        }

        return trends.stream()
                .sorted(Comparator
                        .comparing(OutcomeCoverageTrend::outcomeName)
                        .thenComparing(OutcomeCoverageTrend::outcomeId))
                .toList();
    }

    /**
     * Computes late-lock frequency per team member across the rolling window.
     * Only members who had at least one late-locked week are included.
     */
    private List<LateLockPattern> computeLateLockPatterns(
            Set<UUID> directReportIds,
            int windowWeeks,
            List<WeeklyPlanEntity> windowPlans
    ) {
        Map<UUID, Integer> lateLockCountByUser = new LinkedHashMap<>();

        for (WeeklyPlanEntity plan : windowPlans) {
            if (plan.getLockType() == LockType.LATE_LOCK) {
                lateLockCountByUser.merge(plan.getOwnerUserId(), 1, Integer::sum);
            }
        }

        return directReportIds.stream()
                .filter(userId -> lateLockCountByUser.containsKey(userId))
                .sorted(Comparator.comparing(UUID::toString)) // stable ordering
                .map(userId -> new LateLockPattern(
                        userId.toString(),
                        lateLockCountByUser.get(userId),
                        windowWeeks
                ))
                .toList();
    }

    /**
     * Computes the average days from plan lock to first manager-review submission.
     *
     * <p>This is an approximation of "RECONCILED → review" turnaround because
     * the schema does not record an explicit {@code reconciledAt} timestamp.
     * Plans without a lock timestamp or without any reviews are excluded.
     *
     * @return aggregate stats, or {@code null} if no reviewed plans were found
     */
    private ReviewTurnaroundStats computeReviewTurnaround(
            List<WeeklyPlanEntity> windowPlans,
            List<ManagerReviewEntity> windowReviews
    ) {
        if (windowReviews.isEmpty()) {
            return null;
        }

        // Group reviews by plan ID
        Map<UUID, List<ManagerReviewEntity>> reviewsByPlanId = windowReviews.stream()
                .collect(Collectors.groupingBy(ManagerReviewEntity::getWeeklyPlanId));

        double totalDays = 0;
        int sampleCount = 0;

        for (WeeklyPlanEntity plan : windowPlans) {
            Instant lockedAt = plan.getLockedAt();
            if (lockedAt == null) {
                continue;
            }
            List<ManagerReviewEntity> reviews = reviewsByPlanId.get(plan.getId());
            if (reviews == null || reviews.isEmpty()) {
                continue;
            }

            // First review submission time
            Instant firstReviewAt = reviews.stream()
                    .map(ManagerReviewEntity::getCreatedAt)
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            if (firstReviewAt != null && !firstReviewAt.isBefore(lockedAt)) {
                totalDays += Duration.between(lockedAt, firstReviewAt).toMillis() / 86_400_000.0;
                sampleCount++;
            }
        }

        return sampleCount == 0 ? null : new ReviewTurnaroundStats(totalDays / sampleCount, sampleCount);
    }
}
