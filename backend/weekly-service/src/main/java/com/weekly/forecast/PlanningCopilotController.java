package com.weekly.forecast;

import com.weekly.ai.AiFeatureFlags;
import com.weekly.ai.RateLimiter;
import com.weekly.auth.AuthenticatedUserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.service.PlanValidationException;
import com.weekly.shared.ApiErrorResponse;
import com.weekly.shared.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for manager planning-copilot endpoints.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class PlanningCopilotController {

    private final PlanningCopilotService planningCopilotService;
    private final PlanningCopilotDraftApplyService planningCopilotDraftApplyService;
    private final PlanningCopilotSnapshotRepository snapshotRepository;
    private final AiFeatureFlags featureFlags;
    private final RateLimiter rateLimiter;
    private final AuthenticatedUserContext authenticatedUserContext;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PlanningCopilotController(
            PlanningCopilotService planningCopilotService,
            PlanningCopilotDraftApplyService planningCopilotDraftApplyService,
            PlanningCopilotSnapshotRepository snapshotRepository,
            AiFeatureFlags featureFlags,
            RateLimiter rateLimiter,
            AuthenticatedUserContext authenticatedUserContext,
            ObjectMapper objectMapper) {
        this.planningCopilotService = planningCopilotService;
        this.planningCopilotDraftApplyService = planningCopilotDraftApplyService;
        this.snapshotRepository = snapshotRepository;
        this.featureFlags = featureFlags;
        this.rateLimiter = rateLimiter;
        this.authenticatedUserContext = authenticatedUserContext;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    /** POST /api/v1/ai/team-plan-suggestion */
    @PostMapping("/team-plan-suggestion")
    public ResponseEntity<?> suggestTeamPlan(@RequestBody @Valid TeamPlanSuggestionRequest request) {
        ResponseEntity<?> managerGuard = managerGuard();
        if (managerGuard != null) {
            return managerGuard;
        }

        if (!featureFlags.isPlanningCopilotEnabled()) {
            return ResponseEntity.ok(new TeamPlanSuggestionUnavailableResponse("unavailable"));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        LocalDate weekStart = parseWeekStart(request.weekStart());

        LocalDate today = LocalDate.now(clock);
        boolean regenerate = Boolean.TRUE.equals(request.regenerate());

        if (!regenerate) {
            try {
                Optional<String> cached = snapshotRepository.findSnapshot(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId(),
                        weekStart,
                        today);
                if (cached.isPresent()) {
                    try {
                        var snapshot = objectMapper.readValue(cached.get(), PlanningCopilotService.TeamPlanSuggestionResult.class);
                        return ResponseEntity.ok(new SnapshotEnvelope(snapshot, today.toString()));
                    } catch (JsonProcessingException e) {
                        // Corrupted cache; regenerate
                    }
                }
            } catch (Exception e) {
                // Snapshot table may not exist yet; proceed with fresh generation
            }
        }

        var result = planningCopilotService.suggestTeamPlan(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weekStart);

        try {
            String json = objectMapper.writeValueAsString(result);
            snapshotRepository.upsertSnapshot(
                    authenticatedUserContext.orgId(),
                    authenticatedUserContext.userId(),
                    weekStart,
                    today,
                    json);
        } catch (Exception e) {
            // Non-fatal; snapshot table may not exist or JSON error
        }

        return ResponseEntity.ok(new SnapshotEnvelope(result, today.toString()));
    }

    /** POST /api/v1/ai/team-plan-suggestion/apply */
    @PostMapping("/team-plan-suggestion/apply")
    public ResponseEntity<?> applyTeamPlanSuggestion(@RequestBody @Valid ApplyTeamPlanSuggestionRequest request) {
        ResponseEntity<?> managerGuard = managerGuard();
        if (managerGuard != null) {
            return managerGuard;
        }

        if (!featureFlags.isPlanningCopilotEnabled()) {
            return ResponseEntity.ok(new TeamPlanSuggestionUnavailableResponse("unavailable"));
        }

        if (!rateLimiter.tryAcquire(authenticatedUserContext.userId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiErrorResponse.of(ErrorCode.CONFLICT, "Rate limit exceeded: 20 AI requests per minute"));
        }

        LocalDate weekStart = parseWeekStart(request.weekStart());
        PlanningCopilotDraftApplyService.ApplyTeamPlanSuggestionResult result = planningCopilotDraftApplyService.apply(
                authenticatedUserContext.orgId(),
                authenticatedUserContext.userId(),
                weekStart,
                request.members());
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<?> managerGuard() {
        if (!authenticatedUserContext.isManager()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Manager role required"));
        }
        return null;
    }

    private LocalDate parseWeekStart(String rawWeekStart) {
        if (rawWeekStart == null || rawWeekStart.isBlank()) {
            throw new PlanValidationException(
                    ErrorCode.INVALID_WEEK_START,
                    "weekStart must be an ISO-8601 date",
                    List.of(Map.of("field", "weekStart", "provided", String.valueOf(rawWeekStart))));
        }

        try {
            return LocalDate.parse(rawWeekStart);
        } catch (DateTimeParseException e) {
            throw new PlanValidationException(
                    ErrorCode.INVALID_WEEK_START,
                    "weekStart must be an ISO-8601 date",
                    List.of(Map.of("field", "weekStart", "provided", rawWeekStart)));
        }
    }

    public record TeamPlanSuggestionRequest(@NotBlank String weekStart, Boolean regenerate) {
    }

    public record TeamPlanSuggestionUnavailableResponse(String status) {
    }

    public record ApplyTeamPlanSuggestionRequest(
            @NotBlank String weekStart,
            @NotEmpty List<@Valid TeamMemberApplyRequest> members) {
    }

    public record TeamMemberApplyRequest(
            @NotBlank String userId,
            @NotNull List<@Valid SuggestedCommitApplyRequest> suggestedCommits) {
    }

    public record SuggestedCommitApplyRequest(
            @NotBlank String title,
            String outcomeId,
            String rationale,
            @NotBlank @Pattern(regexp = "KING|QUEEN|ROOK|BISHOP|KNIGHT|PAWN") String chessPriority,
            @PositiveOrZero Double estimatedHours) {
    }
}
    /** Wraps the suggestion result with a generatedAt date for the frontend.
     *  Uses @JsonUnwrapped so the JSON shape is flat: all suggestion fields + generatedAt. */
    record SnapshotEnvelope(
            @com.fasterxml.jackson.annotation.JsonUnwrapped
            PlanningCopilotService.TeamPlanSuggestionResult suggestion,
            String generatedAt) {
    }
