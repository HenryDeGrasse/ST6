package com.weekly.plan.service;

import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CommitDataProvider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Plan-module implementation of {@link CommitDataProvider}.
 *
 * <p>Bridges the AI module's data needs without the AI module directly
 * depending on plan entities or repositories.
 *
 * <p>Enriches each {@link CommitSummary} with three additional context signals:
 * <ol>
 *   <li><b>Structured check-in history</b> — progress-entry records from the
 *       {@code progress_entries} table (step-11), giving the LLM visibility into
 *       mid-week AT_RISK or BLOCKED signals.</li>
 *   <li><b>Carry-forward chain statuses</b> — completion statuses from ancestor
 *       commits linked via the {@code carried_from_commit_id} chain, enabling
 *       the LLM to recognise "this item was partially done last week too".</li>
 *   <li><b>Team category completion rate</b> — team-wide DONE rate for the
 *       commit's category over the last {@value #CATEGORY_RATE_WINDOW_WEEKS} weeks,
 *       giving the LLM statistical calibration context.</li>
 * </ol>
 */
@Component
public class PlanCommitDataProvider implements CommitDataProvider {

    /** Maximum depth to traverse the carried_from_commit_id chain. */
    static final int MAX_CARRY_DEPTH = 3;

    /**
     * Minimum number of historical commits with actuals required to include a
     * category completion rate (smaller samples are not statistically meaningful).
     */
    static final int MIN_CATEGORY_SAMPLE = 3;

    /** Number of historical weeks to look back when computing category completion rates. */
    static final int CATEGORY_RATE_WINDOW_WEEKS = 4;

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final WeeklyCommitActualRepository actualRepository;

    public PlanCommitDataProvider(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            ProgressEntryRepository progressEntryRepository,
            WeeklyCommitActualRepository actualRepository
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.actualRepository = actualRepository;
    }

    @Override
    public List<CommitSummary> getCommitSummaries(UUID orgId, UUID planId) {
        Optional<WeeklyPlanEntity> planOpt = planRepository.findByOrgIdAndId(orgId, planId);
        if (planOpt.isEmpty()) {
            return List.of();
        }
        WeeklyPlanEntity plan = planOpt.get();

        List<WeeklyCommitEntity> commits =
                commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
        if (commits.isEmpty()) {
            return List.of();
        }

        // Batch-load check-in history for all commits in one query
        List<UUID> commitIds = commits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, List<CheckInEntry>> checkInMap = buildCheckInMap(orgId, commitIds);

        // Batch-load carry-forward chain statuses (up to MAX_CARRY_DEPTH levels)
        Map<UUID, List<String>> carryForwardMap = buildCarryForwardStatusMap(orgId, commits);

        // Compute team category completion rates from recent history
        Map<CommitCategory, String> categoryRates =
                computeCategoryCompletionRates(orgId, plan.getWeekStartDate());

        return commits.stream()
                .map(c -> new CommitSummary(
                        c.getId().toString(),
                        c.getTitle(),
                        c.getExpectedResult(),
                        c.getProgressNotes(),
                        checkInMap.getOrDefault(c.getId(), List.of()),
                        carryForwardMap.getOrDefault(c.getId(), List.of()),
                        c.getCategory() != null ? categoryRates.get(c.getCategory()) : null
                ))
                .toList();
    }

    @Override
    public boolean planExists(UUID orgId, UUID planId) {
        return planRepository.findByOrgIdAndId(orgId, planId).isPresent();
    }

    // ── Check-in history ──────────────────────────────────────────────────────

    /**
     * Batch-loads all check-in entries for the given commit IDs and returns a map
     * from commit ID to an ordered list of {@link CheckInEntry} records.
     */
    private Map<UUID, List<CheckInEntry>> buildCheckInMap(UUID orgId, List<UUID> commitIds) {
        if (commitIds.isEmpty()) {
            return Map.of();
        }
        List<ProgressEntryEntity> entries =
                progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(orgId, commitIds);
        return entries.stream()
                .collect(Collectors.groupingBy(
                        ProgressEntryEntity::getCommitId,
                        Collectors.mapping(
                                e -> new CheckInEntry(e.getStatus().name(), e.getNote()),
                                Collectors.toList()
                        )
                ));
    }

    // ── Carry-forward chain ───────────────────────────────────────────────────

    /**
     * Builds a map from commitId → list of prior completion statuses (most-recent first)
     * by traversing the {@code carried_from_commit_id} chain up to {@link #MAX_CARRY_DEPTH}
     * levels.
     *
     * <p>Uses at most {@code MAX_CARRY_DEPTH} batched query pairs (one for actuals, one for
     * ancestor commits) to avoid N+1 database access patterns.
     *
     * <p>If an ancestor commit has been soft-deleted (past the retention window) it will
     * not be found by {@link WeeklyCommitRepository#findAllById} and chain traversal
     * stops early for that branch — this is acceptable behaviour.
     */
    private Map<UUID, List<String>> buildCarryForwardStatusMap(
            UUID orgId, List<WeeklyCommitEntity> commits) {

        // currentToAncestor: current plan commitId → direct ancestor commitId
        Map<UUID, UUID> currentToAncestor = new LinkedHashMap<>();
        for (WeeklyCommitEntity commit : commits) {
            if (commit.getCarriedFromCommitId() != null) {
                currentToAncestor.put(commit.getId(), commit.getCarriedFromCommitId());
            }
        }
        if (currentToAncestor.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<String>> result = new HashMap<>();

        for (int level = 0; level < MAX_CARRY_DEPTH && !currentToAncestor.isEmpty(); level++) {
            List<UUID> ancestorIds =
                    new ArrayList<>(new HashSet<>(currentToAncestor.values()));

            // Batch-load actuals for all ancestors at this level
            Map<UUID, CompletionStatus> actualsByAncestor = actualRepository
                    .findByOrgIdAndCommitIdIn(orgId, ancestorIds).stream()
                    .collect(Collectors.toMap(
                            WeeklyCommitActualEntity::getCommitId,
                            WeeklyCommitActualEntity::getCompletionStatus
                    ));

            // Batch-load ancestor entities to find next-level carry targets
            Map<UUID, UUID> ancestorToItsAncestor =
                    commitRepository.findAllById(ancestorIds).stream()
                            .filter(a -> a.getCarriedFromCommitId() != null)
                            .collect(Collectors.toMap(
                                    WeeklyCommitEntity::getId,
                                    WeeklyCommitEntity::getCarriedFromCommitId
                            ));

            Map<UUID, UUID> nextLevel = new LinkedHashMap<>();
            for (Map.Entry<UUID, UUID> entry : currentToAncestor.entrySet()) {
                UUID commitId = entry.getKey();
                UUID ancestorId = entry.getValue();

                CompletionStatus status = actualsByAncestor.get(ancestorId);
                result.computeIfAbsent(commitId, k -> new ArrayList<>())
                        .add(status != null ? status.name() : "UNKNOWN");

                UUID nextAncestor = ancestorToItsAncestor.get(ancestorId);
                if (nextAncestor != null) {
                    nextLevel.put(commitId, nextAncestor);
                }
            }
            currentToAncestor = nextLevel;
        }

        return result;
    }

    // ── Category completion rates ─────────────────────────────────────────────

    /**
     * Computes per-category DONE rates from the last {@link #CATEGORY_RATE_WINDOW_WEEKS}
     * weeks of org-wide plan data (excluding the current week).
     *
     * <p>Only categories with at least {@link #MIN_CATEGORY_SAMPLE} commits that have an
     * actual record are included — smaller samples are not statistically meaningful.
     *
     * @param orgId     the organisation ID
     * @param weekStart the Monday of the plan week (current week is excluded from history)
     * @return map from commit category to a pre-formatted rate string,
     *         e.g. {@code "OPERATIONS: 85% DONE (team, last 4 wks)"}
     */
    private Map<CommitCategory, String> computeCategoryCompletionRates(
            UUID orgId, LocalDate weekStart) {

        LocalDate windowEnd = weekStart.minusWeeks(1);
        LocalDate windowStart = weekStart.minusWeeks(CATEGORY_RATE_WINDOW_WEEKS);

        List<WeeklyPlanEntity> historicalPlans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, windowStart, windowEnd);
        if (historicalPlans.isEmpty()) {
            return Map.of();
        }

        List<UUID> planIds = historicalPlans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> historicalCommits =
                commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);
        if (historicalCommits.isEmpty()) {
            return Map.of();
        }

        List<UUID> commitIds =
                historicalCommits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, CompletionStatus> statusByCommitId = actualRepository
                .findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                .collect(Collectors.toMap(
                        WeeklyCommitActualEntity::getCommitId,
                        WeeklyCommitActualEntity::getCompletionStatus
                ));

        // Accumulate [totalWithActuals, doneCount] per category
        Map<CommitCategory, long[]> stats = new HashMap<>();
        for (WeeklyCommitEntity commit : historicalCommits) {
            if (commit.getCategory() == null) {
                continue;
            }
            CompletionStatus status = statusByCommitId.get(commit.getId());
            if (status == null) {
                continue; // skip commits without actuals (incomplete reconciliation)
            }
            stats.computeIfAbsent(commit.getCategory(), k -> new long[]{0L, 0L});
            stats.get(commit.getCategory())[0]++;
            if (status == CompletionStatus.DONE) {
                stats.get(commit.getCategory())[1]++;
            }
        }

        Map<CommitCategory, String> rates = new HashMap<>();
        for (Map.Entry<CommitCategory, long[]> entry : stats.entrySet()) {
            long total = entry.getValue()[0];
            if (total < MIN_CATEGORY_SAMPLE) {
                continue; // insufficient data — omit to avoid misleading LLM
            }
            long done = entry.getValue()[1];
            int pct = (int) Math.round(100.0 * done / total);
            rates.put(entry.getKey(), String.format(
                    "%s: %d%% DONE (team, last %d wks)",
                    entry.getKey().name(), pct, CATEGORY_RATE_WINDOW_WEEKS
            ));
        }
        return rates;
    }
}
