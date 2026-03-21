package com.weekly.admin;

import java.util.UUID;

/**
 * Service that computes aggregate admin-dashboard metrics.
 *
 * <p>Three metric areas:
 * <ul>
 *   <li><b>Adoption</b> – weekly funnel: plan created → locked → reconciled → reviewed,
 *       active-user counts, and cadence-compliance rate.</li>
 *   <li><b>AI usage</b> – suggestion acceptance/defer/decline rates and
 *       in-process cache efficiency.</li>
 *   <li><b>RCDO health</b> – outcomes ranked by commit coverage over a
 *       recent window, with stale outcomes (0 commits) highlighted.</li>
 * </ul>
 */
public interface AdminDashboardService {

    int MIN_WEEKS = 1;
    int MAX_WEEKS = 26;

    /**
     * Returns weekly adoption funnel metrics for the given organisation
     * over a rolling window of {@code weeks} Mondays ending on the current week.
     *
     * @param orgId the organisation
     * @param weeks rolling-window size (clamped to [{@value #MIN_WEEKS}, {@value #MAX_WEEKS}])
     * @return adoption metrics with per-week breakdown
     */
    AdoptionMetrics getAdoptionMetrics(UUID orgId, int weeks);

    /**
     * Returns AI-feature usage metrics for the given organisation over a
     * rolling window of {@code weeks} weeks.
     *
     * @param orgId the organisation
     * @param weeks rolling-window size (clamped to [{@value #MIN_WEEKS}, {@value #MAX_WEEKS}])
     * @return AI usage metrics including suggestion feedback rates and cache stats
     */
    AiUsageMetrics getAiUsageMetrics(UUID orgId, int weeks);

    /**
     * Returns the current RCDO health report for the given organisation,
     * examining commits from the last 8 weeks.
     *
     * @param orgId the organisation
     * @return RCDO health report with outcome coverage ranking
     */
    RcdoHealthReport getRcdoHealth(UUID orgId);
}
