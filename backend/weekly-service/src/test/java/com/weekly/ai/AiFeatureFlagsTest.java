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
    void nonSuggestFlagsEnabledByDefault() {
        AiFeatureFlags flags = new AiFeatureFlags();
        assertTrue(flags.isDraftReconciliationEnabled());
        assertTrue(flags.isManagerInsightsEnabled());
        assertTrue(flags.isPlanQualityNudgeEnabled());
        assertTrue(flags.isSuggestNextWorkEnabled());
        assertTrue(flags.isLlmNextWorkRankingEnabled());
        assertTrue(flags.isTargetDateForecastingEnabled());
        assertTrue(flags.isPlanningCopilotEnabled());
        assertTrue(flags.isExecutiveDashboardEnabled());
        assertTrue(flags.isWeeklyPlanningAgentEnabled());
        assertTrue(flags.isMisalignmentAgentEnabled());
        assertTrue(flags.isSuggestEffortTypeEnabled());
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

        flags.setTargetDateForecastingEnabled(true);
        flags.setPlanningCopilotEnabled(true);
        flags.setExecutiveDashboardEnabled(true);
        flags.setWeeklyPlanningAgentEnabled(true);
        flags.setMisalignmentAgentEnabled(true);
        flags.setSuggestEffortTypeEnabled(false);
        assertTrue(flags.isTargetDateForecastingEnabled());
        assertTrue(flags.isPlanningCopilotEnabled());
        assertTrue(flags.isExecutiveDashboardEnabled());
        assertTrue(flags.isWeeklyPlanningAgentEnabled());
        assertTrue(flags.isMisalignmentAgentEnabled());
        assertFalse(flags.isSuggestEffortTypeEnabled());
    }
}
