package com.weekly.shared;

import java.util.List;
import java.util.UUID;

/**
 * Abstraction for accessing commit data needed by the AI suggestion module.
 *
 * <p>This interface lives in the shared package to maintain the module
 * boundary: the AI module and plan module can both depend on shared
 * without depending on each other (enforced by ArchUnit).
 */
public interface CommitDataProvider {

    /**
     * Returns commit summaries for the given plan.
     *
     * @param orgId  the organization ID
     * @param planId the plan ID
     * @return list of commit summaries, or empty if plan not found
     */
    List<CommitSummary> getCommitSummaries(UUID orgId, UUID planId);

    /**
     * Checks if a plan exists for the given org.
     */
    boolean planExists(UUID orgId, UUID planId);

    /**
     * Enriched summary of a commit for AI reconciliation context.
     *
     * @param commitId                     the commit UUID as a string
     * @param title                        the commit title
     * @param expectedResult               the expected result defined at planning time
     * @param progressNotes                free-form progress notes updated during the week
     * @param checkInHistory               structured daily check-in entries from the
     *                                     {@code progress_entries} table (empty if none)
     * @param priorCompletionStatuses      completion statuses from carry-forward ancestor commits,
     *                                     most-recent first (empty if this commit was not carried)
     * @param categoryCompletionRateContext pre-formatted team category completion rate, e.g.
     *                                      "OPERATIONS: 85% DONE (team, last 4 wks)", or
     *                                      {@code null} if insufficient historical data
     */
    record CommitSummary(
            String commitId,
            String title,
            String expectedResult,
            String progressNotes,
            List<CheckInEntry> checkInHistory,
            List<String> priorCompletionStatuses,
            String categoryCompletionRateContext
    ) {}

    /**
     * A single structured check-in entry from the {@code progress_entries} table.
     *
     * @param status the progress status (e.g. "ON_TRACK", "AT_RISK", "BLOCKED", "DONE_EARLY")
     * @param note   the free-form note entered during check-in (may be empty)
     */
    record CheckInEntry(String status, String note) {}
}
