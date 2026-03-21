package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weekly.integration.IntegrationService.UserTicketContext;
import com.weekly.shared.NextWorkDataProvider.CarryForwardItem;
import com.weekly.shared.NextWorkDataProvider.RcdoCoverageGap;
import com.weekly.shared.NextWorkDataProvider.RecentCommitContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PromptBuilder#buildNextWorkSuggestMessages} and
 * {@link PromptBuilder#nextWorkSuggestResponseSchema()}.
 */
class PromptBuilderNextWorkTest {

    private static final LocalDate WEEK_START = LocalDate.of(2025, 3, 17);

    @Test
    void producesThreeMessages() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertEquals(3, messages.size());
    }

    @Test
    void firstMessageIsSystemPrompt() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertEquals(LlmClient.Role.SYSTEM, messages.get(0).role());
        assertTrue(messages.get(0).content().contains("strategic work advisor"));
    }

    @Test
    void secondMessageIsAssistantContext() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertEquals(LlmClient.Role.ASSISTANT, messages.get(1).role());
    }

    @Test
    void thirdMessageIsUserRequest() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertEquals(LlmClient.Role.USER, messages.get(2).role());
        assertTrue(messages.get(2).content().contains("highest-impact"));
        assertTrue(messages.get(2).content().contains(WEEK_START.toString()));
    }

    @Test
    void systemPromptEnforcesOnlyCandidateIds() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertTrue(messages.get(0).content().contains("suggestionId appears in the candidate list"));
    }

    @Test
    void systemPromptMentionsDeclinedHistoryAndImpactRanking() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        String system = messages.get(0).content();
        assertTrue(system.contains("Respect declined-history suppression"));
        assertTrue(system.contains("strategic impact"));
    }

    @Test
    void systemPromptSpecifiesConfidenceAndChessPriorityConstraints() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        String system = messages.get(0).content();
        assertTrue(system.contains("[0.0, 1.0]"));
        assertTrue(system.contains("KING") && system.contains("QUEEN")
                && system.contains("ROOK") && system.contains("BISHOP")
                && system.contains("KNIGHT") && system.contains("PAWN"));
    }

    @Test
    void contextIncludesCandidateSuggestionMetadata() {
        UUID suggestionId = UUID.randomUUID();
        NextWorkSuggestionService.NextWorkSuggestion candidate = new NextWorkSuggestionService.NextWorkSuggestion(
                suggestionId,
                "Ship auth module",
                UUID.randomUUID().toString(),
                "QUEEN",
                0.85,
                "CARRY_FORWARD",
                "Carried from week of 2025-03-10",
                "Not completed last week",
                null,
                null
        );

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(candidate), List.of(), List.of(), List.of(), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains(suggestionId.toString()));
        assertTrue(context.contains("Ship auth module"));
        assertTrue(context.contains("CARRY_FORWARD"));
        assertTrue(context.contains("currentPriority: QUEEN"));
        assertTrue(context.contains("dataConfidence: 0.85"));
    }

    @Test
    void contextIncludesCandidateCountAndWeek() {
        List<NextWorkSuggestionService.NextWorkSuggestion> candidates = List.of(
                buildSuggestion("Task A"),
                buildSuggestion("Task B")
        );

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                candidates, List.of(), List.of(), List.of(), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("2 items"));
        assertTrue(context.contains(WEEK_START.toString()));
    }

    @Test
    void contextIncludesRecentCommitHistoryWhenPresent() {
        RecentCommitContext historyItem = new RecentCommitContext(
                UUID.randomUUID(),
                LocalDate.of(2025, 3, 10),
                "Improve auth UX",
                UUID.randomUUID().toString(),
                "Auth Outcome",
                "Secure Login",
                "Trust",
                "PARTIALLY"
        );

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(historyItem), List.of(), List.of(), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("User's last 4 weeks of commits"));
        assertTrue(context.contains("Improve auth UX"));
        assertTrue(context.contains("status: PARTIALLY"));
        assertTrue(context.contains("outcome: Auth Outcome"));
        assertTrue(context.contains("objective: Secure Login"));
        assertTrue(context.contains("rallyCry: Trust"));
    }

    @Test
    void contextOmitsRecentCommitHistorySectionWhenEmpty() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertFalse(contextContent(messages).contains("User's last 4 weeks of commits"));
    }

    @Test
    void contextCapsRecentCommitHistoryAtTwelveItems() {
        List<RecentCommitContext> history = new java.util.ArrayList<>();
        for (int i = 0; i < 14; i++) {
            history.add(new RecentCommitContext(
                    UUID.randomUUID(),
                    LocalDate.of(2025, 3, 10),
                    "History task " + i,
                    null,
                    null,
                    null,
                    null,
                    "DONE"
            ));
        }

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), history, List.of(), List.of(), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("History task 11"));
        assertFalse(context.contains("History task 12"));
        assertFalse(context.contains("History task 13"));
    }

    @Test
    void contextIncludesCarryForwardSectionWhenPresent() {
        CarryForwardItem carry = new CarryForwardItem(
                UUID.randomUUID(),
                "Finish migration",
                null,
                null,
                null,
                null,
                "Platform Stability",
                null,
                null,
                null,
                null,
                "Complete rollout",
                2,
                LocalDate.of(2025, 3, 3)
        );

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(carry), List.of(), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("Carried-forward items needing attention"));
        assertTrue(context.contains("Finish migration"));
        assertTrue(context.contains("carryForwardWeeks: 2"));
        assertTrue(context.contains("Platform Stability"));
    }

    @Test
    void contextOmitsCarryForwardSectionWhenEmpty() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertFalse(contextContent(messages).contains("Carried-forward items needing attention"));
    }

    @Test
    void contextCapsCarryForwardSectionAtTenItems() {
        List<CarryForwardItem> carries = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            carries.add(new CarryForwardItem(
                    UUID.randomUUID(),
                    "Carry task " + i,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    1,
                    LocalDate.of(2025, 3, 10)
            ));
        }

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), carries, List.of(), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("Carry task 9"));
        assertFalse(context.contains("Carry task 10"));
        assertFalse(context.contains("Carry task 11"));
    }

    @Test
    void contextIncludesTeamCoverageGapsWhenPresent() {
        RcdoCoverageGap gap = new RcdoCoverageGap(
                UUID.randomUUID().toString(),
                "Customer NPS",
                "Improve CX",
                "Customer First",
                3,
                8
        );

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(gap), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("Team outcome coverage gaps"));
        assertTrue(context.contains("Customer NPS"));
        assertTrue(context.contains("Customer First"));
        assertTrue(context.contains("weeksMissing: 3"));
    }

    @Test
    void contextOmitsCoverageGapsSectionWhenEmpty() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertFalse(contextContent(messages).contains("Team outcome coverage gaps"));
    }

    @Test
    void contextCapsCoverageGapsAtEightItems() {
        List<RcdoCoverageGap> gaps = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            gaps.add(new RcdoCoverageGap(
                    UUID.randomUUID().toString(),
                    "Outcome " + i,
                    "Obj " + i,
                    "RC " + i,
                    2,
                    5
            ));
        }

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), gaps, WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("Outcome 7"));
        assertFalse(context.contains("Outcome 8"));
        assertFalse(context.contains("Outcome 9"));
    }

    @Test
    void contextIncludesLinkedTicketsSectionWhenPresent() {
        UserTicketContext ticket = new UserTicketContext(
                "PROJ-42", "JIRA", "In Progress",
                "https://jira.example.com/PROJ-42",
                Instant.parse("2026-03-10T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "Auth Outcome", "Secure Login", "Trust"
        );

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), List.of(ticket), WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("Linked external tickets"));
        assertTrue(context.contains("PROJ-42"));
        assertTrue(context.contains("JIRA"));
        assertTrue(context.contains("In Progress"));
        assertTrue(context.contains("Auth Outcome"));
        assertTrue(context.contains("Trust"));
    }

    @Test
    void contextOmitsLinkedTicketsSectionWhenEmpty() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertFalse(contextContent(messages).contains("Linked external tickets"));
    }

    @Test
    void contextCapsLinkedTicketsAtTenItems() {
        List<UserTicketContext> tickets = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tickets.add(new UserTicketContext(
                    "TICKET-" + i, "JIRA", "Open",
                    null, null, UUID.randomUUID(),
                    UUID.randomUUID().toString(), "Outcome " + i, null, null
            ));
        }

        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), tickets, WEEK_START);

        String context = contextContent(messages);
        assertTrue(context.contains("TICKET-9"));
        assertFalse(context.contains("TICKET-10"));
        assertFalse(context.contains("TICKET-11"));
    }

    @Test
    void sixArgOverloadProducesThreeMessages() {
        List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), List.of(), WEEK_START);
        assertEquals(3, messages.size());
    }

    @Test
    void fiveArgOverloadDelegatesToSixArgWithEmptyTickets() {
        // Both overloads should produce the same result when no tickets
        List<LlmClient.Message> fiveArg = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), WEEK_START);
        List<LlmClient.Message> sixArg = PromptBuilder.buildNextWorkSuggestMessages(
                List.of(), List.of(), List.of(), List.of(), List.of(), WEEK_START);

        assertEquals(fiveArg.size(), sixArg.size());
        for (int i = 0; i < fiveArg.size(); i++) {
            assertEquals(fiveArg.get(i).role(), sixArg.get(i).role());
            assertEquals(fiveArg.get(i).content(), sixArg.get(i).content());
        }
    }

    @Test
    void responseSchemaContainsRankedSuggestionsField() {
        String schema = PromptBuilder.nextWorkSuggestResponseSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("rankedSuggestions"));
    }

    @Test
    void responseSchemaRequiresSuggestionIdConfidenceAndRationale() {
        String schema = PromptBuilder.nextWorkSuggestResponseSchema();
        assertTrue(schema.contains("\"suggestionId\""));
        assertTrue(schema.contains("\"confidence\""));
        assertTrue(schema.contains("\"rationale\""));
    }

    @Test
    void responseSchemaIncludesChessPriorityEnum() {
        String schema = PromptBuilder.nextWorkSuggestResponseSchema();
        assertTrue(schema.contains("suggestedChessPriority"));
        assertTrue(schema.contains("KING") && schema.contains("QUEEN"));
    }

    private static String contextContent(List<LlmClient.Message> messages) {
        return messages.stream()
                .filter(m -> m.role() == LlmClient.Role.ASSISTANT)
                .findFirst()
                .map(LlmClient.Message::content)
                .orElse("");
    }

    private static NextWorkSuggestionService.NextWorkSuggestion buildSuggestion(String title) {
        return new NextWorkSuggestionService.NextWorkSuggestion(
                UUID.randomUUID(),
                title,
                null,
                null,
                0.85,
                "CARRY_FORWARD",
                "Carried from previous week",
                "Not completed",
                null,
                null
        );
    }
}
