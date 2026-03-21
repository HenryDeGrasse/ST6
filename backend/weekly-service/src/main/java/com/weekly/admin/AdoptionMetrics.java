package com.weekly.admin;

import java.util.List;

/**
 * Adoption funnel metrics for the admin dashboard.
 *
 * <p>Tracks how many users progress through the planning lifecycle
 * each week: plan created → locked → reconciled → reviewed. Together
 * these form a conversion funnel that indicates engagement health.
 */
public record AdoptionMetrics(
        int weeks,
        String windowStart,
        String windowEnd,
        int totalActiveUsers,
        double cadenceComplianceRate,
        List<WeeklyAdoptionPoint> weeklyPoints
) {

    /**
     * Per-week breakdown of the adoption funnel.
     *
     * @param weekStart           ISO date of the week's Monday
     * @param activeUsers         distinct users with any plan this week
     * @param plansCreated        total plans in any state this week
     * @param plansLocked         plans that reached LOCKED or beyond
     * @param plansReconciled     plans that reached RECONCILED or CARRY_FORWARD
     * @param plansReviewed       plans where a manager review was submitted
     *                            (reviewStatus = APPROVED or CHANGES_REQUESTED)
     */
    public record WeeklyAdoptionPoint(
            String weekStart,
            int activeUsers,
            int plansCreated,
            int plansLocked,
            int plansReconciled,
            int plansReviewed
    ) {}
}
