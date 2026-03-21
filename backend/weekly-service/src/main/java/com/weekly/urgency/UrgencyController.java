package com.weekly.urgency;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.SlackInfo;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for outcome urgency summary and strategic slack API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/outcomes/urgency-summary}
 *       — returns all outcomes with urgency bands for the org.</li>
 *   <li>{@code GET /api/v1/team/strategic-slack}
 *       — returns current slack band and recommended strategic focus floor.</li>
 * </ul>
 *
 * <p>The caller's {@code orgId} and {@code userId} are sourced exclusively from
 * the validated {@link com.weekly.auth.UserPrincipal} exposed through
 * {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1")
public class UrgencyController {

    private final UrgencyDataProvider urgencyDataProvider;
    private final StrategicSlackService strategicSlackService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public UrgencyController(
            UrgencyDataProvider urgencyDataProvider,
            StrategicSlackService strategicSlackService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.urgencyDataProvider = urgencyDataProvider;
        this.strategicSlackService = strategicSlackService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * GET /api/v1/outcomes/urgency-summary
     *
     * <p>Returns all outcomes with urgency bands for the caller's organisation.
     * Delegates to {@link UrgencyDataProvider#getOrgUrgencySummary(UUID)}.
     *
     * @return 200 with {@code {"outcomes": [...]}} containing a list of
     *         {@link UrgencyInfo} records
     */
    @GetMapping("/outcomes/urgency-summary")
    public ResponseEntity<Map<String, Object>> getUrgencySummary() {
        UUID orgId = authenticatedUserContext.orgId();
        List<UrgencyInfo> outcomes = urgencyDataProvider.getOrgUrgencySummary(orgId);
        return ResponseEntity.ok(Map.of("outcomes", outcomes));
    }

    /**
     * GET /api/v1/team/strategic-slack
     *
     * <p>Returns the current slack band and recommended strategic focus floor
     * for the caller's organisation. Delegates to
     * {@link StrategicSlackService#computeStrategicSlack(UUID, UUID)}.
     *
     * @return 200 with {@code {"slack": {...}}} containing a {@link SlackInfo} record
     */
    @GetMapping("/team/strategic-slack")
    public ResponseEntity<Map<String, Object>> getStrategicSlack() {
        UUID orgId = authenticatedUserContext.orgId();
        UUID userId = authenticatedUserContext.userId();
        SlackInfo slack = strategicSlackService.computeStrategicSlack(orgId, userId);
        return ResponseEntity.ok(Map.of("slack", slack));
    }
}
