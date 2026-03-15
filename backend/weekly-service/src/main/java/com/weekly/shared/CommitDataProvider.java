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
     * Summary of a commit for AI context.
     */
    record CommitSummary(
            String commitId,
            String title,
            String expectedResult,
            String progressNotes
    ) {}
}
