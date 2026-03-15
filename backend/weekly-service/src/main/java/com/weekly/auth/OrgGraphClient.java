package com.weekly.auth;

import java.util.List;
import java.util.UUID;

/**
 * Adapter interface for the org graph / HRIS API (§9.1).
 *
 * <p>Used by the manager dashboard and authorization checks
 * ("is X a direct report of Y?"). Results are cached in Redis
 * with a 15-minute TTL.
 *
 * <p>Failure mode: if unavailable and cache is cold, manager
 * access is denied (fail closed).
 */
public interface OrgGraphClient {

    /**
     * Returns the user IDs of direct reports for the given manager.
     *
     * @param orgId     the organization ID
     * @param managerId the manager's user ID
     * @return list of direct report user IDs (may be empty)
     */
    List<UUID> getDirectReports(UUID orgId, UUID managerId);

    /**
     * Returns direct reports with display names for the given manager.
     *
     * <p>The default implementation maps {@link #getDirectReports(UUID, UUID)}
     * to {@link DirectReport} using the user ID as the display name.
     * Implementations backed by a real HRIS API should override this
     * to return actual names.
     *
     * @param orgId     the organization ID
     * @param managerId the manager's user ID
     * @return list of direct reports with display names (may be empty)
     */
    default List<DirectReport> getDirectReportsWithNames(UUID orgId, UUID managerId) {
        return getDirectReports(orgId, managerId).stream()
                .map(id -> new DirectReport(id, id.toString()))
                .toList();
    }

    /**
     * Checks whether {@code userId} is a direct report of {@code managerId}.
     *
     * @param orgId     the organization ID
     * @param managerId the manager's user ID
     * @param userId    the user to check
     * @return true if the user is a direct report
     */
    default boolean isDirectReport(UUID orgId, UUID managerId, UUID userId) {
        return getDirectReports(orgId, managerId).contains(userId);
    }
}
