package com.weekly.plan.service;

import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.NextWorkDataProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan-module implementation of {@link NextWorkDataProvider}.
 *
 * <p>Queries the {@code weekly_plans}, {@code weekly_commits}, and
 * {@code weekly_commit_actuals} tables to produce carry-forward items and
 * RCDO coverage-gap data without the AI module depending on plan internals.
 */
@Component
public class PlanNextWorkDataProvider implements NextWorkDataProvider {

    /** Minimum recent consecutive missing weeks required to surface a gap. */
    static final int MIN_COVERAGE_GAP_WEEKS = 2;

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository actualRepository;

    public PlanNextWorkDataProvider(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.actualRepository = actualRepository;
    }

    /**
     * Returns carry-forward items from the user's recent reconciled plans.
     *
     * <p>For each RECONCILED or CARRY_FORWARD plan within the look-back window,
     * commits whose actual status is not DONE are returned as carry-forward
     * candidates. Items are sorted by how many consecutive weeks they have
     * been carried (descending) so the most overdue items appear first.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CarryForwardItem> getRecentCarryForwardItems(
            UUID orgId, UUID userId, LocalDate asOf, int weeksBack) {
        LocalDate windowStart = asOf.minusWeeks(weeksBack);
        LocalDate windowEnd = asOf.minusWeeks(1);

        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, windowEnd);

        // Only reconciled / carry-forward plans have actuals
        List<WeeklyPlanEntity> reconciledPlans = plans.stream()
                .filter(p -> p.getState() == PlanState.RECONCILED
                        || p.getState() == PlanState.CARRY_FORWARD)
                .toList();

        if (reconciledPlans.isEmpty()) {
            return List.of();
        }

        List<UUID> planIds = reconciledPlans.stream()
                .map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> allCommits =
                commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);
        Map<UUID, List<WeeklyCommitEntity>> commitsByPlan = allCommits.stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        List<UUID> allCommitIds = allCommits.stream()
                .map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsById = new HashMap<>();
        if (!allCommitIds.isEmpty()) {
            actualRepository.findByOrgIdAndCommitIdIn(orgId, allCommitIds)
                    .forEach(a -> actualsById.put(a.getCommitId(), a));
        }

        // Carry-forward age: track how many consecutive windows each commit appears
        // (keyed by sourceCommitId resolved via carried_from_commit_id chain)
        Map<UUID, Integer> carryForwardWeeksById = new HashMap<>();
        Map<UUID, LocalDate> sourceWeekById = new HashMap<>();

        List<CarryForwardItem> result = new ArrayList<>();

        // Iterate plans oldest-to-newest to accumulate carry-forward weeks
        for (WeeklyPlanEntity plan : reconciledPlans) {
            List<WeeklyCommitEntity> planCommits =
                    commitsByPlan.getOrDefault(plan.getId(), List.of());
            for (WeeklyCommitEntity commit : planCommits) {
                WeeklyCommitActualEntity actual = actualsById.get(commit.getId());
                boolean notDone = actual == null
                        || actual.getCompletionStatus() != CompletionStatus.DONE;
                if (!notDone) {
                    continue;
                }
                // Resolve source commit (follow carry lineage to the original)
                UUID sourceId = resolveSourceCommitId(commit, allCommits);
                int weeks = carryForwardWeeksById.getOrDefault(sourceId, 0) + 1;
                carryForwardWeeksById.put(sourceId, weeks);
                if (!sourceWeekById.containsKey(sourceId)) {
                    sourceWeekById.put(sourceId, plan.getWeekStartDate());
                }
            }
        }

        // Build result from the most recent plan's incomplete commits
        WeeklyPlanEntity latestPlan = reconciledPlans.get(reconciledPlans.size() - 1);
        List<WeeklyCommitEntity> latestCommits =
                commitsByPlan.getOrDefault(latestPlan.getId(), List.of());

        for (WeeklyCommitEntity commit : latestCommits) {
            WeeklyCommitActualEntity actual = actualsById.get(commit.getId());
            boolean notDone = actual == null
                    || actual.getCompletionStatus() != CompletionStatus.DONE;
            if (!notDone) {
                continue;
            }
            UUID sourceId = resolveSourceCommitId(commit, allCommits);
            int cfWeeks = carryForwardWeeksById.getOrDefault(sourceId, 1);
            LocalDate sourceWeek = sourceWeekById.getOrDefault(
                    sourceId, latestPlan.getWeekStartDate());
            result.add(new CarryForwardItem(
                    sourceId,
                    commit.getTitle(),
                    commit.getDescription(),
                    commit.getChessPriority() != null ? commit.getChessPriority().name() : null,
                    commit.getCategory() != null ? commit.getCategory().name() : null,
                    commit.getOutcomeId() != null ? commit.getOutcomeId().toString() : null,
                    commit.getSnapshotOutcomeName(),
                    commit.getSnapshotRallyCryId() != null
                            ? commit.getSnapshotRallyCryId().toString() : null,
                    commit.getSnapshotRallyCryName(),
                    commit.getSnapshotObjectiveName(),
                    commit.getNonStrategicReason(),
                    commit.getExpectedResult(),
                    cfWeeks,
                    sourceWeek
            ));
        }

        // Sort by carry-forward age descending (oldest carries first)
        result.sort(Comparator.comparingInt(CarryForwardItem::carryForwardWeeks).reversed());
        return List.copyOf(result);
    }

    /**
     * Returns the user's recent commit history for LLM prompt context.
     *
     * <p>Entries are ordered newest-first and include strategic metadata plus the
     * best available status signal. When reconciliation actuals exist, the actual
     * completion status is used; otherwise the owning plan's lifecycle state is
     * surfaced so the LLM still sees whether the work is merely locked/in-flight.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RecentCommitContext> getRecentCommitHistory(
            UUID orgId, UUID userId, LocalDate asOf, int weeksBack) {
        LocalDate windowStart = asOf.minusWeeks(weeksBack);
        LocalDate windowEnd = asOf.minusWeeks(1);

        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, windowEnd);
        if (plans.isEmpty()) {
            return List.of();
        }

        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> commits = commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);
        if (commits.isEmpty()) {
            return List.of();
        }

        Map<UUID, WeeklyPlanEntity> plansById = plans.stream().collect(Collectors.toMap(
                WeeklyPlanEntity::getId, p -> p));
        List<UUID> commitIds = commits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsById = new HashMap<>();
        actualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds)
                .forEach(a -> actualsById.put(a.getCommitId(), a));

        return commits.stream()
                .sorted(Comparator
                        .comparing((WeeklyCommitEntity commit) -> {
                            WeeklyPlanEntity plan = plansById.get(commit.getWeeklyPlanId());
                            return plan != null ? plan.getWeekStartDate() : LocalDate.MIN;
                        }).reversed()
                        .thenComparing(WeeklyCommitEntity::getTitle, Comparator.nullsLast(String::compareTo)))
                .map(commit -> toRecentCommitContext(commit, plansById.get(commit.getWeeklyPlanId()), actualsById))
                .toList();
    }

    /**
     * Returns RCDO outcomes that the team has worked on historically but has
     * ignored recently.
     *
     * <p>For each historically-active outcome in the reference window, counts the
     * consecutive missing weeks working backward from {@code asOf - 1 week} up to
     * {@code gapWeeksBack}. Outcomes surface only when they have been untouched for
     * at least {@value #MIN_COVERAGE_GAP_WEEKS} recent weeks, which allows the
     * service layer to rank 2-, 3-, and 4-week gaps by severity.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RcdoCoverageGap> getTeamCoverageGaps(
            UUID orgId, LocalDate asOf, int gapWeeksBack, int refWeeksBack) {
        LocalDate refWindowStart = asOf.minusWeeks(refWeeksBack);
        LocalDate windowEnd = asOf.minusWeeks(1);

        if (windowEnd.isBefore(refWindowStart)) {
            return List.of();
        }

        List<WeeklyPlanEntity> refPlans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, refWindowStart, windowEnd);
        if (refPlans.isEmpty()) {
            return List.of();
        }

        Map<UUID, LocalDate> planWeeks = refPlans.stream()
                .collect(Collectors.toMap(WeeklyPlanEntity::getId, WeeklyPlanEntity::getWeekStartDate));
        List<UUID> planIds = refPlans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> refCommits = commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);
        if (refCommits.isEmpty()) {
            return List.of();
        }

        Map<String, OutcomeActivity> refActivity = aggregateOutcomeActivity(refCommits);
        Map<String, Map<LocalDate, Integer>> weeklyCountsByOutcome = aggregateWeeklyOutcomeCounts(
                refCommits, planWeeks);

        List<RcdoCoverageGap> gaps = new ArrayList<>();
        for (Map.Entry<String, OutcomeActivity> entry : refActivity.entrySet()) {
            String outcomeId = entry.getKey();
            OutcomeActivity activity = entry.getValue();
            int weeksMissing = countRecentMissingWeeks(
                    weeklyCountsByOutcome.getOrDefault(outcomeId, Map.of()),
                    windowEnd,
                    gapWeeksBack);
            if (weeksMissing < MIN_COVERAGE_GAP_WEEKS) {
                continue;
            }
            gaps.add(new RcdoCoverageGap(
                    outcomeId,
                    activity.outcomeName(),
                    activity.objectiveName(),
                    activity.rallyCryName(),
                    weeksMissing,
                    activity.totalCommits()
            ));
        }

        gaps.sort(Comparator.comparingInt(RcdoCoverageGap::weeksMissing).reversed()
                .thenComparing(
                        Comparator.comparingInt(RcdoCoverageGap::teamCommitsPrevWindow).reversed()));

        return List.copyOf(gaps);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Aggregates outcome activity from a list of commits.
     * Returns a map from outcomeId → {@link OutcomeActivity}.
     */
    private Map<String, OutcomeActivity> aggregateOutcomeActivity(
            List<WeeklyCommitEntity> commits) {
        Map<String, OutcomeActivity> activity = new LinkedHashMap<>();
        for (WeeklyCommitEntity commit : commits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }
            String outcomeId = commit.getOutcomeId().toString();
            activity.compute(outcomeId, (id, existing) -> {
                if (existing == null) {
                    return new OutcomeActivity(
                            commit.getSnapshotOutcomeName() != null
                                    ? commit.getSnapshotOutcomeName() : outcomeId,
                            commit.getSnapshotObjectiveName(),
                            commit.getSnapshotRallyCryName(),
                            1
                    );
                }
                return new OutcomeActivity(
                        existing.outcomeName(),
                        existing.objectiveName() != null
                                ? existing.objectiveName()
                                : commit.getSnapshotObjectiveName(),
                        existing.rallyCryName() != null
                                ? existing.rallyCryName()
                                : commit.getSnapshotRallyCryName(),
                        existing.totalCommits() + 1
                );
            });
        }
        return activity;
    }

    /**
     * Builds weekly commit counts per outcome across the loaded reference window.
     */
    private Map<String, Map<LocalDate, Integer>> aggregateWeeklyOutcomeCounts(
            List<WeeklyCommitEntity> commits,
            Map<UUID, LocalDate> planWeeks
    ) {
        Map<String, Map<LocalDate, Integer>> counts = new HashMap<>();
        for (WeeklyCommitEntity commit : commits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }
            LocalDate weekStart = planWeeks.get(commit.getWeeklyPlanId());
            if (weekStart == null) {
                continue;
            }
            counts.computeIfAbsent(commit.getOutcomeId().toString(), ignored -> new HashMap<>())
                    .merge(weekStart, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Counts the consecutive recent weeks with zero team commits for an outcome,
     * working backward from the most recent completed week.
     */
    private int countRecentMissingWeeks(
            Map<LocalDate, Integer> weeklyCounts,
            LocalDate mostRecentWeek,
            int gapWeeksBack
    ) {
        int missingWeeks = 0;
        LocalDate weekCursor = mostRecentWeek;
        for (int i = 0; i < gapWeeksBack; i++, weekCursor = weekCursor.minusWeeks(1)) {
            if (weeklyCounts.getOrDefault(weekCursor, 0) > 0) {
                break;
            }
            missingWeeks++;
        }
        return missingWeeks;
    }

    private RecentCommitContext toRecentCommitContext(
            WeeklyCommitEntity commit,
            WeeklyPlanEntity plan,
            Map<UUID, WeeklyCommitActualEntity> actualsById
    ) {
        WeeklyCommitActualEntity actual = actualsById.get(commit.getId());
        String completionStatus = deriveCompletionStatus(plan, actual);
        LocalDate weekStart = plan != null ? plan.getWeekStartDate() : LocalDate.MIN;
        return new RecentCommitContext(
                commit.getId(),
                weekStart,
                commit.getTitle(),
                commit.getOutcomeId() != null ? commit.getOutcomeId().toString() : null,
                commit.getSnapshotOutcomeName(),
                commit.getSnapshotObjectiveName(),
                commit.getSnapshotRallyCryName(),
                completionStatus
        );
    }

    private String deriveCompletionStatus(
            WeeklyPlanEntity plan,
            WeeklyCommitActualEntity actual
    ) {
        if (actual != null && actual.getCompletionStatus() != null) {
            return actual.getCompletionStatus().name();
        }
        if (plan == null || plan.getState() == null) {
            return "UNKNOWN";
        }
        return switch (plan.getState()) {
            case RECONCILED, CARRY_FORWARD -> CompletionStatus.NOT_DONE.name();
            case DRAFT, LOCKED, RECONCILING -> plan.getState().name();
        };
    }

    /**
     * Resolves the canonical source commit ID by following the carry lineage.
     * If this commit was carried from another commit, walks back to find the root.
     * Stops if the lineage cannot be resolved.
     */
    private UUID resolveSourceCommitId(
            WeeklyCommitEntity commit,
            List<WeeklyCommitEntity> allCommits
    ) {
        if (commit.getCarriedFromCommitId() == null) {
            return commit.getId();
        }
        Map<UUID, WeeklyCommitEntity> byId = allCommits.stream()
                .collect(Collectors.toMap(WeeklyCommitEntity::getId, c -> c,
                        (a, b) -> a)); // keep first
        WeeklyCommitEntity current = commit;
        // Safety limit to prevent infinite loops on corrupt data
        for (int i = 0; i < 20; i++) {
            UUID parentId = current.getCarriedFromCommitId();
            if (parentId == null) {
                return current.getId();
            }
            WeeklyCommitEntity parent = byId.get(parentId);
            if (parent == null) {
                // Lineage goes out of our loaded window — use current as source
                return current.getId();
            }
            current = parent;
        }
        return current.getId();
    }

    // ── Local value types ─────────────────────────────────────────────────────

    /**
     * Aggregated activity for a single outcome across all commits in a window.
     */
    private record OutcomeActivity(
            String outcomeName,
            String objectiveName,
            String rallyCryName,
            int totalCommits
    ) {}
}
