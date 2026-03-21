package com.weekly.config;

import java.util.UUID;

/**
 * Service interface for resolving per-org configuration from the
 * {@code org_policies} table (PRD §5).
 *
 * <p>Results are cached in memory since policies change rarely. On cache miss,
 * the row is loaded from the database. If no row exists for an org, sensible
 * defaults are returned that match the original hardcoded constants in
 * {@code PlanService} (chessMaxKing=1, chessMaxQueen=2, stalenessThreshold=60 min).
 */
public interface OrgPolicyService {

    /**
     * Returns the policy for the given org, loading from DB on cache miss.
     * Never returns null — falls back to {@link #defaultPolicy()} if no row exists.
     *
     * @param orgId the organization ID
     * @return the resolved policy
     */
    OrgPolicy getPolicy(UUID orgId);

    /**
     * Evicts the cached policy for the given org, forcing a DB reload on next access.
     *
     * @param orgId the organization ID to evict
     */
    void evict(UUID orgId);

    /**
     * Persists updated digest schedule fields for the given org.
     *
     * <p>Callers should invoke {@link #evict(UUID)} afterwards to invalidate the
     * cache so subsequent calls to {@link #getPolicy(UUID)} return the new values.
     *
     * @param orgId      the organization ID
     * @param digestDay  day-of-week string (e.g. "FRIDAY")
     * @param digestTime HH:mm time string (e.g. "17:00")
     */
    void updateDigestConfig(UUID orgId, String digestDay, String digestTime);

    /**
     * Returns the system defaults, matching the original hardcoded {@code PlanService} constants:
     * max 1 KING, max 2 QUEEN, RCDO staleness threshold 60 minutes, Friday digest at 17:00.
     *
     * @return the default org policy
     */
    static OrgPolicy defaultPolicy() {
        return new OrgPolicy(true, 1, 2, "MONDAY", "10:00", "FRIDAY", "16:00", true, 60,
                "FRIDAY", "17:00");
    }

    // ── Policy record ─────────────────────────────────────────

    /**
     * Immutable snapshot of an org's policy configuration.
     *
     * @param chessKingRequired             whether at least one KING commit is required
     * @param chessMaxKing                  maximum number of KING commits allowed
     * @param chessMaxQueen                 maximum number of QUEEN commits allowed
     * @param lockDay                       day-of-week for the lock reminder (e.g. "MONDAY")
     * @param lockTime                      time for the lock reminder (e.g. "10:00")
     * @param reconcileDay                  day-of-week for the reconcile reminder (e.g. "FRIDAY")
     * @param reconcileTime                 time for the reconcile reminder (e.g. "16:00")
     * @param blockLockOnStaleRcdo          whether to block locking when RCDO data is stale
     * @param rcdoStalenessThresholdMinutes staleness threshold in minutes
     * @param digestDay                     day-of-week for the weekly digest (e.g. "FRIDAY")
     * @param digestTime                    time for the weekly digest (e.g. "17:00")
     */
    record OrgPolicy(
            boolean chessKingRequired,
            int chessMaxKing,
            int chessMaxQueen,
            String lockDay,
            String lockTime,
            String reconcileDay,
            String reconcileTime,
            boolean blockLockOnStaleRcdo,
            int rcdoStalenessThresholdMinutes,
            String digestDay,
            String digestTime
    ) {}
}
