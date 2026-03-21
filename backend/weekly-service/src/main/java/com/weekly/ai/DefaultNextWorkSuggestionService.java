package com.weekly.ai;

import com.weekly.integration.IntegrationService;
import com.weekly.integration.IntegrationService.UserTicketContext;
import com.weekly.shared.NextWorkDataProvider;
import com.weekly.shared.NextWorkDataProvider.CarryForwardItem;
import com.weekly.shared.NextWorkDataProvider.RcdoCoverageGap;
import com.weekly.shared.NextWorkDataProvider.RecentCommitContext;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link NextWorkSuggestionService}.
 *
 * <h3>Phase 1 — Data-driven candidate generation</h3>
 * <ol>
 *   <li>Query carry-forward items from the last {@value #CARRY_FORWARD_WEEKS} weeks</li>
 *   <li>Compute RCDO coverage gaps (outcomes with a recent 2–{@value #MAX_GAP_WEEKS}
 *       week team coverage drought that had activity in the last {@value #REF_WEEKS} weeks)</li>
 *   <li>Filter out suggestions whose {@code suggestionId} the user has DECLINED
 *       within the last {@value #DECLINE_SUPPRESSION_WEEKS} weeks</li>
 *   <li>Rank: carry-forward by age (older = higher confidence), coverage gaps
 *       by severity ({@code weeksMissing} descending) with urgency multipliers
 *       for tracked outcomes</li>
 *   <li>Cap at {@value #MAX_SUGGESTIONS}</li>
 * </ol>
 *
 * <h3>Phase 2 — LLM re-ranking (when {@code llmNextWorkRankingEnabled} is true)</h3>
 * <ol>
 *   <li>Phase 1 candidates become the LLM input (candidate set)</li>
 *   <li>A 4-week commit history, active carry-forward items, and team coverage gaps are added as context</li>
 *   <li>The LLM re-ranks the candidates by strategic impact and generates rationales</li>
 *   <li>Validated LLM response replaces Phase-1 confidence scores and rationales</li>
 *   <li>Suggestions not returned by the LLM are appended in their original order</li>
 *   <li>Result is cached keyed on {@code orgId + userId + weekStart + context fingerprint}</li>
 *   <li>Falls back to Phase-1 order if the LLM is unavailable or returns an invalid response</li>
 * </ol>
 *
 * <p>Suggestion IDs are generated deterministically from
 * (org + user + source + entityId) so that the same underlying item
 * always produces the same ID for stable feedback correlation.
 *
 * <p>On any unexpected error the method degrades gracefully and returns
 * {@link NextWorkSuggestionService.NextWorkSuggestionsResult#unavailable()}.
 */
@Service
public class DefaultNextWorkSuggestionService implements NextWorkSuggestionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DefaultNextWorkSuggestionService.class);

    /** Number of recent weeks to scan for carry-forward items (Phase 1 candidate window). */
    static final int CARRY_FORWARD_WEEKS = 2;

    /** Number of recent weeks to scan for carry-forward history sent as LLM context. */
    static final int LLM_CONTEXT_HISTORY_WEEKS = 4;

    /** Minimum recent consecutive missing weeks required to surface a coverage gap. */
    static final int MIN_GAP_WEEKS = 2;

    /** Maximum recent consecutive missing weeks considered for coverage-gap severity. */
    static final int MAX_GAP_WEEKS = 4;

    /** Broader reference window to establish historical outcome activity. */
    static final int REF_WEEKS = 8;

    /** DECLINE feedback within this many weeks suppresses re-surfacing. */
    static final int DECLINE_SUPPRESSION_WEEKS = 4;

    /** Maximum number of suggestions returned in a single call. */
    static final int MAX_SUGGESTIONS = 10;

    /** Confidence score assigned to recently carried items (high urgency). */
    static final double CARRY_FORWARD_BASE_CONFIDENCE = 0.85;

    /** Confidence score assigned to coverage gap suggestions. */
    static final double COVERAGE_GAP_BASE_CONFIDENCE = 0.60;

    static final double CRITICAL_URGENCY_MULTIPLIER = 1.4;
    static final double AT_RISK_URGENCY_MULTIPLIER = 1.2;
    static final double NEEDS_ATTENTION_URGENCY_MULTIPLIER = 1.1;

    /** Confidence score assigned to external ticket suggestions (Phase 3). */
    static final double EXTERNAL_TICKET_BASE_CONFIDENCE = 0.75;

    /** Number of weeks to look back when fetching external ticket data. */
    static final int EXTERNAL_TICKET_LOOKBACK_WEEKS = 4;

    private final NextWorkDataProvider dataProvider;
    private final IntegrationService integrationService;
    private final AiSuggestionFeedbackRepository feedbackRepository;
    private final LlmClient llmClient;
    private final AiCacheService cacheService;
    private final UrgencyDataProvider urgencyDataProvider;
    private final boolean llmRankingEnabled;

    public DefaultNextWorkSuggestionService(
            NextWorkDataProvider dataProvider,
            IntegrationService integrationService,
            AiSuggestionFeedbackRepository feedbackRepository,
            LlmClient llmClient,
            AiCacheService cacheService,
            AiFeatureFlags featureFlags,
            UrgencyDataProvider urgencyDataProvider
    ) {
        this.dataProvider = dataProvider;
        this.integrationService = integrationService;
        this.feedbackRepository = feedbackRepository;
        this.llmClient = llmClient;
        this.cacheService = cacheService;
        this.urgencyDataProvider = urgencyDataProvider;
        this.llmRankingEnabled = featureFlags.isLlmNextWorkRankingEnabled();
    }

    @Override
    public NextWorkSuggestionsResult suggestNextWork(
            UUID orgId, UUID userId, LocalDate asOf) {
        try {
            // 1. Fetch carry-forward items (Phase 1 window)
            List<CarryForwardItem> carryForwardItems =
                    dataProvider.getRecentCarryForwardItems(orgId, userId, asOf, CARRY_FORWARD_WEEKS);

            // 2. Fetch RCDO coverage gaps
            List<RcdoCoverageGap> coverageGaps =
                    dataProvider.getTeamCoverageGaps(orgId, asOf, MAX_GAP_WEEKS, REF_WEEKS);

            // 3. Load recently declined suggestion IDs (suppression filter)
            Instant suppressionCutoff = asOf.minusWeeks(DECLINE_SUPPRESSION_WEEKS)
                    .atStartOfDay()
                    .toInstant(java.time.ZoneOffset.UTC);
            Set<UUID> recentlyDeclined = loadDeclinedIds(orgId, userId, suppressionCutoff);

            // 4. Build ranked suggestions (Phase 1 + Phase 3 external tickets)
            List<NextWorkSuggestion> suggestions = new ArrayList<>();

            addCarryForwardSuggestions(
                    orgId, userId, carryForwardItems, recentlyDeclined, suggestions);
            addCoverageGapSuggestions(
                    orgId, userId, coverageGaps, recentlyDeclined, suggestions);

            // Phase 3: enrich with external ticket suggestions
            List<UserTicketContext> linkedTickets = dedupeLinkedTickets(
                    fetchLinkedTickets(orgId, userId, asOf));
            addExternalTicketSuggestions(
                    orgId, userId, linkedTickets, recentlyDeclined, suggestions);

            // 5. Sort by confidence descending
            suggestions.sort(Comparator.comparingDouble(NextWorkSuggestion::confidence).reversed());

            // 6. Cap at MAX_SUGGESTIONS
            if (suggestions.size() > MAX_SUGGESTIONS) {
                suggestions = new ArrayList<>(suggestions.subList(0, MAX_SUGGESTIONS));
            }

            // 7. Phase 2: LLM re-ranking (when enabled and candidates are non-empty)
            if (llmRankingEnabled && !suggestions.isEmpty()) {
                List<RecentCommitContext> recentCommitHistory = fetchRecentCommitHistory(orgId, userId, asOf);
                List<CarryForwardItem> extendedHistory = fetchExtendedHistory(orgId, userId, asOf);
                suggestions = rankWithLlm(orgId, userId, asOf,
                        List.copyOf(suggestions), recentCommitHistory, extendedHistory,
                        coverageGaps, linkedTickets);
            }

            return new NextWorkSuggestionsResult("ok", List.copyOf(suggestions));
        } catch (Exception e) {
            LOG.error("Unexpected error in suggest-next-work for user {}", userId, e);
            return NextWorkSuggestionsResult.unavailable();
        }
    }

    // ── Phase 2: LLM re-ranking ───────────────────────────────────────────────

    /**
     * Sends the Phase-1/Phase-3 candidates to the LLM for strategic re-ranking and
     * rationale enrichment.
     *
     * <p>Cache key: {@code orgId + userId + weekStart + context fingerprint}.
     * Falls back to the original candidate list on any LLM error.
     */
    private List<NextWorkSuggestion> rankWithLlm(
            UUID orgId,
            UUID userId,
            LocalDate asOf,
            List<NextWorkSuggestion> candidates,
            List<RecentCommitContext> recentCommitHistory,
            List<CarryForwardItem> recentCarryForwardHistory,
            List<RcdoCoverageGap> coverageGaps,
            List<UserTicketContext> linkedTickets
    ) {
        try {
            // Cache check
            String fingerprint = computeContextFingerprint(
                    candidates, recentCommitHistory, recentCarryForwardHistory,
                    coverageGaps, linkedTickets);
            String cacheKey = AiCacheService.buildNextWorkKey(orgId, userId, asOf, fingerprint);
            Optional<NextWorkSuggestionsResult> cached =
                    cacheService.get(orgId, cacheKey, NextWorkSuggestionsResult.class);
            if (cached.isPresent()) {
                LOG.debug("Cache hit for LLM next-work ranking: {}", cacheKey);
                return cached.get().suggestions();
            }

            // Build prompt and call LLM
            List<LlmClient.Message> messages = PromptBuilder.buildNextWorkSuggestMessages(
                    candidates, recentCommitHistory, recentCarryForwardHistory,
                    coverageGaps, linkedTickets, asOf);
            String rawResponse = llmClient.complete(
                    messages, PromptBuilder.nextWorkSuggestResponseSchema());

            // Validate response — reject hallucinated IDs
            Set<String> validIds = candidates.stream()
                    .map(s -> s.suggestionId().toString())
                    .collect(Collectors.toSet());
            List<ResponseValidator.NextWorkRankedItem> ranked =
                    ResponseValidator.validateNextWorkSuggestions(rawResponse, validIds);

            if (ranked.isEmpty()) {
                LOG.debug("LLM returned no valid ranked suggestions; using Phase-1 order");
                return candidates;
            }

            // Merge LLM rankings with original suggestion data
            List<NextWorkSuggestion> reranked = mergeRankings(candidates, ranked);

            // Cache the full result
            cacheService.put(orgId, cacheKey, new NextWorkSuggestionsResult("ok", reranked));
            return reranked;

        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("LLM unavailable for next-work ranking, using Phase-1 order: {}", e.getMessage());
            return candidates;
        } catch (Exception e) {
            LOG.warn("Unexpected error in LLM next-work ranking, using Phase-1 order", e);
            return candidates;
        }
    }

    /**
     * Merges LLM re-rankings with the original Phase-1 suggestion data.
     *
     * <p>For each LLM-returned item: applies the LLM confidence score, rationale,
     * and optional chess-priority suggestion while preserving all other fields
     * (title, outcomeId, source, sourceDetail) from the original Phase-1 entry.
     *
     * <p>Candidates not ranked by the LLM (should not occur with a well-formed
     * response) are appended at the end in their original Phase-1 order.
     */
    private List<NextWorkSuggestion> mergeRankings(
            List<NextWorkSuggestion> candidates,
            List<ResponseValidator.NextWorkRankedItem> ranked
    ) {
        Map<String, NextWorkSuggestion> byId = new HashMap<>();
        for (NextWorkSuggestion s : candidates) {
            byId.put(s.suggestionId().toString(), s);
        }

        Set<String> rankedIds = ranked.stream()
                .map(ResponseValidator.NextWorkRankedItem::suggestionId)
                .collect(Collectors.toSet());

        List<NextWorkSuggestion> result = new ArrayList<>(ranked.size() + candidates.size());

        for (ResponseValidator.NextWorkRankedItem r : ranked) {
            NextWorkSuggestion orig = byId.get(r.suggestionId());
            if (orig == null) {
                continue; // Should not happen — validator already checked
            }
            // Prefer LLM chess priority when provided; fall back to Phase-1 value
            String chessPriority = r.suggestedChessPriority() != null
                    ? r.suggestedChessPriority()
                    : orig.suggestedChessPriority();
            result.add(new NextWorkSuggestion(
                    orig.suggestionId(),
                    orig.title(),
                    orig.suggestedOutcomeId(),
                    chessPriority,
                    r.confidence(),
                    orig.source(),
                    orig.sourceDetail(),
                    r.rationale(),
                    orig.externalTicketUrl(),
                    orig.externalTicketStatus()
            ));
        }

        // Append any candidates not covered by the LLM (safety net)
        for (NextWorkSuggestion s : candidates) {
            if (!rankedIds.contains(s.suggestionId().toString())) {
                result.add(s);
            }
        }

        return result;
    }

    /**
     * Fetches the user's recent commit history window for LLM context enrichment.
     * On error, returns an empty list so the LLM prompt is still built and the
     * pipeline continues.
     */
    private List<RecentCommitContext> fetchRecentCommitHistory(
            UUID orgId, UUID userId, LocalDate asOf) {
        try {
            return dataProvider.getRecentCommitHistory(
                    orgId, userId, asOf, LLM_CONTEXT_HISTORY_WEEKS);
        } catch (Exception e) {
            LOG.debug("Could not fetch recent commit history for LLM context: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches a wider carry-forward history window for LLM context enrichment.
     * On error, returns an empty list so the LLM prompt is still built and the
     * pipeline continues.
     */
    private List<CarryForwardItem> fetchExtendedHistory(
            UUID orgId, UUID userId, LocalDate asOf) {
        try {
            return dataProvider.getRecentCarryForwardItems(
                    orgId, userId, asOf, LLM_CONTEXT_HISTORY_WEEKS);
        } catch (Exception e) {
            LOG.debug("Could not fetch extended history for LLM context: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Computes a deterministic fingerprint over the LLM input context so the
     * cache invalidates when the candidate set or history changes.
     */
    private String computeContextFingerprint(
            List<NextWorkSuggestion> candidates,
            List<RecentCommitContext> recentCommitHistory,
            List<CarryForwardItem> carryForwardHistory,
            List<RcdoCoverageGap> gaps,
            List<UserTicketContext> linkedTickets
    ) {
        StringBuilder sb = new StringBuilder();
        candidates.forEach(s -> sb.append("candidate:")
                .append(s.suggestionId()).append('|')
                .append(s.title()).append('|')
                .append(s.suggestedOutcomeId()).append('|')
                .append(s.suggestedChessPriority()).append('|')
                .append(s.confidence()).append('|')
                .append(s.source()).append('|')
                .append(s.sourceDetail()).append(';'));
        recentCommitHistory.forEach(h -> sb.append("recent:")
                .append(h.commitId()).append('|')
                .append(h.weekStart()).append('|')
                .append(h.title()).append('|')
                .append(h.outcomeId()).append('|')
                .append(h.outcomeName()).append('|')
                .append(h.objectiveName()).append('|')
                .append(h.rallyCryName()).append('|')
                .append(h.completionStatus()).append(';'));
        carryForwardHistory.forEach(h -> sb.append("carry:")
                .append(h.sourceCommitId()).append('|')
                .append(h.sourceWeekStart()).append('|')
                .append(h.title()).append('|')
                .append(h.outcomeId()).append('|')
                .append(h.outcomeName()).append('|')
                .append(h.objectiveName()).append('|')
                .append(h.rallyCryName()).append('|')
                .append(h.carryForwardWeeks()).append('|')
                .append(h.expectedResult()).append(';'));
        gaps.forEach(g -> sb.append("gap:")
                .append(g.outcomeId()).append('|')
                .append(g.outcomeName()).append('|')
                .append(g.objectiveName()).append('|')
                .append(g.rallyCryName()).append('|')
                .append(g.weeksMissing()).append('|')
                .append(g.teamCommitsPrevWindow()).append(';'));
        linkedTickets.forEach(t -> sb.append("ticket:")
                .append(t.externalTicketId()).append('|')
                .append(t.provider()).append('|')
                .append(t.externalStatus()).append('|')
                .append(t.outcomeId()).append(';'));
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Carry-forward suggestion building ────────────────────────────────────

    private void addCarryForwardSuggestions(
            UUID orgId,
            UUID userId,
            List<CarryForwardItem> items,
            Set<UUID> recentlyDeclined,
            List<NextWorkSuggestion> out
    ) {
        for (CarryForwardItem item : items) {
            UUID suggestionId = buildCarryForwardSuggestionId(
                    orgId, userId, item.sourceCommitId());
            if (recentlyDeclined.contains(suggestionId)) {
                continue;
            }
            // Older carry-forward items get slightly higher confidence to signal urgency
            double confidence = Math.min(1.0,
                    CARRY_FORWARD_BASE_CONFIDENCE + (item.carryForwardWeeks() - 1) * 0.05);
            String sourceDetail = "Carried from week of " + item.sourceWeekStart();
            String rationale = buildCarryForwardRationale(item);
            out.add(new NextWorkSuggestion(
                    suggestionId,
                    item.title(),
                    item.outcomeId(),
                    item.chessPriority(),
                    confidence,
                    "CARRY_FORWARD",
                    sourceDetail,
                    rationale,
                    null,
                    null
            ));
        }
    }

    private String buildCarryForwardRationale(CarryForwardItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("This item was not completed in the ");
        if (item.carryForwardWeeks() == 1) {
            sb.append("previous week");
        } else {
            sb.append("last ").append(item.carryForwardWeeks()).append(" weeks");
        }
        if (item.outcomeName() != null) {
            sb.append(" and is linked to outcome \"").append(item.outcomeName()).append("\"");
        }
        sb.append(".");
        return sb.toString();
    }

    // ── Coverage-gap suggestion building ─────────────────────────────────────

    private void addCoverageGapSuggestions(
            UUID orgId,
            UUID userId,
            List<RcdoCoverageGap> gaps,
            Set<UUID> recentlyDeclined,
            List<NextWorkSuggestion> out
    ) {
        for (RcdoCoverageGap gap : gaps) {
            UUID suggestionId = buildCoverageGapSuggestionId(orgId, userId, gap.outcomeId());
            if (recentlyDeclined.contains(suggestionId)) {
                continue;
            }
            // Longer gaps get higher confidence; tracked urgent outcomes get an extra boost
            double confidence = Math.min(1.0,
                    (COVERAGE_GAP_BASE_CONFIDENCE + (gap.weeksMissing() - MIN_GAP_WEEKS) * 0.05)
                            * resolveUrgencyMultiplier(orgId, gap.outcomeId()));
            String sourceDetail = "Outcome \"" + gap.outcomeName()
                    + "\" has had 0 team commits for " + gap.weeksMissing() + " weeks";
            String rationale = buildCoverageGapRationale(gap);
            out.add(new NextWorkSuggestion(
                    suggestionId,
                    "Consider contributing to: " + gap.outcomeName(),
                    gap.outcomeId(),
                    null,  // no chess priority suggestion for coverage gaps in Phase 1
                    confidence,
                    "COVERAGE_GAP",
                    sourceDetail,
                    rationale,
                    null,
                    null
            ));
        }
    }

    private String buildCoverageGapRationale(RcdoCoverageGap gap) {
        StringBuilder sb = new StringBuilder();
        sb.append("The team has not committed to \"")
                .append(gap.outcomeName())
                .append("\" (part of ")
                .append(gap.rallyCryName())
                .append(") for ")
                .append(gap.weeksMissing())
                .append(gap.weeksMissing() == 1 ? " week" : " weeks");
        sb.append(". This outcome previously had ")
                .append(gap.teamCommitsPrevWindow())
                .append(" team ")
                .append(gap.teamCommitsPrevWindow() == 1 ? "commit" : "commits")
                .append(" in the prior 8-week window.");
        return sb.toString();
    }

    private double resolveUrgencyMultiplier(UUID orgId, String outcomeId) {
        UUID parsedOutcomeId = parseUuid(outcomeId);
        if (parsedOutcomeId == null) {
            return 1.0;
        }

        try {
            UrgencyInfo urgency = urgencyDataProvider.getOutcomeUrgency(orgId, parsedOutcomeId);
            if (urgency == null || urgency.urgencyBand() == null) {
                return 1.0;
            }
            return switch (urgency.urgencyBand()) {
                case "CRITICAL" -> CRITICAL_URGENCY_MULTIPLIER;
                case "AT_RISK" -> AT_RISK_URGENCY_MULTIPLIER;
                case "NEEDS_ATTENTION" -> NEEDS_ATTENTION_URGENCY_MULTIPLIER;
                default -> 1.0;
            };
        } catch (Exception e) {
            LOG.debug("Could not fetch urgency for outcome {}: {}", outcomeId, e.getMessage());
            return 1.0;
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── External ticket suggestion building (Phase 3) ─────────────────────────

    /**
     * Adds {@code EXTERNAL_TICKET} suggestions derived from unresolved tickets
     * linked to the user's RCDO-strategic commits.
     *
     * <p>Each unique ticket that is not suppressed by a recent DECLINE feedback
     * is surfaced as a suggestion.  Tickets without a synced status receive a
     * slightly lower confidence than tickets with a known non-resolved status
     * (which implies the link was synced recently).
     */
    private void addExternalTicketSuggestions(
            UUID orgId,
            UUID userId,
            List<UserTicketContext> tickets,
            Set<UUID> recentlyDeclined,
            List<NextWorkSuggestion> out
    ) {
        for (UserTicketContext ticket : tickets) {
            UUID suggestionId = buildExternalTicketSuggestionId(
                    orgId, userId, ticket.externalTicketId(), ticket.provider());
            if (recentlyDeclined.contains(suggestionId)) {
                continue;
            }
            // Tickets with a known (non-resolved) status get slightly higher confidence
            double confidence = ticket.externalStatus() != null && !ticket.externalStatus().isBlank()
                    ? EXTERNAL_TICKET_BASE_CONFIDENCE
                    : EXTERNAL_TICKET_BASE_CONFIDENCE - 0.05;
            String sourceDetail = buildExternalTicketSourceDetail(ticket);
            String rationale = buildExternalTicketRationale(ticket);
            String title = buildExternalTicketTitle(ticket);
            out.add(new NextWorkSuggestion(
                    suggestionId,
                    title,
                    ticket.outcomeId(),
                    null,
                    confidence,
                    "EXTERNAL_TICKET",
                    sourceDetail,
                    rationale,
                    ticket.externalTicketUrl(),
                    ticket.externalStatus()
            ));
        }
    }

    private String buildExternalTicketTitle(UserTicketContext ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("Work on ").append(ticket.provider()).append(" ticket: ")
                .append(ticket.externalTicketId());
        return sb.toString();
    }

    private String buildExternalTicketSourceDetail(UserTicketContext ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(ticket.provider()).append("] ")
                .append(ticket.externalTicketId());
        if (ticket.externalStatus() != null && !ticket.externalStatus().isBlank()) {
            sb.append(" — ").append(ticket.externalStatus());
        }
        if (ticket.lastSyncedAt() != null) {
            sb.append(" — updated ")
                    .append(ticket.lastSyncedAt().atOffset(java.time.ZoneOffset.UTC).toLocalDate());
        }
        return sb.toString();
    }

    private String buildExternalTicketRationale(UserTicketContext ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("External ticket ").append(ticket.externalTicketId())
                .append(" (").append(ticket.provider()).append(")");
        if (ticket.externalStatus() != null && !ticket.externalStatus().isBlank()) {
            sb.append(" is currently in status \"").append(ticket.externalStatus()).append("\"");
        } else {
            sb.append(" has an unknown status");
        }
        if (ticket.outcomeName() != null) {
            sb.append(" and is linked to outcome \"").append(ticket.outcomeName()).append("\"");
        }
        sb.append(".");
        if (ticket.lastSyncedAt() != null) {
            sb.append(" Last updated: ")
                    .append(ticket.lastSyncedAt().atOffset(java.time.ZoneOffset.UTC).toLocalDate());
        }
        return sb.toString();
    }

    private List<UserTicketContext> dedupeLinkedTickets(List<UserTicketContext> tickets) {
        if (tickets.size() <= 1) {
            return tickets;
        }

        Map<String, UserTicketContext> uniqueByTicket = new java.util.LinkedHashMap<>();
        tickets.stream()
                .sorted(Comparator.comparing(
                        UserTicketContext::lastSyncedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(ticket -> uniqueByTicket.putIfAbsent(
                        buildExternalTicketKey(ticket.provider(), ticket.externalTicketId()),
                        ticket));
        return List.copyOf(uniqueByTicket.values());
    }

    private String buildExternalTicketKey(String provider, String externalTicketId) {
        return (provider == null ? "" : provider.toUpperCase(java.util.Locale.ROOT))
                + ":" + (externalTicketId == null ? "" : externalTicketId);
    }

    /**
     * Fetches the user's external tickets for Phase 3 enrichment.
     * On error, returns an empty list so the pipeline continues without tickets.
     */
    private List<UserTicketContext> fetchLinkedTickets(
            UUID orgId, UUID userId, LocalDate asOf) {
        try {
            return integrationService.getUnresolvedTicketsForUser(
                    orgId, userId, asOf, EXTERNAL_TICKET_LOOKBACK_WEEKS);
        } catch (Exception e) {
            LOG.debug("Could not fetch external ticket context: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Decline-filter helper ─────────────────────────────────────────────────

    private Set<UUID> loadDeclinedIds(UUID orgId, UUID userId, Instant since) {
        return feedbackRepository
                .findByOrgIdAndUserIdAndCreatedAtAfter(orgId, userId, since)
                .stream()
                .filter(f -> "DECLINE".equals(f.getAction()))
                .map(AiSuggestionFeedbackEntity::getSuggestionId)
                .collect(Collectors.toSet());
    }

    // ── Deterministic suggestion ID generation ────────────────────────────────

    /**
     * Generates a deterministic UUID for a carry-forward suggestion based on
     * the org, user, and source commit ID.
     *
     * <p>Using type-3 (name-based) UUIDs ensures the same suggestion always
     * produces the same ID for feedback correlation, regardless of when it is
     * generated.
     */
    static UUID buildCarryForwardSuggestionId(UUID orgId, UUID userId, UUID sourceCommitId) {
        String name = "CARRY_FORWARD:" + orgId + ":" + userId + ":" + sourceCommitId;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a deterministic UUID for a coverage-gap suggestion based on
     * the org, user, and RCDO outcome ID.
     */
    static UUID buildCoverageGapSuggestionId(UUID orgId, UUID userId, String outcomeId) {
        String name = "COVERAGE_GAP:" + orgId + ":" + userId + ":" + outcomeId;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a deterministic UUID for an external-ticket suggestion based on
     * the org, user, provider, and ticket ID.
     *
     * <p>Using type-3 (name-based) UUIDs ensures the same suggestion always
     * produces the same ID for feedback correlation.
     */
    static UUID buildExternalTicketSuggestionId(
            UUID orgId, UUID userId, String externalTicketId, String provider) {
        String name = "EXTERNAL_TICKET:" + orgId + ":" + userId
                + ":" + provider.toUpperCase() + ":" + externalTicketId;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
