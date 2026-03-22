package com.weekly.ai;

import com.weekly.ai.rag.HydeQueryService;
import com.weekly.ai.rag.OutcomeRiskContext;
import com.weekly.ai.rag.UserWorkContext;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import com.weekly.team.repository.TeamMemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * <p>POST /api/v1/ai/recommend-weekly-issues — Phase 6, HyDE-powered weekly recommendations,
 *     behind {@code hydeRecommendationsEnabled} flag.
 * <p>POST /api/v1/ai/search-issues — Phase 6, semantic search over issue history,
 *     behind {@code ragSearchEnabled} flag.
 * <p>POST /api/v1/ai/suggest-deferrals — Phase 6, overcommit deferral suggestions,
 *     behind {@code overcommitDeferralEnabled} flag.
 * <p>GET /api/v1/ai/coverage-gap-inspirations — Phase 6, coverage gap issue creation
 *     suggestions, behind {@code coverageGapInspirationEnabled} flag.
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
    private final HydeQueryService hydeQueryService;
    private final IssueRepository issueRepository;
    private final OvercommitDeferralService overcommitDeferralService;
    private final CoverageGapInspirationService coverageGapInspirationService;

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
            TeamMemberRepository teamMemberRepository,
            HydeQueryService hydeQueryService,
            IssueRepository issueRepository,
            OvercommitDeferralService overcommitDeferralService,
            CoverageGapInspirationService coverageGapInspirationService
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
        this.hydeQueryService = hydeQueryService;
        this.issueRepository = issueRepository;
        this.overcommitDeferralService = overcommitDeferralService;
        this.coverageGapInspirationService = coverageGapInspirationService;
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

    // ─── HyDE Recommendations ─────────────────────────────────────────────────

    /**
     * POST /ai/recommend-weekly-issues
     *
     * <p>Uses HyDE (Hypothetical Document Embeddings) to recommend backlog issues
     * the authenticated user should pick up this week.
     *
     * <p>Contract-first request shape: { weekStart, teamId?, maxItems? }.
     * The deeper user/risk context is derived server-side over time; for now we at least
     * enforce org/team visibility and never trust callers to provide cross-team state.
     */
    @PostMapping("/recommend-weekly-issues")
    public ResponseEntity<?> recommendWeeklyIssues(
            @RequestBody RecommendWeeklyIssuesRequest request
    ) {
        if (!featureFlags.isHydeRecommendationsEnabled()) {
            return ResponseEntity.ok(new RecommendWeeklyIssuesResponse("unavailable", List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT,
                            "Rate limit exceeded: 20 AI requests per minute"));
        }

        if (request.weekStart() == null || request.weekStart().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, "weekStart is required"));
        }

        List<UUID> permittedTeamIds;
        LocalDate weekStart;
        try {
            permittedTeamIds = resolvePermittedTeamIds(request.teamId());
            weekStart = LocalDate.parse(request.weekStart());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, e.getMessage()));
        }
        if (request.teamId() != null && !request.teamId().isBlank() && permittedTeamIds.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN,
                            "User is not a member of the target team"));
        }
        if ((request.teamId() == null || request.teamId().isBlank()) && permittedTeamIds.isEmpty()) {
            return ResponseEntity.ok(new RecommendWeeklyIssuesResponse("ok", List.of()));
        }
        int topK = (request.maxItems() != null && request.maxItems() > 0) ? request.maxItems() : 10;

        UserWorkContext userContext = new UserWorkContext(
                authenticatedUserContext.userId(),
                authenticatedUserContext.orgId(),
                weekStart,
                40.0,
                0.0,
                List.of(),
                List.of(),
                List.of(),
                permittedTeamIds
        );

        List<RecommendedIssue> recommendations = hydeQueryService
                .recommendWithHyde(userContext, new OutcomeRiskContext(List.of(), List.of()), topK)
                .stream()
                .map(result -> issueRepository.findByOrgIdAndId(authenticatedUserContext.orgId(), result.issueId())
                        .map(issue -> toRecommendedIssue(issue, result.score())))
                .flatMap(java.util.Optional::stream)
                .toList();

        return ResponseEntity.ok(new RecommendWeeklyIssuesResponse("ok", recommendations));
    }

    /**
     * POST /ai/search-issues
     *
     * <p>Performs semantic search over the org's issue history using HyDE.
     * Optional filters: {@code teamId}, {@code status}, {@code effortType}.
     */
    @PostMapping("/search-issues")
    public ResponseEntity<?> searchIssues(
            @RequestBody SearchIssuesRequest request
    ) {
        if (!featureFlags.isRagSearchEnabled()) {
            return ResponseEntity.ok(new SemanticSearchResponse("unavailable", List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT,
                            "Rate limit exceeded: 20 AI requests per minute"));
        }

        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, "query is required"));
        }

        List<UUID> permittedTeamIds;
        try {
            permittedTeamIds = resolvePermittedTeamIds(request.teamId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, e.getMessage()));
        }
        if (request.teamId() != null && !request.teamId().isBlank() && permittedTeamIds.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN,
                            "User is not a member of the target team"));
        }
        if ((request.teamId() == null || request.teamId().isBlank()) && permittedTeamIds.isEmpty()) {
            return ResponseEntity.ok(new SemanticSearchResponse("ok", List.of()));
        }

        int topK = (request.limit() != null && request.limit() > 0) ? request.limit() : 10;
        Map<String, Object> filters = buildSearchFilters(request);

        List<SemanticSearchHit> hits = hydeQueryService
                .searchWithHyde(
                        authenticatedUserContext.orgId(),
                        request.query(),
                        topK,
                        filters,
                        permittedTeamIds)
                .stream()
                .map(result -> issueRepository.findByOrgIdAndId(authenticatedUserContext.orgId(), result.issueId())
                        .map(issue -> toSemanticSearchHit(issue, result.score())))
                .flatMap(java.util.Optional::stream)
                .toList();

        return ResponseEntity.ok(new SemanticSearchResponse("ok", hits));
    }

    private Map<String, Object> buildSearchFilters(SearchIssuesRequest request) {
        Map<String, Object> filters = new java.util.HashMap<>();
        if (request.teamId() != null && !request.teamId().isBlank()) {
            filters.put("teamId", request.teamId());
        }
        if (request.status() != null && !request.status().isBlank()) {
            filters.put("status", request.status());
        }
        if (request.effortType() != null && !request.effortType().isBlank()) {
            filters.put("effortType", request.effortType());
        }
        return filters.isEmpty() ? null : filters;
    }

    private List<UUID> resolvePermittedTeamIds(String requestedTeamId) {
        List<UUID> memberTeamIds = teamMemberRepository
                .findAllByOrgIdAndUserId(authenticatedUserContext.orgId(), authenticatedUserContext.userId())
                .stream()
                .map(member -> member.getTeamId())
                .distinct()
                .toList();

        if (requestedTeamId == null || requestedTeamId.isBlank()) {
            return memberTeamIds;
        }

        UUID teamId = UUID.fromString(requestedTeamId);
        return memberTeamIds.contains(teamId) ? List.of(teamId) : List.of();
    }

    private RecommendedIssue toRecommendedIssue(IssueEntity issue, float score) {
        String rationale = issue.getAiRankRationale() != null && !issue.getAiRankRationale().isBlank()
                ? issue.getAiRankRationale()
                : "Semantic match generated from HyDE recommendation context";
        return new RecommendedIssue(
                issue.getId().toString(),
                issue.getIssueKey(),
                issue.getTitle(),
                issue.getEffortType() != null ? issue.getEffortType().name() : null,
                invokeEnumGetterName(issue, "getChessPriority"),
                rationale,
                score
        );
    }

    private String invokeEnumGetterName(IssueEntity issue, String getterName) {
        try {
            Object value = IssueEntity.class.getMethod(getterName).invoke(issue);
            return value instanceof Enum<?> enumValue ? enumValue.name() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private SemanticSearchHit toSemanticSearchHit(IssueEntity issue, float score) {
        return new SemanticSearchHit(
                issue.getId().toString(),
                issue.getIssueKey(),
                issue.getTitle(),
                score,
                issue.getEffortType() != null ? issue.getEffortType().name() : null,
                issue.getStatus().name()
        );
    }

    // ─── HyDE Request / Response DTOs ─────────────────────────────────────────

    public record RecommendWeeklyIssuesRequest(
            String weekStart,
            String teamId,
            Integer maxItems
    ) {}

    public record RecommendedIssue(
            String issueId,
            String issueKey,
            String title,
            String effortType,
            String chessPriority,
            String rationale,
            double confidence
    ) {}

    public record RecommendWeeklyIssuesResponse(
            String status,
            List<RecommendedIssue> recommendations
    ) {}

    public record SearchIssuesRequest(
            String query,
            String teamId,
            String effortType,
            String status,
            Integer limit
    ) {}

    public record SemanticSearchHit(
            String issueId,
            String issueKey,
            String title,
            double score,
            String effortType,
            String status
    ) {}

    public record SemanticSearchResponse(
            String status,
            List<SemanticSearchHit> hits
    ) {}

    // ─── Overcommit Deferral ──────────────────────────────────────────────────

    /**
     * POST /ai/suggest-deferrals
     *
     * <p>When a user's weekly plan exceeds their realistic capacity cap, returns a ranked
     * list of assignments to suggest deferring back to the backlog. Assignments are ranked
     * by lowest chess priority and lowest outcome urgency — items that are least critical
     * are surfaced first. KING-priority and CRITICAL-urgency items are never suggested.
     *
     * <p>Phase 6 feature, behind {@code overcommitDeferralEnabled} flag.
     * Returns 200 with {@code status: "unavailable"} when the flag is disabled.
     */
    @PostMapping("/suggest-deferrals")
    public ResponseEntity<?> suggestDeferrals(
            @RequestBody SuggestDeferralsRequest request
    ) {
        if (!featureFlags.isOvercommitDeferralEnabled()) {
            return ResponseEntity.ok(
                    new SuggestDeferralsResponse("unavailable", java.math.BigDecimal.ZERO,
                            java.math.BigDecimal.ZERO, "Feature disabled.", List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT,
                            "Rate limit exceeded: 20 AI requests per minute"));
        }

        LocalDate weekStart = null;
        if (request.weekStart() != null && !request.weekStart().isBlank()) {
            try {
                weekStart = LocalDate.parse(request.weekStart());
            } catch (java.time.format.DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR,
                                "weekStart must be a valid ISO date (yyyy-MM-dd)"));
            }
        }

        OvercommitDeferralService.DeferralResult result =
                overcommitDeferralService.suggestDeferrals(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId(),
                        weekStart
                );

        return ResponseEntity.ok(SuggestDeferralsResponse.from(result));
    }

    public record SuggestDeferralsRequest(String weekStart) {}

    public record DeferralSuggestionDto(
            String assignmentId,
            String issueId,
            String issueKey,
            String title,
            double estimatedHours,
            String rationale
    ) {}

    public record SuggestDeferralsResponse(
            String status,
            java.math.BigDecimal totalHours,
            java.math.BigDecimal cap,
            String summary,
            List<DeferralSuggestionDto> deferrals
    ) {
        static SuggestDeferralsResponse from(OvercommitDeferralService.DeferralResult result) {
            List<DeferralSuggestionDto> dtos = result.suggestions().stream()
                    .map(s -> new DeferralSuggestionDto(
                            s.assignmentId().toString(),
                            s.issueId().toString(),
                            s.issueKey(),
                            s.title(),
                            s.estimatedHours() != null ? s.estimatedHours().doubleValue() : 0.0,
                            s.rationale()
                    ))
                    .toList();
            return new SuggestDeferralsResponse(
                    result.status(),
                    result.totalHours(),
                    result.cap(),
                    result.summary(),
                    dtos
            );
        }
    }

    // ─── Coverage Gap Inspirations ─────────────────────────────────────────────

    /**
     * GET /ai/coverage-gap-inspirations
     *
     * <p>Returns issue-creation suggestions for RCDO outcomes that have gone uncovered
     * (zero team commits) for a recent stretch. Each suggestion includes a title,
     * description, estimated effort (from RAG similarity on past DONE issues or a default),
     * and the linked outcome.
     *
     * <p>Phase 6 feature, behind {@code coverageGapInspirationEnabled} flag.
     * Returns 200 with {@code status: "unavailable"} when the flag is disabled.
     */
    @GetMapping("/coverage-gap-inspirations")
    public ResponseEntity<?> coverageGapInspirations(
            @RequestParam(required = false) String weekStart
    ) {
        if (!featureFlags.isCoverageGapInspirationEnabled()) {
            return ResponseEntity.ok(
                    new CoverageGapInspirationsResponse("unavailable", List.of()));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT,
                            "Rate limit exceeded: 20 AI requests per minute"));
        }

        LocalDate monday = null;
        if (weekStart != null && !weekStart.isBlank()) {
            try {
                monday = LocalDate.parse(weekStart);
            } catch (java.time.format.DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR,
                                "weekStart must be a valid ISO date (yyyy-MM-dd)"));
            }
        }

        CoverageGapInspirationService.InspirationResult result =
                coverageGapInspirationService.generateInspirations(
                        authenticatedUserContext.orgId(), monday);

        return ResponseEntity.ok(CoverageGapInspirationsResponse.from(result));
    }

    public record InspirationSuggestionDto(
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName,
            String suggestedTitle,
            String suggestedDescription,
            double estimatedHours,
            String rationale,
            int weeksMissing
    ) {}

    public record CoverageGapInspirationsResponse(
            String status,
            List<InspirationSuggestionDto> inspirations
    ) {
        static CoverageGapInspirationsResponse from(
                CoverageGapInspirationService.InspirationResult result) {
            List<InspirationSuggestionDto> dtos = result.inspirations().stream()
                    .map(s -> new InspirationSuggestionDto(
                            s.outcomeId(),
                            s.outcomeName(),
                            s.objectiveName(),
                            s.rallyCryName(),
                            s.suggestedTitle(),
                            s.suggestedDescription(),
                            s.estimatedHours() != null ? s.estimatedHours().doubleValue() : 0.0,
                            s.rationale(),
                            s.weeksMissing()
                    ))
                    .toList();
            return new CoverageGapInspirationsResponse(result.status(), dtos);
        }
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
