package com.weekly.ai;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for AI-assisted suggestion endpoints.
 *
 * <p>POST /api/v1/ai/suggest-rcdo — MVP ship, always enabled.
 * <p>POST /api/v1/ai/draft-reconciliation — MVP beta, behind feature flag.
 * <p>POST /api/v1/ai/manager-insights — manager beta, behind feature flag.
 *
 * <p>Rate limited to 20 requests per user per minute (PRD §4).
 * On LLM unavailability, returns 200 with {@code status: "unavailable"}.
 *
 * <p>The caller's identity is sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} exposed through
 * {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiSuggestionService aiSuggestionService;
    private final AiFeatureFlags featureFlags;
    private final RateLimiter rateLimiter;
    private final AuthenticatedUserContext authenticatedUserContext;

    public AiController(
            AiSuggestionService aiSuggestionService,
            AiFeatureFlags featureFlags,
            RateLimiter rateLimiter,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.aiSuggestionService = aiSuggestionService;
        this.featureFlags = featureFlags;
        this.rateLimiter = rateLimiter;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * POST /ai/suggest-rcdo
     *
     * <p>Returns AI-suggested RCDO mappings for a commitment.
     * Returns 200 with empty suggestions on LLM unavailability.
     * Returns 429 if rate limit exceeded.
     */
    @PostMapping("/suggest-rcdo")
    public ResponseEntity<?> suggestRcdo(
            @RequestBody SuggestRcdoRequest request
    ) {
        if (!featureFlags.isSuggestRcdoEnabled()) {
            return ResponseEntity.ok(new SuggestRcdoResponse("unavailable", java.util.List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        AiSuggestionService.SuggestionResult result = aiSuggestionService.suggestRcdo(
                authenticatedUserContext.orgId(),
                request.title(),
                request.description()
        );

        SuggestRcdoResponse response = SuggestRcdoResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /ai/draft-reconciliation
     *
     * <p>Returns AI-drafted reconciliation data. Beta feature, behind flag.
     * Returns 200 with empty drafts on LLM unavailability.
     */
    @PostMapping("/draft-reconciliation")
    public ResponseEntity<?> draftReconciliation(
            @RequestBody DraftReconciliationRequest request
    ) {
        if (!featureFlags.isDraftReconciliationEnabled()) {
            return ResponseEntity.ok(new DraftReconciliationResponse("unavailable", java.util.List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        java.util.UUID planId = java.util.UUID.fromString(request.planId());
        AiSuggestionService.ReconciliationDraftResult result =
                aiSuggestionService.draftReconciliation(authenticatedUserContext.orgId(), planId);

        DraftReconciliationResponse response = DraftReconciliationResponse.from(result);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /ai/manager-insights
     *
     * <p>Returns AI-generated manager insight summaries for a week.
     * Beta feature, behind flag. Returns 200 with empty insights on LLM
     * unavailability so the manual dashboard remains fully usable.
     */
    @PostMapping("/manager-insights")
    public ResponseEntity<?> managerInsights(
            @RequestBody ManagerInsightsRequest request
    ) {
        if (!featureFlags.isManagerInsightsEnabled()) {
            return ResponseEntity.ok(new ManagerInsightsResponse("unavailable", null, java.util.List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        LocalDate weekStart = LocalDate.parse(request.weekStart());
        AiSuggestionService.ManagerInsightsResult result =
                aiSuggestionService.draftManagerInsights(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId(),
                        weekStart
                );

        return ResponseEntity.ok(ManagerInsightsResponse.from(result));
    }

    // ─── Request / Response DTOs ────────────────────────────

    public record SuggestRcdoRequest(String title, String description) {}

    public record SuggestRcdoResponse(
            String status,
            java.util.List<RcdoSuggestionDto> suggestions
    ) {
        static SuggestRcdoResponse from(AiSuggestionService.SuggestionResult result) {
            return new SuggestRcdoResponse(
                    result.status(),
                    result.suggestions().stream()
                            .map(s -> new RcdoSuggestionDto(
                                    s.outcomeId(), s.rallyCryName(), s.objectiveName(),
                                    s.outcomeName(), s.confidence(), s.rationale()
                            ))
                            .toList()
            );
        }
    }

    public record RcdoSuggestionDto(
            String outcomeId,
            String rallyCryName,
            String objectiveName,
            String outcomeName,
            double confidence,
            String rationale
    ) {}

    public record DraftReconciliationRequest(String planId) {}

    public record DraftReconciliationResponse(
            String status,
            java.util.List<ReconciliationDraftItemDto> drafts
    ) {
        static DraftReconciliationResponse from(AiSuggestionService.ReconciliationDraftResult result) {
            return new DraftReconciliationResponse(
                    result.status(),
                    result.drafts().stream()
                            .map(d -> new ReconciliationDraftItemDto(
                                    d.commitId(), d.suggestedStatus(),
                                    d.suggestedDeltaReason(), d.suggestedActualResult()
                            ))
                            .toList()
            );
        }
    }

    public record ReconciliationDraftItemDto(
            String commitId,
            String suggestedStatus,
            String suggestedDeltaReason,
            String suggestedActualResult
    ) {}

    public record ManagerInsightsRequest(String weekStart) {}

    public record ManagerInsightsResponse(
            String status,
            String headline,
            java.util.List<ManagerInsightDto> insights
    ) {
        static ManagerInsightsResponse from(AiSuggestionService.ManagerInsightsResult result) {
            return new ManagerInsightsResponse(
                    result.status(),
                    result.headline(),
                    result.insights().stream()
                            .map(i -> new ManagerInsightDto(i.title(), i.detail(), i.severity()))
                            .toList()
            );
        }
    }

    public record ManagerInsightDto(
            String title,
            String detail,
            String severity
    ) {}
}
