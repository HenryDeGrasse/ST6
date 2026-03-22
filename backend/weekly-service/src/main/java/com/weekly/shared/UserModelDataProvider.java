package com.weekly.shared;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared read seam for persisted user-model snapshots.
 */
public interface UserModelDataProvider {

    Optional<UserModelSnapshot> getLatestSnapshot(UUID orgId, UUID userId);

    record UserModelSnapshot(
            UUID userId,
            int weeksAnalyzed,
            String modelJson,
            String computedAt
    ) {}
}
