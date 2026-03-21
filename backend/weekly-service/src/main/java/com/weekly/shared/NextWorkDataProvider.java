package com.weekly.shared;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Abstraction for data required by the AI next-work suggestion service.
 *
 * <p>Lives in the shared package so the AI module can query plan and RCDO
 * data without depending directly on plan-module internals
 * (ArchUnit boundary: AI must not depend on plan).
 */
public interface NextWorkDataProvider {

    /**
     * Returns carry-forward items from the user's recent plans.
     *
     * <p>A carry-forward item is a commit that was not completed
     * (completion status != DONE) in a reconciled or carry_forward-state plan,
     * or a commit that explicitly has a {@code carriedFromCommitId} set.
     * Items are returned newest-first (by week start date of the source plan).
     *
     * @param orgId     the organisation ID
     * @param userId    the plan owner's user ID
     * @param asOf      the reference date (Monday); looks back {@code weeksBack} weeks
     * @param weeksBack how many weeks to look back (e.g. 2)
     * @return carry-forward items; empty if none found in the window
     */
    List<CarryForwardItem> getRecentCarryForwardItems(
            UUID orgId, UUID userId, LocalDate asOf, int weeksBack);

    /**
     * Returns the user's recent commit history for LLM context enrichment.
     *
     * <p>The window is inclusive of the prior {@code weeksBack} planning weeks and
     * excludes the target planning week identified by {@code asOf}. Each entry carries
     * the most specific available strategic metadata (outcome / objective / rally cry)
     * plus a status string derived from reconciliation actuals when present.
     *
     * @param orgId     the organisation ID
     * @param userId    the plan owner's user ID
     * @param asOf      the reference Monday for the current planning week
     * @param weeksBack how many prior weeks to include (for Phase 2 prompts this is 4)
     * @return recent commit history ordered newest-first
     */
    List<RecentCommitContext> getRecentCommitHistory(
            UUID orgId, UUID userId, LocalDate asOf, int weeksBack);

    /**
     * Returns historically-active RCDO outcomes that have gone uncovered recently.
     *
     * <p>The broader reference window spans {@code refWeeksBack} weeks so that
     * only outcomes the team has historically worked on surface as gaps
     * (prevents noise from outcomes that were never activated).
     *
     * <p>{@code gapWeeksBack} is the maximum recent streak to evaluate. Implementations
     * should count consecutive missing weeks backward from {@code asOf - 1 week}
     * and surface outcomes with at least a 2-week recent gap so callers can rank
     * 2-, 3-, and 4-week gaps by severity.
     *
     * @param orgId        the organisation ID
     * @param asOf         the reference Monday (exclusive end)
     * @param gapWeeksBack maximum recent weeks to evaluate for zero coverage (e.g. 4)
     * @param refWeeksBack broader look-back for historical coverage (e.g. 8)
     * @return coverage gap entries sorted by {@link RcdoCoverageGap#weeksMissing()} descending
     */
    List<RcdoCoverageGap> getTeamCoverageGaps(
            UUID orgId, LocalDate asOf, int gapWeeksBack, int refWeeksBack);

    // ── Value objects ─────────────────────────────────────────────────────────

    /**
     * A single carry-forward item derived from a user's historical plans.
     *
     * @param sourceCommitId    UUID of the original commit (used for stable suggestion ID generation)
     * @param title             commit title
     * @param description       commit description (may be null)
     * @param chessPriority     chess priority name (e.g. "QUEEN"); null if unset
     * @param category          commit category name (e.g. "DELIVERY"); null if unset
     * @param outcomeId         RCDO outcome UUID string; null if non-strategic
     * @param outcomeName       snapshot outcome name; null if non-strategic or no snapshot
     * @param rallyCryId        snapshot rally cry UUID string; null before lock
     * @param rallyCryName      snapshot rally cry name; null if no snapshot
     * @param objectiveName     snapshot objective name; null if no snapshot
     * @param nonStrategicReason reason for non-strategic classification; null if strategic
     * @param expectedResult    expected result text
     * @param carryForwardWeeks how many consecutive weeks this item has been carried forward
     * @param sourceWeekStart   ISO date of the source plan's week start
     */
    record CarryForwardItem(
            UUID sourceCommitId,
            String title,
            String description,
            String chessPriority,
            String category,
            String outcomeId,
            String outcomeName,
            String rallyCryId,
            String rallyCryName,
            String objectiveName,
            String nonStrategicReason,
            String expectedResult,
            int carryForwardWeeks,
            LocalDate sourceWeekStart
    ) {}

    /**
     * A historical commit entry used as Phase 2 LLM context.
     *
     * @param commitId          commit UUID
     * @param weekStart         week the commit belonged to
     * @param title             commit title
     * @param outcomeId         linked RCDO outcome UUID string; null if non-strategic
     * @param outcomeName       snapshot outcome name; null if unavailable
     * @param objectiveName     snapshot objective name; null if unavailable
     * @param rallyCryName      snapshot rally cry name; null if unavailable
     * @param completionStatus  DONE / PARTIALLY / NOT_DONE / DROPPED when reconciled,
     *                          otherwise derived from the plan lifecycle state
     */
    record RecentCommitContext(
            UUID commitId,
            LocalDate weekStart,
            String title,
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName,
            String completionStatus
    ) {}

    /**
     * An RCDO outcome that had zero team commits in the recent gap window.
     *
     * @param outcomeId              RCDO outcome UUID string
     * @param outcomeName            outcome name from the RCDO tree
     * @param objectiveName          parent objective name
     * @param rallyCryName           parent rally cry name
     * @param weeksMissing           consecutive recent weeks with zero team commits
     * @param teamCommitsPrevWindow  total team commits to this outcome in the reference window
     */
    record RcdoCoverageGap(
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName,
            int weeksMissing,
            int teamCommitsPrevWindow
    ) {}
}
