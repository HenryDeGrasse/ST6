package com.weekly.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link AuthenticatedUserContext}.
 */
class AuthenticatedUserContextTest {

    private static final UserPrincipal PRINCIPAL = new UserPrincipal(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            Set.of("MANAGER"),
            "America/Los_Angeles"
    );

    private final AuthenticatedUserContext authenticatedUserContext = new AuthenticatedUserContext();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesPrincipalFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );

        assertEquals(PRINCIPAL, authenticatedUserContext.getPrincipal());
        assertEquals(PRINCIPAL.userId(), authenticatedUserContext.userId());
        assertEquals(PRINCIPAL.orgId(), authenticatedUserContext.orgId());
        assertEquals("America/Los_Angeles", authenticatedUserContext.timeZone());
        assertEquals(ZoneId.of("America/Los_Angeles"), authenticatedUserContext.zoneId());
        assertTrue(authenticatedUserContext.isManager());
    }

    @Test
    void throwsWhenAuthenticationIsMissing() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                authenticatedUserContext::getPrincipal
        );

        assertEquals("No authenticated principal is available", exception.getMessage());
    }

    @Test
    void throwsWhenPrincipalTypeIsUnexpected() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of())
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                authenticatedUserContext::getPrincipal
        );

        assertEquals(
                "Authenticated principal is not a UserPrincipal",
                exception.getMessage()
        );
    }
}
