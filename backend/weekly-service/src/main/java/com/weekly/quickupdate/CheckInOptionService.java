package com.weekly.quickupdate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.LlmClient;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.usermodel.UserUpdatePatternEntity;
import com.weekly.usermodel.UserUpdatePatternService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 *   <li>Retrieve the user's top note-text patterns for the commit's category.</li>
 *   <li>Build a structured prompt using {@link CheckInOptionPromptBuilder}.</li>
 *   <li>Call the LLM and parse the JSON response.</li>
 *   <li>On any failure (LLM unavailable, parse error, commit not found), return
 *       {@link CheckInOptionsResponse#empty()} — per PRD §4 fallback contract.</li>
 * </ol>
 */
@Service
public class CheckInOptionService {

    private static final Logger LOG = LoggerFactory.getLogger(CheckInOptionService.class);

    /** Maximum number of user patterns to include in the prompt context. */
    private static final int TOP_PATTERNS_LIMIT = 5;

    private final WeeklyCommitRepository commitRepository;
    private final UserUpdatePatternService userUpdatePatternService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public CheckInOptionService(
            WeeklyCommitRepository commitRepository,
            UserUpdatePatternService userUpdatePatternService,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.commitRepository = commitRepository;
        this.userUpdatePatternService = userUpdatePatternService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates contextual check-in options for the given commit.
     *
     * @param orgId                the authenticated user's organisation
     * @param userId               the authenticated user (used to retrieve personal patterns)
     * @param commitId             the commit to generate options for
     * @param currentStatus        the current progress status (context hint; may be null)
     * @param lastNote             the most recent check-in note (context hint; may be null)
     * @param daysSinceLastCheckIn days elapsed since the last check-in
     * @return generated options, or {@link CheckInOptionsResponse#empty()} on any failure
     */
    public CheckInOptionsResponse generateOptions(
            UUID orgId,
            UUID userId,
            UUID commitId,
            String currentStatus,
            String lastNote,
            int daysSinceLastCheckIn
    ) {
        // 1. Fetch commit and verify it belongs to the requesting org
        Optional<WeeklyCommitEntity> commitOpt = commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId));

        if (commitOpt.isEmpty()) {
            LOG.warn("Commit not found or org mismatch: commitId={}, orgId={}", commitId, orgId);
            return CheckInOptionsResponse.empty();
        }

        WeeklyCommitEntity commit = commitOpt.get();

        // 2. Extract commit context fields
        String title = commit.getTitle();
        String category = commit.getCategory() != null ? commit.getCategory().name() : null;
        String chessPriority = commit.getChessPriority() != null
                ? commit.getChessPriority().name()
                : null;
        String outcomeName = commit.getSnapshotOutcomeName();

        // 3. Retrieve user's top note-text patterns for this category
        List<UserUpdatePatternEntity> patternEntities =
                userUpdatePatternService.getTopPatterns(orgId, userId, category, TOP_PATTERNS_LIMIT);
        List<String> userPatterns = patternEntities.stream()
                .map(UserUpdatePatternEntity::getNoteText)
                .toList();

        // 4. Build prompt messages
        List<LlmClient.Message> messages = CheckInOptionPromptBuilder.buildCheckInOptionMessages(
                title,
                category,
                chessPriority,
                currentStatus,
                lastNote,
                daysSinceLastCheckIn,
                userPatterns,
                outcomeName
        );

        // 5–7. Call LLM, parse response, handle failures
        try {
            String rawResponse = llmClient.complete(messages, CheckInOptionPromptBuilder.RESPONSE_SCHEMA);
            return parseResponse(rawResponse);
        } catch (LlmClient.LlmUnavailableException ex) {
            LOG.warn("LLM unavailable while generating check-in options for commit {}: {}",
                    commitId, ex.getMessage());
            return CheckInOptionsResponse.empty();
        } catch (Exception ex) {
            LOG.warn("Failed to parse check-in options response for commit {}: {}",
                    commitId, ex.getMessage());
            return CheckInOptionsResponse.empty();
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Parses the raw LLM JSON response into a {@link CheckInOptionsResponse}.
     *
     * <p>Expected structure:
     * <pre>{@code
     * {
     *   "statusOptions": ["ON_TRACK", "AT_RISK", ...],
     *   "progressOptions": [
     *     { "text": "Wrapped API integration", "source": "pattern" },
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * @param rawResponse the raw LLM response string
     * @return parsed response, or {@link CheckInOptionsResponse#empty()} if parsing fails
     * @throws Exception if JSON parsing or structure validation fails
     */
    private CheckInOptionsResponse parseResponse(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            return CheckInOptionsResponse.empty();
        }

        JsonNode root = objectMapper.readTree(rawResponse);

        // Parse statusOptions
        List<String> statusOptions = new ArrayList<>();
        JsonNode statusNode = root.get("statusOptions");
        if (statusNode != null && statusNode.isArray()) {
            for (JsonNode element : statusNode) {
                if (element.isTextual()) {
                    statusOptions.add(element.asText());
                }
            }
        }

        // Parse progressOptions
        List<CheckInOptionItem> progressOptions = new ArrayList<>();
        JsonNode progressNode = root.get("progressOptions");
        if (progressNode != null && progressNode.isArray()) {
            for (JsonNode element : progressNode) {
                JsonNode textNode = element.get("text");
                JsonNode sourceNode = element.get("source");
                if (textNode != null && textNode.isTextual()
                        && sourceNode != null && sourceNode.isTextual()) {
                    progressOptions.add(new CheckInOptionItem(
                            textNode.asText(), sourceNode.asText()
                    ));
                }
            }
        }

        // Fall back to the default status list if the LLM returned none
        if (statusOptions.isEmpty()) {
            return new CheckInOptionsResponse("ok",
                    CheckInOptionsResponse.empty().statusOptions(), progressOptions);
        }

        return new CheckInOptionsResponse("ok", statusOptions, progressOptions);
    }
}
