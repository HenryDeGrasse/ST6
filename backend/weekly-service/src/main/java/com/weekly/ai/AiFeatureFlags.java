package com.weekly.ai;

/**
 * Feature flags for AI-assisted workflows.
 *
 * <p>Per PRD §4: RCDO auto-suggest is MVP ship, reconciliation draft
 * and manager insight summaries are MVP beta (behind flags).
 * Wave 1 adds plan quality nudge (data-driven, behind flag).
 * Wave 2 adds next-work suggestions (data-driven, behind flag).
 * Wave 3 adds LLM-ranked next-work suggestions (behind flag).
 */
public class AiFeatureFlags {

    private boolean suggestRcdoEnabled = true;
    private boolean draftReconciliationEnabled = false;
    private boolean managerInsightsEnabled = false;
    private boolean planQualityNudgeEnabled = false;
    private boolean suggestNextWorkEnabled = false;
    private boolean llmNextWorkRankingEnabled = false;

    /**
     * Whether the RCDO auto-suggest endpoint is active.
     * MVP ship: enabled by default.
     */
    public boolean isSuggestRcdoEnabled() {
        return suggestRcdoEnabled;
    }

    public void setSuggestRcdoEnabled(boolean enabled) {
        this.suggestRcdoEnabled = enabled;
    }

    /**
     * Whether the AI reconciliation draft endpoint is active.
     * MVP beta: disabled by default, behind feature flag.
     */
    public boolean isDraftReconciliationEnabled() {
        return draftReconciliationEnabled;
    }

    public void setDraftReconciliationEnabled(boolean enabled) {
        this.draftReconciliationEnabled = enabled;
    }

    /**
     * Whether manager AI insight summaries are active.
     * MVP beta: disabled by default, behind feature flag.
     */
    public boolean isManagerInsightsEnabled() {
        return managerInsightsEnabled;
    }

    public void setManagerInsightsEnabled(boolean enabled) {
        this.managerInsightsEnabled = enabled;
    }

    /**
     * Whether the lock-time AI plan quality nudge endpoint is active.
     * Wave 1: disabled by default, behind feature flag {@code ai.features.plan-quality-nudge-enabled}.
     */
    public boolean isPlanQualityNudgeEnabled() {
        return planQualityNudgeEnabled;
    }

    public void setPlanQualityNudgeEnabled(boolean enabled) {
        this.planQualityNudgeEnabled = enabled;
    }

    /**
     * Whether the AI next-work suggestion endpoints are active.
     * Wave 2: disabled by default, behind feature flag
     * {@code ai.features.suggest-next-work-enabled}.
     */
    public boolean isSuggestNextWorkEnabled() {
        return suggestNextWorkEnabled;
    }

    public void setSuggestNextWorkEnabled(boolean enabled) {
        this.suggestNextWorkEnabled = enabled;
    }

    /**
     * Whether LLM-based re-ranking is applied on top of the data-driven
     * next-work candidate set.
     * Wave 3 / Phase 2: disabled by default, behind feature flag
     * {@code ai.features.llm-next-work-ranking-enabled}.
     *
     * <p>When disabled the data-driven Phase 1 order is returned directly.
     * When enabled the Phase 1 candidate set is sent to the LLM for
     * strategic re-ranking and rationale enrichment, with automatic fallback
     * to Phase 1 order when the LLM is unavailable.
     */
    public boolean isLlmNextWorkRankingEnabled() {
        return llmNextWorkRankingEnabled;
    }

    public void setLlmNextWorkRankingEnabled(boolean enabled) {
        this.llmNextWorkRankingEnabled = enabled;
    }
}
