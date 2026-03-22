package com.weekly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.issues.domain.EffortType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI-powered effort type suggestion for issue creation (Phase 6, Step 9).
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Check cache (title + description hash)</li>
 *   <li>Attempt LLM classification into BUILD/MAINTAIN/COLLABORATE/LEARN</li>
 *   <li>If confidence &lt; 0.6, return no suggestion</li>
 *   <li>On LLM unavailability, fall back to keyword-based heuristic</li>
 *   <li>Cache and return</li>
 * </ol>
 *
 * <p>Returns {@link SuggestionResult} with {@code status: "ok"} and a suggestion,
 * {@code status: "ok"} with no suggestion when confidence is too low,
 * or {@code status: "unavailable"} when the fallback also produces no result.
 */
@Service
public class AiEffortTypeSuggestionService {

    private static final Logger LOG = LoggerFactory.getLogger(AiEffortTypeSuggestionService.class);

    /** Below this threshold the service returns no suggestion. */
    static final double CONFIDENCE_THRESHOLD = 0.6;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient llmClient;
    private final AiCacheService cacheService;

    public AiEffortTypeSuggestionService(LlmClient llmClient, AiCacheService cacheService) {
        this.llmClient = llmClient;
        this.cacheService = cacheService;
    }

    /**
     * Suggests an effort type for the given issue.
     *
     * @param title       issue title (required)
     * @param description issue description (may be null)
     * @param outcomeId   optional RCDO outcome ID for additional context
     * @return suggestion result — never null
     */
    public SuggestionResult suggest(String title, String description, String outcomeId) {
        String cacheKey = buildCacheKey(title, description, outcomeId);
        Optional<SuggestionResult> cached = cacheService.get(cacheKey, SuggestionResult.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        SuggestionResult result = doSuggest(title, description, outcomeId);
        cacheService.put(cacheKey, result);
        return result;
    }

    private SuggestionResult doSuggest(String title, String description, String outcomeId) {
        try {
            List<LlmClient.Message> messages =
                    PromptBuilder.buildEffortTypeSuggestMessages(title, description, outcomeId);
            String raw = llmClient.complete(messages, PromptBuilder.effortTypeSuggestResponseSchema());
            return parseResponse(raw);
        } catch (LlmClient.LlmUnavailableException ex) {
            LOG.debug("LLM unavailable for effort type suggestion, using keyword fallback: {}", ex.getMessage());
            SuggestionResult fallback = keywordFallback(title, description);
            return fallback.suggestedType() != null ? fallback : SuggestionResult.unavailable();
        } catch (Exception ex) {
            LOG.warn("Unexpected error during effort type suggestion, using keyword fallback", ex);
            return keywordFallback(title, description);
        }
    }

    // ─── Response parsing ──────────────────────────────────────────────────────

    private SuggestionResult parseResponse(String raw) {
        try {
            JsonNode node = MAPPER.readTree(raw);
            JsonNode typeNode = node.path("effortType");
            JsonNode confidenceNode = node.path("confidence");

            if (typeNode.isMissingNode() || confidenceNode.isMissingNode()) {
                LOG.debug("LLM effort-type response missing required fields, no suggestion");
                return SuggestionResult.noSuggestion();
            }

            double confidence = confidenceNode.asDouble();
            if (confidence < CONFIDENCE_THRESHOLD) {
                LOG.debug("LLM effort-type confidence {} below threshold {}, no suggestion",
                        confidence, CONFIDENCE_THRESHOLD);
                return SuggestionResult.noSuggestion();
            }

            String effortTypeStr = typeNode.asText().toUpperCase(Locale.ROOT);
            EffortType effortType;
            try {
                effortType = EffortType.valueOf(effortTypeStr);
            } catch (IllegalArgumentException e) {
                LOG.debug("LLM returned unknown effort type '{}', no suggestion", effortTypeStr);
                return SuggestionResult.noSuggestion();
            }

            return SuggestionResult.of(effortType, confidence);
        } catch (Exception ex) {
            LOG.debug("Failed to parse LLM effort-type response, no suggestion", ex);
            return SuggestionResult.noSuggestion();
        }
    }

    // ─── Keyword heuristic fallback ────────────────────────────────────────────

    /**
     * Keyword-based heuristic that classifies effort type when the LLM is unavailable.
     *
     * <p>Applies simple word-matching on the combined title + description text.
     * Returns no suggestion when no keywords match (confidence below threshold).
     */
    SuggestionResult keywordFallback(String title, String description) {
        String text = ((title != null ? title : "") + " " + (description != null ? description : ""))
                .toLowerCase(Locale.ROOT);

        // BUILD keywords — creating new things
        if (matchesAny(text, BUILD_KEYWORDS)) {
            return SuggestionResult.of(EffortType.BUILD, 0.65);
        }

        // MAINTAIN keywords — keeping things running
        if (matchesAny(text, MAINTAIN_KEYWORDS)) {
            return SuggestionResult.of(EffortType.MAINTAIN, 0.65);
        }

        // COLLABORATE keywords — working with others
        if (matchesAny(text, COLLABORATE_KEYWORDS)) {
            return SuggestionResult.of(EffortType.COLLABORATE, 0.65);
        }

        // LEARN keywords — investing in growth
        if (matchesAny(text, LEARN_KEYWORDS)) {
            return SuggestionResult.of(EffortType.LEARN, 0.65);
        }

        return SuggestionResult.noSuggestion();
    }

    private static final List<String> BUILD_KEYWORDS = List.of(
            "build ", "implement", "create ", "develop", "launch", "ship ",
            "deploy ", "scaffold", "generate", "infrastructure", "pipeline", " api",
            "new endpoint", "new feature", "new module", "new service", "new component",
            "refactor", "migrate", "init "
    );

    private static final List<String> MAINTAIN_KEYWORDS = List.of(
            "fix ", "bug", "incident", "outage", "patch ", "hotfix", " ops", "operations",
            "monitor", "alert", "on-call", "oncall", "remediate", "triage",
            "tech debt", "technical debt", "upgrade", "maintain", "maintenance",
            "backup", " cve", "vulnerability", "optimize", "cleanup", "clean up",
            "deprecate", "test failure"
    );

    private static final List<String> COLLABORATE_KEYWORDS = List.of(
            "review", "meeting", "sync", "interview", "hire", "hiring", "onboard",
            "mentor", "feedback", "retro", "retrospective", "standup",
            "stand-up", "1:1", "one-on-one", "collaborate", "coordination", "customer",
            "client", "stakeholder", "align", "present", "demo",
            "workshop", "pair", "pairing", "pr review", "code review"
    );

    private static final List<String> LEARN_KEYWORDS = List.of(
            "learn", "study", "research", "spike:", "spike ", " poc", "proof of concept",
            "explore", "investigate", "experiment", "training", "course", "reading",
            "blog", "conference", "deep dive", "evaluate", "assess",
            "documentation", "knowledge", "discovery"
    );

    private static boolean matchesAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    // ─── Cache key ─────────────────────────────────────────────────────────────

    static String buildCacheKey(String title, String description, String outcomeId) {
        String raw = (title != null ? title : "")
                + ":"
                + (description != null ? description : "")
                + ":"
                + (outcomeId != null ? outcomeId : "");
        return "ai:effort-type:" + sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ─── Result type ───────────────────────────────────────────────────────────

    /**
     * Result of an effort type suggestion attempt.
     *
     * @param status        "ok" or "unavailable"
     * @param suggestedType the suggested effort type, or {@code null} when confidence is too low
     * @param confidence    confidence score 0.0–1.0, or {@code null} when no suggestion
     */
    public record SuggestionResult(String status, EffortType suggestedType, Double confidence) {

        /** Successful suggestion with type and confidence. */
        static SuggestionResult of(EffortType type, double confidence) {
            return new SuggestionResult("ok", type, confidence);
        }

        /** No suggestion — below confidence threshold or insufficient signal. */
        static SuggestionResult noSuggestion() {
            return new SuggestionResult("ok", null, null);
        }

        /** LLM unavailable and keyword fallback also produced no result. */
        static SuggestionResult unavailable() {
            return new SuggestionResult("unavailable", null, null);
        }
    }
}
