package com.weekly.ai;

/**
 * Feature flags for AI-assisted workflows.
 *
 * <p>Feature flags for AI-assisted workflows. In the current local/runtime
 * defaults all product-facing AI flags are enabled, while still remaining
 * individually overrideable via configuration binding.
 */
public class AiFeatureFlags {

    private boolean suggestRcdoEnabled = true;
    private boolean draftReconciliationEnabled = true;
    private boolean managerInsightsEnabled = true;
    private boolean planQualityNudgeEnabled = true;
    private boolean suggestNextWorkEnabled = true;
    private boolean llmNextWorkRankingEnabled = true;
    private boolean targetDateForecastingEnabled = true;
    private boolean planningCopilotEnabled = true;
    private boolean executiveDashboardEnabled = true;
    private boolean weeklyPlanningAgentEnabled = true;
    private boolean misalignmentAgentEnabled = true;
    private boolean suggestEffortTypeEnabled = true;
    private boolean ragSearchEnabled = true;
    private boolean hydeRecommendationsEnabled = true;

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
     */
    public boolean isDraftReconciliationEnabled() {
        return draftReconciliationEnabled;
    }

    public void setDraftReconciliationEnabled(boolean enabled) {
        this.draftReconciliationEnabled = enabled;
    }

    /**
     * Whether manager AI insight summaries are active.
     */
    public boolean isManagerInsightsEnabled() {
        return managerInsightsEnabled;
    }

    public void setManagerInsightsEnabled(boolean enabled) {
        this.managerInsightsEnabled = enabled;
    }

    /**
     * Whether the lock-time AI plan quality nudge endpoint is active.
     */
    public boolean isPlanQualityNudgeEnabled() {
        return planQualityNudgeEnabled;
    }

    public void setPlanQualityNudgeEnabled(boolean enabled) {
        this.planQualityNudgeEnabled = enabled;
    }

    /**
     * Whether the AI next-work suggestion endpoints are active.
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

    /**
     * Whether persisted target-date forecasting is enabled.
     */
    public boolean isTargetDateForecastingEnabled() {
        return targetDateForecastingEnabled;
    }

    public void setTargetDateForecastingEnabled(boolean enabled) {
        this.targetDateForecastingEnabled = enabled;
    }

    /**
     * Whether the manager planning copilot suggestion engine is enabled.
     */
    public boolean isPlanningCopilotEnabled() {
        return planningCopilotEnabled;
    }

    public void setPlanningCopilotEnabled(boolean enabled) {
        this.planningCopilotEnabled = enabled;
    }

    /**
     * Whether the executive strategic health dashboard surfaces are enabled.
     */
    public boolean isExecutiveDashboardEnabled() {
        return executiveDashboardEnabled;
    }

    public void setExecutiveDashboardEnabled(boolean enabled) {
        this.executiveDashboardEnabled = enabled;
    }

    /**
     * Whether the proactive weekly-planning agent is enabled.
     */
    public boolean isWeeklyPlanningAgentEnabled() {
        return weeklyPlanningAgentEnabled;
    }

    public void setWeeklyPlanningAgentEnabled(boolean enabled) {
        this.weeklyPlanningAgentEnabled = enabled;
    }

    /**
     * Whether the proactive misalignment agent is enabled.
     */
    public boolean isMisalignmentAgentEnabled() {
        return misalignmentAgentEnabled;
    }

    public void setMisalignmentAgentEnabled(boolean enabled) {
        this.misalignmentAgentEnabled = enabled;
    }

    /**
     * Whether the AI effort type suggestion endpoint is active.
     * Default: enabled in dev/local. Configurable via {@code ai.features.suggest-effort-type-enabled}.
     */
    public boolean isSuggestEffortTypeEnabled() {
        return suggestEffortTypeEnabled;
    }

    public void setSuggestEffortTypeEnabled(boolean enabled) {
        this.suggestEffortTypeEnabled = enabled;
    }

    /**
     * Whether the RAG semantic search endpoint is active.
     * Default: enabled. Configurable via {@code ai.features.rag-search-enabled}.
     */
    public boolean isRagSearchEnabled() {
        return ragSearchEnabled;
    }

    public void setRagSearchEnabled(boolean enabled) {
        this.ragSearchEnabled = enabled;
    }

    /**
     * Whether the HyDE-powered weekly issue recommendation endpoint is active.
     * Default: enabled. Configurable via {@code ai.features.hyde-recommendations-enabled}.
     */
    public boolean isHydeRecommendationsEnabled() {
        return hydeRecommendationsEnabled;
    }

    public void setHydeRecommendationsEnabled(boolean enabled) {
        this.hydeRecommendationsEnabled = enabled;
    }
}
