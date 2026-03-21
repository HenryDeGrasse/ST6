package com.weekly.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies the UserPrincipal record behavior.
 */
class UserPrincipalTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @Test
    void hasRoleReturnsTrueForExistingRole() {
        UserPrincipal principal = new UserPrincipal(USER_ID, ORG_ID, Set.of("IC", "MANAGER"));
        assertTrue(principal.hasRole("IC"));
        assertTrue(principal.hasRole("MANAGER"));
    }

    @Test
    void hasRoleReturnsFalseForMissingRole() {
        UserPrincipal principal = new UserPrincipal(USER_ID, ORG_ID, Set.of("IC"));
        assertFalse(principal.hasRole("ADMIN"));
    }

    @Test
    void isManagerReturnsTrueForManagerRole() {
        UserPrincipal manager = new UserPrincipal(USER_ID, ORG_ID, Set.of("MANAGER"));
        assertTrue(manager.isManager());
    }

    @Test
    void isManagerReturnsFalseForIcRole() {
        UserPrincipal ic = new UserPrincipal(USER_ID, ORG_ID, Set.of("IC"));
        assertFalse(ic.isManager());
    }

    @Test
    void isAdminReturnsTrueForAdminRole() {
        UserPrincipal admin = new UserPrincipal(USER_ID, ORG_ID, Set.of("ADMIN"));
        assertTrue(admin.isAdmin());
    }

    @Test
    void recordAccessorsReturnCorrectValues() {
        UserPrincipal principal = new UserPrincipal(USER_ID, ORG_ID, Set.of("IC"));
        assertEquals(USER_ID, principal.userId());
        assertEquals(ORG_ID, principal.orgId());
        assertEquals(Set.of("IC"), principal.roles());
    }
}
