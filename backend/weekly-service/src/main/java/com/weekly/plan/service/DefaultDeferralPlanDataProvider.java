package com.weekly.plan.service;

import com.weekly.assignment.domain.WeeklyAssignmentEntity;
import com.weekly.assignment.repository.WeeklyAssignmentRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.DeferralPlanDataProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan-module implementation of {@link DeferralPlanDataProvider}.
 *
 * <p>Queries the {@code weekly_plans} and {@code weekly_assignments} tables to expose the
 * current week's assignment summary to the AI deferral service without creating a direct
 * AI → plan dependency.
 */
@Component
public class DefaultDeferralPlanDataProvider implements DeferralPlanDataProvider {

    private final WeeklyPlanRepository planRepository;
    private final WeeklyAssignmentRepository assignmentRepository;

    public DefaultDeferralPlanDataProvider(
            WeeklyPlanRepository planRepository,
            WeeklyAssignmentRepository assignmentRepository
    ) {
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanAssignmentSummary> getCurrentPlanAssignments(
            UUID orgId, UUID userId, LocalDate weekStart) {
        return planRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, userId, weekStart)
                .map(plan -> {
                    List<WeeklyAssignmentEntity> assignments =
                            assignmentRepository.findAllByOrgIdAndWeeklyPlanId(orgId, plan.getId());
                    return assignments.stream()
                            .map(a -> new PlanAssignmentSummary(
                                    a.getId(),
                                    a.getIssueId(),
                                    a.getChessPriorityOverride()
                            ))
                            .toList();
                })
                .orElse(List.of());
    }
}
