package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.issues.domain.EffortType;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link AiEffortTypeSuggestionService} (Phase 6, Step 9).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Keyword-based heuristic fallback classification</li>
 *   <li>Confidence threshold filtering</li>
 *   <li>LLM response parsing</li>
 *   <li>Cache hit/miss behaviour</li>
 * </ul>
 */
class AiEffortTypeSuggestionServiceTest {

    private LlmClient llmClient;
    private AiCacheService cacheService;
    private AiEffortTypeSuggestionService service;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        cacheService = new AiCacheService(Duration.ofHours(1));
        service = new AiEffortTypeSuggestionService(llmClient, cacheService);
    }

    // ─── Keyword heuristic fallback ──────────────────────────────────────────

    @Nested
    class KeywordHeuristic {

        @ParameterizedTest(name = "BUILD: \"{0}\"")
        @CsvSource({
            "implement OAuth2 login flow, ,BUILD",
            "build the recommendation engine, ,BUILD",
            "deploy the new pipeline to staging, ,BUILD",
            "scaffold new auth module, ,BUILD",
            "develop data export functionality, ,BUILD"
        })
        void classifiesBuildKeywords(String title, String description, String expected) {
            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.keywordFallback(title, description == null || description.isEmpty() ? null : description);

            assertNotNull(result);
            assertEquals("ok", result.status());
            assertEquals(EffortType.valueOf(expected), result.suggestedType());
            assertNotNull(result.confidence());
        }

        @ParameterizedTest(name = "MAINTAIN: \"{0}\"")
        @CsvSource({
            "fix production memory leak, ,MAINTAIN",
            "patch auth library for security, ,MAINTAIN",
            "resolve incident on payment queue, ,MAINTAIN",
            "triage failing test in CI, ,MAINTAIN",
            "upgrade DB dependencies, ,MAINTAIN"
        })
        void classifiesMaintainKeywords(String title, String description, String expected) {
            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.keywordFallback(title, null);

            assertEquals(EffortType.valueOf(expected), result.suggestedType());
        }

        @ParameterizedTest(name = "COLLABORATE: \"{0}\"")
        @CsvSource({
            "review PRs for Q3 sprint, ,COLLABORATE",
            "1:1 with direct reports, ,COLLABORATE",
            "customer call: Acme Corp feedback, ,COLLABORATE",
            "pair with junior devs on auth, ,COLLABORATE",
            "interview candidates for backend role, ,COLLABORATE"
        })
        void classifiesCollaborateKeywords(String title, String description, String expected) {
            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.keywordFallback(title, null);

            assertEquals(EffortType.valueOf(expected), result.suggestedType());
        }

        @ParameterizedTest(name = "LEARN: \"{0}\"")
        @CsvSource({
            "spike: evaluate vector DB options, ,LEARN",
            "research GraphQL federation, ,LEARN",
            "deep dive into Kafka consumer groups, ,LEARN",
            "training: Kubernetes certification, ,LEARN",
            "explore WebAssembly architecture, ,LEARN"
        })
        void classifiesLearnKeywords(String title, String description, String expected) {
            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.keywordFallback(title, null);

            assertEquals(EffortType.valueOf(expected), result.suggestedType());
        }

        @Test
        void returnsNoSuggestionForAmbiguousTitle() {
            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.keywordFallback("Q3 retrospective items", null);

            // "retro" matches COLLABORATE - this is expected by keyword logic
            // Ambiguous to humans but COLLABORATE keyword "retro" fires
            // Use a truly ambiguous title with no keywords
            result = service.keywordFallback("General work item", null);

            assertNotNull(result);
            assertEquals("ok", result.status());
            assertNull(result.suggestedType());
            assertNull(result.confidence());
        }

        @Test
        void usesDescriptionWhenTitleIsAmbiguous() {
            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.keywordFallback("Work item", "Fix the bug causing null pointer");

            assertEquals(EffortType.MAINTAIN, result.suggestedType());
        }

        @Test
        void isCaseInsensitive() {
            AiEffortTypeSuggestionService.SuggestionResult upper =
                    service.keywordFallback("IMPLEMENT new LOGIN FLOW", null);
            AiEffortTypeSuggestionService.SuggestionResult lower =
                    service.keywordFallback("implement new login flow", null);

            assertEquals(upper.suggestedType(), lower.suggestedType());
        }
    }

    // ─── Confidence threshold ────────────────────────────────────────────────

    @Nested
    class ConfidenceThreshold {

        @Test
        void returnsNoSuggestionWhenLlmConfidenceBelowThreshold() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"effortType\": \"BUILD\", \"confidence\": 0.50}");

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("Implement feature", null, null);

            assertEquals("ok", result.status());
            assertNull(result.suggestedType());
        }

        @Test
        void returnsSuggestionWhenLlmConfidenceAtThreshold() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"effortType\": \"BUILD\", \"confidence\": 0.6}");

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("Implement feature", null, null);

            assertEquals("ok", result.status());
            assertEquals(EffortType.BUILD, result.suggestedType());
        }

        @Test
        void returnsSuggestionWhenLlmConfidenceAboveThreshold() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"effortType\": \"MAINTAIN\", \"confidence\": 0.92}");

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("Fix login bug", null, null);

            assertEquals("ok", result.status());
            assertEquals(EffortType.MAINTAIN, result.suggestedType());
            assertEquals(0.92, result.confidence());
        }
    }

    // ─── LLM response parsing ────────────────────────────────────────────────

    @Nested
    class LlmResponseParsing {

        @Test
        void parsesAllEffortTypes() {
            for (EffortType effortType : EffortType.values()) {
                when(llmClient.complete(any(), anyString()))
                        .thenReturn(String.format(
                                "{\"effortType\": \"%s\", \"confidence\": 0.85}", effortType.name()));

                AiEffortTypeSuggestionService.SuggestionResult result =
                        service.suggest("Test title " + effortType, null, null);
                cacheService.clear(); // ensure fresh call each time

                service = new AiEffortTypeSuggestionService(llmClient, cacheService);
                result = service.suggest("Test title " + effortType, null, null);

                assertEquals(effortType, result.suggestedType());
            }
        }

        @Test
        void fallsBackToKeywordWhenLlmUnavailable() {
            when(llmClient.complete(any(), anyString()))
                    .thenThrow(new LlmClient.LlmUnavailableException("timeout"));

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("Fix production bug", null, null);

            // keyword fallback should pick up "fix" → MAINTAIN
            assertEquals("ok", result.status());
            assertEquals(EffortType.MAINTAIN, result.suggestedType());
        }

        @Test
        void returnsUnavailableWhenLlmUnavailableAndHeuristicHasNoSignal() {
            when(llmClient.complete(any(), anyString()))
                    .thenThrow(new LlmClient.LlmUnavailableException("timeout"));

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("General work item", null, null);

            assertEquals("unavailable", result.status());
            assertNull(result.suggestedType());
            assertNull(result.confidence());
        }

        @Test
        void returnsNoSuggestionWhenLlmReturnsUnknownType() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"effortType\": \"UNKNOWN_TYPE\", \"confidence\": 0.90}");

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("Some title", null, null);

            assertEquals("ok", result.status());
            assertNull(result.suggestedType());
        }

        @Test
        void returnsNoSuggestionWhenLlmReturnsMalformedJson() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("not valid json");

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("Some title", null, null);

            assertEquals("ok", result.status());
            assertNull(result.suggestedType());
        }

        @Test
        void returnsNoSuggestionWhenLlmResponseMissingFields() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"other\": \"value\"}");

            AiEffortTypeSuggestionService.SuggestionResult result =
                    service.suggest("Some title", null, null);

            assertEquals("ok", result.status());
            assertNull(result.suggestedType());
        }
    }

    // ─── Cache behaviour ─────────────────────────────────────────────────────

    @Nested
    class CacheBehaviour {

        @Test
        void returnsCachedResultOnSecondCall() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"effortType\": \"BUILD\", \"confidence\": 0.85}");

            service.suggest("Build feature", null, null);
            service.suggest("Build feature", null, null);

            // LLM should only be called once
            verify(llmClient, org.mockito.Mockito.times(1)).complete(any(), anyString());
        }

        @Test
        void differentInputsProduceSeparateCacheEntries() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"effortType\": \"BUILD\", \"confidence\": 0.85}")
                    .thenReturn("{\"effortType\": \"MAINTAIN\", \"confidence\": 0.90}");

            service.suggest("Build feature", null, null);
            service.suggest("Fix bug", null, null);

            verify(llmClient, org.mockito.Mockito.times(2)).complete(any(), anyString());
        }

        @Test
        void outcomeIdContributesToCacheKey() {
            when(llmClient.complete(any(), anyString()))
                    .thenReturn("{\"effortType\": \"BUILD\", \"confidence\": 0.85}")
                    .thenReturn("{\"effortType\": \"COLLABORATE\", \"confidence\": 0.80}");

            service.suggest("Work on feature", null, "outcome-1");
            service.suggest("Work on feature", null, "outcome-2");

            verify(llmClient, org.mockito.Mockito.times(2)).complete(any(), anyString());
        }
    }

    // ─── Cache key ───────────────────────────────────────────────────────────

    @Nested
    class CacheKey {

        @Test
        void sameInputProducesSameKey() {
            String key1 = AiEffortTypeSuggestionService.buildCacheKey("title", "desc", "oid");
            String key2 = AiEffortTypeSuggestionService.buildCacheKey("title", "desc", "oid");
            assertEquals(key1, key2);
        }

        @Test
        void differentTitleProducesDifferentKey() {
            String key1 = AiEffortTypeSuggestionService.buildCacheKey("title A", null, null);
            String key2 = AiEffortTypeSuggestionService.buildCacheKey("title B", null, null);
            assertNotEquals(key1, key2);
        }

        @Test
        void nullDescriptionHandled() {
            String key = AiEffortTypeSuggestionService.buildCacheKey("title", null, null);
            assertNotNull(key);
            assertTrue(key.startsWith("ai:effort-type:"));
        }
    }
}
