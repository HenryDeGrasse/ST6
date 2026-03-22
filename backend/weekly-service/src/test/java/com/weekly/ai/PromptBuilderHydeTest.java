package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weekly.ai.rag.OutcomeRiskContext;
import com.weekly.ai.rag.UserWorkContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PromptBuilder#buildHydeRecommendationPrompt}.
 */
class PromptBuilderHydeTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 23);

    // ── Happy-path: full context ──────────────────────────────────────────────

    @Test
    void returnsThreeMessages() {
        UserWorkContext userCtx = buildFullUserContext();
        OutcomeRiskContext riskCtx = buildFullRiskContext();

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(userCtx, riskCtx);

        assertEquals(3, messages.size());
        assertEquals(LlmClient.Role.SYSTEM, messages.get(0).role());
        assertEquals(LlmClient.Role.ASSISTANT, messages.get(1).role());
        assertEquals(LlmClient.Role.USER, messages.get(2).role());
    }

    @Test
    void systemPromptContainsHydeInstructions() {
        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(buildFullUserContext(), buildFullRiskContext());

        String systemContent = messages.get(0).content();
        assertTrue(systemContent.contains("hypothetical"), "Should mention hypothetical document");
        assertTrue(systemContent.contains("200 words"), "Should mention output size limit");
    }

    @Test
    void assistantContextContainsCapacityInfo() {
        UserWorkContext userCtx = new UserWorkContext(
                USER_ID, ORG_ID, WEEK_START,
                40.0, 16.0, List.of(), List.of(), List.of()
        );

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(userCtx, null);

        String assistantContent = messages.get(1).content();
        assertTrue(assistantContent.contains("24.0"), "Should include remaining 24.0 hours");
        assertTrue(assistantContent.contains("40.0"), "Should include cap 40.0 hours");
        assertTrue(assistantContent.contains("16.0"), "Should include committed 16.0 hours");
    }

    @Test
    void assistantContextContainsAtRiskOutcomes() {
        OutcomeRiskContext riskCtx = new OutcomeRiskContext(
                List.of(new OutcomeRiskContext.AtRiskOutcome(
                        "oc-1", "Improve Retention", "AT_RISK", 14L)),
                List.of()
        );

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(buildMinimalUserContext(), riskCtx);

        String assistantContent = messages.get(1).content();
        assertTrue(assistantContent.contains("Improve Retention"),
                "Should include at-risk outcome name");
        assertTrue(assistantContent.contains("AT_RISK"),
                "Should include urgency band");
        assertTrue(assistantContent.contains("14 days"),
                "Should include days remaining");
    }

    @Test
    void assistantContextContainsCoverageGaps() {
        OutcomeRiskContext riskCtx = new OutcomeRiskContext(
                List.of(),
                List.of(new OutcomeRiskContext.CoverageGap("oc-2", "Reduce Churn", 3))
        );

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(buildMinimalUserContext(), riskCtx);

        String assistantContent = messages.get(1).content();
        assertTrue(assistantContent.contains("Reduce Churn"),
                "Should include coverage gap outcome name");
        assertTrue(assistantContent.contains("3 week"),
                "Should include weeks missing");
    }

    @Test
    void userMessageContainsCurrentPlanItems() {
        UserWorkContext userCtx = new UserWorkContext(
                USER_ID, ORG_ID, WEEK_START,
                40.0, 0.0,
                List.of("Fix auth service bug", "Write Q2 planning doc"),
                List.of(), List.of()
        );

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(userCtx, null);

        String userContent = messages.get(2).content();
        assertTrue(userContent.contains("Fix auth service bug"),
                "Should include current plan items");
        assertTrue(userContent.contains("Write Q2 planning doc"),
                "Should include current plan items");
    }

    @Test
    void userMessageContainsCarriedForwardItems() {
        UserWorkContext userCtx = new UserWorkContext(
                USER_ID, ORG_ID, WEEK_START,
                40.0, 0.0, List.of(),
                List.of(),
                List.of("Refactor payment module")
        );

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(userCtx, null);

        String userContent = messages.get(2).content();
        assertTrue(userContent.contains("Refactor payment module"),
                "Should include carried forward items");
        assertTrue(userContent.contains("carrying forward"),
                "Should include carrying forward label");
    }

    @Test
    void userMessageEndsWithGenerationInstruction() {
        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(buildMinimalUserContext(), null);

        String userContent = messages.get(2).content();
        assertTrue(userContent.contains("hypothetical ideal issue"),
                "Should instruct LLM to generate hypothetical issue");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void emptyPlanListShowsNothingYet() {
        UserWorkContext userCtx = new UserWorkContext(
                USER_ID, ORG_ID, WEEK_START,
                40.0, 0.0, List.of(), List.of(), List.of()
        );

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(userCtx, null);

        String userContent = messages.get(2).content();
        assertTrue(userContent.contains("nothing yet"),
                "Should show 'nothing yet' when plan is empty");
    }

    @Test
    void nullRiskContextProducesValidMessages() {
        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(buildMinimalUserContext(), null);

        assertEquals(3, messages.size());
        // Assistant context should not contain at-risk / coverage sections
        String assistantContent = messages.get(1).content();
        assertFalse(assistantContent.contains("At-risk"),
                "Should not include at-risk section when riskContext is null");
        assertFalse(assistantContent.contains("coverage"),
                "Should not include coverage gap section when riskContext is null");
    }

    @Test
    void emptyRiskContextProducesValidMessages() {
        OutcomeRiskContext riskCtx = new OutcomeRiskContext(List.of(), List.of());
        assertTrue(riskCtx.isEmpty());

        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(buildMinimalUserContext(), riskCtx);

        assertNotNull(messages);
        assertEquals(3, messages.size());
    }

    @Test
    void weekStartAppearsInAssistantContext() {
        List<LlmClient.Message> messages =
                PromptBuilder.buildHydeRecommendationPrompt(buildMinimalUserContext(), null);

        String assistantContent = messages.get(1).content();
        assertTrue(assistantContent.contains("2026-03-23"),
                "Should include week start date");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserWorkContext buildMinimalUserContext() {
        return new UserWorkContext(
                USER_ID, ORG_ID, WEEK_START,
                40.0, 0.0, List.of(), List.of(), List.of()
        );
    }

    private UserWorkContext buildFullUserContext() {
        return new UserWorkContext(
                USER_ID, ORG_ID, WEEK_START,
                40.0, 24.0,
                List.of("Implement login page", "Fix CI pipeline"),
                List.of(UUID.randomUUID()),
                List.of("Migrate legacy auth")
        );
    }

    private OutcomeRiskContext buildFullRiskContext() {
        return new OutcomeRiskContext(
                List.of(new OutcomeRiskContext.AtRiskOutcome(
                        "oc-1", "Improve Retention", "AT_RISK", 14L)),
                List.of(new OutcomeRiskContext.CoverageGap(
                        "oc-2", "Reduce Churn", 3))
        );
    }
}
