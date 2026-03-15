package com.weekly.ai;

/**
 * Feature flags for AI-assisted workflows.
 *
 * <p>Per PRD §4: RCDO auto-suggest is MVP ship, reconciliation draft
 * and manager insight summaries are MVP beta (behind flags).
 */
public class AiFeatureFlags {

    private boolean suggestRcdoEnabled = true;
    private boolean draftReconciliationEnabled = false;
    private boolean managerInsightsEnabled = false;

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
}
