package com.weekly.shared;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared read seam for persisted capacity profiles.
 */
public interface CapacityProfileProvider {

    Optional<CapacityProfileSnapshot> getLatestProfile(UUID orgId, UUID userId);

    record CapacityProfileSnapshot(
            UUID userId,
            int weeksAnalyzed,
            BigDecimal estimationBias,
            BigDecimal realisticWeeklyCap,
            String confidenceLevel,
            String computedAt
    ) {}
}
