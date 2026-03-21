package com.weekly.trends;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for cross-week trend analytics (Wave 1).
 *
 * <p>GET /api/v1/users/me/trends?weeks=N
 *
 * <p>Returns rolling-window metrics for the authenticated user's planning
 * history: strategic alignment, carry-forward velocity, completion accuracy,
 * priority/category distributions, and generated insights.
 *
 * <p>The caller's identity is sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} via {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class TrendsController {

    private final TrendsService trendsService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public TrendsController(
            TrendsService trendsService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.trendsService = trendsService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * Returns cross-week trend data for the authenticated user.
     *
     * @param weeks number of weeks to include in the rolling window (1–26, default 8)
     * @return 200 with {@link TrendsResponse}, or 422 if the weeks parameter is out of range
     */
    @GetMapping("/trends")
    public ResponseEntity<?> getMyTrends(
            @RequestParam(defaultValue = "8") int weeks
    ) {
        if (weeks < DefaultTrendsService.MIN_WEEKS || weeks > DefaultTrendsService.MAX_WEEKS) {
            return ResponseEntity
                    .unprocessableEntity()
                    .body(ApiErrorResponse.of(
                            ErrorCode.VALIDATION_ERROR,
                            "weeks must be between " + DefaultTrendsService.MIN_WEEKS
                                    + " and " + DefaultTrendsService.MAX_WEEKS
                    ));
        }
        TrendsResponse response = trendsService.computeTrends(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weeks
        );
        return ResponseEntity.ok(response);
    }
}
