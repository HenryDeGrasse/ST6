package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.rcdo.InMemoryRcdoClient;
import com.weekly.rcdo.RcdoTree;
import com.weekly.shared.CommitDataProvider;
import com.weekly.shared.ManagerInsightDataProvider;
import com.weekly.shared.TeamRcdoUsageProvider;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    private TeamRcdoUsageProvider teamRcdoUsageProvider;
    private UrgencyDataProvider urgencyDataProvider;
    private DefaultAiSuggestionService service;

    @BeforeEach
    void setUp() {
        llmClient = new StubLlmClient();
        rcdoClient = new InMemoryRcdoClient();
        cacheService = new AiCacheService(Duration.ofHours(1));
        commitDataProvider = mock(CommitDataProvider.class);
        managerInsightDataProvider = mock(ManagerInsightDataProvider.class);
        teamRcdoUsageProvider = mock(TeamRcdoUsageProvider.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        // Default: empty team usage (no team context)
        when(teamRcdoUsageProvider.getTeamRcdoUsage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TeamRcdoUsageProvider.TeamRcdoUsageResult(List.of(), Set.of()));
        when(urgencyDataProvider.getOrgUrgencySummary(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        service = new DefaultAiSuggestionService(
                llmClient, rcdoClient, cacheService, commitDataProvider,
                managerInsightDataProvider, teamRcdoUsageProvider, urgencyDataProvider
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

        @Test
        void usesQuarterCoverageForZeroCoveragePromptContext() {
            String coveredThisQuarter = UUID.randomUUID().toString();
            String uncoveredThisQuarter = UUID.randomUUID().toString();
            RcdoTree tree = new RcdoTree(List.of(
                    new RcdoTree.RallyCry("rc1", "Revenue Growth", List.of(
                            new RcdoTree.Objective("obj1", "Enterprise Sales", "rc1", List.of(
                                    new RcdoTree.Outcome(coveredThisQuarter, "Covered Outcome", "obj1"),
                                    new RcdoTree.Outcome(uncoveredThisQuarter, "Uncovered Outcome", "obj1")
                            ))
                    ))
            ));
            rcdoClient.setTree(ORG_ID, tree);
            when(teamRcdoUsageProvider.getTeamRcdoUsage(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new TeamRcdoUsageProvider.TeamRcdoUsageResult(List.of(), Set.of(coveredThisQuarter)));

            class CapturingLlmClient implements LlmClient {
                private List<Message> lastMessages = List.of();

                @Override
                public String complete(List<Message> messages, String responseSchema) {
                    this.lastMessages = messages;
                    return "{\"suggestions\": []}";
                }
            }

            CapturingLlmClient capturingLlmClient = new CapturingLlmClient();
            DefaultAiSuggestionService localService = new DefaultAiSuggestionService(
                    capturingLlmClient,
                    rcdoClient,
                    new AiCacheService(Duration.ofHours(1)),
                    commitDataProvider,
                    managerInsightDataProvider,
                    teamRcdoUsageProvider,
                    urgencyDataProvider
            );

            AiSuggestionService.SuggestionResult result = localService.suggestRcdo(
                    ORG_ID, "Pipeline review", "Prioritise account coverage"
            );

            assertEquals("ok", result.status());
            String assistantContent = capturingLlmClient.lastMessages.stream()
                    .filter(message -> message.role() == LlmClient.Role.ASSISTANT)
                    .findFirst()
                    .map(LlmClient.Message::content)
                    .orElseThrow();
            String zeroCoverageSection = assistantContent.substring(
                    assistantContent.indexOf("Outcomes with 0 commits from your team this quarter")
            );
            assertTrue(zeroCoverageSection.contains("- Uncovered Outcome\n"));
            assertFalse(zeroCoverageSection.contains("- Covered Outcome\n"),
                    "Quarter-covered outcomes should not appear in the zero-coverage prompt section");
        }

        @Test
        void includesCandidateUrgencyAnnotationsAndInvalidatesCacheWhenUrgencyChanges() {
            String outcomeId = UUID.randomUUID().toString();
            RcdoTree tree = new RcdoTree(List.of(
                    new RcdoTree.RallyCry("rc1", "Revenue Growth", List.of(
                            new RcdoTree.Objective("obj1", "Enterprise Sales", "rc1", List.of(
                                    new RcdoTree.Outcome(outcomeId, "Close Q1 deals", "obj1")
                            ))
                    ))
            ));
            rcdoClient.setTree(ORG_ID, tree);

            when(urgencyDataProvider.getOrgUrgencySummary(ORG_ID)).thenReturn(
                    List.of(new UrgencyInfo(
                            UUID.fromString(outcomeId),
                            "Close Q1 deals",
                            LocalDate.of(2026, 3, 20),
                            BigDecimal.valueOf(35),
                            BigDecimal.valueOf(70),
                            "AT_RISK",
                            11
                    )),
                    List.of(new UrgencyInfo(
                            UUID.fromString(outcomeId),
                            "Close Q1 deals",
                            LocalDate.of(2026, 3, 20),
                            BigDecimal.valueOf(35),
                            BigDecimal.valueOf(70),
                            "CRITICAL",
                            11
                    ))
            );

            class CountingCapturingLlmClient implements LlmClient {
                private int callCount;
                private List<Message> lastMessages = List.of();

                @Override
                public String complete(List<Message> messages, String responseSchema) {
                    callCount++;
                    lastMessages = messages;
                    return "{\"suggestions\": []}";
                }

                int callCount() {
                    return callCount;
                }
            }

            CountingCapturingLlmClient capturingLlmClient = new CountingCapturingLlmClient();
            DefaultAiSuggestionService localService = new DefaultAiSuggestionService(
                    capturingLlmClient,
                    rcdoClient,
                    new AiCacheService(Duration.ofHours(1)),
                    commitDataProvider,
                    managerInsightDataProvider,
                    teamRcdoUsageProvider,
                    urgencyDataProvider
            );

            localService.suggestRcdo(ORG_ID, "Close enterprise deals", "Focus on Q1 pipeline");
            String assistantContent = capturingLlmClient.lastMessages.stream()
                    .filter(message -> message.role() == LlmClient.Role.ASSISTANT)
                    .findFirst()
                    .map(LlmClient.Message::content)
                    .orElseThrow();
            assertTrue(assistantContent.contains("urgencyBand: AT_RISK"));
            assertTrue(assistantContent.contains("urgencyPreference: FAVOR_HIGH_URGENCY"));
            assertTrue(assistantContent.contains("Urgency preference guidance:"));

            localService.suggestRcdo(ORG_ID, "Close enterprise deals", "Focus on Q1 pipeline");

            assertEquals(2, capturingLlmClient.callCount(),
                    "Cache key should change when candidate urgency context changes");
        }

        @Test
        void invalidatesCachedSuggestionWhenTeamPromptContextChanges() {
            String outcomeId = UUID.randomUUID().toString();
            RcdoTree tree = new RcdoTree(List.of(
                    new RcdoTree.RallyCry("rc1", "Revenue Growth", List.of(
                            new RcdoTree.Objective("obj1", "Enterprise Sales", "rc1", List.of(
                                    new RcdoTree.Outcome(outcomeId, "Close Q1 deals", "obj1")
                            ))
                    ))
            ));
            rcdoClient.setTree(ORG_ID, tree);

            when(teamRcdoUsageProvider.getTeamRcdoUsage(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(
                            new TeamRcdoUsageProvider.TeamRcdoUsageResult(
                                    List.of(new TeamRcdoUsageProvider.OutcomeUsage(
                                            outcomeId,
                                            "Close Q1 deals",
                                            2
                                    )),
                                    Set.of(outcomeId)
                            ),
                            new TeamRcdoUsageProvider.TeamRcdoUsageResult(
                                    List.of(new TeamRcdoUsageProvider.OutcomeUsage(
                                            outcomeId,
                                            "Close Q1 deals",
                                            7
                                    )),
                                    Set.of()
                            )
                    );

            class TeamCacheBypassingAiCacheService extends AiCacheService {
                TeamCacheBypassingAiCacheService() {
                    super(Duration.ofHours(1));
                }

                @Override
                public <T> java.util.Optional<T> get(UUID orgId, String cacheKey, Class<T> type) {
                    if (cacheKey.startsWith("ai:team-rcdo-usage:")) {
                        return java.util.Optional.empty();
                    }
                    return super.get(orgId, cacheKey, type);
                }

                @Override
                public <T> void put(UUID orgId, String cacheKey, T value) {
                    if (cacheKey.startsWith("ai:team-rcdo-usage:")) {
                        return;
                    }
                    super.put(orgId, cacheKey, value);
                }
            }

            class CountingCapturingLlmClient implements LlmClient {
                private int callCount;
                private List<Message> lastMessages = List.of();

                @Override
                public String complete(List<Message> messages, String responseSchema) {
                    callCount++;
                    lastMessages = messages;
                    return "{\"suggestions\": []}";
                }

                int callCount() {
                    return callCount;
                }
            }

            CountingCapturingLlmClient capturingLlmClient = new CountingCapturingLlmClient();
            DefaultAiSuggestionService localService = new DefaultAiSuggestionService(
                    capturingLlmClient,
                    rcdoClient,
                    new TeamCacheBypassingAiCacheService(),
                    commitDataProvider,
                    managerInsightDataProvider,
                    teamRcdoUsageProvider,
                    urgencyDataProvider
            );

            localService.suggestRcdo(ORG_ID, "Close enterprise deals", "Focus on Q1 pipeline");
            String firstAssistantContent = capturingLlmClient.lastMessages.stream()
                    .filter(message -> message.role() == LlmClient.Role.ASSISTANT)
                    .findFirst()
                    .map(LlmClient.Message::content)
                    .orElseThrow();
            assertTrue(firstAssistantContent.contains("Close Q1 deals (2 commits)"));
            assertFalse(firstAssistantContent.contains("Outcomes with 0 commits from your team this quarter"));

            localService.suggestRcdo(ORG_ID, "Close enterprise deals", "Focus on Q1 pipeline");
            String secondAssistantContent = capturingLlmClient.lastMessages.stream()
                    .filter(message -> message.role() == LlmClient.Role.ASSISTANT)
                    .findFirst()
                    .map(LlmClient.Message::content)
                    .orElseThrow();

            assertEquals(2, capturingLlmClient.callCount(),
                    "Cache key should change when team prompt context changes");
            assertTrue(secondAssistantContent.contains("Close Q1 deals (7 commits)"));
            assertTrue(secondAssistantContent.contains("Outcomes with 0 commits from your team this quarter"));
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
                                    commitId.toString(), "Ship feature", "Feature shipped",
                                    "In progress", List.of(), List.of(), null
                            )
                    ));

            AiSuggestionService.ReconciliationDraftResult result =
                    service.draftReconciliation(ORG_ID, planId);

            assertEquals("ok", result.status());
            assertFalse(result.drafts().isEmpty());
            assertEquals(commitId.toString(), result.drafts().get(0).commitId());
        }

        @Test
        void invalidatesCachedDraftWhenCheckInContentChangesButCountDoesNot() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();

            when(commitDataProvider.planExists(eq(ORG_ID), eq(planId))).thenReturn(true);
            when(commitDataProvider.getCommitSummaries(eq(ORG_ID), eq(planId)))
                    .thenReturn(
                            List.of(new CommitDataProvider.CommitSummary(
                                    commitId.toString(),
                                    "Ship feature",
                                    "Feature shipped",
                                    "In progress",
                                    List.of(new CommitDataProvider.CheckInEntry("AT_RISK", "blocked by API")),
                                    List.of("PARTIALLY"),
                                    "DELIVERY: 50% DONE (team, last 4 wks)"
                            )),
                            List.of(new CommitDataProvider.CommitSummary(
                                    commitId.toString(),
                                    "Ship feature",
                                    "Feature shipped",
                                    "In progress",
                                    List.of(new CommitDataProvider.CheckInEntry("AT_RISK", "unblocked after fix")),
                                    List.of("PARTIALLY"),
                                    "DELIVERY: 50% DONE (team, last 4 wks)"
                            ))
                    );

            class CountingLlmClient implements LlmClient {
                private int callCount;

                @Override
                public String complete(List<Message> messages, String responseSchema) {
                    callCount++;
                    return String.format("""
                            {
                              "drafts": [
                                {
                                  "commitId": "%s",
                                  "suggestedStatus": "PARTIALLY",
                                  "suggestedDeltaReason": "Check-in context changed",
                                  "suggestedActualResult": "Made partial progress"
                                }
                              ]
                            }""", commitId);
                }

                int callCount() {
                    return callCount;
                }
            }

            CountingLlmClient countingLlmClient = new CountingLlmClient();
            DefaultAiSuggestionService localService = new DefaultAiSuggestionService(
                    countingLlmClient,
                    rcdoClient,
                    new AiCacheService(Duration.ofHours(1)),
                    commitDataProvider,
                    managerInsightDataProvider,
                    teamRcdoUsageProvider,
                    urgencyDataProvider
            );

            localService.draftReconciliation(ORG_ID, planId);
            localService.draftReconciliation(ORG_ID, planId);

            assertEquals(2, countingLlmClient.callCount(),
                    "Cache key should change when check-in content changes, even if the entry count is unchanged");
        }

        @Test
        void returnsUnavailableWhenLlmDown() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();

            when(commitDataProvider.planExists(eq(ORG_ID), eq(planId))).thenReturn(true);
            when(commitDataProvider.getCommitSummaries(eq(ORG_ID), eq(planId)))
                    .thenReturn(List.of(
                            new CommitDataProvider.CommitSummary(
                                    commitId.toString(), "Ship feature", "Feature shipped",
                                    "", List.of(), List.of(), null
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
            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(managerId), eq(weekStart),
                    eq(ManagerInsightDataProvider.DEFAULT_WINDOW_WEEKS)))
                    .thenReturn(baseManagerContext(weekStart));

            AiSuggestionService.ManagerInsightsResult result =
                    service.draftManagerInsights(ORG_ID, managerId, weekStart);

            assertEquals("ok", result.status());
            assertFalse(result.insights().isEmpty());
            assertEquals("INFO", result.insights().get(0).severity());
        }

        @Test
        void invalidatesCachedManagerInsightsWhenUrgencyContextChanges() {
            UUID managerId = UUID.randomUUID();
            LocalDate weekStart = LocalDate.of(2026, 3, 9);

            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(managerId), eq(weekStart),
                    eq(ManagerInsightDataProvider.DEFAULT_WINDOW_WEEKS)))
                    .thenReturn(
                            managerContextWithUrgency(weekStart, "AT_RISK", BigDecimal.valueOf(55)),
                            managerContextWithUrgency(weekStart, "CRITICAL", BigDecimal.valueOf(70))
                    );

            class CountingLlmClient implements LlmClient {
                private int callCount;

                @Override
                public String complete(List<Message> messages, String responseSchema) {
                    callCount++;
                    return """
                            {
                              "headline": "Attention needed",
                              "insights": [
                                {
                                  "title": "Urgency changed",
                                  "detail": "Outcome urgency shifted.",
                                  "severity": "WARNING"
                                }
                              ]
                            }""";
                }

                int callCount() {
                    return callCount;
                }
            }

            CountingLlmClient countingLlmClient = new CountingLlmClient();
            DefaultAiSuggestionService localService = new DefaultAiSuggestionService(
                    countingLlmClient,
                    rcdoClient,
                    new AiCacheService(Duration.ofHours(1)),
                    commitDataProvider,
                    managerInsightDataProvider,
                    teamRcdoUsageProvider,
                    urgencyDataProvider
            );

            localService.draftManagerInsights(ORG_ID, managerId, weekStart);
            localService.draftManagerInsights(ORG_ID, managerId, weekStart);

            assertEquals(2, countingLlmClient.callCount(),
                    "Cache key should change when urgency/slack prompt context changes");
        }

        @Test
        void invalidatesCachedManagerInsightsWhenDiagnosticContextChanges() {
            UUID managerId = UUID.randomUUID();
            LocalDate weekStart = LocalDate.of(2026, 3, 9);

            when(managerInsightDataProvider.getManagerWeekContext(
                    eq(ORG_ID), eq(managerId), eq(weekStart),
                    eq(ManagerInsightDataProvider.DEFAULT_WINDOW_WEEKS)))
                    .thenReturn(
                            managerContextWithDiagnosticDeliveryShift(weekStart, 0.6),
                            managerContextWithDiagnosticDeliveryShift(weekStart, 0.8)
                    );

            class CountingLlmClient implements LlmClient {
                private int callCount;

                @Override
                public String complete(List<Message> messages, String responseSchema) {
                    callCount++;
                    return """
                            {
                              "headline": "Diagnostic drift",
                              "insights": [
                                {
                                  "title": "Category mix changed",
                                  "detail": "Delivery focus moved.",
                                  "severity": "INFO"
                                }
                              ]
                            }""";
                }

                int callCount() {
                    return callCount;
                }
            }

            CountingLlmClient countingLlmClient = new CountingLlmClient();
            DefaultAiSuggestionService localService = new DefaultAiSuggestionService(
                    countingLlmClient,
                    rcdoClient,
                    new AiCacheService(Duration.ofHours(1)),
                    commitDataProvider,
                    managerInsightDataProvider,
                    teamRcdoUsageProvider,
                    urgencyDataProvider
            );

            localService.draftManagerInsights(ORG_ID, managerId, weekStart);
            localService.draftManagerInsights(ORG_ID, managerId, weekStart);

            assertEquals(2, countingLlmClient.callCount(),
                    "Cache key should change when diagnostic prompt context changes");
        }

        private ManagerInsightDataProvider.ManagerWeekContext baseManagerContext(LocalDate weekStart) {
            return managerContextWithUrgency(weekStart, "AT_RISK", BigDecimal.valueOf(55));
        }

        private ManagerInsightDataProvider.ManagerWeekContext managerContextWithDiagnosticDeliveryShift(
                LocalDate weekStart,
                double deliveryShare
        ) {
            return new ManagerInsightDataProvider.ManagerWeekContext(
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
                    )),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    new ManagerInsightDataProvider.DiagnosticContext(
                            List.of(new ManagerInsightDataProvider.UserCategoryShiftContext(
                                    "user-1",
                                    java.util.Map.of("DELIVERY", deliveryShare),
                                    java.util.Map.of("DISCOVERY", 1.0 - deliveryShare)
                            )),
                            List.of(new ManagerInsightDataProvider.UserOutcomeCoverageContext(
                                    "user-1",
                                    List.of(new ManagerInsightDataProvider.UserOutcomeWeeklyCountContext(
                                            "outcome-1",
                                            weekStart.toString(),
                                            2
                                    ))
                            )),
                            List.of(new ManagerInsightDataProvider.UserBlockerFrequencyContext(
                                    "user-1", 1, 0, 3
                            ))
                    ),
                    List.of(new ManagerInsightDataProvider.OutcomeUrgencyContext(
                            "outcome-1",
                            "Close Q1 deals",
                            weekStart.plusWeeks(2).toString(),
                            BigDecimal.valueOf(35),
                            BigDecimal.valueOf(50),
                            "AT_RISK",
                            14
                    )),
                    new ManagerInsightDataProvider.StrategicSlackContext(
                            "LOW_SLACK",
                            BigDecimal.valueOf(0.55),
                            2,
                            1
                    )
            );
        }

        private ManagerInsightDataProvider.ManagerWeekContext managerContextWithUrgency(
                LocalDate weekStart,
                String urgencyBand,
                BigDecimal strategicFocusFloor
        ) {
            return new ManagerInsightDataProvider.ManagerWeekContext(
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
                    )),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    new ManagerInsightDataProvider.DiagnosticContext(
                            List.of(new ManagerInsightDataProvider.UserCategoryShiftContext(
                                    "user-1",
                                    java.util.Map.of("DELIVERY", 0.6),
                                    java.util.Map.of("DISCOVERY", 0.4)
                            )),
                            List.of(new ManagerInsightDataProvider.UserOutcomeCoverageContext(
                                    "user-1",
                                    List.of(new ManagerInsightDataProvider.UserOutcomeWeeklyCountContext(
                                            "outcome-1",
                                            weekStart.toString(),
                                            2
                                    ))
                            )),
                            List.of(new ManagerInsightDataProvider.UserBlockerFrequencyContext(
                                    "user-1", 1, 0, 3
                            ))
                    ),
                    List.of(new ManagerInsightDataProvider.OutcomeUrgencyContext(
                            "outcome-1",
                            "Close Q1 deals",
                            weekStart.plusWeeks(2).toString(),
                            BigDecimal.valueOf(35),
                            BigDecimal.valueOf(50),
                            urgencyBand,
                            14
                    )),
                    new ManagerInsightDataProvider.StrategicSlackContext(
                            "LOW_SLACK",
                            strategicFocusFloor,
                            2,
                            1
                    )
            );
        }
    }
}
