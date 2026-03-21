package com.weekly.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AiFeatureFlags} — verifies defaults match PRD:
 * suggest-rcdo is MVP ship (enabled), reconciliation-draft and manager-insights
 * are MVP beta (disabled).
 */
class AiFeatureFlagsTest {

    @Test
    void suggestRcdoEnabledByDefault() {
        AiFeatureFlags flags = new AiFeatureFlags();
        assertTrue(flags.isSuggestRcdoEnabled(), "RCDO suggest is MVP ship, should be enabled");
    }

    @Test
    void draftReconciliationDisabledByDefault() {
        AiFeatureFlags flags = new AiFeatureFlags();
        assertFalse(flags.isDraftReconciliationEnabled(), "Draft reconciliation is beta, should be disabled");
    }

    @Test
    void managerInsightsDisabledByDefault() {
        AiFeatureFlags flags = new AiFeatureFlags();
        assertFalse(flags.isManagerInsightsEnabled(), "Manager insights is beta, should be disabled");
    }

    @Test
    void planQualityNudgeDisabledByDefault() {
        AiFeatureFlags flags = new AiFeatureFlags();
        assertFalse(flags.isPlanQualityNudgeEnabled(), "Plan quality nudge is Wave 1, should be disabled by default");
    }

    @Test
    void suggestNextWorkDisabledByDefault() {
        AiFeatureFlags flags = new AiFeatureFlags();
        assertFalse(flags.isSuggestNextWorkEnabled(), "Next-work suggestions are Wave 2, should be disabled by default");
    }

    @Test
    void llmNextWorkRankingDisabledByDefault() {
        AiFeatureFlags flags = new AiFeatureFlags();
        assertFalse(flags.isLlmNextWorkRankingEnabled(),
                "LLM re-ranking is Wave 3, should be disabled by default");
    }

    @Test
    void flagsCanBeToggled() {
        AiFeatureFlags flags = new AiFeatureFlags();
        flags.setDraftReconciliationEnabled(true);
        assertTrue(flags.isDraftReconciliationEnabled());

        flags.setSuggestRcdoEnabled(false);
        assertFalse(flags.isSuggestRcdoEnabled());

        flags.setPlanQualityNudgeEnabled(true);
        assertTrue(flags.isPlanQualityNudgeEnabled());

        flags.setSuggestNextWorkEnabled(true);
        assertTrue(flags.isSuggestNextWorkEnabled());

        flags.setLlmNextWorkRankingEnabled(true);
        assertTrue(flags.isLlmNextWorkRankingEnabled());
    }
}
