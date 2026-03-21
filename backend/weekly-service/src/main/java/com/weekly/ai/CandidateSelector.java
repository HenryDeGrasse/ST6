package com.weekly.ai;

import com.weekly.rcdo.RcdoTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Narrows the full RCDO tree to a candidate set of ~50 outcomes
 * for LLM context, per PRD §4 scalability guidance.
 *
 * <p>Uses lexical similarity (keyword overlap) between the commitment
 * text and outcome/objective/rally-cry names. If the full tree is small
 * enough (≤ maxCandidates), returns all outcomes as candidates.
 *
 * <p>An optional set of high-usage outcome IDs can be supplied to boost
 * outcomes that the team is actively working toward (Wave 1 team-context
 * enrichment). Boosted outcomes receive a {@link #HIGH_USAGE_SCORE_BOOST}
 * additive bonus to their lexical score so they are more likely to appear
 * in the top-N even when their keyword match is weaker.
 */
public final class CandidateSelector {

    /** Default maximum candidates sent to LLM context. */
    public static final int DEFAULT_MAX_CANDIDATES = 50;

    /**
     * Additive score bonus applied to outcomes present in the high-usage set.
     * Value chosen so that even a zero-keyword-match high-usage outcome rises
     * above most low-relevance outcomes while strong keyword matches still win.
     */
    static final double HIGH_USAGE_SCORE_BOOST = 0.3;

    private CandidateSelector() {}

    /**
     * Selects the top-N candidate outcomes from the RCDO tree based on
     * lexical relevance to the commitment title and description.
     *
     * @param tree          the full RCDO tree
     * @param title         the commitment title
     * @param description   the commitment description (may be null)
     * @param maxCandidates maximum number of candidates to return
     * @return ranked list of candidate outcomes (best matches first)
     */
    public static List<PromptBuilder.CandidateOutcome> select(
            RcdoTree tree,
            String title,
            String description,
            int maxCandidates
    ) {
        return select(tree, title, description, maxCandidates, Set.of());
    }

    /**
     * Selects the top-N candidate outcomes from the RCDO tree, optionally
     * boosting outcomes that appear in {@code highUsageOutcomeIds}.
     *
     * <p>When {@code highUsageOutcomeIds} is non-empty, outcomes that the
     * team actively links to receive a score bonus of {@link #HIGH_USAGE_SCORE_BOOST},
     * making them more likely to surface in the LLM candidate context even
     * when their keyword overlap with the current commitment text is modest.
     *
     * @param tree                the full RCDO tree
     * @param title               the commitment title
     * @param description         the commitment description (may be null)
     * @param maxCandidates       maximum number of candidates to return
     * @param highUsageOutcomeIds outcome IDs to boost (empty set = no boost)
     * @return ranked list of candidate outcomes (best matches first)
     */
    public static List<PromptBuilder.CandidateOutcome> select(
            RcdoTree tree,
            String title,
            String description,
            int maxCandidates,
            Set<String> highUsageOutcomeIds
    ) {
        List<ScoredCandidate> scored = new ArrayList<>();
        String searchText = normalize(title + " " + (description != null ? description : ""));
        String[] searchTokens = searchText.split("\\s+");

        for (RcdoTree.RallyCry rc : tree.rallyCries()) {
            for (RcdoTree.Objective obj : rc.objectives()) {
                for (RcdoTree.Outcome o : obj.outcomes()) {
                    double score = computeScore(searchTokens, rc.name(), obj.name(), o.name());
                    if (!highUsageOutcomeIds.isEmpty() && highUsageOutcomeIds.contains(o.id())) {
                        score += HIGH_USAGE_SCORE_BOOST;
                    }
                    scored.add(new ScoredCandidate(
                            new PromptBuilder.CandidateOutcome(
                                    o.id(), o.name(), obj.name(), rc.name()
                            ),
                            score
                    ));
                }
            }
        }

        // Always sort by score so the returned list is ranked best-first,
        // even when the full tree fits within maxCandidates.
        return scored.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(maxCandidates)
                .map(ScoredCandidate::candidate)
                .toList();
    }

    /**
     * Computes a relevance score based on keyword overlap.
     */
    static double computeScore(String[] searchTokens, String rallyCryName, String objectiveName, String outcomeName) {
        String combined = normalize(rallyCryName + " " + objectiveName + " " + outcomeName);
        double matches = 0;
        for (String token : searchTokens) {
            if (token.length() >= 3 && combined.contains(token)) {
                matches += 1.0;
            }
        }
        // Normalize by number of search tokens to get a 0–1 score
        return searchTokens.length > 0 ? matches / searchTokens.length : 0;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim();
    }

    private record ScoredCandidate(PromptBuilder.CandidateOutcome candidate, double score) {}
}
