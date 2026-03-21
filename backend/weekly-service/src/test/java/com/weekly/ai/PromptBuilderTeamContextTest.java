package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying that {@link PromptBuilder#buildRcdoSuggestMessages}
 * correctly incorporates team-context signals into the ASSISTANT context message.
 */
class PromptBuilderTeamContextTest {

    private static final List<PromptBuilder.CandidateOutcome> ONE_CANDIDATE = List.of(
            new PromptBuilder.CandidateOutcome("outcome-1", "Close Q1 Deals", "Sales", "Revenue")
    );

    // ── Team context absent ──────────────────────────────────────────────────

    @Test
    void noTeamContextSectionWhenTopOutcomesEmpty() {
        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Fix login bug", "OAuth flow is broken",
                ONE_CANDIDATE,
                List.of(),
                List.of()
        );

        String assistantContent = assistantContent(messages);
        assertFalse(assistantContent.contains("Team usage context"),
                "Team usage section must be absent when topTeamOutcomes is empty");
    }

    @Test
    void noZeroCoverageLineWhenListEmpty() {
        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Fix login bug", "OAuth flow is broken",
                ONE_CANDIDATE,
                List.of(),
                List.of()
        );

        String assistantContent = assistantContent(messages);
        assertFalse(assistantContent.contains("0 commits"),
                "Zero-coverage line must be absent when list is empty");
    }

    // ── Team context present ─────────────────────────────────────────────────

    @Test
    void includesTopTeamOutcomesSection() {
        List<PromptBuilder.TeamOutcomeUsage> topOutcomes = List.of(
                new PromptBuilder.TeamOutcomeUsage("Close Q1 Deals", 5),
                new PromptBuilder.TeamOutcomeUsage("Improve NPS", 3)
        );

        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Sales pipeline", null,
                ONE_CANDIDATE,
                topOutcomes,
                List.of()
        );

        String assistantContent = assistantContent(messages);
        assertTrue(assistantContent.contains("Top 5 outcomes your team linked to this week"),
                "Should include top team outcomes header");
        assertTrue(assistantContent.contains("Close Q1 Deals (5 commits)"),
                "Should list first top outcome with commit count");
        assertTrue(assistantContent.contains("Improve NPS (3 commits)"),
                "Should list second top outcome with commit count");
    }

    @Test
    void includesZeroCoverageSection() {
        List<String> zeroCoverage = List.of("Reduce Churn", "Expand APAC");

        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Sales pipeline", null,
                ONE_CANDIDATE,
                List.of(),
                zeroCoverage
        );

        String assistantContent = assistantContent(messages);
        assertTrue(assistantContent.contains("Outcomes with 0 commits from your team this quarter"),
                "Should include zero-coverage header");
        assertTrue(assistantContent.contains("Reduce Churn"),
                "Should list first zero-coverage outcome");
        assertTrue(assistantContent.contains("Expand APAC"),
                "Should list second zero-coverage outcome");
    }

    @Test
    void includesBothSectionsWhenBothProvided() {
        List<PromptBuilder.TeamOutcomeUsage> topOutcomes = List.of(
                new PromptBuilder.TeamOutcomeUsage("Close Q1 Deals", 4)
        );
        List<String> zeroCoverage = List.of("Reduce Churn");

        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Pipeline review", "Quarterly review",
                ONE_CANDIDATE,
                topOutcomes,
                zeroCoverage
        );

        String assistantContent = assistantContent(messages);
        assertTrue(assistantContent.contains("Top 5 outcomes your team linked to this week"));
        assertTrue(assistantContent.contains("Outcomes with 0 commits from your team this quarter"));
    }

    @Test
    void capsTopOutcomesAtFive() {
        List<PromptBuilder.TeamOutcomeUsage> topOutcomes = List.of(
                new PromptBuilder.TeamOutcomeUsage("Outcome A", 10),
                new PromptBuilder.TeamOutcomeUsage("Outcome B", 9),
                new PromptBuilder.TeamOutcomeUsage("Outcome C", 8),
                new PromptBuilder.TeamOutcomeUsage("Outcome D", 7),
                new PromptBuilder.TeamOutcomeUsage("Outcome E", 6),
                new PromptBuilder.TeamOutcomeUsage("Outcome F", 5)  // 6th — should be omitted
        );

        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Pipeline", null,
                ONE_CANDIDATE,
                topOutcomes,
                List.of()
        );

        String assistantContent = assistantContent(messages);
        assertTrue(assistantContent.contains("Outcome A (10 commits)"), "First 5 should be present");
        assertTrue(assistantContent.contains("Outcome E (6 commits)"), "Fifth should be present");
        assertFalse(assistantContent.contains("Outcome F"),
                "Sixth outcome must be omitted (cap at 5)");
    }

    @Test
    void capsZeroCoverageAtTen() {
        List<String> zeroCoverage = List.of(
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"  // 11 items
        );

        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Pipeline", null,
                ONE_CANDIDATE,
                List.of(),
                zeroCoverage
        );

        String assistantContent = assistantContent(messages);
        assertTrue(assistantContent.contains("- J\n"), "10th item should be included");
        assertFalse(assistantContent.contains("- K\n"),
                "11th item must be omitted (cap at 10)");
    }

    @Test
    void candidatesStillPresentWithTeamContext() {
        List<PromptBuilder.TeamOutcomeUsage> topOutcomes = List.of(
                new PromptBuilder.TeamOutcomeUsage("Close Q1 Deals", 5)
        );

        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Sales pipeline", null,
                ONE_CANDIDATE,
                topOutcomes,
                List.of()
        );

        String assistantContent = assistantContent(messages);
        assertTrue(assistantContent.contains("Available RCDO outcomes:"),
                "Candidate outcomes section must still be present with team context");
        assertTrue(assistantContent.contains("outcomeId: outcome-1"),
                "Individual candidate entries must still be present");
    }

    @Test
    void threeArgOverloadProducesNoTeamContext() {
        // Verify the backward-compatible 3-arg overload produces no team context
        List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                "Pipeline", "Description", ONE_CANDIDATE
        );

        String assistantContent = assistantContent(messages);
        assertTrue(assistantContent.contains("Available RCDO outcomes:"),
                "Candidate section should be present");
        assertFalse(assistantContent.contains("Team usage context"),
                "3-arg overload must not produce team context section");
    }

    // ── CandidateSelector boost ──────────────────────────────────────────────

    @Test
    void candidateSelectorBoostsHighUsageOutcomes() {
        String highUsageId = "outcome-high-usage";
        String lowUsageId = "outcome-low-usage";

        // Both outcomes have zero keyword overlap with the search query "zzz yyy xxx"
        // so without a boost the tie is broken arbitrarily; with a boost highUsageId wins.
        com.weekly.rcdo.RcdoTree tree = new com.weekly.rcdo.RcdoTree(List.of(
                new com.weekly.rcdo.RcdoTree.RallyCry("rc1", "Revenue", List.of(
                        new com.weekly.rcdo.RcdoTree.Objective("obj1", "Sales", "rc1", List.of(
                                new com.weekly.rcdo.RcdoTree.Outcome(highUsageId, "Alpha Strategic Goal", "obj1"),
                                new com.weekly.rcdo.RcdoTree.Outcome(lowUsageId, "Beta Operational Work", "obj1")
                        ))
                ))
        ));

        java.util.Set<String> highUsageIds = java.util.Set.of(highUsageId);

        // With boost: highUsageId should rank first (it gets HIGH_USAGE_SCORE_BOOST, other gets 0)
        List<PromptBuilder.CandidateOutcome> withBoost = CandidateSelector.select(
                tree, "zzz yyy xxx", null, 1, highUsageIds
        );
        assertFalse(withBoost.isEmpty());
        assertEquals(highUsageId, withBoost.get(0).outcomeId(),
                "High-usage outcome should rank first when boost dominates (no keyword overlap for either)");

        // Without boost and maxCandidates=2: both should be returned (tree fits)
        List<PromptBuilder.CandidateOutcome> allCandidates = CandidateSelector.select(
                tree, "zzz yyy xxx", null, 50, java.util.Set.of()
        );
        assertEquals(2, allCandidates.size(),
                "All outcomes should be returned when tree fits in maxCandidates");
    }

    @Test
    void candidateSelectorBoostReordersResultsWhenAllFit() {
        // When the tree has fewer outcomes than maxCandidates, all outcomes should still
        // be returned, but boosted outcomes should rank first in the prompt candidate list.
        com.weekly.rcdo.RcdoTree tree = new com.weekly.rcdo.RcdoTree(List.of(
                new com.weekly.rcdo.RcdoTree.RallyCry("rc1", "Revenue", List.of(
                        new com.weekly.rcdo.RcdoTree.Objective("obj1", "Sales", "rc1", List.of(
                                new com.weekly.rcdo.RcdoTree.Outcome("o1", "Outcome One", "obj1"),
                                new com.weekly.rcdo.RcdoTree.Outcome("o2", "Outcome Two", "obj1")
                        ))
                ))
        ));

        List<PromptBuilder.CandidateOutcome> candidates = CandidateSelector.select(
                tree, "zzz yyy xxx", null, 50, java.util.Set.of("o2")
        );

        assertEquals(2, candidates.size(), "All outcomes should be returned when tree fits in maxCandidates");
        assertEquals("o2", candidates.get(0).outcomeId(),
                "Boosted outcomes should still rank first when all candidates fit");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String assistantContent(List<LlmClient.Message> messages) {
        return messages.stream()
                .filter(m -> m.role() == LlmClient.Role.ASSISTANT)
                .findFirst()
                .map(LlmClient.Message::content)
                .orElse("");
    }
}
