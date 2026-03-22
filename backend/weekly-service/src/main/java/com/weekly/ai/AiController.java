package com.weekly.ai;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import com.weekly.team.repository.TeamMemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AI-assisted suggestion endpoints.
 *
 * <p>POST /api/v1/ai/suggest-rcdo — MVP ship, always enabled.
 * <p>POST /api/v1/ai/draft-reconciliation — MVP beta, behind feature flag.
 * <p>POST /api/v1/ai/manager-insights — manager beta, behind feature flag.
 * <p>POST /api/v1/ai/plan-quality-check — Wave 1, behind {@code planQualityNudge} flag.
 * <p>POST /api/v1/ai/suggest-next-work — Wave 2, behind {@code suggestNextWork} flag.
 * <p>POST /api/v1/ai/suggestion-feedback — Wave 2, behind {@code suggestNextWork} flag.
 * <p>POST /api/v1/ai/suggest-effort-type — Phase 6, behind {@code suggestEffortType} flag.
 * <p>POST /api/v1/ai/rank-backlog — Phase 6, on-demand backlog ranking trigger.
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
    private final PlanQualityService planQualityService;
    private final NextWorkSuggestionService nextWorkSuggestionService;
    private final AiSuggestionFeedbackRepository feedbackRepository;
    private final AiFeatureFlags featureFlags;
    private final RateLimiter rateLimiter;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final AiEffortTypeSuggestionService effortTypeSuggestionService;
    private final BacklogRankingService backlogRankingService;
    private final TeamMemberRepository teamMemberRepository;

    public AiController(
            AiSuggestionService aiSuggestionService,
            PlanQualityService planQualityService,
            NextWorkSuggestionService nextWorkSuggestionService,
            AiSuggestionFeedbackRepository feedbackRepository,
            AiFeatureFlags featureFlags,
            RateLimiter rateLimiter,
            AuthenticatedUserContext authenticatedUserContext,
            AiEffortTypeSuggestionService effortTypeSuggestionService,
            BacklogRankingService backlogRankingService,
            TeamMemberRepository teamMemberRepository
    ) {
        this.aiSuggestionService = aiSuggestionService;
        this.planQualityService = planQualityService;
        this.nextWorkSuggestionService = nextWorkSuggestionService;
        this.feedbackRepository = feedbackRepository;
        this.featureFlags = featureFlags;
        this.rateLimiter = rateLimiter;
        this.authenticatedUserContext = authenticatedUserContext;
        this.effortTypeSuggestionService = effortTypeSuggestionService;
        this.backlogRankingService = backlogRankingService;
        this.teamMemberRepository = teamMemberRepository;
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

    // ─── Effort Type Suggestion ──────────────────────────────

    /**
     * POST /ai/suggest-effort-type
     *
     * <p>Returns an AI-suggested effort type (BUILD/MAINTAIN/COLLABORATE/LEARN)
     * for an issue being created. Phase 6 feature, behind {@code suggestEffortType} flag.
     * Returns 200 with no suggestion when the LLM has low confidence or is unavailable.
     */
    @PostMapping("/suggest-effort-type")
    public ResponseEntity<?> suggestEffortType(
            @RequestBody SuggestEffortTypeRequest request
    ) {
        if (!featureFlags.isSuggestEffortTypeEnabled()) {
            return ResponseEntity.ok(new SuggestEffortTypeResponse("unavailable", null, null));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        AiEffortTypeSuggestionService.SuggestionResult result =
                effortTypeSuggestionService.suggest(
                        request.title(),
                        request.description(),
                        request.outcomeId()
                );

        return ResponseEntity.ok(SuggestEffortTypeResponse.from(result));
    }

    public record SuggestEffortTypeRequest(
            String title,
            String description,
            String outcomeId
    ) {}

    public record SuggestEffortTypeResponse(
            String status,
            String suggestedType,
            Double confidence
    ) {
        static SuggestEffortTypeResponse from(AiEffortTypeSuggestionService.SuggestionResult result) {
            return new SuggestEffortTypeResponse(
                    result.status(),
                    result.suggestedType() != null ? result.suggestedType().name() : null,
                    result.confidence()
            );
        }
    }

    // ─── Backlog Ranking ─────────────────────────────────────────────────────

    /**
     * POST /ai/rank-backlog
     *
     * <p>On-demand trigger: ranks all open issues for the specified team using
     * the deterministic formula (urgency × time_pressure × effort_fit × dependency_bonus).
     * Persists the computed ranks and returns the ranked list ordered by rank ascending
     * (rank 1 = highest priority).
     *
     * <p>Phase 6 feature. Does not require a feature flag — the ranking algorithm is
     * always available. Rate limited at 20 requests per user per minute.
     */
    @PostMapping("/rank-backlog")
    public ResponseEntity<?> rankBacklog(@RequestBody RankBacklogRequest request) {
        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT,
                            "Rate limit exceeded: 20 AI requests per minute"));
        }

        java.util.UUID teamId = java.util.UUID.fromString(request.teamId());
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN,
                            "User is not a member of the target team"));
        }

        List<BacklogRankingService.RankedIssue> ranked =
                backlogRankingService.rankTeamBacklog(authenticatedUserContext.orgId(), teamId);

        List<RankedIssueDto> dtos = ranked.stream()
                .map(r -> new RankedIssueDto(r.issueId().toString(), r.rank(), r.rationale()))
                .toList();

        return ResponseEntity.ok(new RankBacklogResponse("ok", dtos));
    }

    public record RankBacklogRequest(String teamId) {}

    public record RankedIssueDto(String issueId, int rank, String rationale) {}

    public record RankBacklogResponse(String status, List<RankedIssueDto> rankedIssues) {}

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

    // ─── Plan Quality Check ──────────────────────────────────

    /**
     * POST /ai/plan-quality-check
     *
     * <p>Runs data-driven quality checks on the given plan and returns nudge items.
     * Wave 1 feature, behind the {@code planQualityNudge} flag.
     * Returns 200 with empty nudges when the flag is disabled.
     */
    @PostMapping("/plan-quality-check")
    public ResponseEntity<?> planQualityCheck(
            @RequestBody PlanQualityCheckRequest request
    ) {
        if (!featureFlags.isPlanQualityNudgeEnabled()) {
            return ResponseEntity.ok(new PlanQualityCheckResponse("unavailable", java.util.List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        java.util.UUID planId = java.util.UUID.fromString(request.planId());
        PlanQualityService.QualityCheckResult result = planQualityService.checkPlanQuality(
                authenticatedUserContext.orgId(),
                planId,
                authenticatedUserContext.userId()
        );

        return ResponseEntity.ok(PlanQualityCheckResponse.from(result));
    }

    public record PlanQualityCheckRequest(String planId) {}

    public record PlanQualityCheckResponse(
            String status,
            java.util.List<QualityNudgeDto> nudges
    ) {
        static PlanQualityCheckResponse from(PlanQualityService.QualityCheckResult result) {
            return new PlanQualityCheckResponse(
                    result.status(),
                    result.nudges().stream()
                            .map(n -> new QualityNudgeDto(n.type(), n.message(), n.severity()))
                            .toList()
            );
        }
    }

    public record QualityNudgeDto(
            String type,
            String message,
            String severity
    ) {}

    // ─── Next-Work Suggestions ───────────────────────────────────────────────

    /**
     * POST /ai/suggest-next-work
     *
     * <p>Returns data-driven next-work suggestions for the authenticated user.
     * Wave 2 feature, behind the {@code suggestNextWork} flag.
     * Returns 200 with status "unavailable" and empty suggestions when the flag
     * is disabled.
     *
     * <p>Phase 1: pure data queries — no LLM. Surfaces carry-forward items
     * and RCDO coverage gaps, filtered by recent DECLINE feedback.
     */
    @PostMapping("/suggest-next-work")
    public ResponseEntity<?> suggestNextWork(
            @RequestBody SuggestNextWorkRequest request
    ) {
        if (!featureFlags.isSuggestNextWorkEnabled()) {
            return ResponseEntity.ok(
                    new SuggestNextWorkResponse("unavailable", List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(
                            ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        LocalDate asOf = resolveAsOf(request.weekStart());
        NextWorkSuggestionService.NextWorkSuggestionsResult result =
                nextWorkSuggestionService.suggestNextWork(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId(),
                        asOf
                );

        return ResponseEntity.ok(SuggestNextWorkResponse.from(result));
    }

    /**
     * POST /ai/suggestion-feedback
     *
     * <p>Records user feedback (ACCEPT, DEFER, DECLINE) on a next-work suggestion.
     * Wave 2 feature, behind the {@code suggestNextWork} flag.
     * Upsert semantics: submitting feedback for a suggestion the user has already
     * acted on updates the previous record.
     */
    @PostMapping("/suggestion-feedback")
    public ResponseEntity<?> suggestionFeedback(
            @RequestBody @Valid SuggestionFeedbackRequest request
    ) {
        if (!featureFlags.isSuggestNextWorkEnabled()) {
            return ResponseEntity.ok(new SuggestionFeedbackResponse("unavailable"));
        }

        UUID orgId = authenticatedUserContext.orgId();
        UUID userId = authenticatedUserContext.userId();
        UUID suggestionId = UUID.fromString(request.suggestionId());

        // Upsert: update if existing feedback found, otherwise create
        feedbackRepository.findByOrgIdAndUserIdAndSuggestionId(orgId, userId, suggestionId)
                .ifPresentOrElse(
                        existing -> {
                            existing.updateFeedback(
                                    request.action(),
                                    request.reason(),
                                    request.sourceType(),
                                    request.sourceDetail());
                            feedbackRepository.save(existing);
                        },
                        () -> feedbackRepository.save(new AiSuggestionFeedbackEntity(
                                UUID.randomUUID(),
                                orgId,
                                userId,
                                suggestionId,
                                request.action(),
                                request.reason(),
                                request.sourceType(),
                                request.sourceDetail()
                        ))
                );

        return ResponseEntity.ok(new SuggestionFeedbackResponse("ok"));
    }

    /** Resolves the effective reference Monday for suggestion queries. */
    private LocalDate resolveAsOf(String weekStart) {
        if (weekStart != null && !weekStart.isBlank()) {
            return LocalDate.parse(weekStart);
        }
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    // ─── Next-Work Request / Response DTOs ──────────────────────────────────

    public record SuggestNextWorkRequest(String weekStart) {}

    public record SuggestNextWorkResponse(
            String status,
            List<NextWorkSuggestionDto> suggestions
    ) {
        static SuggestNextWorkResponse from(
                NextWorkSuggestionService.NextWorkSuggestionsResult result) {
            return new SuggestNextWorkResponse(
                    result.status(),
                    result.suggestions().stream()
                            .map(s -> new NextWorkSuggestionDto(
                                    s.suggestionId().toString(),
                                    s.title(),
                                    s.suggestedOutcomeId(),
                                    s.suggestedChessPriority(),
                                    s.confidence(),
                                    s.source(),
                                    s.sourceDetail(),
                                    s.rationale(),
                                    s.externalTicketUrl(),
                                    s.externalTicketStatus()
                            ))
                            .toList()
            );
        }
    }

    public record NextWorkSuggestionDto(
            String suggestionId,
            String title,
            String suggestedOutcomeId,
            String suggestedChessPriority,
            double confidence,
            String source,
            String sourceDetail,
            String rationale,
            String externalTicketUrl,
            String externalTicketStatus
    ) {}

    public record SuggestionFeedbackRequest(
            String suggestionId,
            @Pattern(regexp = "ACCEPT|DEFER|DECLINE",
                    message = "action must be one of: ACCEPT, DEFER, DECLINE")
            String action,
            String reason,
            String sourceType,
            String sourceDetail
    ) {}

    public record SuggestionFeedbackResponse(String status) {}
}
