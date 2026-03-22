package com.weekly.analytics;

import com.weekly.shared.PredictionDataProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Shared adapter exposing rule-based predictions to other modules.
 */
@Service
public class DefaultPredictionDataProvider implements PredictionDataProvider {

    private final PredictionService predictionService;

    public DefaultPredictionDataProvider(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Override
    public List<PredictionSignal> getUserPredictions(UUID orgId, UUID userId) {
        return predictionService.getUserPredictions(orgId, userId).stream()
                .map(prediction -> new PredictionSignal(
                        prediction.type(),
                        prediction.likely(),
                        prediction.confidence(),
                        prediction.reason()))
                .toList();
    }
}
