package com.weekly.plan.service;

import com.weekly.plan.dto.RcdoRollupResponse;
import com.weekly.plan.dto.TeamSummaryResponseDto;
import com.weekly.shared.ManagerInsightDataProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Plan-module implementation of {@link ManagerInsightDataProvider}.
 *
 * <p>Builds the manager-week summary that the AI module uses for beta
 * insight generation while keeping the AI module decoupled from plan internals.
 */
@Component
public class PlanManagerInsightDataProvider implements ManagerInsightDataProvider {

    private final TeamDashboardService teamDashboardService;

    public PlanManagerInsightDataProvider(TeamDashboardService teamDashboardService) {
        this.teamDashboardService = teamDashboardService;
    }

    @Override
    public ManagerWeekContext getManagerWeekContext(UUID orgId, UUID managerId, LocalDate weekStart) {
        TeamSummaryResponseDto summary = teamDashboardService.getTeamSummary(
                orgId, managerId, weekStart,
                0, Integer.MAX_VALUE,
                null, null, null, null, null, null
        );
        RcdoRollupResponse rollup = teamDashboardService.getRcdoRollup(orgId, managerId, weekStart);

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
                        .toList()
        );
    }
}
