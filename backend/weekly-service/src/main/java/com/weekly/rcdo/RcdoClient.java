package com.weekly.rcdo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter interface for the RCDO hierarchy service (§9.1).
 *
 * <p>Reads the Rally Cry → Defining Objective → Outcome tree.
 * Results are cached in Redis with a 5-minute TTL. On cache miss
 * the implementation fetches from the upstream PA RCDO service.
 *
 * <p>Failure mode: cached read-only view on service unavailability.
 * Plan locking is blocked if cache is stale beyond the configurable
 * threshold (default 60 minutes).
 */
public interface RcdoClient {

    /**
     * Returns the full RCDO tree for the given org.
     */
    RcdoTree getTree(UUID orgId);

    /**
     * Typeahead search for outcomes matching the query.
     */
    List<RcdoSearchResult> search(UUID orgId, String query);

    /**
     * Look up a specific outcome by ID for snapshot population.
     */
    Optional<RcdoOutcomeDetail> getOutcome(UUID orgId, UUID outcomeId);

    /**
     * Returns whether the cached RCDO data is fresh enough for lock validation.
     *
     * @param orgId                 the organization ID
     * @param stalenessThresholdMin staleness threshold in minutes
     * @return true if cache is fresh enough
     */
    boolean isCacheFresh(UUID orgId, int stalenessThresholdMin);

    /**
     * Returns the instant the RCDO cache was last refreshed for the given org,
     * or {@code null} if no data has ever been loaded.
     *
     * <p>Used to populate the {@code lastRefreshedAt} detail in the
     * {@code RCDO_VALIDATION_STALE} error response (Appendix A).
     *
     * @param orgId the organization ID
     * @return the last-refresh timestamp, or null
     */
    java.time.Instant getLastRefreshedAt(UUID orgId);
}
