package com.weekly.ai;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for data-driven plan quality checks (Wave 1, step 5).
 *
 * <p>Assesses the strategic quality of a plan before locking and returns
 * a set of {@link QualityNudge} items that guide the user toward
 * better planning hygiene. No LLM is needed — all checks are data-driven.
 *
 * <p>Checks performed (Phase 1):
 * <ol>
 *   <li>Coverage gaps — zero commits linked to the team's top rally cries</li>
 *   <li>Category imbalance — plan heavily skewed to one category vs last week</li>
 *   <li>Chess distribution balance — missing KING or PAWN-heavy allocation</li>
 *   <li>RCDO alignment — user's rate compared to the team average</li>
 * </ol>
 */
public interface PlanQualityService {

    /**
     * Runs all quality checks for the given plan and returns nudge items.
     *
     * @param orgId  the authenticated user's organisation ID
     * @param planId the plan to assess
     * @param userId the authenticated user (plan owner)
     * @return quality check result with status and list of nudges
     */
    QualityCheckResult checkPlanQuality(UUID orgId, UUID planId, UUID userId);

    /**
     * Result of a plan quality check.
     *
     * @param status one of {@code "ok"} or {@code "unavailable"}
     * @param nudges list of quality nudges; empty when status is "unavailable"
     */
    record QualityCheckResult(
            String status,
            List<QualityNudge> nudges
    ) {
        /** Convenience factory for graceful-degradation fallback. */
        public static QualityCheckResult unavailable() {
            return new QualityCheckResult("unavailable", List.of());
        }
    }
}
