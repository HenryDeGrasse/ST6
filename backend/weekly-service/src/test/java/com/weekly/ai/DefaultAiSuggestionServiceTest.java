package com.weekly.ai;

import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import com.weekly.shared.CommitDataProvider;
import com.weekly.shared.ManagerInsightDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAiSuggestionService}.
 */
class DefaultAiSuggestionServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    private StubLlmClient llmClient;
    private InMemoryRcdoClient rcdoClient;
    private AiCacheService cacheService;
    private CommitDataProvider commitDataProvider;
    private ManagerInsightDataProvider managerInsightDataProvider;
    private DefaultAiSuggestionService service;

    @BeforeEach
    void setUp() {
        llmClient = new StubLlmClient();
        rcdoClient = new InMemoryRcdoClient();
        cacheService = new AiCacheService(Duration.ofHours(1));
        commitDataProvider = mock(CommitDataProvider.class);
        managerInsightDataProvider = mock(ManagerInsightDataProvider.class);
        service = new DefaultAiSuggestionService(
                llmClient, rcdoClient, cacheService, commitDataProvider, managerInsightDataProvider
        );
    }

    private RcdoTree buildTestTree() {
        String outcomeId = UUID.randomUUID().toString();
        return new RcdoTree(List.of(
                new RcdoTree.RallyCry("rc1", "Revenue Growth", List.of(
                        new RcdoTree.Objective("obj1", "Enterprise Sales", "rc1", List.of(
                                new RcdoTree.Outcome(outcomeId, "Close Q1 deals", "obj1")
                        ))
                ))
        ));
    }

    @Nested
    class SuggestRcdo {

        @Test
        void returnsUnavailableWhenRcdoTreeEmpty() {
            AiSuggestionService.SuggestionResult result = service.suggestRcdo(
                    ORG_ID, "Build API", "REST endpoint for users"
            );
            assertEquals("unavailable", result.status());
            assertTrue(result.suggestions().isEmpty());
        }

        @Test
        void returnsSuggestionsFromLlm() {
            RcdoTree tree = buildTestTree();
            rcdoClient.setTree(ORG_ID, tree);
            String outcomeId = tree.rallyCries().get(0).objectives().get(0).outcomes().get(0).id();

            // The stub client will return the first candidate
            AiSuggestionService.SuggestionResult result = service.suggestRcdo(
                    ORG_ID, "Close enterprise deals", "Focus on Q1 pipeline"
            );

            assertEquals("ok", result.status());
            assertFalse(result.suggestions().isEmpty());
            assertEquals(outcomeId, result.suggestions().get(0).outcomeId());
        }

        @Test
        void returnsCachedResult() {
            RcdoTree tree = buildTestTree();
            rcdoClient.setTree(ORG_ID, tree);

            // First call populates cache
            AiSuggestionService.SuggestionResult first = service.suggestRcdo(
                    ORG_ID, "Close deals", "Q1 pipeline"
            );

            // Make LLM unavailable
            llmClient.setAvailable(false);

            // Second call should use cache
            AiSuggestionService.SuggestionResult second = service.suggestRcdo(
                    ORG_ID, "Close deals", "Q1 pipeline"
            );

            assertEquals(first.status(), second.status());
            assertEquals(first.suggestions().size(), second.suggestions().size());
        }

        @Test
        void invalidatesCachedSuggestionWhenRcdoTreeChanges() {
            RcdoTree firstTree = buildTestTree();
            rcdoClient.setTree(ORG_ID, firstTree);

            AiSuggestionService.SuggestionResult first = service.suggestRcdo(
                    ORG_ID, "Close deals", "Q1 pipeline"
            );
            String firstOutcomeId = first.suggestions().get(0).outcomeId();

            RcdoTree secondTree = new RcdoTree(List.of(
                    new RcdoTree.RallyCry("rc1", "Revenue Growth", List.of(
                            new RcdoTree.Objective("obj1", "Enterprise Sales", "rc1", List.of(
                                    new RcdoTree.Outcome(UUID.randomUUID().toString(), "Expand renewals", "obj1")
                            ))
                    ))
            ));
            rcdoClient.setTree(ORG_ID, secondTree);

            llmClient.setAvailable(false);

            AiSuggestionService.SuggestionResult second = service.suggestRcdo(
                    ORG_ID, "Close deals", "Q1 pipeline"
            );

            assertEquals("unavailable", second.status(),
                    "Cache key should change when the RCDO tree contents change");
            assertFalse(firstOutcomeId.equals(secondTree.rallyCries().get(0)
                    .objectives().get(0).outcomes().get(0).id()));
        }

        @Test
        void returnsUnavailableWhenLlmDown() {
            RcdoTree tree = buildTestTree();
            rcdoClient.setTree(ORG_ID, tree);
            llmClient.setAvailable(false);

            AiSuggestionService.SuggestionResult result = service.suggestRcdo(
                    ORG_ID, "Build feature", "New feature work"
            );

            assertEquals("unavailable", result.status());
            assertTrue(result.suggestions().isEmpty());
        }

        @Test
        void rejectsHallucinatedOutcomeIds() {
            RcdoTree tree = buildTestTree();
            rcdoClient.setTree(ORG_ID, tree);

            // Override LLM to return a fake outcome ID
            llmClient.setOverrideResponse("""
                    {
                      "suggestions": [
                        {
                          "outcomeId": "fake-id-not-in-tree",
                          "rallyCryName": "Fake",
                          "objectiveName": "Fake",
                          "outcomeName": "Fake",
                          "confidence": 0.99,
                          "rationale": "Hallucinated"
                        }
                      ]
                    }""");

            AiSuggestionService.SuggestionResult result = service.suggestRcdo(
                    ORG_ID, "Something", "Description"
            );

            assertEquals("ok", result.status());
            assertTrue(result.suggestions().isEmpty(),
                    "Hallucinated outcome IDs should be rejected");
        }
    }

    @Nested
    class DraftReconciliation {

        @Test
        void returnsUnavailableWhenPlanNotFound() {
            UUID planId = UUID.randomUUID();
            when(commitDataProvider.planExists(eq(ORG_ID), eq(planId))).thenReturn(false);

            AiSuggestionService.ReconciliationDraftResult result =
                    service.draftReconciliation(ORG_ID, planId);

            assertEquals("unavailable", result.status());
            assertTrue(result.drafts().isEmpty());
        }

        @Test
        void returnsDraftItemsForCommits() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();

            when(commitDataProvider.planExists(eq(ORG_ID), eq(planId))).thenReturn(true);
            when(commitDataProvider.getCommitSummaries(eq(ORG_ID), eq(planId)))
                    .thenReturn(List.of(
                            new CommitDataProvider.CommitSummary(
                                    commitId.toString(), "Ship feature", "Feature shipped", "In progress"
                            )
                    ));

            AiSuggestionService.ReconciliationDraftResult result =
                    service.draftReconciliation(ORG_ID, planId);

            assertEquals("ok", result.status());
            assertFalse(result.drafts().isEmpty());
            assertEquals(commitId.toString(), result.drafts().get(0).commitId());
        }

        @Test
        void returnsUnavailableWhenLlmDown() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();

            when(commitDataProvider.planExists(eq(ORG_ID), eq(planId))).thenReturn(true);
            when(commitDataProvider.getCommitSummaries(eq(ORG_ID), eq(planId)))
                    .thenReturn(List.of(
                            new CommitDataProvider.CommitSummary(
                                    commitId.toString(), "Ship feature", "Feature shipped", ""
                            )
                    ));

            llmClient.setAvailable(false);

            AiSuggestionService.ReconciliationDraftResult result =
                    service.draftReconciliation(ORG_ID, planId);

            assertEquals("unavailable", result.status());
            assertTrue(result.drafts().isEmpty());
        }
    }

    @Nested
    class ManagerInsights {

        @Test
        void returnsManagerInsightsFromLlm() {
            UUID managerId = UUID.randomUUID();
            LocalDate weekStart = LocalDate.of(2026, 3, 9);
            when(managerInsightDataProvider.getManagerWeekContext(eq(ORG_ID), eq(managerId), eq(weekStart)))
                    .thenReturn(new ManagerInsightDataProvider.ManagerWeekContext(
                            weekStart.toString(),
                            new ManagerInsightDataProvider.ReviewCounts(1, 2, 0),
                            List.of(new ManagerInsightDataProvider.TeamMemberContext(
                                    UUID.randomUUID().toString(),
                                    "RECONCILED",
                                    "REVIEW_PENDING",
                                    5,
                                    1,
                                    0,
                                    0,
                                    1,
                                    2,
                                    false,
                                    false
                            )),
                            List.of(new ManagerInsightDataProvider.RcdoFocusContext(
                                    UUID.randomUUID().toString(),
                                    "Close Q1 deals",
                                    "Enterprise Sales",
                                    "Revenue Growth",
                                    4,
                                    1,
                                    2
                            ))
                    ));

            AiSuggestionService.ManagerInsightsResult result =
                    service.draftManagerInsights(ORG_ID, managerId, weekStart);

            assertEquals("ok", result.status());
            assertFalse(result.insights().isEmpty());
            assertEquals("INFO", result.insights().get(0).severity());
        }
    }
}
