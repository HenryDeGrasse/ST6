package com.weekly.ai;

import com.weekly.rcdo.RcdoTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Narrows the full RCDO tree to a candidate set of ~50 outcomes
 * for LLM context, per PRD §4 scalability guidance.
 *
 * <p>Uses lexical similarity (keyword overlap) between the commitment
 * text and outcome/objective/rally-cry names. If the full tree is small
 * enough (≤ maxCandidates), returns all outcomes as candidates.
 */
public final class CandidateSelector {

    /** Default maximum candidates sent to LLM context. */
    public static final int DEFAULT_MAX_CANDIDATES = 50;

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
        List<ScoredCandidate> scored = new ArrayList<>();
        String searchText = normalize(title + " " + (description != null ? description : ""));
        String[] searchTokens = searchText.split("\\s+");

        for (RcdoTree.RallyCry rc : tree.rallyCries()) {
            for (RcdoTree.Objective obj : rc.objectives()) {
                for (RcdoTree.Outcome o : obj.outcomes()) {
                    double score = computeScore(searchTokens, rc.name(), obj.name(), o.name());
                    scored.add(new ScoredCandidate(
                            new PromptBuilder.CandidateOutcome(
                                    o.id(), o.name(), obj.name(), rc.name()
                            ),
                            score
                    ));
                }
            }
        }

        // If tree is small enough, return all
        if (scored.size() <= maxCandidates) {
            return scored.stream()
                    .map(ScoredCandidate::candidate)
                    .toList();
        }

        // Sort by score descending, take top N
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
