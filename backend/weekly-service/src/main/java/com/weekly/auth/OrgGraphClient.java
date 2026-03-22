package com.weekly.auth;

import java.util.List;
import java.util.Map;
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
     * Returns the best-available org roster for the given organisation.
     *
     * <p>This is the seam used by timezone-aware scheduled agents and future
     * executive rollups. Implementations that only support manager-local lookups
     * may safely return an empty list.
     */
    default List<OrgRosterEntry> getOrgRoster(UUID orgId) {
        return List.of();
    }

    /**
     * Returns org-wide team groupings keyed by manager.
     *
     * <p>The default implementation derives groups from {@link #getOrgRoster(UUID)}.
     * When no org roster is available, the safe fallback is an empty map.
     */
    default Map<UUID, OrgTeamGroup> getOrgTeamGroups(UUID orgId) {
        List<OrgRosterEntry> roster = getOrgRoster(orgId);

        Map<UUID, List<OrgRosterEntry>> reportsByManager = roster.stream()
                .filter(entry -> entry.managerId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        OrgRosterEntry::managerId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        Map<UUID, OrgRosterEntry> rosterByUserId = roster.stream()
                .collect(java.util.stream.Collectors.toMap(
                        OrgRosterEntry::userId,
                        entry -> entry,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));

        Map<UUID, OrgTeamGroup> groups = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, List<OrgRosterEntry>> entry : reportsByManager.entrySet()) {
            OrgRosterEntry manager = rosterByUserId.get(entry.getKey());
            String managerDisplayName = manager != null
                    ? manager.displayName()
                    : entry.getKey().toString();
            groups.put(
                    entry.getKey(),
                    new OrgTeamGroup(entry.getKey(), managerDisplayName, List.copyOf(entry.getValue())));
        }
        return groups;
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
