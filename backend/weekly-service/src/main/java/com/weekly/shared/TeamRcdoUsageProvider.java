package com.weekly.shared;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Abstraction for team RCDO outcome usage data needed by the AI suggestion module.
 *
 * <p>Lives in the shared package so the AI module can query team-context
 * signals without depending directly on plan module internals
 * (ArchUnit boundary: AI must not depend on plan).
 */
public interface TeamRcdoUsageProvider {

    /**
     * Returns outcome usage aggregated across the entire team for the given org and week.
     * Results are sorted by {@link OutcomeUsage#commitCount()} descending (highest usage first).
     *
     * @param orgId     the organisation ID
     * @param weekStart the Monday of the target week
     * @return usage snapshot for the current week plus quarter-to-date coverage metadata
     */
    TeamRcdoUsageResult getTeamRcdoUsage(UUID orgId, LocalDate weekStart);

    /**
     * Aggregated result for a single org+week team RCDO usage query.
     *
     * @param outcomes                    current-week outcome usages sorted by commitCount descending; never null
     * @param coveredOutcomeIdsThisQuarter outcome IDs the team has linked at least once in the quarter-to-date window
     */
    record TeamRcdoUsageResult(
            List<OutcomeUsage> outcomes,
            Set<String> coveredOutcomeIdsThisQuarter
    ) {}

    /**
     * Per-outcome usage entry for one week.
     *
     * @param outcomeId   the RCDO outcome UUID as a string
     * @param outcomeName snapshot outcome name; falls back to outcomeId when no snapshot available
     * @param commitCount number of commits linked to this outcome in the week
     */
    record OutcomeUsage(
            String outcomeId,
            String outcomeName,
            int commitCount
    ) {}
}
