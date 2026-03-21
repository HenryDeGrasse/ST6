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
                "Improve activation"
        );

        assertEquals(2, messages.size());
        assertEquals(LlmClient.Role.SYSTEM, messages.get(0).role());
        assertEquals(LlmClient.Role.USER, messages.get(1).role());
        assertEquals(
                "You are a check-in assistant for weekly planning. "
                        + "Generate concise progress update options for a work commitment. "
                        + "Return valid JSON matching the response schema. "
                        + "Each progress option should be a short phrase (under 50 chars). "
                        + "Include 4-5 options mixing status continuations and common transitions.",
                messages.get(0).content()
        );
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
                "Improve activation"
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
        assertTrue(userMessage.endsWith("Generate contextual progress update options."));
    }

    @Test
    void userMessageOmitsPatternsWhenListIsEmpty() {
        List<LlmClient.Message> messages = CheckInOptionPromptBuilder.buildCheckInOptionMessages(
                "Close out migration",
                "OPERATIONS",
                "ROOK",
                "ON_TRACK",
                null,
                0,
                List.of(),
                null
        );

        String userMessage = messages.get(1).content();
        assertTrue(userMessage.contains("outcomeName: (none)"));
        assertTrue(userMessage.contains("lastNote: (none)"));
        assertFalse(userMessage.contains("User's common phrases:"));
    }

    @Test
    void responseSchemaRequiresTopLevelFields() {
        String schema = CheckInOptionPromptBuilder.RESPONSE_SCHEMA;

        assertTrue(schema.contains("\"required\": [\"statusOptions\", \"progressOptions\"]"));
        assertTrue(schema.contains("\"statusOptions\""));
        assertTrue(schema.contains("\"progressOptions\""));
        assertTrue(schema.contains("\"text\""));
        assertTrue(schema.contains("\"source\""));
    }
}
