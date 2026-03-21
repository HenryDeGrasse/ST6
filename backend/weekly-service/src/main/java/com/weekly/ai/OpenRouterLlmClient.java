package com.weekly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM client that calls OpenRouter's chat completions API.
 *
 * <p>OpenRouter provides a unified API across models (Claude, GPT-4, Llama, etc.)
 * using the OpenAI-compatible chat completions format.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@code ai.openrouter.api-key} — your OpenRouter API key</li>
 *   <li>{@code ai.openrouter.model} — model identifier (default: {@code anthropic/claude-sonnet-4})</li>
 *   <li>{@code ai.openrouter.base-url} — API base URL (default: {@code https://openrouter.ai/api/v1})</li>
 * </ul>
 *
 * <p>Hard timeout of 5 seconds per request per PRD §4.
 */
public class OpenRouterLlmClient implements LlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenRouterLlmClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public OpenRouterLlmClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String complete(List<Message> messages, String responseSchema) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 2048);

            ArrayNode messagesArray = requestBody.putArray("messages");
            for (Message msg : messages) {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", mapRole(msg.role()));
                msgNode.put("content", msg.content());
            }

            // Request JSON response format
            ObjectNode responseFormat = requestBody.putObject("response_format");
            responseFormat.put("type", "json_object");

            String bodyJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://weekly-commitments.internal")
                    .header("X-Title", "Weekly Commitments AI")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            LOG.debug("OpenRouter request: model={}, messages={}", model, messages.size());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warn("OpenRouter returned HTTP {}: {}", response.statusCode(),
                        truncate(response.body(), 500));
                throw new LlmUnavailableException(
                        "OpenRouter returned HTTP " + response.statusCode());
            }

            // Extract the content from the response
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isEmpty() || choices.isMissingNode()) {
                LOG.warn("OpenRouter returned empty choices: {}", truncate(response.body(), 500));
                throw new LlmUnavailableException("OpenRouter returned empty choices");
            }

            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new LlmUnavailableException("OpenRouter returned blank content");
            }

            LOG.debug("OpenRouter response: {} chars", content.length());
            return content;

        } catch (LlmUnavailableException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            LOG.warn("OpenRouter request timed out after {}s", TIMEOUT.toSeconds());
            throw new LlmUnavailableException("OpenRouter request timed out", e);
        } catch (Exception e) {
            LOG.error("OpenRouter request failed", e);
            throw new LlmUnavailableException("OpenRouter request failed: " + e.getMessage(), e);
        }
    }

    private String mapRole(Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
