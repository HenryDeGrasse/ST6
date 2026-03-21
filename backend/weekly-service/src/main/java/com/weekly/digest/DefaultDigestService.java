package com.weekly.digest;

import com.weekly.auth.OrgGraphClient;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ManagerInsightDataProvider;
import com.weekly.shared.ManagerInsightDataProvider.ManagerWeekContext;
import com.weekly.shared.ManagerInsightDataProvider.TeamMemberContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link DigestService}.
 *
 * <p>Delegates to {@link ManagerInsightDataProvider} for the bulk of the aggregation
 * (plan status, review counts, carry-forward streaks, late-lock patterns, RCDO focus)
 * and queries {@link ProgressEntryRepository} separately for the DONE_EARLY commit count.
 *
 * <p>Uses a 4-week rolling window for historical metrics (streaks, late-lock frequency).
 */
@Component
public class DefaultDigestService implements DigestService {

    /** Rolling window for historical metrics (carry-forward streaks, late-lock patterns). */
    static final int HISTORY_WINDOW_WEEKS = 4;

    private final ManagerInsightDataProvider managerInsightDataProvider;
    private final OrgGraphClient orgGraphClient;
    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final ProgressEntryRepository progressEntryRepository;

    public DefaultDigestService(
            ManagerInsightDataProvider managerInsightDataProvider,
            OrgGraphClient orgGraphClient,
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            ProgressEntryRepository progressEntryRepository
    ) {
        this.managerInsightDataProvider = managerInsightDataProvider;
        this.orgGraphClient = orgGraphClient;
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.progressEntryRepository = progressEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DigestPayload buildDigestPayload(UUID orgId, UUID managerId, LocalDate weekStart) {
        // ── Team context (reuses existing 4-week aggregation logic) ─────────
        ManagerWeekContext ctx = managerInsightDataProvider
                .getManagerWeekContext(orgId, managerId, weekStart, HISTORY_WINDOW_WEEKS);

        // ── Plan status counts from team member snapshots ────────────────────
        int reconciledCount = 0;
        int lockedCount = 0;
        int draftCount = 0;
        int staleCount = 0;

        for (TeamMemberContext member : ctx.teamMembers()) {
            if (member.stale()) {
                staleCount++;
            } else if (member.state() == null) {
                // No plan exists for this member this week — counts as draft/missing
                draftCount++;
            } else {
                switch (member.state()) {
                    case "RECONCILED", "CARRY_FORWARD" -> reconciledCount++;
                    case "LOCKED", "RECONCILING" -> lockedCount++;
                    default -> draftCount++; // DRAFT
                }
            }
        }

        // ── Attention items ──────────────────────────────────────────────────
        List<String> carryForwardStreakUserIds = ctx.carryForwardStreaks().stream()
                .map(ManagerInsightDataProvider.CarryForwardStreak::userId)
                .toList();

        List<String> stalePlanUserIds = ctx.teamMembers().stream()
                .filter(TeamMemberContext::stale)
                .map(TeamMemberContext::userId)
                .toList();

        List<String> lateLockUserIds = ctx.lateLockPatterns().stream()
                .map(ManagerInsightDataProvider.LateLockPattern::userId)
                .toList();

        // ── RCDO alignment rate / week-over-week trend ──────────────────────
        double rcdoAlignmentRate = computeRcdoAlignmentRate(ctx);
        Double previousRcdoAlignmentRate = computePreviousRcdoAlignmentRate(orgId, managerId, weekStart);

        // ── Done-early count (progress_entries) ──────────────────────────────
        int doneEarlyCount = computeDoneEarlyCount(orgId, managerId, weekStart);

        return new DigestPayload(
                weekStart.toString(),
                ctx.teamMembers().size(),
                reconciledCount,
                lockedCount,
                draftCount,
                staleCount,
                ctx.reviewCounts().pending(),
                carryForwardStreakUserIds,
                stalePlanUserIds,
                lateLockUserIds,
                rcdoAlignmentRate,
                previousRcdoAlignmentRate,
                doneEarlyCount
        );
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private double computeRcdoAlignmentRate(ManagerWeekContext ctx) {
        int totalCommits = ctx.teamMembers().stream()
                .mapToInt(TeamMemberContext::commitCount)
                .sum();
        int strategicCommits = ctx.rcdoFocuses().stream()
                .mapToInt(ManagerInsightDataProvider.RcdoFocusContext::commitCount)
                .sum();
        return totalCommits > 0 ? (double) strategicCommits / totalCommits : 0.0;
    }

    private Double computePreviousRcdoAlignmentRate(UUID orgId, UUID managerId, LocalDate weekStart) {
        ManagerWeekContext previousCtx = managerInsightDataProvider
                .getManagerWeekContext(orgId, managerId, weekStart.minusWeeks(1), HISTORY_WINDOW_WEEKS);
        if (previousCtx == null) {
            return null;
        }

        int previousTotalCommits = previousCtx.teamMembers().stream()
                .mapToInt(TeamMemberContext::commitCount)
                .sum();
        if (previousTotalCommits == 0) {
            return null;
        }
        return computeRcdoAlignmentRate(previousCtx);
    }

    /**
     * Counts the number of distinct commits that received at least one DONE_EARLY
     * check-in entry during the target week.
     */
    private int computeDoneEarlyCount(UUID orgId, UUID managerId, LocalDate weekStart) {
        List<UUID> directReports = orgGraphClient.getDirectReports(orgId, managerId);
        if (directReports.isEmpty()) {
            return 0;
        }

        List<WeeklyPlanEntity> plans = planRepository
                .findByOrgIdAndWeekStartDateAndOwnerUserIdIn(orgId, weekStart, directReports);
        if (plans.isEmpty()) {
            return 0;
        }

        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> commits = commitRepository
                .findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);
        if (commits.isEmpty()) {
            return 0;
        }

        List<UUID> commitIds = commits.stream().map(WeeklyCommitEntity::getId).toList();
        Instant weekStartInstant = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextWeekStartInstant = weekStart.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return (int) progressEntryRepository
                .findByOrgIdAndCommitIdInOrderByCreatedAtAsc(orgId, commitIds)
                .stream()
                .filter(e -> e.getStatus() == ProgressStatus.DONE_EARLY)
                .filter(e -> !e.getCreatedAt().isBefore(weekStartInstant)
                        && e.getCreatedAt().isBefore(nextWeekStartInstant))
                .map(e -> e.getCommitId())
                .distinct()
                .count();
    }
}
