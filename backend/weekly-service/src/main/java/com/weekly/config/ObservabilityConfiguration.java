package com.weekly.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for weekly-service metrics.
 *
 * <p>Per PRD §9.7 and §14.1, custom metrics are registered for:
 * <ul>
 *   <li>AI/LLM request latency and token usage</li>
 *   <li>Outbox event publish lag</li>
 *   <li>Plan lifecycle event counts</li>
 * </ul>
 */
@Configuration
public class ObservabilityConfiguration {

    @Bean
    public Timer aiLlmRequestTimer(MeterRegistry registry) {
        return Timer.builder("ai.llm.request.seconds")
                .description("Time spent calling the LLM API")
                .tag("provider", "unknown")
                .register(registry);
    }

    @Bean
    public MetricsHelper metricsHelper(MeterRegistry registry) {
        return new MetricsHelper(registry);
    }

    /**
     * Central helper for recording custom business metrics.
     */
    public static class MetricsHelper {

        private final MeterRegistry registry;
        private final AtomicLong outboxUnpublishedCount;

        public MetricsHelper(MeterRegistry registry) {
            this.registry = registry;
            this.outboxUnpublishedCount = registry.gauge(
                    "outbox.unpublished.count",
                    new AtomicLong(0L)
            );
        }

        public void recordPlanTransition(String fromState, String toState) {
            registry.counter("plan.transitions.total",
                    "from", fromState,
                    "to", toState
            ).increment();
        }

        public void recordOutboxPublished(String eventType) {
            registry.counter("outbox.published.total",
                    "eventType", eventType
            ).increment();
        }

        public void recordOutboxUnpublishedGauge(long count) {
            outboxUnpublishedCount.set(count);
        }

        public void recordAiSuggestionOutcome(String outcome) {
            registry.counter("ai.suggestion.outcome.total",
                    "outcome", outcome
            ).increment();
        }

        public void recordAiTokens(String direction, long count) {
            registry.counter("ai.llm.tokens.total",
                    "direction", direction
            ).increment(count);
        }

        public MeterRegistry getRegistry() {
            return registry;
        }
    }
}
