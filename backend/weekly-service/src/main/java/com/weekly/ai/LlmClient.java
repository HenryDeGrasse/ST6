package com.weekly.ai;

import java.util.List;

/**
 * Provider abstraction for LLM calls (§9.5).
 *
 * <p>To switch providers (Claude → OpenAI → local model),
 * implement a new {@code LlmClient}. No changes needed in
 * {@link AiSuggestionService}, prompt builders, or validators.
 * The active client is selected by configuration ({@code ai.provider}).
 */
public interface LlmClient {

    /**
     * Sends structured messages to the LLM and returns the raw response.
     *
     * @param messages       ordered messages (system, context, user)
     * @param responseSchema JSON schema the response must conform to
     * @return the raw LLM response text
     * @throws LlmUnavailableException on timeout (5s hard limit) or provider error
     */
    String complete(List<Message> messages, String responseSchema);

    /** A single message in the conversation. */
    record Message(Role role, String content) {}

    /** Message roles for structured prompt separation. */
    enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    /** Thrown when the LLM is unreachable or times out. */
    class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String message) {
            super(message);
        }

        public LlmUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
