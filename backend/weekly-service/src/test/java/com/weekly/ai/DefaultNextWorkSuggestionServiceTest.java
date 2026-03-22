package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.config.OrgPolicyService;
import com.weekly.integration.IntegrationService;
import com.weekly.shared.NextWorkDataProvider;
import com.weekly.shared.NextWorkDataProvider.CarryForwardItem;
import com.weekly.shared.NextWorkDataProvider.RcdoCoverageGap;
import com.weekly.shared.NextWorkDataProvider.RecentCommitContext;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultNextWorkSuggestionService}.
 *
 * <p>The {@code NextWorkDataProvider} interface uses primitive {@code int}
 * parameters; Mockito stubs for those positions must use {@code anyInt()} rather
 * than {@code any()} to avoid null-unboxing NPEs.
 *
 * <p>Phase 2 LLM ranking is tested in {@link LlmRankingTests}.
 * The existing Phase 1 tests keep LLM ranking disabled so they exercise
 * only the data-driven path.
 */
class DefaultNextWorkSuggestionServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START =
            LocalDate.now().with(DayOfWeek.MONDAY);

    private NextWorkDataProvider dataProvider;
    private IntegrationService integrationService;
    private AiSuggestionFeedbackRepository feedbackRepository;
    private LlmClient llmClient;
    private AiCacheService cacheService;
    private AiFeatureFlags featureFlags;
    private OrgPolicyService orgPolicyService;
    private UrgencyDataProvider urgencyDataProvider;
    private DefaultNextWorkSuggestionService service;

    @BeforeEach
    void setUp() {
        dataProvider = mock(NextWorkDataProvider.class);
        integrationService = mock(IntegrationService.class);
        feedbackRepository = mock(AiSuggestionFeedbackRepository.class);
        llmClient = mock(LlmClient.class);
        cacheService = new AiCacheService(Duration.ofHours(1));
        featureFlags = new AiFeatureFlags();
        orgPolicyService = mock(OrgPolicyService.class);
        urgencyDataProvider = mock(UrgencyDataProvider.class);
        when(orgPolicyService.getPolicy(eq(ORG_ID)))
                .thenReturn(OrgPolicyService.defaultPolicy());
        service = new DefaultNextWorkSuggestionService(
                dataProvider, integrationService, feedbackRepository,
                llmClient, cacheService, featureFlags, orgPolicyService,
                urgencyDataProvider);

        // Default stubs: return empty collections for all calls.
        // Use anyInt() for primitive int parameters to avoid null-unboxing NPE.
        when(dataProvider.getRecentCarryForwardItems(
                eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                .thenReturn(List.of());
        when(dataProvider.getTeamCoverageGaps(
                eq(ORG_ID), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(dataProvider.getRecentCommitHistory(
                eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                .thenReturn(List.of());
        when(feedbackRepository.findByOrgIdAndUserIdAndCreatedAtAfter(
                eq(ORG_ID), eq(USER_ID), any()))
                .thenReturn(List.of());
        when(integrationService.getUnresolvedTicketsForUser(
                eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                .thenReturn(List.of());
        when(urgencyDataProvider.getOutcomeUrgency(eq(ORG_ID), any()))
                .thenReturn(null);
        when(dataProvider.getCurrentPlanChessCounts(eq(ORG_ID), eq(USER_ID), any()))
                .thenReturn(Map.of());
    }

    // ── Basic result structure ────────────────────────────────────────────────

    @Test
    void returnsOkStatusWithEmptyListWhenNoData() {
        NextWorkSuggestionService.NextWorkSuggestionsResult result =
                service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

        assertEquals("ok", result.status());
        assertNotNull(result.suggestions());
        assertTrue(result.suggestions().isEmpty());
    }

    // ── Carry-forward suggestions ─────────────────────────────────────────────

    @Nested
    class CarryForwardSuggestions {

        @Test
        void surfacesCarryForwardItem() {
            UUID commitId = UUID.randomUUID();
            UUID outcomeId = UUID.randomUUID();
            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Ship auth module", "Implements JWT auth",
                    "QUEEN", "DELIVERY", outcomeId.toString(), "Auth Outcome",
                    null, "RC1", "OBJ1", null, "Expected: Auth working",
                    1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size());

            NextWorkSuggestionService.NextWorkSuggestion suggestion =
                    result.suggestions().get(0);
            assertEquals("Ship auth module", suggestion.title());
            assertEquals("CARRY_FORWARD", suggestion.source());
            assertEquals(outcomeId.toString(), suggestion.suggestedOutcomeId());
            assertEquals("QUEEN", suggestion.suggestedChessPriority());
            assertTrue(suggestion.confidence()
                    >= DefaultNextWorkSuggestionService.CARRY_FORWARD_BASE_CONFIDENCE);
            assertNotNull(suggestion.suggestionId());
            assertNotNull(suggestion.rationale());
            assertTrue(suggestion.rationale().contains("previous week"));
        }

        @Test
        void olderCarryForwardItemsGetHigherConfidence() {
            UUID commit1 = UUID.randomUUID();
            UUID commit2 = UUID.randomUUID();
            CarryForwardItem oneWeek = new CarryForwardItem(
                    commit1, "Item A", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            CarryForwardItem twoWeeks = new CarryForwardItem(
                    commit2, "Item B", null, null, null, null, null,
                    null, null, null, null, "", 2, WEEK_START.minusWeeks(2)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(oneWeek, twoWeeks));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            // Two-week carry-forward should have higher confidence
            double conf1 = result.suggestions().stream()
                    .filter(s -> "Item A".equals(s.title()))
                    .findFirst().orElseThrow().confidence();
            double conf2 = result.suggestions().stream()
                    .filter(s -> "Item B".equals(s.title()))
                    .findFirst().orElseThrow().confidence();
            assertTrue(conf2 > conf1, "Older carry-forward should have higher confidence");
        }

        @Test
        void rationaleContainsOutcomeNameWhenPresent() {
            UUID commitId = UUID.randomUUID();
            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Ship feature", null, null, null,
                    UUID.randomUUID().toString(), "Important Outcome",
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            String rationale = result.suggestions().get(0).rationale();
            assertTrue(rationale.contains("Important Outcome"),
                    "Rationale should mention the outcome name");
        }

        @Test
        void suggestionIdIsDeterministicForSameInputs() {
            UUID commitId = UUID.randomUUID();
            UUID id1 = DefaultNextWorkSuggestionService.buildCarryForwardSuggestionId(
                    ORG_ID, USER_ID, commitId);
            UUID id2 = DefaultNextWorkSuggestionService.buildCarryForwardSuggestionId(
                    ORG_ID, USER_ID, commitId);
            assertEquals(id1, id2, "Same inputs must produce same suggestion ID");
        }

        @Test
        void differentCommitsProduceDifferentSuggestionIds() {
            UUID commit1 = UUID.randomUUID();
            UUID commit2 = UUID.randomUUID();
            UUID id1 = DefaultNextWorkSuggestionService.buildCarryForwardSuggestionId(
                    ORG_ID, USER_ID, commit1);
            UUID id2 = DefaultNextWorkSuggestionService.buildCarryForwardSuggestionId(
                    ORG_ID, USER_ID, commit2);
            assertTrue(!id1.equals(id2), "Different commits must produce different IDs");
        }
    }

    // ── Coverage-gap suggestions ──────────────────────────────────────────────

    @Nested
    class CoverageGapSuggestions {

        @Test
        void surfacesCoverageGapSuggestion() {
            String outcomeId = UUID.randomUUID().toString();
            RcdoCoverageGap gap = new RcdoCoverageGap(
                    outcomeId, "NPS Outcome", "Improve Customer Satisfaction",
                    "Retain Customers", 3, 12
            );
            when(dataProvider.getTeamCoverageGaps(
                    eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(1, result.suggestions().size());
            NextWorkSuggestionService.NextWorkSuggestion suggestion =
                    result.suggestions().get(0);
            assertEquals("COVERAGE_GAP", suggestion.source());
            assertEquals(outcomeId, suggestion.suggestedOutcomeId());
            assertTrue(suggestion.title().contains("NPS Outcome"));
            assertTrue(suggestion.confidence()
                    >= DefaultNextWorkSuggestionService.COVERAGE_GAP_BASE_CONFIDENCE);
            assertTrue(suggestion.rationale().contains("Retain Customers"));
            assertTrue(suggestion.rationale().contains("3"));  // weeksMissing
            assertTrue(suggestion.rationale().contains("12")); // teamCommitsPrevWindow
        }

        @Test
        void longerGapsGetHigherConfidence() {
            String out1 = UUID.randomUUID().toString();
            String out2 = UUID.randomUUID().toString();
            RcdoCoverageGap gap2Weeks = new RcdoCoverageGap(
                    out1, "Out A", "Obj A", "RC A", 2, 5);
            RcdoCoverageGap gap4Weeks = new RcdoCoverageGap(
                    out2, "Out B", "Obj B", "RC B", 4, 5);
            when(dataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap2Weeks, gap4Weeks));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            double confA = result.suggestions().stream()
                    .filter(s -> s.suggestedOutcomeId() != null
                            && s.suggestedOutcomeId().equals(out1))
                    .findFirst().orElseThrow().confidence();
            double confB = result.suggestions().stream()
                    .filter(s -> s.suggestedOutcomeId() != null
                            && s.suggestedOutcomeId().equals(out2))
                    .findFirst().orElseThrow().confidence();
            assertTrue(confB > confA,
                    "4-week gap should have higher confidence than 2-week gap");
        }

        @Test
        void urgencyMultiplierBoostsTrackedCoverageGapSuggestions() {
            UUID trackedOutcomeId = UUID.randomUUID();
            RcdoCoverageGap gap = new RcdoCoverageGap(
                    trackedOutcomeId.toString(), "Renewals", "Obj", "RC", 2, 5);
            when(dataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, trackedOutcomeId))
                    .thenReturn(new UrgencyInfo(
                            trackedOutcomeId,
                            "Renewals",
                            WEEK_START.plusWeeks(2),
                            BigDecimal.valueOf(25),
                            BigDecimal.valueOf(60),
                            "CRITICAL",
                            14
                    ));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(1, result.suggestions().size());
            assertEquals(
                    DefaultNextWorkSuggestionService.COVERAGE_GAP_BASE_CONFIDENCE
                            * DefaultNextWorkSuggestionService.CRITICAL_URGENCY_MULTIPLIER,
                    result.suggestions().get(0).confidence(),
                    0.001);
        }

        @Test
        void urgencyMultiplierIsNotAppliedForUntrackedCoverageGapSuggestions() {
            String nonTrackedOutcomeId = "legacy-outcome-id";
            RcdoCoverageGap gap = new RcdoCoverageGap(
                    nonTrackedOutcomeId, "Legacy Outcome", "Obj", "RC", 2, 5);
            when(dataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(1, result.suggestions().size());
            assertEquals(
                    DefaultNextWorkSuggestionService.COVERAGE_GAP_BASE_CONFIDENCE,
                    result.suggestions().get(0).confidence(),
                    0.001);
        }

        @Test
        void urgencyProviderFailureDoesNotMakeWholeResultUnavailable() {
            UUID trackedOutcomeId = UUID.randomUUID();
            RcdoCoverageGap gap = new RcdoCoverageGap(
                    trackedOutcomeId.toString(), "Renewals", "Obj", "RC", 2, 5);
            when(dataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));
            when(urgencyDataProvider.getOutcomeUrgency(ORG_ID, trackedOutcomeId))
                    .thenThrow(new RuntimeException("urgency unavailable"));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size());
            assertEquals(
                    DefaultNextWorkSuggestionService.COVERAGE_GAP_BASE_CONFIDENCE,
                    result.suggestions().get(0).confidence(),
                    0.001);
        }
    }

    // ── Decline suppression ───────────────────────────────────────────────────

    @Nested
    class DeclineSuppression {

        @Test
        void declinedCarryForwardItemIsFiltered() {
            UUID commitId = UUID.randomUUID();
            UUID suggestionId = DefaultNextWorkSuggestionService.buildCarryForwardSuggestionId(
                    ORG_ID, USER_ID, commitId);

            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Ship auth", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            AiSuggestionFeedbackEntity feedback = new AiSuggestionFeedbackEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, suggestionId,
                    "DECLINE", null, "CARRY_FORWARD", null
            );
            when(feedbackRepository.findByOrgIdAndUserIdAndCreatedAtAfter(
                    eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(List.of(feedback));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertTrue(result.suggestions().isEmpty(),
                    "DECLINED item should not be surfaced");
        }

        @Test
        void declinedCoverageGapIsFiltered() {
            String outcomeId = UUID.randomUUID().toString();
            UUID suggestionId = DefaultNextWorkSuggestionService.buildCoverageGapSuggestionId(
                    ORG_ID, USER_ID, outcomeId);

            RcdoCoverageGap gap = new RcdoCoverageGap(
                    outcomeId, "NPS Outcome", "Obj", "RC", 2, 8
            );
            when(dataProvider.getTeamCoverageGaps(eq(ORG_ID), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(gap));

            AiSuggestionFeedbackEntity feedback = new AiSuggestionFeedbackEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, suggestionId,
                    "DECLINE", null, "COVERAGE_GAP", null
            );
            when(feedbackRepository.findByOrgIdAndUserIdAndCreatedAtAfter(
                    eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(List.of(feedback));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertTrue(result.suggestions().isEmpty(),
                    "DECLINED coverage gap should not be surfaced");
        }

        @Test
        void acceptedItemIsStillSurfaced() {
            UUID commitId = UUID.randomUUID();
            UUID suggestionId = DefaultNextWorkSuggestionService.buildCarryForwardSuggestionId(
                    ORG_ID, USER_ID, commitId);

            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Ship auth", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            // ACCEPT feedback — should not suppress
            AiSuggestionFeedbackEntity feedback = new AiSuggestionFeedbackEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, suggestionId,
                    "ACCEPT", null, null, null
            );
            when(feedbackRepository.findByOrgIdAndUserIdAndCreatedAtAfter(
                    eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(List.of(feedback));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(1, result.suggestions().size(),
                    "ACCEPT feedback should not suppress the suggestion");
        }
    }

    // ── Ranking and capping ───────────────────────────────────────────────────

    @Nested
    class RankingAndCapping {

        @Test
        void suggestionsAreSortedByConfidenceDescending() {
            UUID commit1 = UUID.randomUUID();
            UUID commit2 = UUID.randomUUID();
            // 2-week carry has higher base confidence than 1-week
            CarryForwardItem oneWeek = new CarryForwardItem(
                    commit1, "Item 1 week", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            CarryForwardItem twoWeeks = new CarryForwardItem(
                    commit2, "Item 2 weeks", null, null, null, null, null,
                    null, null, null, null, "", 2, WEEK_START.minusWeeks(2)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(oneWeek, twoWeeks));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(2, result.suggestions().size());
            assertTrue(result.suggestions().get(0).confidence()
                    >= result.suggestions().get(1).confidence(),
                    "Suggestions should be sorted highest confidence first");
        }

        @Test
        void resultCappedAtMaxSuggestions() {
            // Generate more carry-forward items than MAX_SUGGESTIONS
            List<CarryForwardItem> items = new ArrayList<>();
            for (int i = 0; i < DefaultNextWorkSuggestionService.MAX_SUGGESTIONS + 5; i++) {
                items.add(new CarryForwardItem(
                        UUID.randomUUID(), "Item " + i, null, null, null, null, null,
                        null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
                ));
            }
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(items);

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(DefaultNextWorkSuggestionService.MAX_SUGGESTIONS,
                    result.suggestions().size(),
                    "Result should be capped at MAX_SUGGESTIONS");
        }
    }

    // ── Graceful degradation ──────────────────────────────────────────────────

    @Test
    void returnsUnavailableOnUnexpectedException() {
        when(dataProvider.getRecentCarryForwardItems(
                eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                .thenThrow(new RuntimeException("DB error"));

        NextWorkSuggestionService.NextWorkSuggestionsResult result =
                service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

        assertEquals("unavailable", result.status());
        assertTrue(result.suggestions().isEmpty());
    }

    // ── LLM ranking is not called when disabled ───────────────────────────────

    @Test
    void llmIsNotCalledWhenRankingDisabled() {
        UUID commitId = UUID.randomUUID();
        CarryForwardItem item = new CarryForwardItem(
                commitId, "Ship auth", null, null, null, null, null,
                null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
        );
        when(dataProvider.getRecentCarryForwardItems(
                eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                .thenReturn(List.of(item));

        // Construct a service with LLM ranking explicitly disabled.
        // The service reads the flag once at construction time, so we must set it
        // before creating the instance rather than after.
        AiFeatureFlags rankingDisabledFlags = new AiFeatureFlags();
        rankingDisabledFlags.setLlmNextWorkRankingEnabled(false);
        DefaultNextWorkSuggestionService serviceWithRankingDisabled = new DefaultNextWorkSuggestionService(
                dataProvider, integrationService, feedbackRepository,
                llmClient, cacheService, rankingDisabledFlags, orgPolicyService,
                urgencyDataProvider);

        serviceWithRankingDisabled.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

        verify(llmClient, never()).complete(any(), anyString());
    }

    // ── Phase 3: External ticket suggestions ─────────────────────────────────

    @Nested
    class ExternalTicketSuggestions {

        private IntegrationService.UserTicketContext makeTicket(
                String ticketId, String provider, String status, String outcomeId, String outcomeName) {
            return new IntegrationService.UserTicketContext(
                    ticketId, provider, status,
                    "https://tracker.example.com/" + ticketId,
                    Instant.parse("2026-03-10T10:00:00Z"),
                    UUID.randomUUID(),
                    outcomeId, outcomeName, "Obj A", "RC A"
            );
        }

        @Test
        void surfacesExternalTicketSuggestion() {
            String outcomeId = UUID.randomUUID().toString();
            IntegrationService.UserTicketContext ticket =
                    makeTicket("PROJ-42", "JIRA", "In Progress", outcomeId, "Auth Outcome");
            when(integrationService.getUnresolvedTicketsForUser(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(ticket));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size());

            NextWorkSuggestionService.NextWorkSuggestion s = result.suggestions().get(0);
            assertEquals("EXTERNAL_TICKET", s.source());
            assertEquals(outcomeId, s.suggestedOutcomeId());
            assertTrue(s.title().contains("PROJ-42"));
            assertEquals("https://tracker.example.com/PROJ-42", s.externalTicketUrl());
            assertEquals("In Progress", s.externalTicketStatus());
            assertTrue(s.sourceDetail().contains("updated 2026-03-10"));
            assertTrue(s.confidence() >= DefaultNextWorkSuggestionService.EXTERNAL_TICKET_BASE_CONFIDENCE - 0.1);
            assertNotNull(s.rationale());
            assertTrue(s.rationale().contains("PROJ-42"));
            assertTrue(s.rationale().contains("Last updated: 2026-03-10"));
        }

        @Test
        void externalTicketIdIsDeterministic() {
            UUID id1 = DefaultNextWorkSuggestionService.buildExternalTicketSuggestionId(
                    ORG_ID, USER_ID, "PROJ-42", "JIRA");
            UUID id2 = DefaultNextWorkSuggestionService.buildExternalTicketSuggestionId(
                    ORG_ID, USER_ID, "PROJ-42", "JIRA");
            assertEquals(id1, id2, "Same inputs must produce same suggestion ID");
        }

        @Test
        void differentTicketsProduceDifferentIds() {
            UUID id1 = DefaultNextWorkSuggestionService.buildExternalTicketSuggestionId(
                    ORG_ID, USER_ID, "PROJ-42", "JIRA");
            UUID id2 = DefaultNextWorkSuggestionService.buildExternalTicketSuggestionId(
                    ORG_ID, USER_ID, "PROJ-43", "JIRA");
            assertTrue(!id1.equals(id2), "Different ticket IDs must produce different suggestion IDs");
        }

        @Test
        void declinedExternalTicketIsSuppressed() {
            String ticketId = "PROJ-99";
            UUID suggestionId = DefaultNextWorkSuggestionService.buildExternalTicketSuggestionId(
                    ORG_ID, USER_ID, ticketId, "JIRA");

            IntegrationService.UserTicketContext ticket =
                    makeTicket(ticketId, "JIRA", "In Progress", UUID.randomUUID().toString(), "Outcome");
            when(integrationService.getUnresolvedTicketsForUser(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(ticket));

            AiSuggestionFeedbackEntity feedback = new AiSuggestionFeedbackEntity(
                    UUID.randomUUID(), ORG_ID, USER_ID, suggestionId,
                    "DECLINE", null, "EXTERNAL_TICKET", null
            );
            when(feedbackRepository.findByOrgIdAndUserIdAndCreatedAtAfter(
                    eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(List.of(feedback));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertTrue(result.suggestions().isEmpty(),
                    "DECLINED external ticket should not be surfaced");
        }

        @Test
        void ticketWithNullStatusGetsSlighltyLowerConfidence() {
            String outcomeId = UUID.randomUUID().toString();
            IntegrationService.UserTicketContext noStatusTicket =
                    makeTicket("PROJ-10", "LINEAR", null, outcomeId, "Some Outcome");
            IntegrationService.UserTicketContext withStatusTicket =
                    makeTicket("PROJ-11", "LINEAR", "In Review", outcomeId, "Some Outcome");
            when(integrationService.getUnresolvedTicketsForUser(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(noStatusTicket, withStatusTicket));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            double confNoStatus = result.suggestions().stream()
                    .filter(s -> s.externalTicketStatus() == null)
                    .findFirst().orElseThrow().confidence();
            double confWithStatus = result.suggestions().stream()
                    .filter(s -> "In Review".equals(s.externalTicketStatus()))
                    .findFirst().orElseThrow().confidence();
            assertTrue(confWithStatus > confNoStatus,
                    "Ticket with known status should have higher confidence");
        }

        @Test
        void duplicateTicketContextsCollapseToSingleSuggestionUsingNewestContext() {
            String outcomeA = UUID.randomUUID().toString();
            String outcomeB = UUID.randomUUID().toString();
            IntegrationService.UserTicketContext olderTicket = new IntegrationService.UserTicketContext(
                    "PROJ-77", "JIRA", "Open",
                    "https://tracker.example.com/PROJ-77",
                    Instant.parse("2026-03-09T10:00:00Z"),
                    UUID.randomUUID(),
                    outcomeA, "Old Outcome", "Obj A", "RC A"
            );
            IntegrationService.UserTicketContext newerTicket = new IntegrationService.UserTicketContext(
                    "PROJ-77", "JIRA", "In Progress",
                    "https://tracker.example.com/PROJ-77",
                    Instant.parse("2026-03-12T10:00:00Z"),
                    UUID.randomUUID(),
                    outcomeB, "New Outcome", "Obj B", "RC B"
            );
            when(integrationService.getUnresolvedTicketsForUser(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(olderTicket, newerTicket));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(1, result.suggestions().size(),
                    "A ticket linked multiple times should only surface once");
            NextWorkSuggestionService.NextWorkSuggestion suggestion = result.suggestions().get(0);
            assertEquals("In Progress", suggestion.externalTicketStatus());
            assertEquals(outcomeB, suggestion.suggestedOutcomeId());
            assertTrue(suggestion.sourceDetail().contains("updated 2026-03-12"));
            assertTrue(suggestion.rationale().contains("New Outcome"));
        }

        @Test
        void externalTicketSuggestionsCoexistWithCarryForward() {
            UUID commitId = UUID.randomUUID();
            CarryForwardItem carry = new CarryForwardItem(
                    commitId, "Carry item", null, "QUEEN", null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(carry));

            IntegrationService.UserTicketContext ticket =
                    makeTicket("PROJ-55", "JIRA", "In Progress",
                            UUID.randomUUID().toString(), "Some Outcome");
            when(integrationService.getUnresolvedTicketsForUser(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(ticket));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(2, result.suggestions().size());
            assertTrue(result.suggestions().stream()
                    .anyMatch(s -> "CARRY_FORWARD".equals(s.source())));
            assertTrue(result.suggestions().stream()
                    .anyMatch(s -> "EXTERNAL_TICKET".equals(s.source())));
        }

        @Test
        void externalTicketFetchErrorDegracefullyWithEmptyList() {
            when(integrationService.getUnresolvedTicketsForUser(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenThrow(new RuntimeException("Integration service unavailable"));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            // Should still return ok with no ticket suggestions
            assertEquals("ok", result.status());
            assertTrue(result.suggestions().isEmpty());
        }
    }

    // ── Phase 2 LLM-enhanced pipeline ─────────────────────────────────────────

    @Nested
    class LlmRankingTests {

        private DefaultNextWorkSuggestionService llmEnabledService;
        private LlmClient mockLlm;
        private AiCacheService testCache;

        @BeforeEach
        void setUpLlmService() {
            mockLlm = mock(LlmClient.class);
            testCache = new AiCacheService(Duration.ofHours(1));

            AiFeatureFlags flags = new AiFeatureFlags();
            flags.setLlmNextWorkRankingEnabled(true);

            llmEnabledService = new DefaultNextWorkSuggestionService(
                    dataProvider, integrationService, feedbackRepository,
                    mockLlm, testCache, flags, orgPolicyService,
                    urgencyDataProvider);
        }

        @Test
        void llmReranksCarryForwardCandidates() {
            UUID commitId = UUID.randomUUID();
            UUID suggestionId = DefaultNextWorkSuggestionService
                    .buildCarryForwardSuggestionId(ORG_ID, USER_ID, commitId);

            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Refactor auth", null, "ROOK", null,
                    null, null, null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            String llmResponse = String.format("""
                    {
                      "rankedSuggestions": [
                        {
                          "suggestionId": "%s",
                          "confidence": 0.95,
                          "suggestedChessPriority": "QUEEN",
                          "rationale": "LLM-generated: critical auth work must ship this week"
                        }
                      ]
                    }""", suggestionId);
            when(mockLlm.complete(any(), anyString())).thenReturn(llmResponse);

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size());
            NextWorkSuggestionService.NextWorkSuggestion s = result.suggestions().get(0);
            assertEquals(0.95, s.confidence(), 0.001);
            assertEquals("QUEEN", s.suggestedChessPriority());
            assertTrue(s.rationale().contains("LLM-generated"));
            // Original data preserved
            assertEquals("Refactor auth", s.title());
            assertEquals("CARRY_FORWARD", s.source());
        }

        @Test
        void llmResultIsCached() {
            UUID commitId = UUID.randomUUID();
            UUID suggestionId = DefaultNextWorkSuggestionService
                    .buildCarryForwardSuggestionId(ORG_ID, USER_ID, commitId);

            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Task X", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            String llmResponse = String.format("""
                    {"rankedSuggestions": [{"suggestionId": "%s", "confidence": 0.80, "rationale": "r"}]}
                    """, suggestionId);
            when(mockLlm.complete(any(), anyString())).thenReturn(llmResponse);

            // First call: LLM invoked
            llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);
            // Second call: should use cache
            llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            // LLM should have been called only once
            verify(mockLlm, org.mockito.Mockito.times(1)).complete(any(), anyString());
        }

        @Test
        void cacheInvalidatesWhenPromptContextChanges() {
            UUID commitId = UUID.randomUUID();
            UUID suggestionId = DefaultNextWorkSuggestionService
                    .buildCarryForwardSuggestionId(ORG_ID, USER_ID, commitId);

            CarryForwardItem firstItem = new CarryForwardItem(
                    commitId, "Task X", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            CarryForwardItem updatedItem = new CarryForwardItem(
                    commitId, "Task X renamed", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(firstItem), List.of(updatedItem));

            RecentCommitContext historyA = new RecentCommitContext(
                    UUID.randomUUID(), WEEK_START.minusWeeks(1), "History A",
                    null, null, null, null, "PARTIALLY");
            RecentCommitContext historyB = new RecentCommitContext(
                    UUID.randomUUID(), WEEK_START.minusWeeks(1), "History B",
                    null, null, null, null, "PARTIALLY");
            when(dataProvider.getRecentCommitHistory(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(historyA), List.of(historyB));

            String llmResponse = String.format(
                    "{\"rankedSuggestions\": [{\"suggestionId\": \"%s\", \"confidence\": 0.80, \"rationale\": \"r\"}]}",
                    suggestionId);
            when(mockLlm.complete(any(), anyString())).thenReturn(llmResponse);

            llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);
            llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            verify(mockLlm, org.mockito.Mockito.times(2)).complete(any(), anyString());
        }

        @Test
        void fallsBackToPhase1OnLlmUnavailable() {
            UUID commitId = UUID.randomUUID();
            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Ship feature", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            when(mockLlm.complete(any(), anyString()))
                    .thenThrow(new LlmClient.LlmUnavailableException("timeout"));

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            // Should gracefully fall back to Phase 1 result
            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size());
            assertEquals("Ship feature", result.suggestions().get(0).title());
        }

        @Test
        void fallsBackToPhase1WhenLlmReturnsNoValidSuggestions() {
            UUID commitId = UUID.randomUUID();
            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Ship feature", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            // LLM returns empty valid suggestions
            when(mockLlm.complete(any(), anyString()))
                    .thenReturn("{\"rankedSuggestions\": []}");

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size(),
                    "Should fall back to Phase-1 data when LLM returns no valid suggestions");
        }

        @Test
        void llmHallucinatedSuggestionIdsAreRejected() {
            UUID commitId = UUID.randomUUID();
            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Ship feature", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            // LLM returns a hallucinated ID not in the candidate set
            when(mockLlm.complete(any(), anyString())).thenReturn("""
                    {"rankedSuggestions": [
                      {"suggestionId": "00000000-0000-0000-0000-000000000000",
                       "confidence": 0.99,
                       "rationale": "hallucinated"}
                    ]}
                    """);

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            // Hallucinated suggestion rejected → falls back to Phase-1
            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size());
            assertEquals("Ship feature", result.suggestions().get(0).title());
        }

        @Test
        void llmChessPriorityOverridesPhase1WhenProvided() {
            UUID commitId = UUID.randomUUID();
            UUID suggestionId = DefaultNextWorkSuggestionService
                    .buildCarryForwardSuggestionId(ORG_ID, USER_ID, commitId);

            // Phase 1 assigns ROOK priority
            CarryForwardItem item = new CarryForwardItem(
                    commitId, "Refactor DB", null, "ROOK", null,
                    null, null, null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item));

            // LLM upgrades to KING
            String llmResponse = String.format("""
                    {"rankedSuggestions": [{
                      "suggestionId": "%s",
                      "confidence": 0.90,
                      "suggestedChessPriority": "KING",
                      "rationale": "Critical blocker"
                    }]}""", suggestionId);
            when(mockLlm.complete(any(), anyString())).thenReturn(llmResponse);

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("KING", result.suggestions().get(0).suggestedChessPriority(),
                    "LLM chess priority should override Phase-1 value");
        }

        @Test
        void candidatesNotReturnedByLlmAreAppendedAtEnd() {
            UUID commit1 = UUID.randomUUID();
            UUID commit2 = UUID.randomUUID();
            UUID suggestionId1 = DefaultNextWorkSuggestionService
                    .buildCarryForwardSuggestionId(ORG_ID, USER_ID, commit1);

            CarryForwardItem item1 = new CarryForwardItem(
                    commit1, "Task A", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            CarryForwardItem item2 = new CarryForwardItem(
                    commit2, "Task B", null, null, null, null, null,
                    null, null, null, null, "", 1, WEEK_START.minusWeeks(1)
            );
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(item1, item2));

            // LLM only ranks the first suggestion
            String llmResponse = String.format("""
                    {"rankedSuggestions": [{
                      "suggestionId": "%s",
                      "confidence": 0.88,
                      "rationale": "Top priority"
                    }]}""", suggestionId1);
            when(mockLlm.complete(any(), anyString())).thenReturn(llmResponse);

            NextWorkSuggestionService.NextWorkSuggestionsResult result =
                    llmEnabledService.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals(2, result.suggestions().size(),
                    "Candidates not ranked by LLM should be appended");
            assertEquals("Task A", result.suggestions().get(0).title(),
                    "LLM-ranked item should be first");
            assertEquals("Task B", result.suggestions().get(1).title(),
                    "Unranked item appended at end");
        }
    }

    // ── Chess-aware priority downgrade ───────────────────────────────────────

    @Nested
    class ChessDowngrade {

        @Test
        void downgradesKingSuggestionWhenPlanAlreadyHasMaxKings() {
            // Plan already has 1 KING (the max)
            when(dataProvider.getCurrentPlanChessCounts(eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(Map.of("KING", 1, "ROOK", 2));

            // Carry-forward item with KING priority
            CarryForwardItem kingItem = new CarryForwardItem(
                    UUID.randomUUID(), "Carried KING task", null,
                    "KING", "DELIVERY",
                    UUID.randomUUID().toString(), "Outcome A",
                    null, "RC1", "OBJ1", null, "Expected result",
                    1, WEEK_START.minusWeeks(1));
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(kingItem));

            var result = service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ok", result.status());
            assertEquals(1, result.suggestions().size());
            assertEquals("ROOK", result.suggestions().get(0).suggestedChessPriority(),
                    "KING should be downgraded to ROOK when plan is at max kings");
            assertTrue(result.suggestions().get(0).rationale().contains("Priority adjusted"),
                    "Rationale should mention the downgrade");
        }

        @Test
        void doesNotDowngradeWhenPlanHasRoomForKing() {
            // Plan has 0 KINGs — room for one more
            when(dataProvider.getCurrentPlanChessCounts(eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(Map.of("ROOK", 3));

            CarryForwardItem kingItem = new CarryForwardItem(
                    UUID.randomUUID(), "KING task", null,
                    "KING", "DELIVERY",
                    UUID.randomUUID().toString(), "Outcome A",
                    null, "RC1", "OBJ1", null, "Expected result",
                    1, WEEK_START.minusWeeks(1));
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(kingItem));

            var result = service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("KING", result.suggestions().get(0).suggestedChessPriority(),
                    "KING should remain when plan has room");
        }

        @Test
        void downgradesQueenWhenPlanAtMaxQueens() {
            when(dataProvider.getCurrentPlanChessCounts(eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(Map.of("QUEEN", 2));

            CarryForwardItem queenItem = new CarryForwardItem(
                    UUID.randomUUID(), "Carried QUEEN task", null,
                    "QUEEN", "DELIVERY",
                    UUID.randomUUID().toString(), "Outcome B",
                    null, "RC1", "OBJ1", null, "Expected result",
                    1, WEEK_START.minusWeeks(1));
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(queenItem));

            var result = service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("ROOK", result.suggestions().get(0).suggestedChessPriority(),
                    "QUEEN should be downgraded to ROOK when plan is at max queens");
        }

        @Test
        void noDowngradeWhenNoDraftPlanExists() {
            // Empty map = no DRAFT plan
            when(dataProvider.getCurrentPlanChessCounts(eq(ORG_ID), eq(USER_ID), any()))
                    .thenReturn(Map.of());

            CarryForwardItem kingItem = new CarryForwardItem(
                    UUID.randomUUID(), "KING task", null,
                    "KING", "DELIVERY",
                    UUID.randomUUID().toString(), "Outcome A",
                    null, "RC1", "OBJ1", null, "Expected result",
                    1, WEEK_START.minusWeeks(1));
            when(dataProvider.getRecentCarryForwardItems(
                    eq(ORG_ID), eq(USER_ID), any(), anyInt()))
                    .thenReturn(List.of(kingItem));

            var result = service.suggestNextWork(ORG_ID, USER_ID, WEEK_START);

            assertEquals("KING", result.suggestions().get(0).suggestedChessPriority(),
                    "KING should remain when there's no DRAFT plan to check against");
        }
    }
}
