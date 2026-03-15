package com.weekly.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * AI suggestion service interface (§9.5).
 *
 * <p>Provides RCDO auto-suggest and reconciliation draft capabilities.
 * Implementations use the {@link LlmClient} abstraction for provider
 * independence.
 *
 * <p>All AI outputs are suggestions — never auto-committed. The user
 * confirms every output.
 */
public interface AiSuggestionService {

    /**
     * Suggests RCDO mappings for a commitment.
     *
     * @param orgId       the organization ID
     * @param title       the commit title
     * @param description the commit description
     * @return suggestion result (may be empty on LLM unavailability)
     */
    SuggestionResult suggestRcdo(UUID orgId, String title, String description);

    /**
     * Drafts reconciliation data for a plan's commits.
     *
     * @param orgId  the organization ID
     * @param planId the plan ID
     * @return reconciliation draft items (may be empty on LLM unavailability)
     */
    ReconciliationDraftResult draftReconciliation(UUID orgId, UUID planId);

    /** Result from RCDO suggestion. */
    record SuggestionResult(
            String status,
            List<RcdoSuggestion> suggestions
    ) {
        public static SuggestionResult unavailable() {
            return new SuggestionResult("unavailable", List.of());
        }
    }

    /** A single RCDO suggestion from the AI. */
    record RcdoSuggestion(
            String outcomeId,
            String rallyCryName,
            String objectiveName,
            String outcomeName,
            double confidence,
            String rationale
    ) {}

    /**
     * Drafts manager insight summaries for a manager dashboard week.
     *
     * @param orgId     the organization ID
     * @param managerId the requesting manager's user ID
     * @param weekStart the week being summarized
     * @return insight summary result (may be empty on LLM unavailability)
     */
    ManagerInsightsResult draftManagerInsights(UUID orgId, UUID managerId, LocalDate weekStart);

    /** Result from reconciliation draft. */
    record ReconciliationDraftResult(
            String status,
            List<ReconciliationDraftItem> drafts
    ) {
        public static ReconciliationDraftResult unavailable() {
            return new ReconciliationDraftResult("unavailable", List.of());
        }
    }

    /** A single reconciliation draft item. */
    record ReconciliationDraftItem(
            String commitId,
            String suggestedStatus,
            String suggestedDeltaReason,
            String suggestedActualResult
    ) {}

    /** Result from manager insight drafting. */
    record ManagerInsightsResult(
            String status,
            String headline,
            List<ManagerInsight> insights
    ) {
        public static ManagerInsightsResult unavailable() {
            return new ManagerInsightsResult("unavailable", null, List.of());
        }
    }

    /** A single manager insight item. */
    record ManagerInsight(
            String title,
            String detail,
            String severity
    ) {}
}
