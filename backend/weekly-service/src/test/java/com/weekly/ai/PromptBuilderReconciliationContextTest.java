package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weekly.shared.CommitDataProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying that {@link PromptBuilder#buildReconciliationDraftMessages}
 * correctly incorporates the enriched reconciliation context (step-15):
 * structured check-in history, carry-forward statuses, and team category rates.
 */
class PromptBuilderReconciliationContextTest {

    // ── System prompt rules ───────────────────────────────────────────────────

    @Test
    void systemPromptMentionsCheckInSignals() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        String system = messages.get(0).content();
        assertTrue(system.contains("check-in history"),
                "System prompt should mention check-in history");
        assertTrue(system.contains("AT_RISK") || system.contains("BLOCKED"),
                "System prompt should mention AT_RISK/BLOCKED signals");
    }

    @Test
    void systemPromptMentionsCarryForwardContext() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        String system = messages.get(0).content();
        assertTrue(system.contains("carry-forward"),
                "System prompt should mention carry-forward context");
    }

    @Test
    void systemPromptMentionsCategoryCompletionRates() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        String system = messages.get(0).content();
        assertTrue(system.contains("team category completion rates")
                        || system.contains("category completion rates"),
                "System prompt should mention category completion rates");
    }

    @Test
    void systemPromptRetainsConservativenessRule() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        String system = messages.get(0).content();
        assertTrue(system.contains("conservative") || system.contains("PARTIALLY"),
                "System prompt should still warn to be conservative");
    }

    // ── Basic structure ────────────────────────────────────────────────────────

    @Test
    void producesTwoMessages() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        assertEquals(2, messages.size(), "Should produce SYSTEM and USER messages");
    }

    @Test
    void firstMessageIsSystemPrompt() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        assertEquals(LlmClient.Role.SYSTEM, messages.get(0).role());
    }

    @Test
    void secondMessageIsUserContext() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        assertEquals(LlmClient.Role.USER, messages.get(1).role());
    }

    @Test
    void userContextContainsBasicCommitFields() {
        List<LlmClient.Message> messages = buildMessages(
                "Build auth module", "Auth deployed", "Half done", List.of(), List.of(), null);

        String context = messages.get(1).content();
        assertTrue(context.contains("Build auth module"), "Should include commit title");
        assertTrue(context.contains("Auth deployed"), "Should include expected result");
        assertTrue(context.contains("Half done"), "Should include progress notes");
    }

    // ── Check-in history inclusion ────────────────────────────────────────────

    @Test
    void omitsCheckInSectionWhenEmpty() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        assertFalse(messages.get(1).content().contains("check-ins"),
                "Check-in section should be absent when no check-in entries exist");
    }

    @Test
    void includesCheckInSectionWithStatusAndNote() {
        List<CommitDataProvider.CheckInEntry> checkIns = List.of(
                new CommitDataProvider.CheckInEntry("AT_RISK", "blocked by API issues"),
                new CommitDataProvider.CheckInEntry("ON_TRACK", "unblocked after fix")
        );

        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", checkIns, List.of(), null);

        String context = messages.get(1).content();
        assertTrue(context.contains("check-ins"), "Should include check-ins section");
        assertTrue(context.contains("AT_RISK"), "Should include AT_RISK status");
        assertTrue(context.contains("blocked by API issues"), "Should include check-in note");
        assertTrue(context.contains("ON_TRACK"), "Should include ON_TRACK status");
        assertTrue(context.contains("unblocked after fix"), "Should include second note");
    }

    @Test
    void includesCheckInWithEmptyNoteOmitsQuotedText() {
        List<CommitDataProvider.CheckInEntry> checkIns = List.of(
                new CommitDataProvider.CheckInEntry("BLOCKED", "")
        );

        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", checkIns, List.of(), null);

        String context = messages.get(1).content();
        assertTrue(context.contains("BLOCKED"), "Should include BLOCKED status");
        assertFalse(context.contains("\"\""),
                "Empty note should not produce quoted empty string");
    }

    @Test
    void includesDoneEarlySignalInCheckIns() {
        List<CommitDataProvider.CheckInEntry> checkIns = List.of(
                new CommitDataProvider.CheckInEntry("DONE_EARLY", "finished ahead of schedule")
        );

        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", checkIns, List.of(), null);

        String context = messages.get(1).content();
        assertTrue(context.contains("DONE_EARLY"), "Should include DONE_EARLY check-in signal");
        assertTrue(context.contains("finished ahead of schedule"), "Should include note");
    }

    // ── Carry-forward context inclusion ──────────────────────────────────────

    @Test
    void omitsCarryForwardSectionWhenEmpty() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        assertFalse(messages.get(1).content().contains("carry-forward"),
                "Carry-forward section should be absent when no prior statuses exist");
    }

    @Test
    void includesCarryForwardWithDepthAndStatuses() {
        List<String> priorStatuses = List.of("PARTIALLY", "NOT_DONE");

        List<LlmClient.Message> messages = buildMessages(
                "Slow task", "Expected", "Notes", List.of(), priorStatuses, null);

        String context = messages.get(1).content();
        assertTrue(context.contains("carry-forward"), "Should include carry-forward section");
        assertTrue(context.contains("2 time(s)"), "Should show carry-forward depth");
        assertTrue(context.contains("PARTIALLY"), "Should include prior PARTIALLY status");
        assertTrue(context.contains("NOT_DONE"), "Should include prior NOT_DONE status");
    }

    @Test
    void singleCarryForwardShowsOneTime() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of("NOT_DONE"), null);

        String context = messages.get(1).content();
        assertTrue(context.contains("1 time(s)") || context.contains("1 time"),
                "Single carry-forward should show depth of 1");
        assertTrue(context.contains("NOT_DONE"), "Should include the prior status");
    }

    // ── Category completion rate inclusion ────────────────────────────────────

    @Test
    void omitsCategoryRateSectionWhenNull() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), null);

        assertFalse(messages.get(1).content().contains("team rate"),
                "Team rate section should be absent when categoryCompletionRateContext is null");
    }

    @Test
    void omitsCategoryRateSectionWhenBlank() {
        List<LlmClient.Message> messages = buildMessages(
                "Task", "Expected", "Notes", List.of(), List.of(), "   ");

        assertFalse(messages.get(1).content().contains("team rate"),
                "Team rate section should be absent when categoryCompletionRateContext is blank");
    }

    @Test
    void includesCategoryRateWhenProvided() {
        String rateContext = "OPERATIONS: 85% DONE (team, last 4 wks)";
        List<LlmClient.Message> messages = buildMessages(
                "Ops task", "Expected", "Notes", List.of(), List.of(), rateContext);

        String context = messages.get(1).content();
        assertTrue(context.contains("team rate"), "Should include team rate label");
        assertTrue(context.contains("OPERATIONS"), "Should include category name");
        assertTrue(context.contains("85%"), "Should include completion percentage");
    }

    // ── Multiple commits ───────────────────────────────────────────────────────

    @Test
    void handlesMultipleCommitsWithMixedEnrichment() {
        String commitId1 = UUID.randomUUID().toString();
        String commitId2 = UUID.randomUUID().toString();

        List<PromptBuilder.CommitContext> commits = List.of(
                new PromptBuilder.CommitContext(
                        commitId1, "Rich commit", "Expected A", "In progress",
                        List.of(new CommitDataProvider.CheckInEntry("AT_RISK", "blocked")),
                        List.of("PARTIALLY"),
                        "DELIVERY: 75% DONE (team, last 4 wks)"
                ),
                new PromptBuilder.CommitContext(
                        commitId2, "Plain commit", "Expected B", "Not started",
                        List.of(), List.of(), null
                )
        );

        List<LlmClient.Message> messages =
                PromptBuilder.buildReconciliationDraftMessages(commits);

        String context = messages.get(1).content();
        // Rich commit should have all sections
        assertTrue(context.contains("Rich commit"));
        assertTrue(context.contains("AT_RISK"));
        assertTrue(context.contains("carry-forward"));
        assertTrue(context.contains("DELIVERY"));

        // Plain commit should have just basic fields
        assertTrue(context.contains("Plain commit"));
        assertTrue(context.contains("Not started"));
    }

    @Test
    void allCommitIdsAppearInContext() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        String id3 = UUID.randomUUID().toString();

        List<PromptBuilder.CommitContext> commits = List.of(
                new PromptBuilder.CommitContext(id1, "T1", "E1", "N1",
                        List.of(), List.of(), null),
                new PromptBuilder.CommitContext(id2, "T2", "E2", "N2",
                        List.of(), List.of(), null),
                new PromptBuilder.CommitContext(id3, "T3", "E3", "N3",
                        List.of(), List.of(), null)
        );

        String context = PromptBuilder.buildReconciliationDraftMessages(commits)
                .get(1).content();

        assertTrue(context.contains(id1), "Should include commitId1");
        assertTrue(context.contains(id2), "Should include commitId2");
        assertTrue(context.contains(id3), "Should include commitId3");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static List<LlmClient.Message> buildMessages(
            String title,
            String expectedResult,
            String progressNotes,
            List<CommitDataProvider.CheckInEntry> checkIns,
            List<String> priorStatuses,
            String categoryRateContext
    ) {
        String commitId = UUID.randomUUID().toString();
        return PromptBuilder.buildReconciliationDraftMessages(List.of(
                new PromptBuilder.CommitContext(
                        commitId, title, expectedResult, progressNotes,
                        checkIns, priorStatuses, categoryRateContext
                )
        ));
    }
}
