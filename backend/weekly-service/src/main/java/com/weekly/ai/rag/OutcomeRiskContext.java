package com.weekly.ai.rag;

import java.util.List;

/**
 * Aggregated context about outcome risk and coverage gaps, used as input
 * to the HyDE recommendation prompt (Phase 6, Step 13).
 *
 * <p>At-risk outcomes are those in the AT_RISK or CRITICAL urgency band.
 * Coverage gaps are outcomes with zero recent commits from the user's team.
 *
 * @param atRiskOutcomes   outcomes currently flagged as AT_RISK or CRITICAL
 * @param coverageGaps     outcomes with no team commits in the recent window
 */
public record OutcomeRiskContext(
        List<AtRiskOutcome> atRiskOutcomes,
        List<CoverageGap> coverageGaps
) {

    /**
     * An outcome that is at risk of missing its target.
     *
     * @param outcomeId     the RCDO outcome UUID
     * @param outcomeName   human-readable outcome name
     * @param urgencyBand   AT_RISK or CRITICAL
     * @param daysRemaining days until target date (null if no target date)
     */
    public record AtRiskOutcome(
            String outcomeId,
            String outcomeName,
            String urgencyBand,
            Long daysRemaining
    ) {}

    /**
     * An outcome that has not received team commit coverage recently.
     *
     * @param outcomeId       the RCDO outcome UUID
     * @param outcomeName     human-readable outcome name
     * @param weeksMissing    number of consecutive weeks without commits
     */
    public record CoverageGap(
            String outcomeId,
            String outcomeName,
            int weeksMissing
    ) {}

    /** Returns {@code true} when there are no at-risk outcomes and no coverage gaps. */
    public boolean isEmpty() {
        return (atRiskOutcomes == null || atRiskOutcomes.isEmpty())
                && (coverageGaps == null || coverageGaps.isEmpty());
    }
}
