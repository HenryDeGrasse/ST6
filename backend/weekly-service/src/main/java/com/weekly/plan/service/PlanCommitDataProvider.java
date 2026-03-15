package com.weekly.plan.service;

import com.weekly.shared.CommitDataProvider;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Plan-module implementation of {@link CommitDataProvider}.
 *
 * <p>Bridges the AI module's data needs without the AI module directly
 * depending on plan entities or repositories.
 */
@Component
public class PlanCommitDataProvider implements CommitDataProvider {

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;

    public PlanCommitDataProvider(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
    }

    @Override
    public List<CommitSummary> getCommitSummaries(UUID orgId, UUID planId) {
        if (!planExists(orgId, planId)) {
            return List.of();
        }

        List<WeeklyCommitEntity> commits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
        return commits.stream()
                .map(c -> new CommitSummary(
                        c.getId().toString(),
                        c.getTitle(),
                        c.getExpectedResult(),
                        c.getProgressNotes()
                ))
                .toList();
    }

    @Override
    public boolean planExists(UUID orgId, UUID planId) {
        return planRepository.findByOrgIdAndId(orgId, planId).isPresent();
    }
}
