package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.ai.rag.HydeQueryService;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.team.repository.TeamMemberRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link AiController}.
 */
class AiControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(USER_ID, ORG_ID, Set.of());

    private AiSuggestionService aiService;
    private PlanQualityService planQualityService;
    private NextWorkSuggestionService nextWorkSuggestionService;
    private AiSuggestionFeedbackRepository feedbackRepository;
    private AiFeatureFlags featureFlags;
    private RateLimiter rateLimiter;
    private AuthenticatedUserContext authenticatedUserContext;
    private AiEffortTypeSuggestionService effortTypeSuggestionService;
    private BacklogRankingService backlogRankingService;
    private TeamMemberRepository teamMemberRepository;
    private HydeQueryService hydeQueryService;
    private IssueRepository issueRepository;
    private AiController controller;

    @BeforeEach
    void setUp() {
        aiService = mock(AiSuggestionService.class);
        planQualityService = mock(PlanQualityService.class);
        nextWorkSuggestionService = mock(NextWorkSuggestionService.class);
        feedbackRepository = mock(AiSuggestionFeedbackRepository.class);
        effortTypeSuggestionService = mock(AiEffortTypeSuggestionService.class);
        backlogRankingService = mock(BacklogRankingService.class);
        teamMemberRepository = mock(TeamMemberRepository.class);
        hydeQueryService = mock(HydeQueryService.class);
        issueRepository = mock(IssueRepository.class);
        featureFlags = new AiFeatureFlags();
        rateLimiter = new RateLimiter(20, java.time.Duration.ofMinutes(1));
        authenticatedUserContext = new AuthenticatedUserContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );
        controller = new AiController(
                aiService, planQualityService, nextWorkSuggestionService,
                feedbackRepository, featureFlags, rateLimiter, authenticatedUserContext,
                effortTypeSuggestionService, backlogRankingService, teamMemberRepository,
                hydeQueryService, issueRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class SuggestRcdo {

        @Test
        void returnsOkWithSuggestions() {
            var suggestion = new AiSuggestionService.RcdoSuggestion(
                    UUID.randomUUID().toString(), "RC1", "OBJ1", "OUT1", 0.85, "Good match"
            );
            when(aiService.suggestRcdo(eq(ORG_ID), eq("my title"), eq("my desc")))
                    .thenReturn(new AiSuggestionService.SuggestionResult("ok", List.of(suggestion)));

            var request = new AiController.SuggestRcdoRequest("my title", "my desc");
            ResponseEntity<?> response = controller.suggestRcdo(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestRcdoResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals(1, body.suggestions().size());
            assertEquals("RC1", body.suggestions().get(0).rallyCryName());
        }

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setSuggestRcdoEnabled(false);
            var request = new AiController.SuggestRcdoRequest("title", null);

            ResponseEntity<?> response = controller.suggestRcdo(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestRcdoResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertEquals(0, body.suggestions().size());
            verify(aiService, never()).suggestRcdo(any(), any(), any());
        }

        @Test
        void returns429WhenRateLimited() {
            // Exhaust rate limit
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService,
                    planQualityService,
                    nextWorkSuggestionService,
                    feedbackRepository,
                    featureFlags,
                    strictLimiter,
                    authenticatedUserContext,
                    effortTypeSuggestionService,
                    backlogRankingService,
                    teamMemberRepository,
                    hydeQueryService,
                    issueRepository
            );
            var request = new AiController.SuggestRcdoRequest("title", null);

            ResponseEntity<?> response = strictController.suggestRcdo(request);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            verify(aiService, never()).suggestRcdo(any(), any(), any());
        }

        @Test
        void returnsUnavailableOnLlmError() {
            when(aiService.suggestRcdo(eq(ORG_ID), eq("title"), eq(null)))
                    .thenReturn(AiSuggestionService.SuggestionResult.unavailable());

            var request = new AiController.SuggestRcdoRequest("title", null);
            ResponseEntity<?> response = controller.suggestRcdo(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestRcdoResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertEquals(0, body.suggestions().size());
        }
    }

    @Nested
    class DraftReconciliation {

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setDraftReconciliationEnabled(false);
            var request = new AiController.DraftReconciliationRequest(UUID.randomUUID().toString());

            ResponseEntity<?> response = controller.draftReconciliation(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.DraftReconciliationResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertEquals(0, body.drafts().size());
        }

        @Test
        void returnsOkWhenEnabled() {
            featureFlags.setDraftReconciliationEnabled(true);
            UUID planId = UUID.randomUUID();
            var draftItem = new AiSuggestionService.ReconciliationDraftItem(
                    UUID.randomUUID().toString(), "DONE", null, "Completed"
            );
            when(aiService.draftReconciliation(eq(ORG_ID), eq(planId)))
                    .thenReturn(new AiSuggestionService.ReconciliationDraftResult("ok", List.of(draftItem)));

            var request = new AiController.DraftReconciliationRequest(planId.toString());
            ResponseEntity<?> response = controller.draftReconciliation(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.DraftReconciliationResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals(1, body.drafts().size());
        }

        @Test
        void returns429WhenRateLimited() {
            featureFlags.setDraftReconciliationEnabled(true);
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService,
                    planQualityService,
                    nextWorkSuggestionService,
                    feedbackRepository,
                    featureFlags,
                    strictLimiter,
                    authenticatedUserContext,
                    effortTypeSuggestionService,
                    backlogRankingService,
                    teamMemberRepository,
                    hydeQueryService,
                    issueRepository
            );
            var request = new AiController.DraftReconciliationRequest(UUID.randomUUID().toString());

            ResponseEntity<?> response = strictController.draftReconciliation(request);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        }
    }

    @Nested
    class PlanQualityCheck {

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setPlanQualityNudgeEnabled(false);
            var request = new AiController.PlanQualityCheckRequest(UUID.randomUUID().toString());

            ResponseEntity<?> response = controller.planQualityCheck(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.PlanQualityCheckResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertEquals(0, body.nudges().size());
        }

        @Test
        void returnsNudgesWhenEnabled() {
            featureFlags.setPlanQualityNudgeEnabled(true);
            UUID planId = UUID.randomUUID();
            var nudge = new QualityNudge("COVERAGE_GAP", "Missing top rally cries.", "WARNING");
            when(planQualityService.checkPlanQuality(eq(ORG_ID), eq(planId), eq(USER_ID)))
                    .thenReturn(new PlanQualityService.QualityCheckResult("ok", List.of(nudge)));

            var request = new AiController.PlanQualityCheckRequest(planId.toString());
            ResponseEntity<?> response = controller.planQualityCheck(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.PlanQualityCheckResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals(1, body.nudges().size());
            assertEquals("COVERAGE_GAP", body.nudges().get(0).type());
            assertEquals("WARNING", body.nudges().get(0).severity());
        }

        @Test
        void returns429WhenRateLimited() {
            featureFlags.setPlanQualityNudgeEnabled(true);
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService,
                    planQualityService,
                    nextWorkSuggestionService,
                    feedbackRepository,
                    featureFlags,
                    strictLimiter,
                    authenticatedUserContext,
                    effortTypeSuggestionService,
                    backlogRankingService,
                    teamMemberRepository,
                    hydeQueryService,
                    issueRepository
            );
            var request = new AiController.PlanQualityCheckRequest(UUID.randomUUID().toString());

            ResponseEntity<?> response = strictController.planQualityCheck(request);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        }

        @Test
        void returnsEmptyNudgesWhenServiceReturnsUnavailable() {
            featureFlags.setPlanQualityNudgeEnabled(true);
            UUID planId = UUID.randomUUID();
            when(planQualityService.checkPlanQuality(eq(ORG_ID), eq(planId), eq(USER_ID)))
                    .thenReturn(PlanQualityService.QualityCheckResult.unavailable());

            var request = new AiController.PlanQualityCheckRequest(planId.toString());
            ResponseEntity<?> response = controller.planQualityCheck(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.PlanQualityCheckResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertEquals(0, body.nudges().size());
        }
    }

    @Nested
    class ManagerInsights {

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setManagerInsightsEnabled(false);
            var request = new AiController.ManagerInsightsRequest("2026-03-09");

            ResponseEntity<?> response = controller.managerInsights(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.ManagerInsightsResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertEquals(0, body.insights().size());
        }

        @Test
        void returnsOkWhenEnabled() {
            featureFlags.setManagerInsightsEnabled(true);
            var insight = new AiSuggestionService.ManagerInsight(
                    "Review backlog",
                    "Two reports still need review.",
                    "WARNING"
            );
            when(aiService.draftManagerInsights(
                    eq(ORG_ID), eq(USER_ID), eq(java.time.LocalDate.parse("2026-03-09"))))
                    .thenReturn(new AiSuggestionService.ManagerInsightsResult(
                            "ok",
                            "Team focus is healthy overall.",
                            List.of(insight)
                    ));

            var request = new AiController.ManagerInsightsRequest("2026-03-09");
            ResponseEntity<?> response = controller.managerInsights(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.ManagerInsightsResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals("Team focus is healthy overall.", body.headline());
            assertEquals(1, body.insights().size());
        }
    }

    @Nested
    class SuggestNextWork {

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setSuggestNextWorkEnabled(false);
            var request = new AiController.SuggestNextWorkRequest(null);

            ResponseEntity<?> response = controller.suggestNextWork(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestNextWorkResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertEquals(0, body.suggestions().size());
        }

        @Test
        void returnsOkWithSuggestionsWhenEnabled() {
            featureFlags.setSuggestNextWorkEnabled(true);
            UUID suggestionId = UUID.randomUUID();
            var suggestion = new NextWorkSuggestionService.NextWorkSuggestion(
                    suggestionId,
                    "Ship feature X",
                    UUID.randomUUID().toString(),
                    "QUEEN",
                    0.85,
                    "CARRY_FORWARD",
                    "Carried from week of 2026-03-09",
                    "Not completed last week",
                    null,
                    null
            );
            when(nextWorkSuggestionService.suggestNextWork(
                    eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(new NextWorkSuggestionService.NextWorkSuggestionsResult(
                            "ok", List.of(suggestion)));

            var request = new AiController.SuggestNextWorkRequest(null);
            ResponseEntity<?> response = controller.suggestNextWork(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestNextWorkResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals(1, body.suggestions().size());
            assertEquals(suggestionId.toString(), body.suggestions().get(0).suggestionId());
            assertEquals("Ship feature X", body.suggestions().get(0).title());
            assertEquals("CARRY_FORWARD", body.suggestions().get(0).source());
        }

        @Test
        void returns429WhenRateLimited() {
            featureFlags.setSuggestNextWorkEnabled(true);
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService,
                    planQualityService,
                    nextWorkSuggestionService,
                    feedbackRepository,
                    featureFlags,
                    strictLimiter,
                    authenticatedUserContext,
                    effortTypeSuggestionService,
                    backlogRankingService,
                    teamMemberRepository,
                    hydeQueryService,
                    issueRepository
            );
            var request = new AiController.SuggestNextWorkRequest(null);

            ResponseEntity<?> response = strictController.suggestNextWork(request);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        }

        @Test
        void usesExplicitWeekStartWhenProvided() {
            featureFlags.setSuggestNextWorkEnabled(true);
            when(nextWorkSuggestionService.suggestNextWork(
                    eq(ORG_ID), eq(USER_ID), eq(java.time.LocalDate.parse("2026-03-09"))))
                    .thenReturn(new NextWorkSuggestionService.NextWorkSuggestionsResult(
                            "ok", List.of()));

            var request = new AiController.SuggestNextWorkRequest("2026-03-09");
            ResponseEntity<?> response = controller.suggestNextWork(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(nextWorkSuggestionService)
                    .suggestNextWork(ORG_ID, USER_ID, java.time.LocalDate.parse("2026-03-09"));
        }
    }

    @Nested
    class SuggestionFeedback {

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setSuggestNextWorkEnabled(false);
            var request = new AiController.SuggestionFeedbackRequest(
                    UUID.randomUUID().toString(), "DECLINE", null, null, null);

            ResponseEntity<?> response = controller.suggestionFeedback(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestionFeedbackResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
        }

        @Test
        void savesFeedbackWhenEnabled() {
            featureFlags.setSuggestNextWorkEnabled(true);
            UUID suggestionId = UUID.randomUUID();
            when(feedbackRepository.findByOrgIdAndUserIdAndSuggestionId(
                    eq(ORG_ID), eq(USER_ID), eq(suggestionId)))
                    .thenReturn(java.util.Optional.empty());
            when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new AiController.SuggestionFeedbackRequest(
                    suggestionId.toString(), "DECLINE", "Not relevant", "CARRY_FORWARD", null);
            ResponseEntity<?> response = controller.suggestionFeedback(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestionFeedbackResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
        }

        @Test
        void updatesFeedbackWhenAlreadyExists() {
            featureFlags.setSuggestNextWorkEnabled(true);
            UUID suggestionId = UUID.randomUUID();
            AiSuggestionFeedbackEntity existing = new AiSuggestionFeedbackEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, suggestionId,
                    "DEFER", null, null, null);
            Instant originalTimestamp = Instant.now().minus(30, ChronoUnit.DAYS);
            ReflectionTestUtils.setField(existing, "createdAt", originalTimestamp);
            when(feedbackRepository.findByOrgIdAndUserIdAndSuggestionId(
                    eq(ORG_ID), eq(USER_ID), eq(suggestionId)))
                    .thenReturn(java.util.Optional.of(existing));
            when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new AiController.SuggestionFeedbackRequest(
                    suggestionId.toString(), "DECLINE", "Changed my mind",
                    "COVERAGE_GAP", "4-week drought");
            ResponseEntity<?> response = controller.suggestionFeedback(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("DECLINE", existing.getAction());
            assertEquals("Changed my mind", existing.getReason());
            assertEquals("COVERAGE_GAP", existing.getSourceType());
            assertEquals("4-week drought", existing.getSourceDetail());
            assertTrue(existing.getCreatedAt().isAfter(originalTimestamp));
        }
    }

    // ─── SuggestEffortType ───────────────────────────────────────────────────

    @Nested
    class SuggestEffortType {

        @Test
        void returnsOkWithSuggestedType() {
            var result = new AiEffortTypeSuggestionService.SuggestionResult(
                    "ok", com.weekly.issues.domain.EffortType.BUILD, 0.85);
            when(effortTypeSuggestionService.suggest(eq("Build login page"), eq(null), eq(null)))
                    .thenReturn(result);

            var request = new AiController.SuggestEffortTypeRequest("Build login page", null, null);
            ResponseEntity<?> response = controller.suggestEffortType(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestEffortTypeResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals("BUILD", body.suggestedType());
            assertEquals(0.85, body.confidence());
        }

        @Test
        void returnsOkWithNoSuggestionWhenLowConfidence() {
            var result = new AiEffortTypeSuggestionService.SuggestionResult("ok", null, null);
            when(effortTypeSuggestionService.suggest(any(), any(), any())).thenReturn(result);

            var request = new AiController.SuggestEffortTypeRequest("Do stuff", null, null);
            ResponseEntity<?> response = controller.suggestEffortType(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestEffortTypeResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertNull(body.suggestedType());
            assertNull(body.confidence());
        }

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setSuggestEffortTypeEnabled(false);
            var request = new AiController.SuggestEffortTypeRequest("Build login page", null, null);

            ResponseEntity<?> response = controller.suggestEffortType(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestEffortTypeResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
            assertNull(body.suggestedType());
            verify(effortTypeSuggestionService, never()).suggest(any(), any(), any());
        }

        @Test
        void returns429WhenRateLimited() {
            featureFlags.setSuggestEffortTypeEnabled(true);
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService,
                    planQualityService,
                    nextWorkSuggestionService,
                    feedbackRepository,
                    featureFlags,
                    strictLimiter,
                    authenticatedUserContext,
                    effortTypeSuggestionService,
                    backlogRankingService,
                    teamMemberRepository,
                    hydeQueryService,
                    issueRepository
            );
            var request = new AiController.SuggestEffortTypeRequest("Build login page", null, null);

            ResponseEntity<?> response = strictController.suggestEffortType(request);

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            verify(effortTypeSuggestionService, never()).suggest(any(), any(), any());
        }

        @Test
        void passesOutcomeIdToService() {
            String outcomeId = UUID.randomUUID().toString();
            var result = new AiEffortTypeSuggestionService.SuggestionResult(
                    "ok", com.weekly.issues.domain.EffortType.COLLABORATE, 0.75);
            when(effortTypeSuggestionService.suggest(
                    eq("Pair on feature"), eq("Work together"), eq(outcomeId)))
                    .thenReturn(result);

            var request = new AiController.SuggestEffortTypeRequest(
                    "Pair on feature", "Work together", outcomeId);
            ResponseEntity<?> response = controller.suggestEffortType(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SuggestEffortTypeResponse) response.getBody();
            assertNotNull(body);
            assertEquals("COLLABORATE", body.suggestedType());
        }
    }

    @Nested
    class RankBacklog {

        @Test
        void returnsRankedIssuesUsingPersistedRankOrder() {
            UUID teamId = UUID.randomUUID();
            UUID issueId = UUID.randomUUID();
            when(teamMemberRepository.existsByTeamIdAndUserId(teamId, USER_ID)).thenReturn(true);
            when(backlogRankingService.rankTeamBacklog(ORG_ID, teamId))
                    .thenReturn(List.of(new BacklogRankingService.RankedIssue(issueId, 1, 6.0,
                            "urgency=CRITICAL(4) × time_pressure=1.00 × effort_fit=1.0 × dependency_bonus=1.5 → score=6.000")));

            var request = new AiController.RankBacklogRequest(teamId.toString());
            ResponseEntity<?> response = controller.rankBacklog(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.RankBacklogResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals(1, body.rankedIssues().size());
            assertEquals(issueId.toString(), body.rankedIssues().get(0).issueId());
            assertEquals(1, body.rankedIssues().get(0).rank());
        }

        @Test
        void returnsForbiddenWhenUserIsNotTeamMember() {
            UUID teamId = UUID.randomUUID();
            when(teamMemberRepository.existsByTeamIdAndUserId(teamId, USER_ID)).thenReturn(false);

            ResponseEntity<?> response = controller.rankBacklog(
                    new AiController.RankBacklogRequest(teamId.toString()));

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(backlogRankingService, never()).rankTeamBacklog(any(), any());
        }

        @Test
        void returns429WhenRateLimited() {
            UUID teamId = UUID.randomUUID();
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService,
                    planQualityService,
                    nextWorkSuggestionService,
                    feedbackRepository,
                    featureFlags,
                    strictLimiter,
                    authenticatedUserContext,
                    effortTypeSuggestionService,
                    backlogRankingService,
                    teamMemberRepository,
                    hydeQueryService,
                    issueRepository
            );

            ResponseEntity<?> response = strictController.rankBacklog(
                    new AiController.RankBacklogRequest(teamId.toString()));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            verify(backlogRankingService, never()).rankTeamBacklog(any(), any());
        }
    }

    // ─── RecommendWeeklyIssues ────────────────────────────────────────────────

    @Nested
    class RecommendWeeklyIssues {

        @Test
        void returnsOkWithIssueResults() {
            UUID issueId = UUID.randomUUID();
            UUID teamId = UUID.randomUUID();
            var member = mock(com.weekly.team.domain.TeamMemberEntity.class);
            when(member.getTeamId()).thenReturn(teamId);
            when(teamMemberRepository.findAllByOrgIdAndUserId(ORG_ID, USER_ID)).thenReturn(List.of(member));
            when(hydeQueryService.recommendWithHyde(any(), any(), eq(10)))
                    .thenReturn(List.of(new HydeQueryService.IssueId(issueId, 0.9f)));

            var issue = new com.weekly.issues.domain.IssueEntity(
                    issueId, ORG_ID, teamId, "PLAT-42", 42, "Optimize cache", USER_ID);
            issue.setEffortType(com.weekly.issues.domain.EffortType.MAINTAIN);
            issue.setChessPriority(com.weekly.plan.domain.ChessPriority.QUEEN);
            issue.setAiRankRationale("Addresses an at-risk outcome with available capacity");
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(java.util.Optional.of(issue));

            var request = new AiController.RecommendWeeklyIssuesRequest(
                    "2026-03-23", null, 10);
            ResponseEntity<?> response = controller.recommendWeeklyIssues(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.RecommendWeeklyIssuesResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals(1, body.recommendations().size());
            assertEquals(issueId.toString(), body.recommendations().get(0).issueId());
            assertEquals("PLAT-42", body.recommendations().get(0).issueKey());
            assertEquals("MAINTAIN", body.recommendations().get(0).effortType());
            assertEquals("QUEEN", body.recommendations().get(0).chessPriority());
        }

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setHydeRecommendationsEnabled(false);
            var request = new AiController.RecommendWeeklyIssuesRequest(
                    "2026-03-23", null, 10);
            ResponseEntity<?> response = controller.recommendWeeklyIssues(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.RecommendWeeklyIssuesResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
        }

        @Test
        void returns429WhenRateLimited() {
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService, planQualityService, nextWorkSuggestionService,
                    feedbackRepository, featureFlags, strictLimiter, authenticatedUserContext,
                    effortTypeSuggestionService, backlogRankingService, teamMemberRepository,
                    hydeQueryService, issueRepository
            );

            ResponseEntity<?> response = strictController.recommendWeeklyIssues(
                    new AiController.RecommendWeeklyIssuesRequest("2026-03-23", null, 10));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        }

        @Test
        void returnsForbiddenWhenFilteringToTeamUserDoesNotBelongTo() {
            UUID foreignTeamId = UUID.randomUUID();
            when(teamMemberRepository.findAllByOrgIdAndUserId(ORG_ID, USER_ID)).thenReturn(List.of());

            ResponseEntity<?> response = controller.recommendWeeklyIssues(
                    new AiController.RecommendWeeklyIssuesRequest("2026-03-23", foreignTeamId.toString(), 10));

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(hydeQueryService, never()).recommendWithHyde(any(), any(), anyInt());
        }
    }

    // ─── SearchIssues ─────────────────────────────────────────────────────────

    @Nested
    class SearchIssues {

        @Test
        void returnsOkWithSearchResults() {
            UUID issueId = UUID.randomUUID();
            UUID teamId = UUID.randomUUID();
            var member = mock(com.weekly.team.domain.TeamMemberEntity.class);
            when(member.getTeamId()).thenReturn(teamId);
            when(teamMemberRepository.findAllByOrgIdAndUserId(ORG_ID, USER_ID)).thenReturn(List.of(member));
            when(hydeQueryService.searchWithHyde(eq(ORG_ID), eq("caching"), eq(10), any(), any()))
                    .thenReturn(List.of(new HydeQueryService.IssueId(issueId, 0.85f)));

            var issue = new com.weekly.issues.domain.IssueEntity(
                    issueId, ORG_ID, teamId, "PLAT-99", 99, "Implement caching", USER_ID);
            issue.setEffortType(com.weekly.issues.domain.EffortType.BUILD);
            when(issueRepository.findByOrgIdAndId(ORG_ID, issueId)).thenReturn(java.util.Optional.of(issue));

            var request = new AiController.SearchIssuesRequest("caching", null, null, null, 10);
            ResponseEntity<?> response = controller.searchIssues(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SemanticSearchResponse) response.getBody();
            assertNotNull(body);
            assertEquals("ok", body.status());
            assertEquals(1, body.hits().size());
            assertEquals("PLAT-99", body.hits().get(0).issueKey());
            assertEquals("BUILD", body.hits().get(0).effortType());
            assertEquals("OPEN", body.hits().get(0).status());
        }

        @Test
        void returnsBadRequestWhenQueryBlank() {
            var request = new AiController.SearchIssuesRequest("", null, null, null, 10);
            ResponseEntity<?> response = controller.searchIssues(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
            featureFlags.setRagSearchEnabled(false);
            var request = new AiController.SearchIssuesRequest("anything", null, null, null, 10);
            ResponseEntity<?> response = controller.searchIssues(request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            var body = (AiController.SemanticSearchResponse) response.getBody();
            assertNotNull(body);
            assertEquals("unavailable", body.status());
        }

        @Test
        void returns429WhenRateLimited() {
            RateLimiter strictLimiter = new RateLimiter(0, java.time.Duration.ofMinutes(1));
            AiController strictController = new AiController(
                    aiService, planQualityService, nextWorkSuggestionService,
                    feedbackRepository, featureFlags, strictLimiter, authenticatedUserContext,
                    effortTypeSuggestionService, backlogRankingService, teamMemberRepository,
                    hydeQueryService, issueRepository
            );

            ResponseEntity<?> response = strictController.searchIssues(
                    new AiController.SearchIssuesRequest("query", null, null, null, 10));

            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        }

        @Test
        void returnsForbiddenWhenFilteringToTeamUserDoesNotBelongTo() {
            UUID foreignTeamId = UUID.randomUUID();
            when(teamMemberRepository.findAllByOrgIdAndUserId(ORG_ID, USER_ID)).thenReturn(List.of());

            ResponseEntity<?> response = controller.searchIssues(
                    new AiController.SearchIssuesRequest("query", foreignTeamId.toString(), null, null, 10));

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(hydeQueryService, never()).searchWithHyde(any(), any(), anyInt(), any(), any());
        }
    }
}
