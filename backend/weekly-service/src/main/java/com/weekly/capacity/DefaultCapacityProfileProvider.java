package com.weekly.capacity;

import com.weekly.shared.CapacityProfileProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Shared adapter exposing persisted capacity profiles to forecasting and agent flows.
 */
@Service
public class DefaultCapacityProfileProvider implements CapacityProfileProvider {

    private final CapacityProfileService capacityProfileService;

    public DefaultCapacityProfileProvider(CapacityProfileService capacityProfileService) {
        this.capacityProfileService = capacityProfileService;
    }

    @Override
    public Optional<CapacityProfileSnapshot> getLatestProfile(UUID orgId, UUID userId) {
        return capacityProfileService.getProfile(orgId, userId)
                .map(profile -> new CapacityProfileSnapshot(
                        profile.getUserId(),
                        profile.getWeeksAnalyzed(),
                        profile.getEstimationBias(),
                        profile.getRealisticWeeklyCap(),
                        profile.getConfidenceLevel(),
                        profile.getComputedAt() != null ? profile.getComputedAt().toString() : null));
    }
}
