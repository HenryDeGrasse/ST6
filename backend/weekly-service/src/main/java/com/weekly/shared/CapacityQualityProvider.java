package com.weekly.shared;

import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction for retrieving capacity-related quality signals about a plan.
 *
 * <p>Lives in the shared package so that modules consuming capacity signals
 * (e.g. AI, digest, notifications) do not need to depend directly on the
 * capacity module internals (ArchUnit boundary enforcement).
 *
 * <p>Implementations are expected to reside in the capacity module.
 */
public interface CapacityQualityProvider {

    /**
     * Returns an overcommitment warning for the given plan, if one can be determined.
     *
     * <p>Returns {@link Optional#empty()} when no profile exists for the user or
     * when there is insufficient historical data to make a determination.
     * Returns a present {@link Optional} containing the warning (which may have
     * level {@link OvercommitLevel#NONE}) when capacity data is available.
     *
     * @param orgId  the organisation ID
     * @param planId the plan whose commits are to be evaluated
     * @param userId the owner of the plan; used for authorisation and profile lookup
     * @return optional overcommitment warning; empty if no capacity data is available
     */
    Optional<OvercommitWarning> getOvercommitmentWarning(UUID orgId, UUID planId, UUID userId);
}
