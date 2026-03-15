package com.weekly.ai;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiController}.
 */
class AiControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(USER_ID, ORG_ID, Set.of());

    private AiSuggestionService aiService;
    private AiFeatureFlags featureFlags;
    private RateLimiter rateLimiter;
    private AuthenticatedUserContext authenticatedUserContext;
    private AiController controller;

    @BeforeEach
    void setUp() {
        aiService = mock(AiSuggestionService.class);
        featureFlags = new AiFeatureFlags();
        rateLimiter = new RateLimiter(20, java.time.Duration.ofMinutes(1));
        authenticatedUserContext = new AuthenticatedUserContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );
        controller = new AiController(aiService, featureFlags, rateLimiter, authenticatedUserContext);
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
            // Default: draftReconciliationEnabled = false
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
    class ManagerInsights {

        @Test
        void returnsUnavailableWhenFeatureDisabled() {
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
}
