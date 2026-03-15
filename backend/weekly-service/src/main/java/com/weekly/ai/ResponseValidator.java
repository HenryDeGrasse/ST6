package com.weekly.ai;

import java.util.ArrayList;
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
