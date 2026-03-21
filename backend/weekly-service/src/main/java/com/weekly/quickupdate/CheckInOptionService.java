package com.weekly.quickupdate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.LlmClient;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.usermodel.TeamPatternService;
import com.weekly.usermodel.UserModelService;
import com.weekly.usermodel.UserProfileResponse;
import com.weekly.usermodel.UserUpdatePatternEntity;
import com.weekly.usermodel.UserUpdatePatternService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that generates personalised check-in option suggestions for the Quick
 * Update card.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Fetch the commit and verify it belongs to the requesting org.</li>
 *   <li>Load the user's learned note history for the commit category.</li>
 *   <li>Top up with org-level team-common patterns when personal history is sparse.</li>
 *   <li>Load the latest user-model snapshot for prompt enrichment.</li>
 *   <li>Build a structured prompt using {@link CheckInOptionPromptBuilder}.</li>
 *   <li>Call the LLM and merge deterministic seeded options with AI-generated remainder.</li>
 *   <li>On any failure (LLM unavailable, parse error, commit not found), return
 *       {@link CheckInOptionsResponse#empty()} — per PRD §4 fallback contract.</li>
 * </ol>
 */
@Service
public class CheckInOptionService {

    private static final Logger LOG = LoggerFactory.getLogger(CheckInOptionService.class);

    /** Maximum number of personal/team patterns to fetch. */
    private static final int TOP_PATTERNS_LIMIT = 5;

    /** Maximum number of progress options returned to the client. */
    private static final int MAX_PROGRESS_OPTIONS = 5;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final WeeklyCommitRepository commitRepository;
    private final UserUpdatePatternService userUpdatePatternService;
    private final TeamPatternService teamPatternService;
    private final UserModelService userModelService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public CheckInOptionService(
            WeeklyCommitRepository commitRepository,
            UserUpdatePatternService userUpdatePatternService,
            TeamPatternService teamPatternService,
            UserModelService userModelService,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.commitRepository = commitRepository;
        this.userUpdatePatternService = userUpdatePatternService;
        this.teamPatternService = teamPatternService;
        this.userModelService = userModelService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates contextual check-in options for the given commit.
     */
    public CheckInOptionsResponse generateOptions(
            UUID orgId,
            UUID userId,
            UUID commitId,
            String currentStatus,
            String lastNote,
            int daysSinceLastCheckIn
    ) {
        Optional<WeeklyCommitEntity> commitOpt = commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId));

        if (commitOpt.isEmpty()) {
            LOG.warn("Commit not found or org mismatch: commitId={}, orgId={}", commitId, orgId);
            return CheckInOptionsResponse.empty();
        }

        WeeklyCommitEntity commit = commitOpt.get();
        String title = commit.getTitle();
        String category = commit.getCategory() != null ? commit.getCategory().name() : null;
        String chessPriority = commit.getChessPriority() != null
                ? commit.getChessPriority().name()
                : null;
        String outcomeName = commit.getSnapshotOutcomeName();

        List<String> userPatterns = collectDistinctOptionTexts(
                userUpdatePatternService.getTopPatterns(orgId, userId, category, TOP_PATTERNS_LIMIT).stream()
                        .map(UserUpdatePatternEntity::getNoteText)
                        .toList(),
                TOP_PATTERNS_LIMIT,
                List.<String>of()
        );

        int remainingTeamSlots = Math.max(0, TOP_PATTERNS_LIMIT - userPatterns.size());
        List<String> teamPatterns = remainingTeamSlots == 0
                ? List.<String>of()
                : collectDistinctOptionTexts(
                        teamPatternService.getTopPatterns(orgId, category, TOP_PATTERNS_LIMIT),
                        remainingTeamSlots,
                        userPatterns
                );

        String userModelSummary = userModelService.getSnapshot(orgId, userId)
                .map(profile -> summarizeUserModel(profile, category))
                .orElse(null);

        List<LlmClient.Message> messages = CheckInOptionPromptBuilder.buildCheckInOptionMessages(
                title,
                category,
                chessPriority,
                currentStatus,
                lastNote,
                daysSinceLastCheckIn,
                userPatterns,
                teamPatterns,
                outcomeName,
                userModelSummary
        );

        try {
            String rawResponse = llmClient.complete(messages, CheckInOptionPromptBuilder.RESPONSE_SCHEMA);
            return parseAndMergeResponse(rawResponse, userPatterns, teamPatterns);
        } catch (LlmClient.LlmUnavailableException ex) {
            LOG.warn("LLM unavailable while generating check-in options for commit {}: {}",
                    commitId, ex.getMessage());
            return fallbackResponse(userPatterns, teamPatterns);
        } catch (Exception ex) {
            LOG.warn("Failed to parse check-in options response for commit {}: {}",
                    commitId, ex.getMessage());
            return fallbackResponse(userPatterns, teamPatterns);
        }
    }

    private CheckInOptionsResponse parseAndMergeResponse(
            String rawResponse,
            List<String> userPatterns,
            List<String> teamPatterns
    ) throws Exception {
        List<String> statusOptions = new ArrayList<>();
        List<String> aiOptions = new ArrayList<>();

        if (rawResponse != null && !rawResponse.isBlank()) {
            JsonNode root = objectMapper.readTree(rawResponse);

            JsonNode statusNode = root.get("statusOptions");
            if (statusNode != null && statusNode.isArray()) {
                for (JsonNode element : statusNode) {
                    if (element.isTextual()) {
                        statusOptions.add(element.asText());
                    }
                }
            }

            JsonNode progressNode = root.get("progressOptions");
            if (progressNode != null && progressNode.isArray()) {
                for (JsonNode element : progressNode) {
                    JsonNode textNode = element.get("text");
                    if (textNode != null && textNode.isTextual()) {
                        aiOptions.add(textNode.asText());
                    }
                }
            }
        }

        if (statusOptions.isEmpty()) {
            statusOptions = CheckInOptionsResponse.empty().statusOptions();
        }

        return new CheckInOptionsResponse(
                "ok",
                statusOptions,
                mergeProgressOptions(userPatterns, teamPatterns, aiOptions)
        );
    }

    private CheckInOptionsResponse fallbackResponse(List<String> userPatterns, List<String> teamPatterns) {
        return new CheckInOptionsResponse(
                "ok",
                CheckInOptionsResponse.empty().statusOptions(),
                mergeProgressOptions(userPatterns, teamPatterns, List.of())
        );
    }

    private List<CheckInOptionItem> mergeProgressOptions(
            List<String> userPatterns,
            List<String> teamPatterns,
            List<String> aiOptions
    ) {
        Map<String, CheckInOptionItem> deduped = new LinkedHashMap<>();

        addSeededOptions(deduped, userPatterns, "user_history");
        addSeededOptions(deduped, teamPatterns, "team_common");
        addSeededOptions(deduped, aiOptions, "ai_generated");

        return deduped.values().stream()
                .limit(MAX_PROGRESS_OPTIONS)
                .toList();
    }

    private void addSeededOptions(
            Map<String, CheckInOptionItem> deduped,
            List<String> texts,
            String source
    ) {
        if (texts == null || texts.isEmpty()) {
            return;
        }

        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String normalizedKey = normalizeOptionKey(text);
            if (normalizedKey.isBlank()) {
                continue;
            }
            deduped.putIfAbsent(normalizedKey, new CheckInOptionItem(normalizeOptionText(text), source));
        }
    }

    private List<String> collectDistinctOptionTexts(
            List<String> texts,
            int limit,
            List<String> existingTexts
    ) {
        if (limit <= 0 || texts == null || texts.isEmpty()) {
            return List.of();
        }

        Map<String, String> deduped = new LinkedHashMap<>();
        if (existingTexts != null) {
            for (String existingText : existingTexts) {
                String normalizedKey = normalizeOptionKey(existingText);
                if (!normalizedKey.isBlank()) {
                    deduped.put(normalizedKey, normalizeOptionText(existingText));
                }
            }
        }

        List<String> results = new ArrayList<>();
        for (String text : texts) {
            String normalizedKey = normalizeOptionKey(text);
            if (normalizedKey.isBlank() || deduped.containsKey(normalizedKey)) {
                continue;
            }
            String normalizedText = normalizeOptionText(text);
            deduped.put(normalizedKey, normalizedText);
            results.add(normalizedText);
            if (results.size() == limit) {
                break;
            }
        }
        return results;
    }

    private String normalizeOptionKey(String text) {
        return normalizeOptionText(text).toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionText(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    private String summarizeUserModel(UserProfileResponse profile, String category) {
        List<String> summaryParts = new ArrayList<>();

        if (profile.performanceProfile() != null) {
            summaryParts.add("completion reliability "
                    + formatRate(profile.performanceProfile().completionReliability()));

            if (category != null && profile.performanceProfile().categoryCompletionRates() != null) {
                Double categoryStrength = profile.performanceProfile().categoryCompletionRates().get(category);
                if (categoryStrength != null) {
                    summaryParts.add("current category " + category + " done rate "
                            + formatRate(categoryStrength));
                }
            }

            if (profile.performanceProfile().topCategories() != null
                    && !profile.performanceProfile().topCategories().isEmpty()) {
                summaryParts.add("top categories "
                        + String.join(", ", profile.performanceProfile().topCategories()));
            }
        }

        if (profile.preferences() != null && profile.preferences().avgCheckInsPerWeek() > 0) {
            summaryParts.add("avg check-ins/week "
                    + String.format(java.util.Locale.US, "%.1f", profile.preferences().avgCheckInsPerWeek()));
        }

        return summaryParts.isEmpty() ? null : String.join("; ", summaryParts);
    }

    private String formatRate(double value) {
        return String.format(java.util.Locale.US, "%.0f%%", value * 100);
    }
}
