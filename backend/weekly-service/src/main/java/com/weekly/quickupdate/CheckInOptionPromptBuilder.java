package com.weekly.quickupdate;

import com.weekly.ai.LlmClient;
import java.util.ArrayList;
import java.util.List;

/**
 * Constructs structured prompts for AI-generated check-in option suggestions
 * on the Quick Update card.
 *
 * <p>Security: user-authored text (commit titles, notes, personal phrase
 * patterns) is placed in USER role messages, never concatenated with system
 * prompts. This mitigates prompt injection per PRD §4.
 *
 * <p>This is a utility class — instantiation is prohibited.
 */
public final class CheckInOptionPromptBuilder {

    /** Placeholder used when optional context fields are missing. */
    private static final String NONE = "(none)";

    private CheckInOptionPromptBuilder() {}

    /**
     * JSON schema that check-in option LLM responses must conform to.
     *
     * <p>Responses must contain:
     * <ul>
     *   <li>{@code statusOptions} — an array of status label strings</li>
     *   <li>{@code progressOptions} — an array of progress option objects, each with
     *       a {@code text} (the short phrase, under 50 chars)</li>
     * </ul>
     */
    public static final String RESPONSE_SCHEMA = """
            {
              "type": "object",
              "required": ["statusOptions", "progressOptions"],
              "properties": {
                "statusOptions": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "progressOptions": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "required": ["text"],
                    "properties": {
                      "text": { "type": "string" }
                    }
                  }
                }
              }
            }
            """;

    /**
     * Builds the ordered LLM messages for generating contextual check-in options.
     *
     * <p>Produces two messages:
     * <ol>
     *   <li>A SYSTEM message containing the assistant persona, generation rules,
     *       and derived user-model summary.</li>
     *   <li>A USER message containing the commitment context block ({@code commitTitle},
     *       {@code category}, {@code chessPriority}, {@code outcomeName},
     *       {@code currentStatus}, {@code lastNote}, {@code daysSinceLastCheckIn},
     *       personal phrase history, and team-common fallbacks). Placing
     *       user-authored text in the USER role mitigates prompt injection per PRD §4.</li>
     * </ol>
     */
    public static List<LlmClient.Message> buildCheckInOptionMessages(
            String commitTitle,
            String category,
            String chessPriority,
            String currentStatus,
            String lastNote,
            int daysSinceLastCheckIn,
            List<String> userPatterns,
            List<String> teamPatterns,
            String outcomeName,
            String userModelSummary
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                "You are a check-in assistant for weekly planning. "
                        + "Generate concise progress update options for a work commitment. "
                        + "Return valid JSON matching the response schema. "
                        + "Each progress option should be a short phrase (under 50 chars). "
                        + "Do not include source labels in the text. "
                        + "Use the derived user-model summary to tailor the tone and relevance. "
                        + "Derived user model summary: " + contextValue(userModelSummary)
        ));

        StringBuilder context = new StringBuilder();
        context.append("commitTitle: ").append(contextValue(commitTitle)).append('\n');
        context.append("category: ").append(contextValue(category)).append('\n');
        context.append("chessPriority: ").append(contextValue(chessPriority)).append('\n');
        context.append("outcomeName: ").append(contextValue(outcomeName)).append('\n');
        context.append("currentStatus: ").append(contextValue(currentStatus)).append('\n');
        context.append("lastNote: ").append(contextValue(lastNote)).append('\n');
        context.append("daysSinceLastCheckIn: ").append(daysSinceLastCheckIn).append('\n');
        if (userPatterns != null && !userPatterns.isEmpty()) {
            context.append("User's common phrases: ").append(userPatterns).append('\n');
        }
        if (teamPatterns != null && !teamPatterns.isEmpty()) {
            context.append("Team's common phrases: ").append(teamPatterns).append('\n');
        }
        context.append('\n').append("Generate contextual progress update options.");

        messages.add(new LlmClient.Message(LlmClient.Role.USER, context.toString()));
        return messages;
    }

    private static String contextValue(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }
}
