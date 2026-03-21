package com.weekly.ai;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the AI suggestion module.
 *
 * <p>Wires up the LLM client, rate limiter, cache, and feature flags.
 *
 * <p>Provider selection via {@code ai.provider}:
 * <ul>
 *   <li>{@code stub} (default) — deterministic fake responses for dev/test</li>
 *   <li>{@code openrouter} — real LLM calls via OpenRouter API</li>
 * </ul>
 */
@Configuration
public class AiConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    @ConfigurationProperties(prefix = "ai.features")
    public AiFeatureFlags aiFeatureFlags() {
        return new AiFeatureFlags();
    }

    @Bean
    public LlmClient llmClient(
            @Value("${ai.provider:stub}") String provider,
            @Value("${ai.openrouter.api-key:}") String openRouterApiKey,
            @Value("${ai.openrouter.model:anthropic/claude-sonnet-4}") String openRouterModel,
            @Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}") String openRouterBaseUrl
    ) {
        return switch (provider.toLowerCase()) {
            case "openrouter" -> {
                if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
                    LOG.error("ai.provider=openrouter but ai.openrouter.api-key is not set! Falling back to stub.");
                    yield new StubLlmClient();
                }
                LOG.info("AI provider: OpenRouter (model={}, baseUrl={})", openRouterModel, openRouterBaseUrl);
                yield new OpenRouterLlmClient(openRouterApiKey, openRouterModel, openRouterBaseUrl);
            }
            case "stub" -> {
                LOG.info("AI provider: stub (deterministic fake responses)");
                yield new StubLlmClient();
            }
            default -> {
                LOG.warn("Unknown ai.provider='{}', falling back to stub", provider);
                yield new StubLlmClient();
            }
        };
    }

    @Bean
    public RateLimiter aiRateLimiter() {
        // 20 AI requests per user per minute (PRD §4)
        return new RateLimiter(20, Duration.ofMinutes(1));
    }

    @Bean
    public AiCacheService aiCacheService() {
        // 1-hour TTL for suggestion cache (PRD §4)
        return new AiCacheService(Duration.ofHours(1));
    }
}
