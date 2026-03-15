package com.weekly.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void flagsCanBeToggled() {
        AiFeatureFlags flags = new AiFeatureFlags();
        flags.setDraftReconciliationEnabled(true);
        assertTrue(flags.isDraftReconciliationEnabled());

        flags.setSuggestRcdoEnabled(false);
        assertFalse(flags.isSuggestRcdoEnabled());
    }
}
