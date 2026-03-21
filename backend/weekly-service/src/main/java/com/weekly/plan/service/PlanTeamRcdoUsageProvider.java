package com.weekly.plan.service;

import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.TeamRcdoUsageProvider;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan-module implementation of {@link TeamRcdoUsageProvider}.
 *
 * <p>Queries all plans and their commits for a given org+week, then
 * aggregates commit counts per RCDO outcome. Results are returned sorted
 * by commit count descending so callers can trivially take the top-N.
 *
 * <p>Results are intended to be cached for ~1 hour by the AI module
 * (see {@code AiCacheService.buildTeamRcdoUsageKey}).
 */
@Component
public class PlanTeamRcdoUsageProvider implements TeamRcdoUsageProvider {

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;

    public PlanTeamRcdoUsageProvider(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TeamRcdoUsageResult getTeamRcdoUsage(UUID orgId, LocalDate weekStart) {
        List<WeeklyCommitEntity> weekCommits = loadCommitsForWeek(orgId, weekStart, weekStart);
        List<WeeklyCommitEntity> quarterCommits = loadCommitsForWeek(
                orgId, startOfQuarter(weekStart), weekStart
        );

        // Accumulate counts and capture outcome names as encountered for the current week
        Map<String, int[]> countByOutcomeId = new LinkedHashMap<>();
        Map<String, String> nameByOutcomeId = new LinkedHashMap<>();

        for (WeeklyCommitEntity commit : weekCommits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }
            String outcomeId = commit.getOutcomeId().toString();
            countByOutcomeId.computeIfAbsent(outcomeId, k -> new int[]{0})[0]++;
            // Prefer snapshot name; only store the first non-null one seen
            if (commit.getSnapshotOutcomeName() != null
                    && !nameByOutcomeId.containsKey(outcomeId)) {
                nameByOutcomeId.put(outcomeId, commit.getSnapshotOutcomeName());
            }
        }

        List<OutcomeUsage> outcomes = countByOutcomeId.entrySet().stream()
                .map(e -> new OutcomeUsage(
                        e.getKey(),
                        nameByOutcomeId.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue()[0]
                ))
                .sorted(Comparator.comparingInt(OutcomeUsage::commitCount).reversed()
                        .thenComparing(OutcomeUsage::outcomeName))
                .toList();

        Set<String> coveredOutcomeIdsThisQuarter = quarterCommits.stream()
                .map(WeeklyCommitEntity::getOutcomeId)
                .filter(java.util.Objects::nonNull)
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return new TeamRcdoUsageResult(outcomes, coveredOutcomeIdsThisQuarter);
    }

    private List<WeeklyCommitEntity> loadCommitsForWeek(UUID orgId, LocalDate startDate, LocalDate endDate) {
        List<WeeklyPlanEntity> plans = planRepository.findByOrgIdAndWeekStartDateBetween(orgId, startDate, endDate);
        if (plans.isEmpty()) {
            return List.of();
        }
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        return commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);
    }

    private LocalDate startOfQuarter(LocalDate weekStart) {
        int startMonth = ((weekStart.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(weekStart.getYear(), startMonth, 1);
    }
}
