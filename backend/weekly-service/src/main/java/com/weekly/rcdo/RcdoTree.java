package com.weekly.rcdo;

import java.util.List;

/**
 * The full RCDO hierarchy for an org.
 */
public record RcdoTree(List<RallyCry> rallyCries) {

    public record RallyCry(
            String id,
            String name,
            List<Objective> objectives
    ) {}

    public record Objective(
            String id,
            String name,
            String rallyCryId,
            List<Outcome> outcomes
    ) {}

    public record Outcome(
            String id,
            String name,
            String objectiveId
    ) {}
}
