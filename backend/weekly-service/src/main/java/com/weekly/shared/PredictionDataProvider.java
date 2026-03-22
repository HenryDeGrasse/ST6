package com.weekly.shared;

import java.util.List;
import java.util.UUID;

/**
 * Shared read seam for rule-based prediction signals.
 */
public interface PredictionDataProvider {

    List<PredictionSignal> getUserPredictions(UUID orgId, UUID userId);

    record PredictionSignal(
            String type,
            boolean likely,
            String confidence,
            String reason
    ) {}
}
