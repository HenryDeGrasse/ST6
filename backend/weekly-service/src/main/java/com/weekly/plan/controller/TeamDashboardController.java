package com.weekly.plan.controller;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.plan.dto.RcdoRollupResponse;
import com.weekly.plan.dto.TeamSummaryResponseDto;
import com.weekly.plan.dto.WeeklyCommitResponse;
import com.weekly.plan.service.TeamDashboardService;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for manager team dashboard endpoints.
 *
 * <p>Provides team summary, RCDO roll-up, and drill-down into
 * individual direct reports' plans.
 *
 * <p>The caller's identity ({@code managerId}, {@code orgId}) comes
 * exclusively from the validated {@link com.weekly.auth.UserPrincipal}
 * exposed through {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1")
public class TeamDashboardController {

    private final TeamDashboardService teamDashboardService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public TeamDashboardController(
            TeamDashboardService teamDashboardService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.teamDashboardService = teamDashboardService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * GET /weeks/{weekStart}/team/summary
     * Returns paginated, filtered team summary for manager's direct reports.
     */
    @GetMapping("/weeks/{weekStart}/team/summary")
    public ResponseEntity<TeamSummaryResponseDto> getTeamSummary(
            @PathVariable String weekStart,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String outcomeId,
            @RequestParam(required = false) Boolean incomplete,
            @RequestParam(required = false) Boolean nonStrategic,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        TeamSummaryResponseDto response = teamDashboardService.getTeamSummary(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weekStartDate,
                page,
                size,
                state,
                outcomeId,
                incomplete,
                nonStrategic,
                priority,
                category
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /weeks/{weekStart}/team/rcdo-rollup
     * Returns RCDO roll-up of commits across direct reports.
     */
    @GetMapping("/weeks/{weekStart}/team/rcdo-rollup")
    public ResponseEntity<RcdoRollupResponse> getRcdoRollup(
            @PathVariable String weekStart
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        RcdoRollupResponse response = teamDashboardService.getRcdoRollup(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weekStartDate
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /weeks/{weekStart}/plans/{userId}
     * Manager drill-down: get a specific user's plan for the given week.
     */
    @GetMapping("/weeks/{weekStart}/plans/{userId}")
    public ResponseEntity<?> getUserPlan(
            @PathVariable String weekStart,
            @PathVariable UUID userId
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        return teamDashboardService.getUserPlanForManager(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                userId,
                weekStartDate
        )
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                        .body(ApiErrorResponse.of(
                                ErrorCode.NOT_FOUND,
                                "Plan not found for user " + userId + " and week " + weekStartDate
                        )));
    }

    /**
     * GET /weeks/{weekStart}/plans/{userId}/commits
     * Manager drill-down: get a direct report's commits for the given week.
     */
    @GetMapping("/weeks/{weekStart}/plans/{userId}/commits")
    public ResponseEntity<?> getUserPlanCommits(
            @PathVariable String weekStart,
            @PathVariable UUID userId
    ) {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        return teamDashboardService.getUserPlanForManager(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                userId,
                weekStartDate
        )
                .<ResponseEntity<?>>map(plan -> {
                    List<WeeklyCommitResponse> commits = teamDashboardService.getPlanCommits(
                            authenticatedUserContext.orgId(),
                            UUID.fromString(plan.id())
                    );
                    return ResponseEntity.ok(commits);
                })
                .orElseGet(() -> ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                        .body(ApiErrorResponse.of(
                                ErrorCode.NOT_FOUND,
                                "Plan not found for user " + userId + " and week " + weekStartDate
                        )));
    }
}
