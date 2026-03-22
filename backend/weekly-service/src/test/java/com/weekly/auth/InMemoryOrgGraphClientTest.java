package com.weekly.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryOrgGraphClientTest {

    private static final UUID ORG_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID REPORT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000010");
    private static final UUID UNASSIGNED_ID = UUID.fromString("c0000000-0000-0000-0000-000000000099");

    @Test
    void getOrgRosterIncludesRegisteredUsersWithoutRelationships() {
        InMemoryOrgGraphClient client = new InMemoryOrgGraphClient();
        client.registerUser(MANAGER_ID, "Grace Hopper", "America/New_York");
        client.registerUser(REPORT_ID, "Ada Lovelace", "Europe/London");
        client.registerUser(UNASSIGNED_ID, "Top Level", "America/Los_Angeles");
        client.setDirectReports(ORG_ID, MANAGER_ID, List.of(REPORT_ID));

        List<OrgRosterEntry> roster = client.getOrgRoster(ORG_ID);

        assertEquals(3, roster.size());
        Map<UUID, OrgRosterEntry> rosterByUserId = roster.stream()
                .collect(java.util.stream.Collectors.toMap(OrgRosterEntry::userId, entry -> entry));
        assertEquals("Grace Hopper", rosterByUserId.get(MANAGER_ID).displayName());
        assertEquals(MANAGER_ID, rosterByUserId.get(REPORT_ID).managerId());
        assertEquals("Top Level", rosterByUserId.get(UNASSIGNED_ID).displayName());
        assertEquals("America/Los_Angeles", rosterByUserId.get(UNASSIGNED_ID).timeZone());
    }

    @Test
    void getOrgTeamGroupsUsesManagerDisplayNamesFromRoster() {
        InMemoryOrgGraphClient client = new InMemoryOrgGraphClient();
        client.registerUser(MANAGER_ID, "Grace Hopper", "America/New_York");
        client.registerUser(REPORT_ID, "Ada Lovelace", "Europe/London");
        client.setDirectReports(ORG_ID, MANAGER_ID, List.of(REPORT_ID));

        Map<UUID, OrgTeamGroup> groups = client.getOrgTeamGroups(ORG_ID);

        assertEquals(1, groups.size());
        assertEquals("Grace Hopper", groups.get(MANAGER_ID).managerDisplayName());
        assertEquals(
                List.of(REPORT_ID),
                groups.get(MANAGER_ID).members().stream().map(OrgRosterEntry::userId).toList());
        assertTrue(groups.containsKey(MANAGER_ID));
    }
}
