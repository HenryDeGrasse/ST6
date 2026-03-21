package com.weekly.admin;

import com.weekly.ai.AiCacheService;
import com.weekly.ai.AiSuggestionFeedbackEntity;
import com.weekly.ai.AiSuggestionFeedbackRepository;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.ReviewStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link AdminDashboardService}.
 *
 * <p>All three metric areas load data via existing Spring Data JPA repositories
 * and perform aggregation in-process, following the same pattern as
 * {@link com.weekly.trends.DefaultTrendsService}.
 *
 * <h3>Adoption metrics</h3>
 * <ul>
 *   <li>Loads all plans for the org in the rolling window.</li>
 *   <li>Groups by {@code week_start_date} and counts distinct users / funnel stages.</li>
 *   <li>Cadence compliance = plans locked ON_TIME / total locked plans.</li>
 * </ul>
 *
 * <h3>AI usage metrics</h3>
 * <ul>
 *   <li>Loads {@code ai_suggestion_feedback} records for the window and counts by action.</li>
 *   <li>Reads org-scoped hit/miss counters from {@link AiCacheService} (in-process; reset on
 *       restart) and converts them into coarse token-spend / token-saved estimates.</li>
 * </ul>
 *
 * <h3>RCDO health</h3>
 * <ul>
 *   <li>Loads locked commits with a non-null {@code snapshot_outcome_id} from the last
 *       {@value #RCDO_WINDOW_WEEKS} weeks.</li>
 *   <li>Merges commit counts against the full RCDO tree so uncovered outcomes are surfaced.</li>
 * </ul>
 */
@Service
public class DefaultAdminDashboardService implements AdminDashboardService {

    /** Fixed window used for RCDO health analysis. */
    static final int RCDO_WINDOW_WEEKS = 8;

    /**
     * Coarse token estimate per cacheable AI request.
     *
     * <p>The current AI surfaces in this service range from a few hundred to roughly
     * 1.5K tokens/request depending on feature. We intentionally use a single round-number
     * heuristic so admins get directional spend/savings visibility without introducing
     * model-specific pricing logic into this step.
     */
    static final long APPROX_TOKENS_PER_CACHEABLE_REQUEST = 1_000L;

    /**
     * Plan states that indicate the plan was locked (at any point in its lifecycle).
     * Includes all states that follow LOCKED in the state machine.
     */
    private static final Set<PlanState> LOCKED_OR_BEYOND = EnumSet.of(
            PlanState.LOCKED, PlanState.RECONCILING, PlanState.RECONCILED, PlanState.CARRY_FORWARD);

    /**
     * Plan states that indicate the plan has been reconciled.
     */
    private static final Set<PlanState> RECONCILED_OR_BEYOND = EnumSet.of(
            PlanState.RECONCILED, PlanState.CARRY_FORWARD);

    /**
     * Review statuses that indicate a manager actually reviewed the plan
     * (not just pending or not applicable).
     */
    private static final Set<ReviewStatus> REVIEWED_STATUSES = EnumSet.of(
            ReviewStatus.APPROVED, ReviewStatus.CHANGES_REQUESTED);

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final AiSuggestionFeedbackRepository feedbackRepository;
    private final AiCacheService aiCacheService;
    private final RcdoClient rcdoClient;

    public DefaultAdminDashboardService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            AiSuggestionFeedbackRepository feedbackRepository,
            AiCacheService aiCacheService,
            RcdoClient rcdoClient
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.feedbackRepository = feedbackRepository;
        this.aiCacheService = aiCacheService;
        this.rcdoClient = rcdoClient;
    }

    // ── Adoption Metrics ──────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Loads all plans in the rolling window and aggregates the funnel counts
     * in-memory, grouped by {@code week_start_date}.
     */
    @Override
    @Transactional(readOnly = true)
    public AdoptionMetrics getAdoptionMetrics(UUID orgId, int weeks) {
        int clamped = Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, weeks));

        LocalDate windowEnd = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate windowStart = windowEnd.minusWeeks(clamped - 1L);

        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, windowStart, windowEnd);

        // ── Group plans by week ───────────────────────────────────────────────
        Map<LocalDate, List<WeeklyPlanEntity>> byWeek = plans.stream()
                .collect(Collectors.groupingBy(WeeklyPlanEntity::getWeekStartDate));

        // ── Per-week funnel points (all Mondays in window, sparse fill) ───────
        List<AdoptionMetrics.WeeklyAdoptionPoint> weeklyPoints = new ArrayList<>();
        LocalDate cursor = windowStart;
        while (!cursor.isAfter(windowEnd)) {
            List<WeeklyPlanEntity> weekPlans = byWeek.getOrDefault(cursor, List.of());
            weeklyPoints.add(buildWeekPoint(cursor, weekPlans));
            cursor = cursor.plusWeeks(1);
        }

        // ── Aggregate totals ──────────────────────────────────────────────────
        long totalActiveUsers = plans.stream()
                .map(WeeklyPlanEntity::getOwnerUserId)
                .distinct()
                .count();

        long totalLocked = plans.stream()
                .filter(p -> p.getLockType() != null)
                .count();
        long onTimeLocked = plans.stream()
                .filter(p -> p.getLockType() == com.weekly.plan.domain.LockType.ON_TIME)
                .count();
        double cadenceComplianceRate = totalLocked == 0
                ? 0.0
                : (double) onTimeLocked / totalLocked;

        return new AdoptionMetrics(
                clamped,
                windowStart.toString(),
                windowEnd.toString(),
                (int) totalActiveUsers,
                cadenceComplianceRate,
                weeklyPoints
        );
    }

    private AdoptionMetrics.WeeklyAdoptionPoint buildWeekPoint(
            LocalDate weekStart, List<WeeklyPlanEntity> weekPlans
    ) {
        int activeUsers = (int) weekPlans.stream()
                .map(WeeklyPlanEntity::getOwnerUserId)
                .distinct()
                .count();
        int plansCreated = weekPlans.size();
        int plansLocked = (int) weekPlans.stream()
                .filter(p -> LOCKED_OR_BEYOND.contains(p.getState()))
                .count();
        int plansReconciled = (int) weekPlans.stream()
                .filter(p -> RECONCILED_OR_BEYOND.contains(p.getState()))
                .count();
        int plansReviewed = (int) weekPlans.stream()
                .filter(p -> REVIEWED_STATUSES.contains(p.getReviewStatus()))
                .count();
        return new AdoptionMetrics.WeeklyAdoptionPoint(
                weekStart.toString(),
                activeUsers,
                plansCreated,
                plansLocked,
                plansReconciled,
                plansReviewed
        );
    }

    // ── AI Usage Metrics ──────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Loads suggestion feedback records created within the rolling window.
     * Org-scoped cache hit/miss counters are read from {@link AiCacheService};
     * they accumulate since the last service restart and are translated into
     * coarse token-spend / token-saved estimates.
     */
    @Override
    @Transactional(readOnly = true)
    public AiUsageMetrics getAiUsageMetrics(UUID orgId, int weeks) {
        int clamped = Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, weeks));

        LocalDate windowEnd = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate windowStart = windowEnd.minusWeeks(clamped - 1L);
        Instant since = windowStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        List<AiSuggestionFeedbackEntity> feedback =
                feedbackRepository.findByOrgIdAndCreatedAtAfter(orgId, since);

        long totalFeedbackCount = feedback.size();
        long acceptedCount = feedback.stream()
                .filter(f -> "ACCEPT".equals(f.getAction()))
                .count();
        long deferredCount = feedback.stream()
                .filter(f -> "DEFER".equals(f.getAction()))
                .count();
        long declinedCount = feedback.stream()
                .filter(f -> "DECLINE".equals(f.getAction()))
                .count();
        double acceptanceRate = totalFeedbackCount == 0
                ? 0.0
                : (double) acceptedCount / totalFeedbackCount;

        long hits = aiCacheService.getCacheHits(orgId);
        long misses = aiCacheService.getCacheMisses(orgId);
        long totalCacheRequests = hits + misses;
        double cacheHitRate = totalCacheRequests == 0
                ? 0.0
                : (double) hits / totalCacheRequests;
        long approximateTokensSpent = misses * APPROX_TOKENS_PER_CACHEABLE_REQUEST;
        long approximateTokensSaved = hits * APPROX_TOKENS_PER_CACHEABLE_REQUEST;

        return new AiUsageMetrics(
                clamped,
                windowStart.toString(),
                windowEnd.toString(),
                totalFeedbackCount,
                acceptedCount,
                deferredCount,
                declinedCount,
                acceptanceRate,
                hits,
                misses,
                cacheHitRate,
                approximateTokensSpent,
                approximateTokensSaved
        );
    }

    // ── RCDO Health ───────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Examines the last {@value #RCDO_WINDOW_WEEKS} weeks of locked commits
     * and merges the commit counts against the full RCDO tree so uncovered
     * (stale) outcomes can be identified.
     */
    @Override
    @Transactional(readOnly = true)
    public RcdoHealthReport getRcdoHealth(UUID orgId) {
        LocalDate windowEnd = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate windowStart = windowEnd.minusWeeks(RCDO_WINDOW_WEEKS - 1L);

        // ── 1. Load plans in window ───────────────────────────────────────────
        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, windowStart, windowEnd);
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();

        // ── 2. Load commits for those plans, keep only locked ones (snapshot set) ─
        List<WeeklyCommitEntity> allCommits = planIds.isEmpty()
                ? List.of()
                : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        // Count commits per snapshotOutcomeId (non-null snapshots only)
        Map<UUID, Long> commitCountByOutcome = allCommits.stream()
                .filter(c -> c.getSnapshotOutcomeId() != null)
                .collect(Collectors.groupingBy(
                        WeeklyCommitEntity::getSnapshotOutcomeId, Collectors.counting()));

        // ── 3. Load RCDO tree and build health items ──────────────────────────
        RcdoTree tree = rcdoClient.getTree(orgId);

        List<RcdoHealthReport.OutcomeHealthItem> allItems = buildHealthItems(tree, commitCountByOutcome);

        int totalOutcomes = allItems.size();
        int coveredOutcomes = (int) allItems.stream()
                .filter(item -> item.commitCount() > 0)
                .count();

        // Top outcomes: all, sorted by commitCount descending
        List<RcdoHealthReport.OutcomeHealthItem> topOutcomes = allItems.stream()
                .filter(item -> item.commitCount() > 0)
                .sorted(Comparator.comparingInt(RcdoHealthReport.OutcomeHealthItem::commitCount).reversed())
                .toList();

        // Stale outcomes: zero commits, sorted alphabetically by outcomeName
        List<RcdoHealthReport.OutcomeHealthItem> staleOutcomes = allItems.stream()
                .filter(item -> item.commitCount() == 0)
                .sorted(Comparator.comparing(RcdoHealthReport.OutcomeHealthItem::outcomeName))
                .toList();

        return new RcdoHealthReport(
                Instant.now().toString(),
                RCDO_WINDOW_WEEKS,
                totalOutcomes,
                coveredOutcomes,
                topOutcomes,
                staleOutcomes
        );
    }

    private List<RcdoHealthReport.OutcomeHealthItem> buildHealthItems(
            RcdoTree tree, Map<UUID, Long> commitCountByOutcome
    ) {
        List<RcdoHealthReport.OutcomeHealthItem> items = new ArrayList<>();
        for (RcdoTree.RallyCry rc : tree.rallyCries()) {
            for (RcdoTree.Objective obj : rc.objectives()) {
                for (RcdoTree.Outcome outcome : obj.outcomes()) {
                    int commitCount = 0;
                    try {
                        UUID outcomeUuid = UUID.fromString(outcome.id());
                        commitCount = commitCountByOutcome
                                .getOrDefault(outcomeUuid, 0L)
                                .intValue();
                    } catch (IllegalArgumentException ignored) {
                        // Non-UUID outcome ID: treated as 0 commits
                    }
                    items.add(new RcdoHealthReport.OutcomeHealthItem(
                            outcome.id(),
                            outcome.name(),
                            obj.id(),
                            obj.name(),
                            rc.id(),
                            rc.name(),
                            commitCount
                    ));
                }
            }
        }
        return items;
    }
}
