package com.weekly.analytics;

import com.weekly.analytics.dto.Prediction;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for rule-based predictions on the manager dashboard.
 *
 * <p>Base path: {@code /api/v1/analytics}
 *
 * <p>Access control: the caller must either have the {@code MANAGER} role <em>or</em>
 * be requesting predictions for their own user ID (self-service). Any other
 * request returns {@code 403 Forbidden}.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class PredictionController {

    private final PredictionService predictionService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public PredictionController(
            PredictionService predictionService,
            AuthenticatedUserContext authenticatedUserContext) {
        this.predictionService = predictionService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * GET /api/v1/analytics/predictions/{userId}
     *
     * <p>Returns all likely predictions for the given user. Only predictions where
     * {@code likely=true} are included (actionable alerts only).
     *
     * <p>Access: manager role OR self-service ({@code userId} matches the
     * authenticated user's ID).
     *
     * @param userId UUID of the user to generate predictions for
     * @return 200 with {@link List}&lt;{@link Prediction}&gt; JSON,
     *         or 403 if the caller is neither a manager nor the target user
     */
    @GetMapping("/predictions/{userId}")
    public ResponseEntity<?> getUserPredictions(@PathVariable UUID userId) {
        UUID callerId = authenticatedUserContext.userId();
        boolean isSelf = callerId.equals(userId);
        boolean isManager = authenticatedUserContext.isManager();

        if (!isManager && !isSelf) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(
                            ErrorCode.FORBIDDEN,
                            "Manager role required or predictions must be for the authenticated user"));
        }

        List<Prediction> predictions = predictionService.getUserPredictions(
                authenticatedUserContext.orgId(), userId);

        return ResponseEntity.ok(predictions);
    }
}
