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
     *       a {@code text} (the short phrase, under 50 chars) and a {@code source}
     *       (for example, {@code "ai"} or {@code "pattern"})</li>
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
                    "required": ["text", "source"],
                    "properties": {
                      "text": { "type": "string" },
                      "source": { "type": "string" }
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
     *   <li>A SYSTEM message containing the assistant persona and generation rules.</li>
     *   <li>A USER message containing the commitment context block ({@code commitTitle},
     *       {@code category}, {@code chessPriority}, {@code outcomeName},
     *       {@code currentStatus}, {@code lastNote}, {@code daysSinceLastCheckIn},
     *       and optional user phrase patterns). Placing user-authored text in the
     *       USER role mitigates prompt injection per PRD §4.</li>
     * </ol>
     *
     * @param commitTitle the title of the commitment (user-authored)
     * @param category the commitment category (for example, {@code "ENGINEERING"}
     *                 or {@code "OPERATIONS"})
     * @param chessPriority the chess-piece priority (for example, {@code "KING"}
     *                      or {@code "QUEEN"})
     * @param currentStatus the current progress status (for example,
     *                      {@code "ON_TRACK"} or {@code "AT_RISK"})
     * @param lastNote the most recent check-in note; blank or {@code null} values are
     *                 rendered as {@value #NONE}
     * @param daysSinceLastCheckIn number of days since the last check-in;
     *                             {@code 0} indicates a same-day or no-gap update
     * @param userPatterns the user's most frequently used phrases for this context;
     *                     empty list means no personalisation data is available
     * @param outcomeName the linked outcome name; blank or {@code null} values are
     *                    rendered as {@value #NONE}
     * @return ordered messages for the LLM — [{@code SYSTEM}, {@code USER}]
     */
    public static List<LlmClient.Message> buildCheckInOptionMessages(
            String commitTitle,
            String category,
            String chessPriority,
            String currentStatus,
            String lastNote,
            int daysSinceLastCheckIn,
            List<String> userPatterns,
            String outcomeName
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                "You are a check-in assistant for weekly planning. "
                        + "Generate concise progress update options for a work commitment. "
                        + "Return valid JSON matching the response schema. "
                        + "Each progress option should be a short phrase (under 50 chars). "
                        + "Include 4-5 options mixing status continuations and common transitions."
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
        context.append('\n').append("Generate contextual progress update options.");

        messages.add(new LlmClient.Message(LlmClient.Role.USER, context.toString()));
        return messages;
    }

    private static String contextValue(String value) {
        return value == null || value.isBlank() ? NONE : value;
    }
}
