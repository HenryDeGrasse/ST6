package com.weekly.plan.service;

import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.LockType;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.dto.RcdoRollupItem;
import com.weekly.plan.dto.RcdoRollupResponse;
import com.weekly.plan.dto.ReviewStatusCountsResponse;
import com.weekly.plan.dto.TeamMemberSummaryResponse;
import com.weekly.plan.dto.TeamSummaryResponseDto;
import com.weekly.plan.dto.WeeklyCommitResponse;
import com.weekly.plan.dto.WeeklyPlanResponse;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for manager team dashboard queries.
 *
 * <p>Provides paginated team summaries, RCDO roll-ups, and filter support.
 * Uses a query-driven, index-backed approach as the PRD recommends for MVP.
 *
 * <p>Direct-report resolution is delegated to {@link OrgGraphClient}, with
 * results aggressively cached (15 min TTL).
 */
@Service
public class TeamDashboardService {

    private final OrgGraphClient orgGraphClient;
    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository commitActualRepository;
    private final CommitValidator commitValidator;

    public TeamDashboardService(
            OrgGraphClient orgGraphClient,
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository commitActualRepository,
            CommitValidator commitValidator
    ) {
        this.orgGraphClient = orgGraphClient;
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.commitActualRepository = commitActualRepository;
        this.commitValidator = commitValidator;
    }

    /**
     * Returns a paginated summary of direct reports' plan statuses for
     * the given week, with optional filters.
     *
     * @param orgId      the org ID from JWT
     * @param managerId  the manager's user ID
     * @param weekStart  the ISO Monday of the target week
     * @param page       zero-based page number
     * @param size       page size
     * @param stateFilter      optional plan state filter
     * @param outcomeIdFilter  optional RCDO outcome ID filter
     * @param incompleteFilter optional incomplete-commits-only filter
     * @param nonStrategicFilter optional non-strategic-only filter
     * @param priorityFilter   optional chess priority filter
     * @param categoryFilter   optional category filter
     * @return paginated team summary
     */
    @Transactional(readOnly = true)
    public TeamSummaryResponseDto getTeamSummary(
            UUID orgId, UUID managerId, LocalDate weekStart,
            int page, int size,
            String stateFilter, String outcomeIdFilter,
            Boolean incompleteFilter, Boolean nonStrategicFilter,
            String priorityFilter, String categoryFilter
    ) {
        validateWeekStart(weekStart);

        List<DirectReport> directReportsWithNames = orgGraphClient.getDirectReportsWithNames(orgId, managerId);
        if (directReportsWithNames.isEmpty()) {
            return emptyResponse(weekStart, page, size);
        }

        List<UUID> directReports = directReportsWithNames.stream()
                .map(DirectReport::userId).toList();
        Map<UUID, String> displayNamesByUser = directReportsWithNames.stream()
                .collect(Collectors.toMap(DirectReport::userId, DirectReport::displayName));

        // Fetch all plans for direct reports in this week
        List<WeeklyPlanEntity> plans = planRepository
                .findByOrgIdAndWeekStartDateAndOwnerUserIdIn(orgId, weekStart, directReports);

        Map<UUID, WeeklyPlanEntity> plansByUser = plans.stream()
                .collect(Collectors.toMap(WeeklyPlanEntity::getOwnerUserId, Function.identity()));

        // Fetch all commits for these plans
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> allCommits = planIds.isEmpty()
                ? List.of()
                : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        Map<UUID, List<WeeklyCommitEntity>> commitsByPlan = allCommits.stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        // Build per-user summaries
        LocalDate currentWeekStart = currentWeekMonday();
        List<TeamMemberSummaryResponse> allSummaries = new ArrayList<>();

        for (UUID userId : directReports) {
            WeeklyPlanEntity plan = plansByUser.get(userId);
            String displayName = displayNamesByUser.getOrDefault(userId, userId.toString());
            TeamMemberSummaryResponse summary = buildMemberSummary(
                    userId, displayName, plan, commitsByPlan, currentWeekStart
            );

            // Apply filters
            if (!matchesFilters(summary, plan, commitsByPlan,
                    stateFilter, outcomeIdFilter, incompleteFilter,
                    nonStrategicFilter, priorityFilter, categoryFilter)) {
                continue;
            }

            allSummaries.add(summary);
        }

        // Compute review status counts across ALL direct reports (before pagination)
        ReviewStatusCountsResponse reviewCounts = computeReviewCounts(plansByUser);

        // Paginate
        int totalElements = allSummaries.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<TeamMemberSummaryResponse> pageContent = allSummaries.subList(fromIndex, toIndex);

        return new TeamSummaryResponseDto(
                weekStart.toString(), pageContent, reviewCounts,
                page, size, totalElements, totalPages
        );
    }

    /**
     * Returns RCDO roll-up of commits across direct reports for a given week.
     * Groups commits by outcome and counts chess priorities per group.
     *
     * @param orgId     the org ID
     * @param managerId the manager's user ID
     * @param weekStart the target week
     * @return RCDO roll-up with non-strategic count
     */
    @Transactional(readOnly = true)
    public RcdoRollupResponse getRcdoRollup(UUID orgId, UUID managerId, LocalDate weekStart) {
        validateWeekStart(weekStart);

        List<UUID> directReports = orgGraphClient.getDirectReports(orgId, managerId);
        if (directReports.isEmpty()) {
            return new RcdoRollupResponse(weekStart.toString(), List.of(), 0);
        }

        List<WeeklyPlanEntity> plans = planRepository
                .findByOrgIdAndWeekStartDateAndOwnerUserIdIn(orgId, weekStart, directReports);

        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> allCommits = planIds.isEmpty()
                ? List.of()
                : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        // Group by outcome
        Map<UUID, List<WeeklyCommitEntity>> byOutcome = new LinkedHashMap<>();
        int nonStrategicCount = 0;

        for (WeeklyCommitEntity commit : allCommits) {
            if (commit.getOutcomeId() != null) {
                byOutcome.computeIfAbsent(commit.getOutcomeId(), k -> new ArrayList<>()).add(commit);
            } else if (commit.getNonStrategicReason() != null && !commit.getNonStrategicReason().isBlank()) {
                nonStrategicCount++;
            }
        }

        List<RcdoRollupItem> items = new ArrayList<>();
        for (var entry : byOutcome.entrySet()) {
            List<WeeklyCommitEntity> commits = entry.getValue();
            // Use snapshot names if available (post-lock), otherwise IDs only
            WeeklyCommitEntity representative = commits.get(0);
            items.add(buildRollupItem(entry.getKey(), representative, commits));
        }

        return new RcdoRollupResponse(weekStart.toString(), items, nonStrategicCount);
    }

    /**
     * Returns a specific direct report's plan for the requested week.
     */
    @Transactional(readOnly = true)
    public Optional<WeeklyPlanResponse> getUserPlanForManager(
            UUID orgId, UUID managerId, UUID reportUserId, LocalDate weekStart
    ) {
        validateWeekStart(weekStart);
        assertManagerAccess(orgId, managerId, reportUserId);

        return planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(orgId, reportUserId, weekStart)
                .map(WeeklyPlanResponse::from);
    }

    /**
     * Returns commits for a plan already authorized for manager drill-down.
     * Includes actuals when the plan is in RECONCILING or later state.
     */
    @Transactional(readOnly = true)
    public List<WeeklyCommitResponse> getPlanCommits(UUID orgId, UUID planId) {
        List<WeeklyCommitEntity> commits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);

        // Load actuals if the plan is in reconciliation or later
        Map<UUID, WeeklyCommitActualEntity> actualsMap = Map.of();
        if (!commits.isEmpty()) {
            WeeklyPlanEntity plan = planRepository.findByOrgIdAndId(orgId, planId).orElse(null);
            if (plan != null && isAtOrAfterReconciliation(plan.getState())) {
                List<UUID> commitIds = commits.stream()
                        .map(WeeklyCommitEntity::getId).toList();
                actualsMap = commitActualRepository
                        .findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                        .collect(Collectors.toMap(
                                WeeklyCommitActualEntity::getCommitId,
                                Function.identity()));
            }
        }

        Map<UUID, WeeklyCommitActualEntity> finalActualsMap = actualsMap;
        return commits.stream()
                .map(commit -> WeeklyCommitResponse.from(
                        commit, commitValidator.validate(commit),
                        finalActualsMap.get(commit.getId())))
                .toList();
    }

    // ── Internal helpers ─────────────────────────────────────

    private TeamMemberSummaryResponse buildMemberSummary(
            UUID userId, String displayName, WeeklyPlanEntity plan,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlan,
            LocalDate currentWeekStart
    ) {
        if (plan == null) {
            return new TeamMemberSummaryResponse(
                    userId.toString(), displayName, null, null, null,
                    0, 0, 0, 0, 0, 0, null, false, false
            );
        }

        List<WeeklyCommitEntity> commits = commitsByPlan.getOrDefault(plan.getId(), List.of());

        int commitCount = commits.size();
        // issueCount: commits with validation errors (missing required fields)
        int issueCount = 0;
        int nonStrategicCount = 0;
        int kingCount = 0;
        int queenCount = 0;

        for (WeeklyCommitEntity c : commits) {
            if (!commitValidator.validate(c).isEmpty()) {
                issueCount++;
            }
            if (c.getNonStrategicReason() != null && !c.getNonStrategicReason().isBlank()) {
                nonStrategicCount++;
            }
            if (c.getChessPriority() == ChessPriority.KING) {
                kingCount++;
            }
            if (c.getChessPriority() == ChessPriority.QUEEN) {
                queenCount++;
            }
        }

        // incompleteCount reflects finalized reconciliation outcomes only.
        // For RECONCILED/CARRY_FORWARD plans, count saved actuals with a
        // completionStatus other than DONE. Draft/locked/reconciling plans do
        // not surface reconciliation incompleteness yet, so this stays 0.
        int incompleteCount = 0;
        if (isFinalizedReconciliationState(plan.getState()) && !commits.isEmpty()) {
            List<UUID> commitIds = commits.stream()
                    .map(WeeklyCommitEntity::getId).toList();
            incompleteCount = (int) commitActualRepository
                    .findByOrgIdAndCommitIdIn(plan.getOrgId(), commitIds).stream()
                    .filter(actual -> actual.getCompletionStatus() != CompletionStatus.DONE)
                    .count();
        }

        boolean isStale = plan.getState() == PlanState.DRAFT
                && plan.getWeekStartDate().isBefore(currentWeekStart);
        boolean isLateLock = plan.getLockType() == LockType.LATE_LOCK;

        return new TeamMemberSummaryResponse(
                userId.toString(),
                displayName,
                plan.getId().toString(),
                plan.getState().name(),
                plan.getReviewStatus().name(),
                commitCount, incompleteCount, issueCount, nonStrategicCount,
                kingCount, queenCount,
                plan.getUpdatedAt() != null ? plan.getUpdatedAt().toString() : null,
                isStale, isLateLock
        );
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private boolean matchesFilters(
            TeamMemberSummaryResponse summary, WeeklyPlanEntity plan,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlan,
            String stateFilter, String outcomeIdFilter,
            Boolean incompleteFilter, Boolean nonStrategicFilter,
            String priorityFilter, String categoryFilter
    ) {
        // State filter
        if (stateFilter != null && !stateFilter.isEmpty()) {
            if (summary.state() == null || !summary.state().equals(stateFilter)) {
                return false;
            }
        }

        // Outcome ID filter: user has at least one commit linked to this outcome
        if (outcomeIdFilter != null && !outcomeIdFilter.isEmpty() && plan != null) {
            UUID outcomeId = UUID.fromString(outcomeIdFilter);
            List<WeeklyCommitEntity> commits = commitsByPlan.getOrDefault(plan.getId(), List.of());
            boolean hasOutcome = commits.stream()
                    .anyMatch(c -> outcomeId.equals(c.getOutcomeId()));
            if (!hasOutcome) {
                return false;
            }
        }

        // Incomplete filter: user has at least one reconciled/carry-forward
        // commit whose completion status is not DONE.
        if (Boolean.TRUE.equals(incompleteFilter) && summary.incompleteCount() == 0) {
            return false;
        }

        // Non-strategic filter: user has at least one non-strategic commit
        if (Boolean.TRUE.equals(nonStrategicFilter) && summary.nonStrategicCount() == 0) {
            return false;
        }

        // Priority filter: user has at least one commit with this chess priority
        if (priorityFilter != null && !priorityFilter.isEmpty() && plan != null) {
            ChessPriority priority = ChessPriority.valueOf(priorityFilter);
            List<WeeklyCommitEntity> commits = commitsByPlan.getOrDefault(plan.getId(), List.of());
            boolean hasPriority = commits.stream()
                    .anyMatch(c -> c.getChessPriority() == priority);
            if (!hasPriority) {
                return false;
            }
        }

        // Category filter
        if (categoryFilter != null && !categoryFilter.isEmpty() && plan != null) {
            List<WeeklyCommitEntity> commits = commitsByPlan.getOrDefault(plan.getId(), List.of());
            boolean hasCategory = commits.stream()
                    .anyMatch(c -> c.getCategory() != null
                            && c.getCategory().name().equals(categoryFilter));
            if (!hasCategory) {
                return false;
            }
        }

        return true;
    }

    private ReviewStatusCountsResponse computeReviewCounts(Map<UUID, WeeklyPlanEntity> plansByUser) {
        int pending = 0;
        int approved = 0;
        int changesRequested = 0;

        for (WeeklyPlanEntity plan : plansByUser.values()) {
            switch (plan.getReviewStatus()) {
                case REVIEW_PENDING -> pending++;
                case APPROVED -> approved++;
                case CHANGES_REQUESTED -> changesRequested++;
                default -> { /* REVIEW_NOT_APPLICABLE — not counted */ }
            }
        }

        return new ReviewStatusCountsResponse(pending, approved, changesRequested);
    }

    private RcdoRollupItem buildRollupItem(
            UUID outcomeId, WeeklyCommitEntity representative,
            List<WeeklyCommitEntity> commits
    ) {
        int kingCount = 0;
        int queenCount = 0;
        int rookCount = 0;
        int bishopCount = 0;
        int knightCount = 0;
        int pawnCount = 0;

        for (WeeklyCommitEntity c : commits) {
            if (c.getChessPriority() == null) {
                continue;
            }
            switch (c.getChessPriority()) {
                case KING -> kingCount++;
                case QUEEN -> queenCount++;
                case ROOK -> rookCount++;
                case BISHOP -> bishopCount++;
                case KNIGHT -> knightCount++;
                case PAWN -> pawnCount++;
            }
        }

        // Use snapshot names if available, else use null (pre-lock commits)
        String outcomeName = representative.getSnapshotOutcomeName();
        String objectiveId = representative.getSnapshotObjectiveId() != null
                ? representative.getSnapshotObjectiveId().toString() : null;
        String objectiveName = representative.getSnapshotObjectiveName();
        String rallyCryId = representative.getSnapshotRallyCryId() != null
                ? representative.getSnapshotRallyCryId().toString() : null;
        String rallyCryName = representative.getSnapshotRallyCryName();

        return new RcdoRollupItem(
                outcomeId.toString(), outcomeName,
                objectiveId, objectiveName,
                rallyCryId, rallyCryName,
                commits.size(),
                kingCount, queenCount, rookCount, bishopCount, knightCount, pawnCount
        );
    }

    private TeamSummaryResponseDto emptyResponse(LocalDate weekStart, int page, int size) {
        return new TeamSummaryResponseDto(
                weekStart.toString(), List.of(),
                new ReviewStatusCountsResponse(0, 0, 0),
                page, size, 0, 0
        );
    }

    private void assertManagerAccess(UUID orgId, UUID managerId, UUID reportUserId) {
        if (!orgGraphClient.isDirectReport(orgId, managerId, reportUserId)) {
            throw new PlanValidationException(
                    ErrorCode.FORBIDDEN,
                    "Manager access denied for user " + reportUserId,
                    List.of(Map.of("targetUserId", reportUserId.toString()))
            );
        }
    }

    private void validateWeekStart(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new PlanValidationException(
                    ErrorCode.INVALID_WEEK_START,
                    "weekStart must be a Monday",
                    List.of(Map.of("provided", weekStart.toString()))
            );
        }
    }

    private boolean isAtOrAfterReconciliation(PlanState state) {
        return state == PlanState.RECONCILING
                || state == PlanState.RECONCILED
                || state == PlanState.CARRY_FORWARD;
    }

    private boolean isFinalizedReconciliationState(PlanState state) {
        return state == PlanState.RECONCILED
                || state == PlanState.CARRY_FORWARD;
    }

    static LocalDate currentWeekMonday() {
        LocalDate today = LocalDate.now();
        return today.with(DayOfWeek.MONDAY);
    }
}
