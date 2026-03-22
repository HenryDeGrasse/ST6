package com.weekly.auth;

import java.util.List;
import java.util.UUID;

/**
 * Org-wide grouping of a manager and their current rostered reports.
 *
 * @param managerId          the manager's user ID
 * @param managerDisplayName the manager's best-available display name
 * @param members            current direct reports for the manager
 */
public record OrgTeamGroup(
        UUID managerId,
        String managerDisplayName,
        List<OrgRosterEntry> members
) {}
