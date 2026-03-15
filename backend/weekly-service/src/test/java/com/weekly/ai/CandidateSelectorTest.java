package com.weekly.ai;

import com.weekly.rcdo.RcdoTree;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CandidateSelector}.
 */
class CandidateSelectorTest {

    @Test
    void returnsAllWhenTreeSmall() {
        RcdoTree tree = new RcdoTree(List.of(
                new RcdoTree.RallyCry("rc1", "Revenue Growth", List.of(
                        new RcdoTree.Objective("obj1", "Sales", "rc1", List.of(
                                new RcdoTree.Outcome(UUID.randomUUID().toString(), "Close deals", "obj1"),
                                new RcdoTree.Outcome(UUID.randomUUID().toString(), "Expand accounts", "obj1")
                        ))
                ))
        ));

        List<PromptBuilder.CandidateOutcome> candidates = CandidateSelector.select(
                tree, "Close enterprise deals", null, 50
        );

        assertEquals(2, candidates.size(), "Small tree should return all outcomes");
    }

    @Test
    void narrowsToMaxCandidates() {
        // Build a tree with > maxCandidates outcomes
        var outcomes = new java.util.ArrayList<RcdoTree.Outcome>();
        for (int i = 0; i < 10; i++) {
            outcomes.add(new RcdoTree.Outcome(
                    UUID.randomUUID().toString(), "Outcome " + i, "obj1"
            ));
        }
        RcdoTree tree = new RcdoTree(List.of(
                new RcdoTree.RallyCry("rc1", "Growth", List.of(
                        new RcdoTree.Objective("obj1", "Sales", "rc1", outcomes)
                ))
        ));

        List<PromptBuilder.CandidateOutcome> candidates = CandidateSelector.select(
                tree, "Sales target", null, 3
        );

        assertEquals(3, candidates.size(), "Should be narrowed to max candidates");
    }

    @Test
    void ranksRelevantCandidatesHigher() {
        String salesId = UUID.randomUUID().toString();
        String engineeringId = UUID.randomUUID().toString();
        String cookingId = UUID.randomUUID().toString();

        RcdoTree tree = new RcdoTree(List.of(
                new RcdoTree.RallyCry("rc1", "Revenue", List.of(
                        new RcdoTree.Objective("obj1", "Enterprise Sales", "rc1", List.of(
                                new RcdoTree.Outcome(salesId, "Close enterprise deals", "obj1")
                        ))
                )),
                new RcdoTree.RallyCry("rc2", "Engineering", List.of(
                        new RcdoTree.Objective("obj2", "Platform", "rc2", List.of(
                                new RcdoTree.Outcome(engineeringId, "Build API platform", "obj2")
                        ))
                )),
                new RcdoTree.RallyCry("rc3", "Other", List.of(
                        new RcdoTree.Objective("obj3", "Kitchen", "rc3", List.of(
                                new RcdoTree.Outcome(cookingId, "Prepare lunch menu", "obj3")
                        ))
                ))
        ));

        List<PromptBuilder.CandidateOutcome> candidates = CandidateSelector.select(
                tree, "Close the enterprise sales deal", "Q1 pipeline", 2
        );

        assertEquals(2, candidates.size());
        // The sales-related outcome should rank first
        assertEquals(salesId, candidates.get(0).outcomeId());
    }

    @Test
    void handlesEmptyTree() {
        RcdoTree tree = new RcdoTree(List.of());

        List<PromptBuilder.CandidateOutcome> candidates = CandidateSelector.select(
                tree, "anything", null, 50
        );

        assertTrue(candidates.isEmpty());
    }
}
