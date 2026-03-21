package com.weekly.quickupdate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.weekly.ai.LlmClient;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CheckInOptionPromptBuilder}.
 */
class CheckInOptionPromptBuilderTest {

    @Test
    void buildsTwoMessagesWithSystemAndUserRoles() {
        List<LlmClient.Message> messages = CheckInOptionPromptBuilder.buildCheckInOptionMessages(
                "Ship onboarding flow",
                "DELIVERY",
                "QUEEN",
                "ON_TRACK",
                "API is ready for UI hookup",
                2,
                List.of("Wrapped API integration", "Waiting on review"),
                List.of("Daily rollout status"),
                "Improve activation",
                "completion reliability 82%; current category DELIVERY done rate 90%; top categories DELIVERY, OPERATIONS; avg check-ins/week 2.3"
        );

        assertEquals(2, messages.size());
        assertEquals(LlmClient.Role.SYSTEM, messages.get(0).role());
        assertEquals(LlmClient.Role.USER, messages.get(1).role());
        assertTrue(messages.get(0).content().contains("Derived user model summary:"));
        assertTrue(messages.get(0).content().contains("completion reliability 82%"));
        assertTrue(messages.get(0).content().contains("current category DELIVERY done rate 90%"));
        assertTrue(messages.get(0).content().contains("top categories DELIVERY, OPERATIONS"));
        assertTrue(messages.get(0).content().contains("avg check-ins/week 2.3"));
    }

    @Test
    void userMessageContainsStructuredContextBlock() {
        List<LlmClient.Message> messages = CheckInOptionPromptBuilder.buildCheckInOptionMessages(
                "Ship onboarding flow",
                "DELIVERY",
                "QUEEN",
                "AT_RISK",
                "Blocked on final copy",
                3,
                List.of("Wrapped API integration", "Waiting on review"),
                List.of("Daily rollout status"),
                "Improve activation",
                "completion reliability 82%; current category DELIVERY done rate 90%"
        );

        String userMessage = messages.get(1).content();
        assertTrue(userMessage.contains("commitTitle: Ship onboarding flow"));
        assertTrue(userMessage.contains("category: DELIVERY"));
        assertTrue(userMessage.contains("chessPriority: QUEEN"));
        assertTrue(userMessage.contains("outcomeName: Improve activation"));
        assertTrue(userMessage.contains("currentStatus: AT_RISK"));
        assertTrue(userMessage.contains("lastNote: Blocked on final copy"));
        assertTrue(userMessage.contains("daysSinceLastCheckIn: 3"));
        assertTrue(userMessage.contains(
                "User's common phrases: [Wrapped API integration, Waiting on review]"));
        assertTrue(userMessage.contains("Team's common phrases: [Daily rollout status]"));
        assertTrue(userMessage.endsWith("Generate contextual progress update options."));
    }

    @Test
    void userMessageOmitsPatternsWhenListsAreEmpty() {
        List<LlmClient.Message> messages = CheckInOptionPromptBuilder.buildCheckInOptionMessages(
                "Close out migration",
                "OPERATIONS",
                "ROOK",
                "ON_TRACK",
                null,
                0,
                List.of(),
                List.of(),
                null,
                null
        );

        String userMessage = messages.get(1).content();
        assertTrue(userMessage.contains("outcomeName: (none)"));
        assertTrue(userMessage.contains("lastNote: (none)"));
        assertFalse(userMessage.contains("User's common phrases:"));
        assertFalse(userMessage.contains("Team's common phrases:"));
        assertTrue(messages.get(0).content().contains("Derived user model summary: (none)"));
    }

    @Test
    void responseSchemaRequiresTopLevelFields() {
        String schema = CheckInOptionPromptBuilder.RESPONSE_SCHEMA;

        assertTrue(schema.contains("\"required\": [\"statusOptions\", \"progressOptions\"]"));
        assertTrue(schema.contains("\"statusOptions\""));
        assertTrue(schema.contains("\"progressOptions\""));
        assertTrue(schema.contains("\"text\""));
        assertFalse(schema.contains("\"source\""));
    }
}
