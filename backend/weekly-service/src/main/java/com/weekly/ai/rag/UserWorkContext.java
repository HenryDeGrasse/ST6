package com.weekly.ai.rag;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated context about a user's current work situation, used as input
 * to the HyDE recommendation prompt (Phase 6, Step 13).
 *
 * <p>Fields may be null/empty when data is unavailable; the prompt builder
 * handles absent sections gracefully.
 *
 * @param userId                the user whose plan is being recommended for
 * @param orgId                 the org scoping recommendations
 * @param weekStart             the Monday being planned for
 * @param realisticWeeklyCapHours user's realistic weekly capacity in hours
 * @param alreadyCommittedHours hours already allocated in the current plan
 * @param currentPlanItemTitles titles of issues already in the plan (to avoid re-suggesting)
 * @param recentOutcomeIds      outcome IDs the user has been contributing to recently
 * @param carriedForwardTitles  titles of items the user is carrying forward this week
 * @param accessibleTeamIds     teams the caller is allowed to retrieve issues from
 */
public record UserWorkContext(
        UUID userId,
        UUID orgId,
        LocalDate weekStart,
        double realisticWeeklyCapHours,
        double alreadyCommittedHours,
        List<String> currentPlanItemTitles,
        List<UUID> recentOutcomeIds,
        List<String> carriedForwardTitles,
        List<UUID> accessibleTeamIds
) {

    public UserWorkContext(
            UUID userId,
            UUID orgId,
            LocalDate weekStart,
            double realisticWeeklyCapHours,
            double alreadyCommittedHours,
            List<String> currentPlanItemTitles,
            List<UUID> recentOutcomeIds,
            List<String> carriedForwardTitles
    ) {
        this(
                userId,
                orgId,
                weekStart,
                realisticWeeklyCapHours,
                alreadyCommittedHours,
                currentPlanItemTitles,
                recentOutcomeIds,
                carriedForwardTitles,
                List.of()
        );
    }

    /** Returns the remaining capacity in hours (may be negative if over-committed). */
    public double remainingCapacityHours() {
        return realisticWeeklyCapHours - alreadyCommittedHours;
    }
}
