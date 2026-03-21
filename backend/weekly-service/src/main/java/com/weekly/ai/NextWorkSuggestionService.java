package com.weekly.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service that generates data-driven "next work" suggestions for a user.
 *
 * <p>Phase 1 implementation is pure data queries — no LLM. It surfaces:
 * <ul>
 *   <li>Carry-forward items from the last 2 weeks (commits not yet completed)</li>
 *   <li>RCDO coverage gaps (outcomes with zero team commits in the last 2–4 weeks)</li>
 * </ul>
 *
 * <p>Results are filtered against the user's recent feedback
 * (DECLINE actions within the last 4 weeks suppress re-surfacing).
 *
 * <p>Phase 2 will layer LLM-based ranking and rationale enrichment on top of
 * the same data pipeline.
 */
public interface NextWorkSuggestionService {

    /**
     * Generates next-work suggestions for the given user as of the current week.
     *
     * @param orgId  the organisation ID
     * @param userId the authenticated user's ID
     * @param asOf   the Monday of the current week used as the reference date
     * @return suggestion result; never null; may contain empty list when no
     *         relevant data is available
     */
    NextWorkSuggestionsResult suggestNextWork(UUID orgId, UUID userId, LocalDate asOf);

    // ── Result types ──────────────────────────────────────────────────────────

    /**
     * Top-level result of a next-work suggestion run.
     *
     * @param status      "ok" when checks ran; "unavailable" on error
     * @param suggestions ordered list of suggestions (highest-confidence first)
     */
    record NextWorkSuggestionsResult(
            String status,
            List<NextWorkSuggestion> suggestions
    ) {
        /** Returns an unavailable result suitable for error-fallback. */
        public static NextWorkSuggestionsResult unavailable() {
            return new NextWorkSuggestionsResult("unavailable", List.of());
        }
    }

    /**
     * A single next-work suggestion surfaced to the user.
     *
     * @param suggestionId          stable deterministic UUID for feedback correlation
     * @param title                 suggested commit title
     * @param suggestedOutcomeId    RCDO outcome UUID string (may be null for non-strategic carries)
     * @param suggestedChessPriority recommended chess priority (may be null)
     * @param confidence            confidence score in [0.0, 1.0]
     * @param source                "CARRY_FORWARD", "COVERAGE_GAP", or "EXTERNAL_TICKET"
     * @param sourceDetail          human-readable detail about the source (e.g., week date)
     * @param rationale             human-readable explanation of why this is suggested
     * @param externalTicketUrl     URL to the ticket in the provider's web UI; null for non-ticket sources
     * @param externalTicketStatus  last-synced status label from the provider; null for non-ticket sources
     */
    record NextWorkSuggestion(
            UUID suggestionId,
            String title,
            String suggestedOutcomeId,
            String suggestedChessPriority,
            double confidence,
            String source,
            String sourceDetail,
            String rationale,
            String externalTicketUrl,
            String externalTicketStatus
    ) {}
}
