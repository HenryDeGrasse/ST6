package com.weekly.plan.service;

import java.util.UUID;

/**
 * A single commit suggestion produced by {@link DraftFromHistoryService}.
 *
 * <p>Each suggestion carries the original planning fields from a historical commit
 * plus a {@code source} tag that indicates why the commit was surfaced:
 * <ul>
 *   <li>{@link CommitSource#CARRIED_FORWARD} — not completed in the most recent
 *       reconciled plan</li>
 *   <li>{@link CommitSource#RECURRING} — similar title or outcome ID appeared in
 *       2+ consecutive historical weeks</li>
 *   <li>{@link CommitSource#COVERAGE_GAP} — coverage gap from the future
 *       {@code NextWorkService}</li>
 * </ul>
 *
 * <p>{@code sourceCommitId} is the ID of the historical commit from which this
 * suggestion was derived.  For CARRIED_FORWARD suggestions, it enables the
 * Phase 6 dual-write layer to reuse the same issue rather than creating a new one.
 */
public record SuggestedCommit(
        UUID commitId,
        String title,
        String description,
        String chessPriority,
        String category,
        String outcomeId,
        String nonStrategicReason,
        String expectedResult,
        CommitSource source,
        UUID sourceCommitId
) {
}
