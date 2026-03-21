package com.weekly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ObservabilityConfiguration.MetricsHelper}:
 * verifies custom metrics are properly recorded (PRD §9.7, §14.1).
 */
class ObservabilityConfigurationTest {

    private MeterRegistry registry;
    private ObservabilityConfiguration.MetricsHelper metricsHelper;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsHelper = new ObservabilityConfiguration.MetricsHelper(registry);
    }

    @Test
    void recordsPlanTransitionCounter() {
        metricsHelper.recordPlanTransition("DRAFT", "LOCKED");

        double count = registry.counter("plan.transitions.total",
                "from", "DRAFT", "to", "LOCKED"
        ).count();
        assertEquals(1.0, count);
    }

    @Test
    void recordsOutboxPublishedCounter() {
        metricsHelper.recordOutboxPublished("plan.locked");
        metricsHelper.recordOutboxPublished("plan.locked");

        double count = registry.counter("outbox.published.total",
                "eventType", "plan.locked"
        ).count();
        assertEquals(2.0, count);
    }

    @Test
    void recordsAiSuggestionOutcome() {
        metricsHelper.recordAiSuggestionOutcome("cache_hit");

        double count = registry.counter("ai.suggestion.outcome.total",
                "outcome", "cache_hit"
        ).count();
        assertEquals(1.0, count);
    }

    @Test
    void updatesOutboxUnpublishedGauge() {
        metricsHelper.recordOutboxUnpublishedGauge(7);
        assertEquals(7.0, registry.get("outbox.unpublished.count").gauge().value());

        metricsHelper.recordOutboxUnpublishedGauge(3);
        assertEquals(3.0, registry.get("outbox.unpublished.count").gauge().value());
    }

    @Test
    void recordsAiTokenUsage() {
        metricsHelper.recordAiTokens("input", 500);

        double count = registry.counter("ai.llm.tokens.total",
                "direction", "input"
        ).count();
        assertEquals(500.0, count);
    }

    @Test
    void registryIsAccessible() {
        assertNotNull(metricsHelper.getRegistry());
    }
}
