package com.weekly.plan.service;

import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.PlanQualityDataProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan-module implementation of {@link PlanQualityDataProvider}.
 *
 * <p>Bridges the AI module's plan-quality data needs without the AI module
 * depending directly on plan entities or repositories.
 */
@Component
public class PlanCommitQualityDataProvider implements PlanQualityDataProvider {

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;

    public PlanCommitQualityDataProvider(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanQualityContext getPlanQualityContext(UUID orgId, UUID planId, UUID userId) {
        Optional<WeeklyPlanEntity> plan = planRepository.findByOrgIdAndId(orgId, planId);
        if (plan.isEmpty() || !userId.equals(plan.get().getOwnerUserId())) {
            return PlanQualityContext.empty();
        }
        List<WeeklyCommitEntity> commits =
                commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
        return new PlanQualityContext(
                true,
                plan.get().getWeekStartDate().toString(),
                toSummaries(commits)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlanQualityContext getPreviousWeekQualityContext(
            UUID orgId, UUID userId, LocalDate currentWeekStart) {
        LocalDate previousWeekStart = currentWeekStart.minusWeeks(1);
        Optional<WeeklyPlanEntity> plan = planRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, userId, previousWeekStart);
        if (plan.isEmpty()) {
            return PlanQualityContext.empty();
        }
        List<WeeklyCommitEntity> commits =
                commitRepository.findByOrgIdAndWeeklyPlanId(orgId, plan.get().getId());
        return new PlanQualityContext(
                true,
                previousWeekStart.toString(),
                toSummaries(commits)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public double getTeamStrategicAlignmentRate(UUID orgId, LocalDate weekStart) {
        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, weekStart, weekStart);
        if (plans.isEmpty()) {
            return 0.0;
        }
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> commits =
                commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);
        if (commits.isEmpty()) {
            return 0.0;
        }
        long strategic = commits.stream()
                .filter(c -> c.getOutcomeId() != null)
                .count();
        return (double) strategic / commits.size();
    }

    // ── Private helpers ───────────────────────────────────────

    private List<CommitQualitySummary> toSummaries(List<WeeklyCommitEntity> commits) {
        return commits.stream()
                .map(c -> new CommitQualitySummary(
                        c.getCategory() != null ? c.getCategory().name() : null,
                        c.getChessPriority() != null ? c.getChessPriority().name() : null,
                        c.getOutcomeId() != null ? c.getOutcomeId().toString() : null,
                        c.getSnapshotRallyCryId() != null
                                ? c.getSnapshotRallyCryId().toString() : null
                ))
                .toList();
    }
}
