package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
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
    private AiController controller;

    @BeforeEach
    void setUp() {
        aiService = mock(AiSuggestionService.class);
        planQualityService = mock(PlanQualityService.class);
        nextWorkSuggestionService = mock(NextWorkSuggestionService.class);
        feedbackRepository = mock(AiSuggestionFeedbackRepository.class);
        featureFlags = new AiFeatureFlags();
        rateLimiter = new RateLimiter(20, java.time.Duration.ofMinutes(1));
        authenticatedUserContext = new AuthenticatedUserContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );
        controller = new AiController(
                aiService, planQualityService, nextWorkSuggestionService,
                feedbackRepository, featureFlags, rateLimiter, authenticatedUserContext);
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
                    authenticatedUserContext
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
                    authenticatedUserContext
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
                    authenticatedUserContext
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
                    authenticatedUserContext
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
}
