package com.weekly.ai;

import com.weekly.ai.rag.EmbeddingClient;
import com.weekly.ai.rag.ScoredMatch;
import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.shared.NextWorkDataProvider;
import com.weekly.shared.NextWorkDataProvider.RcdoCoverageGap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coverage gap inspiration service (Phase 6, Step 14).
 *
 * <p>For each RCDO outcome that has gone uncovered (zero team commits) for a recent
 * stretch, generates an issue-creation suggestion to help the user address the gap.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Fetch team coverage gaps via {@link NextWorkDataProvider#getTeamCoverageGaps}.</li>
 *   <li>For each gap, attempt to estimate effort by finding similar DONE issues via
 *       RAG similarity search on the gap's outcome name.  The estimated hours are the
 *       median of the top-K similar DONE issues' {@code estimatedHours} values.</li>
 *   <li>When RAG / the embedding client is unavailable, fall back to a configurable
 *       default effort estimate ({@value #DEFAULT_EFFORT_HOURS} h).</li>
 *   <li>Build a suggested issue title and description from a template.</li>
 * </ol>
 *
 * <p>Returns an empty list when no coverage gaps are found.
 * Returns a single "unavailable" result when the data provider fails entirely.
 */
@Service
public class CoverageGapInspirationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(CoverageGapInspirationService.class);

    /** Default estimated hours when no similar past issues can be found. */
    static final double DEFAULT_EFFORT_HOURS = 4.0;

    /** Number of similar DONE issues to retrieve for effort estimation. */
    static final int SIMILAR_ISSUES_TOP_K = 5;

    /** Coverage gap look-back window: maximum recent consecutive missing weeks to evaluate. */
    static final int GAP_WEEKS_BACK = 4;

    /** Broader reference window to establish historical outcome activity. */
    static final int REF_WEEKS_BACK = 8;

    private final NextWorkDataProvider nextWorkDataProvider;
    private final IssueRepository issueRepository;
    private final EmbeddingClient embeddingClient;

    public CoverageGapInspirationService(
            NextWorkDataProvider nextWorkDataProvider,
            IssueRepository issueRepository,
            EmbeddingClient embeddingClient
    ) {
        this.nextWorkDataProvider = nextWorkDataProvider;
        this.issueRepository = issueRepository;
        this.embeddingClient = embeddingClient;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates issue-creation inspirations for the current coverage gaps.
     *
     * @param orgId     the organisation ID (used for issue lookups)
     * @param weekStart the reference Monday; defaults to the current week if null
     * @return a {@link InspirationResult}; never null
     */
    public InspirationResult generateInspirations(UUID orgId, LocalDate weekStart) {
        try {
            LocalDate monday = resolveMonday(weekStart);
            List<RcdoCoverageGap> gaps = nextWorkDataProvider.getTeamCoverageGaps(
                    orgId, monday, GAP_WEEKS_BACK, REF_WEEKS_BACK);

            if (gaps.isEmpty()) {
                return new InspirationResult("ok", List.of());
            }

            List<InspirationSuggestion> suggestions = new ArrayList<>(gaps.size());
            for (RcdoCoverageGap gap : gaps) {
                InspirationSuggestion suggestion = buildSuggestion(orgId, gap);
                suggestions.add(suggestion);
            }

            return new InspirationResult("ok", suggestions);
        } catch (Exception e) {
            LOG.error("Unexpected error in generateInspirations for org {}", orgId, e);
            return InspirationResult.unavailable();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private LocalDate resolveMonday(LocalDate weekStart) {
        if (weekStart != null) {
            return weekStart;
        }
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private InspirationSuggestion buildSuggestion(UUID orgId, RcdoCoverageGap gap) {
        String suggestedTitle = buildTitle(gap);
        String suggestedDescription = buildDescription(gap);
        BigDecimal estimatedHours = estimateEffort(orgId, gap);
        String rationale = buildRationale(gap);

        return new InspirationSuggestion(
                gap.outcomeId(),
                gap.outcomeName(),
                gap.objectiveName(),
                gap.rallyCryName(),
                suggestedTitle,
                suggestedDescription,
                estimatedHours,
                rationale,
                gap.weeksMissing()
        );
    }

    private static String buildTitle(RcdoCoverageGap gap) {
        return "Contribute to: " + gap.outcomeName();
    }

    private static String buildDescription(RcdoCoverageGap gap) {
        StringBuilder sb = new StringBuilder();
        sb.append("The team has not committed to \"")
                .append(gap.outcomeName())
                .append("\" for ")
                .append(gap.weeksMissing())
                .append(gap.weeksMissing() == 1 ? " week." : " weeks.");
        if (gap.rallyCryName() != null) {
            sb.append("\n\nThis outcome is part of the \"")
                    .append(gap.rallyCryName())
                    .append("\" rally cry");
            if (gap.objectiveName() != null) {
                sb.append(", under objective \"").append(gap.objectiveName()).append("\"");
            }
            sb.append(".");
        }
        sb.append("\n\nConsider creating an issue to address this coverage gap "
                + "and re-establish team engagement with this outcome.");
        return sb.toString();
    }

    /**
     * Attempts to estimate effort by finding similar DONE issues via embedding similarity.
     * Falls back to {@value #DEFAULT_EFFORT_HOURS} h when RAG is unavailable or returns no results.
     */
    private BigDecimal estimateEffort(UUID orgId, RcdoCoverageGap gap) {
        try {
            String queryText = gap.outcomeName()
                    + (gap.objectiveName() != null ? " " + gap.objectiveName() : "");
            float[] queryVector = embeddingClient.embed(queryText);

            Map<String, Object> filter = Map.of(
                    "orgId", orgId.toString(),
                    "status", IssueStatus.DONE.name()
            );

            List<ScoredMatch> matches = embeddingClient.query(
                    queryVector, SIMILAR_ISSUES_TOP_K, filter);

            if (matches.isEmpty()) {
                return BigDecimal.valueOf(DEFAULT_EFFORT_HOURS);
            }

            // Collect estimatedHours from matched DONE issues
            List<BigDecimal> hoursList = new ArrayList<>();
            for (ScoredMatch match : matches) {
                try {
                    UUID issueId = UUID.fromString(match.id());
                    Optional<IssueEntity> issueOpt =
                            issueRepository.findByOrgIdAndId(orgId, issueId);
                    issueOpt.filter(i -> i.getStatus() == IssueStatus.DONE)
                            .filter(i -> i.getEstimatedHours() != null
                                    && i.getEstimatedHours().compareTo(BigDecimal.ZERO) > 0)
                            .ifPresent(i -> hoursList.add(i.getEstimatedHours()));
                } catch (IllegalArgumentException e) {
                    LOG.debug("Skipping non-UUID match id '{}' during effort estimation", match.id());
                }
            }

            if (hoursList.isEmpty()) {
                return BigDecimal.valueOf(DEFAULT_EFFORT_HOURS);
            }

            return computeMedian(hoursList);
        } catch (Exception e) {
            LOG.debug("Could not estimate effort via RAG for gap '{}': {}",
                    gap.outcomeName(), e.getMessage());
            return BigDecimal.valueOf(DEFAULT_EFFORT_HOURS);
        }
    }

    /**
     * Computes the median of a non-empty list of BigDecimal values, rounded to 1 decimal place.
     */
    static BigDecimal computeMedian(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2).setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal mid1 = sorted.get(n / 2 - 1);
        BigDecimal mid2 = sorted.get(n / 2);
        return mid1.add(mid2)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
    }

    private static String buildRationale(RcdoCoverageGap gap) {
        return String.format(
                "Outcome \"%s\" (part of %s) has had 0 team commits for %d %s. "
                + "Previously received %d team %s in the prior 8-week window.",
                gap.outcomeName(),
                gap.rallyCryName() != null ? "\"" + gap.rallyCryName() + "\"" : "the rally cry",
                gap.weeksMissing(),
                gap.weeksMissing() == 1 ? "week" : "weeks",
                gap.teamCommitsPrevWindow(),
                gap.teamCommitsPrevWindow() == 1 ? "commit" : "commits"
        );
    }

    // ── Public result types ───────────────────────────────────────────────────

    /**
     * A single issue-creation inspiration for a coverage gap.
     *
     * @param outcomeId           the RCDO outcome UUID string
     * @param outcomeName         the outcome name
     * @param objectiveName       the parent objective name (may be null)
     * @param rallyCryName        the parent rally cry name (may be null)
     * @param suggestedTitle      suggested issue title
     * @param suggestedDescription suggested issue description
     * @param estimatedHours      estimated effort (from RAG or default)
     * @param rationale           human-readable explanation for this suggestion
     * @param weeksMissing        consecutive weeks with zero team commits
     */
    public record InspirationSuggestion(
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName,
            String suggestedTitle,
            String suggestedDescription,
            BigDecimal estimatedHours,
            String rationale,
            int weeksMissing
    ) {}

    /**
     * Result returned by {@link #generateInspirations}.
     *
     * @param status      "ok" | "unavailable"
     * @param inspirations list of issue creation inspirations (may be empty)
     */
    public record InspirationResult(
            String status,
            List<InspirationSuggestion> inspirations
    ) {
        static InspirationResult unavailable() {
            return new InspirationResult("unavailable", List.of());
        }
    }
}
