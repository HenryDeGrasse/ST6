package com.weekly.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates LLM responses against the expected schema and candidate set.
 *
 * <p>Per PRD §4: LLM can only select from RCDO IDs provided in the prompt
 * context. Free-text IDs are rejected by schema validation. This eliminates
 * hallucinated entities.
 */
public final class ResponseValidator {

    private ResponseValidator() {}

    private static final Pattern SUGGESTIONS_PATTERN =
            Pattern.compile("\"suggestions\"\\s*:\\s*\\[([^\\]]*)]", Pattern.DOTALL);
    private static final Pattern OUTCOME_ID_PATTERN =
            Pattern.compile("\"outcomeId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONFIDENCE_PATTERN =
            Pattern.compile("\"confidence\"\\s*:\\s*([\\d.]+)");
    private static final Pattern RATIONALE_PATTERN =
            Pattern.compile("\"rationale\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RALLY_CRY_PATTERN =
            Pattern.compile("\"rallyCryName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OBJECTIVE_PATTERN =
            Pattern.compile("\"objectiveName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OUTCOME_NAME_PATTERN =
            Pattern.compile("\"outcomeName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HEADLINE_PATTERN =
            Pattern.compile("\"headline\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern INSIGHTS_PATTERN =
            Pattern.compile("\"insights\"\\s*:\\s*\\[([^\\]]*)]", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DETAIL_PATTERN =
            Pattern.compile("\"detail\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SEVERITY_PATTERN =
            Pattern.compile("\"severity\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Parses and validates RCDO suggestion response from LLM.
     *
     * @param rawResponse     the raw LLM response text
     * @param validOutcomeIds the set of valid outcome IDs from the candidate set
     * @return validated suggestions (may be empty if validation fails)
     */
    public static List<AiSuggestionService.RcdoSuggestion> validateRcdoSuggestions(
            String rawResponse,
            Set<String> validOutcomeIds
    ) {
        List<AiSuggestionService.RcdoSuggestion> results = new ArrayList<>();

        if (rawResponse == null || rawResponse.isBlank()) {
            return results;
        }

        // Extract suggestions array content
        Matcher sugMatcher = SUGGESTIONS_PATTERN.matcher(rawResponse);
        if (!sugMatcher.find()) {
            return results;
        }

        // Split the suggestions array into individual objects
        String suggestionsContent = sugMatcher.group(0);
        // Find all individual suggestion objects
        Pattern objPattern = Pattern.compile("\\{([^}]+)}", Pattern.DOTALL);
        Matcher objMatcher = objPattern.matcher(suggestionsContent);

        while (objMatcher.find()) {
            String objStr = objMatcher.group(0);
            AiSuggestionService.RcdoSuggestion suggestion = parseSuggestion(objStr, validOutcomeIds);
            if (suggestion != null) {
                results.add(suggestion);
            }
        }

        // Sort by confidence descending
        results.sort((a, b) -> Double.compare(b.confidence(), a.confidence()));

        // Limit to 5 suggestions
        if (results.size() > 5) {
            return results.subList(0, 5);
        }

        return results;
    }

    /**
     * Parses and validates reconciliation draft items from LLM.
     */
    public static List<AiSuggestionService.ReconciliationDraftItem> validateReconciliationDraft(
            String rawResponse,
            Set<String> validCommitIds
    ) {
        List<AiSuggestionService.ReconciliationDraftItem> results = new ArrayList<>();

        if (rawResponse == null || rawResponse.isBlank()) {
            return results;
        }

        Pattern draftsPattern = Pattern.compile("\"drafts\"\\s*:\\s*\\[([^\\]]*)]", Pattern.DOTALL);
        Matcher draftsMatcher = draftsPattern.matcher(rawResponse);
        if (!draftsMatcher.find()) {
            return results;
        }

        String draftsContent = draftsMatcher.group(0);
        Pattern objPattern = Pattern.compile("\\{([^}]+)}", Pattern.DOTALL);
        Matcher objMatcher = objPattern.matcher(draftsContent);

        Pattern commitIdPat = Pattern.compile("\"commitId\"\\s*:\\s*\"([^\"]+)\"");
        Pattern statusPat = Pattern.compile("\"suggestedStatus\"\\s*:\\s*\"([^\"]+)\"");
        Pattern deltaPat = Pattern.compile("\"suggestedDeltaReason\"\\s*:\\s*\"([^\"]+)\"");
        Pattern actualPat = Pattern.compile("\"suggestedActualResult\"\\s*:\\s*\"([^\"]+)\"");
        Set<String> validStatuses = Set.of("DONE", "PARTIALLY", "NOT_DONE", "DROPPED");

        while (objMatcher.find()) {
            String objStr = objMatcher.group(0);
            String commitId = extractField(commitIdPat, objStr);
            String status = extractField(statusPat, objStr);
            String deltaReason = extractField(deltaPat, objStr);
            String actualResult = extractField(actualPat, objStr);

            if (commitId != null && validCommitIds.contains(commitId)
                    && status != null && validStatuses.contains(status)
                    && actualResult != null) {
                results.add(new AiSuggestionService.ReconciliationDraftItem(
                        commitId, status, deltaReason, actualResult
                ));
            }
        }

        return results;
    }

    /**
     * Parses and validates manager insight summary responses from the LLM.
     */
    public static AiSuggestionService.ManagerInsightsResult validateManagerInsights(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return AiSuggestionService.ManagerInsightsResult.unavailable();
        }

        String headline = extractField(HEADLINE_PATTERN, rawResponse);
        if (headline == null || headline.isBlank()) {
            return AiSuggestionService.ManagerInsightsResult.unavailable();
        }

        Matcher insightsMatcher = INSIGHTS_PATTERN.matcher(rawResponse);
        if (!insightsMatcher.find()) {
            return new AiSuggestionService.ManagerInsightsResult("ok", headline, List.of());
        }

        List<AiSuggestionService.ManagerInsight> insights = new ArrayList<>();
        Pattern objPattern = Pattern.compile("\\{([^}]+)}", Pattern.DOTALL);
        Matcher objMatcher = objPattern.matcher(insightsMatcher.group(0));
        Set<String> validSeverities = Set.of("INFO", "WARNING", "POSITIVE");

        while (objMatcher.find()) {
            String objStr = objMatcher.group(0);
            String title = extractField(TITLE_PATTERN, objStr);
            String detail = extractField(DETAIL_PATTERN, objStr);
            String severity = extractField(SEVERITY_PATTERN, objStr);

            if (title != null && detail != null && severity != null && validSeverities.contains(severity)) {
                insights.add(new AiSuggestionService.ManagerInsight(title, detail, severity));
            }
        }

        return new AiSuggestionService.ManagerInsightsResult("ok", headline, insights);
    }

    // ── Next-work suggestion ranking ─────────────────────────────────────────

    private static final Set<String> VALID_CHESS_PRIORITIES =
            Set.of("KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN");

    private static final Pattern RANKED_SUGGESTIONS_PATTERN =
            Pattern.compile("\"rankedSuggestions\"\\s*:\\s*\\[", Pattern.DOTALL);
    private static final Pattern SUGGESTION_ID_PATTERN =
            Pattern.compile("\"suggestionId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CHESS_PRIORITY_PATTERN =
            Pattern.compile("\"suggestedChessPriority\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Parses and validates LLM re-ranking responses for next-work suggestions.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>Each {@code suggestionId} must be present in {@code validSuggestionIds}
     *       (prevents hallucinated entries)</li>
     *   <li>{@code confidence} must be in [0.0, 1.0]</li>
     *   <li>{@code suggestedChessPriority} must be a valid enum value if present</li>
     *   <li>{@code rationale} must be non-blank</li>
     * </ul>
     *
     * @param rawResponse        raw LLM response text
     * @param validSuggestionIds UUID strings from the Phase-1 candidate set
     * @return validated ranked items; may be empty if validation fails or IDs are hallucinated
     */
    public static List<NextWorkRankedItem> validateNextWorkSuggestions(
            String rawResponse,
            Set<String> validSuggestionIds
    ) {
        List<NextWorkRankedItem> results = new ArrayList<>();

        if (rawResponse == null || rawResponse.isBlank()) {
            return results;
        }

        // Locate the rankedSuggestions array
        Matcher startMatcher = RANKED_SUGGESTIONS_PATTERN.matcher(rawResponse);
        if (!startMatcher.find()) {
            return results;
        }

        // Extract everything after "rankedSuggestions": [
        String afterBracket = rawResponse.substring(startMatcher.end());

        // Find all individual suggestion objects using a brace-depth parser
        List<String> objectStrings = extractJsonObjects(afterBracket);
        Set<String> seenSuggestionIds = new HashSet<>();

        for (String objStr : objectStrings) {
            NextWorkRankedItem item = parseNextWorkRankedItem(objStr, validSuggestionIds);
            if (item != null && seenSuggestionIds.add(item.suggestionId())) {
                results.add(item);
            }
        }

        return results;
    }

    /**
     * Extracts JSON object strings from the beginning of a string that starts
     * immediately after a {@code [} bracket. Stops at the matching {@code ]}.
     */
    private static List<String> extractJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        int i = 0;
        int len = content.length();
        while (i < len) {
            // Skip whitespace and commas
            while (i < len && (content.charAt(i) == ' ' || content.charAt(i) == '\n'
                    || content.charAt(i) == '\r' || content.charAt(i) == '\t'
                    || content.charAt(i) == ',')) {
                i++;
            }
            if (i >= len || content.charAt(i) != '{') {
                break; // No more objects (reached ] or end)
            }
            // Walk to end of this object
            int depth = 0;
            int start = i;
            while (i < len) {
                char c = content.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        objects.add(content.substring(start, i + 1));
                        i++;
                        break;
                    }
                } else if (c == '"') {
                    // Skip string literal to avoid counting braces inside strings
                    i++;
                    while (i < len && content.charAt(i) != '"') {
                        if (content.charAt(i) == '\\') {
                            i++; // skip escaped char
                        }
                        i++;
                    }
                }
                i++;
            }
        }
        return objects;
    }

    private static NextWorkRankedItem parseNextWorkRankedItem(
            String objStr, Set<String> validSuggestionIds) {

        String suggestionId = extractField(SUGGESTION_ID_PATTERN, objStr);
        String confidenceStr = extractField(CONFIDENCE_PATTERN, objStr);
        String chessPriority = extractField(CHESS_PRIORITY_PATTERN, objStr);
        String rationale = extractField(RATIONALE_PATTERN, objStr);

        // Required fields
        if (suggestionId == null || confidenceStr == null || rationale == null
                || rationale.isBlank()) {
            return null;
        }

        // suggestionId must be in the valid candidate set
        if (!validSuggestionIds.contains(suggestionId)) {
            return null;
        }

        // confidence must be in [0.0, 1.0]
        double confidence;
        try {
            confidence = Double.parseDouble(confidenceStr);
            if (confidence < 0.0 || confidence > 1.0) {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }

        // chessPriority must be a valid enum value when present
        if (chessPriority != null && !VALID_CHESS_PRIORITIES.contains(chessPriority)) {
            chessPriority = null; // silently drop invalid value rather than reject the item
        }

        return new NextWorkRankedItem(suggestionId, confidence, chessPriority, rationale);
    }

    /**
     * A single validated item from the LLM next-work re-ranking response.
     *
     * @param suggestionId          UUID string from the Phase-1 candidate set
     * @param confidence            re-ranked confidence in [0.0, 1.0]
     * @param suggestedChessPriority chess priority recommendation; {@code null} if not provided
     *                               or the LLM returned an invalid value
     * @param rationale             LLM-generated strategic rationale
     */
    public record NextWorkRankedItem(
            String suggestionId,
            double confidence,
            String suggestedChessPriority,
            String rationale
    ) {}

    private static AiSuggestionService.RcdoSuggestion parseSuggestion(
            String objStr, Set<String> validOutcomeIds) {
        String outcomeId = extractField(OUTCOME_ID_PATTERN, objStr);
        String rallyCryName = extractField(RALLY_CRY_PATTERN, objStr);
        String objectiveName = extractField(OBJECTIVE_PATTERN, objStr);
        String outcomeName = extractField(OUTCOME_NAME_PATTERN, objStr);
        String confidenceStr = extractField(CONFIDENCE_PATTERN, objStr);
        String rationale = extractField(RATIONALE_PATTERN, objStr);

        // Validate all required fields are present
        if (outcomeId == null || rallyCryName == null || objectiveName == null
                || outcomeName == null || confidenceStr == null || rationale == null) {
            return null;
        }

        // Validate outcome ID is in the valid set (prevents hallucinated entities)
        if (!validOutcomeIds.contains(outcomeId)) {
            return null;
        }

        // Validate confidence is a valid number in range
        double confidence;
        try {
            confidence = Double.parseDouble(confidenceStr);
            if (confidence < 0 || confidence > 1) {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return new AiSuggestionService.RcdoSuggestion(
                outcomeId, rallyCryName, objectiveName, outcomeName, confidence, rationale
        );
    }

    private static String extractField(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
