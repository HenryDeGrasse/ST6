package com.weekly.shared;

import java.util.List;
import java.util.UUID;

/**
 * Abstraction for outcome urgency and strategic slack data.
 *
 * <p>Lives in the shared package so downstream phases (Phase 2/4/5) can consume
 * urgency and slack data without depending on {@code com.weekly.urgency} internals
 * (ArchUnit boundary: shared must not depend on any specific module).
 *
 * <p>The concrete implementation {@code DefaultUrgencyDataProvider} resides in
 * {@code com.weekly.urgency} and bridges the shared interface to the urgency
 * persistence layer.
 */
public interface UrgencyDataProvider {

    /**
     * Returns urgency information for a single RCDO outcome.
     *
     * @param orgId     the organisation ID
     * @param outcomeId the outcome ID
     * @return urgency info, or {@code null} if no metadata has been registered
     *         for the given outcome
     */
    UrgencyInfo getOutcomeUrgency(UUID orgId, UUID outcomeId);

    /**
     * Returns urgency information for all tracked outcomes in the given organisation.
     *
     * @param orgId the organisation ID
     * @return list of urgency info records; empty if no outcomes are tracked
     */
    List<UrgencyInfo> getOrgUrgencySummary(UUID orgId);

    /**
     * Returns the strategic slack summary for the organisation.
     *
     * <p>In Phase 3 this is computed org-wide. The {@code managerId} parameter is
     * reserved for future per-team scoping in later phases.
     *
     * @param orgId     the organisation ID
     * @param managerId the manager's user ID (reserved for future scoping)
     * @return org-wide slack info summary
     */
    SlackInfo getStrategicSlack(UUID orgId, UUID managerId);
}
