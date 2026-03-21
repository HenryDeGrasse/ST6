package com.weekly.digest;

import java.util.List;

/**
 * Immutable data snapshot for a manager's weekly digest notification.
 *
 * <p>Aggregated once per manager per week by {@link DefaultDigestService} and
 * stored as the JSONB payload of a {@code WEEKLY_DIGEST} notification.
 *
 * @param weekStart                ISO-8601 date of the week Monday (e.g. "2026-03-16")
 * @param totalMemberCount         total number of direct reports in the team
 * @param reconciledCount          plans in RECONCILED or CARRY_FORWARD state
 * @param lockedCount              plans in LOCKED state
 * @param draftCount               plans in DRAFT state (non-stale)
 * @param staleCount               plans in DRAFT state whose week has already passed
 * @param reviewQueueSize           plans with REVIEW_PENDING status awaiting manager review
 * @param carryForwardStreakUserIds user IDs of team members with active carry-forward streaks
 * @param stalePlanUserIds          user IDs of team members whose plans are stale
 * @param lateLockUserIds           user IDs of team members who late-locked during the window
 * @param rcdoAlignmentRate         fraction of commits linked to an RCDO outcome [0.0, 1.0]
 * @param previousRcdoAlignmentRate previous week's alignment rate, or {@code null} when unavailable
 * @param doneEarlyCount            number of commits that received a DONE_EARLY check-in this week
 */
public record DigestPayload(
        String weekStart,
        int totalMemberCount,
        int reconciledCount,
        int lockedCount,
        int draftCount,
        int staleCount,
        int reviewQueueSize,
        List<String> carryForwardStreakUserIds,
        List<String> stalePlanUserIds,
        List<String> lateLockUserIds,
        double rcdoAlignmentRate,
        Double previousRcdoAlignmentRate,
        int doneEarlyCount
) {}
