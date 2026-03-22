package com.weekly.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link DevRequestAuthenticator}.
 */
class DevRequestAuthenticatorTest {

    private static final UUID ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private DevRequestAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new DevRequestAuthenticator();
    }

    // ── Structured dev token tests ──────────────────────────────────

    @Nested
    class StructuredDevToken {

        @Test
        void extractsIdentityFromDevToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String token = "dev:" + USER_ID + ":" + ORG_ID + ":MANAGER,IC";
            request.addHeader("Authorization", "Bearer " + token);

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(USER_ID, principal.userId());
            assertEquals(ORG_ID, principal.orgId());
            assertEquals(2, principal.roles().size());
            assertTrue(principal.hasRole("MANAGER"));
            assertTrue(principal.hasRole("IC"));
            assertEquals(UserPrincipal.DEFAULT_TIME_ZONE, principal.timeZone());
        }

        @Test
        void extractsOptionalTimeZoneFromDevToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String token = "dev:" + USER_ID + ":" + ORG_ID + ":MANAGER:America/New_York";
            request.addHeader("Authorization", "Bearer " + token);

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals("America/New_York", principal.timeZone());
        }

        @Test
        void handlesDevTokenWithNoRoles() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String token = "dev:" + USER_ID + ":" + ORG_ID + ":";
            request.addHeader("Authorization", "Bearer " + token);

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(USER_ID, principal.userId());
            assertEquals(ORG_ID, principal.orgId());
            assertTrue(principal.roles().isEmpty());
        }

        @Test
        void handlesDevTokenWithSingleRole() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            String token = "dev:" + USER_ID + ":" + ORG_ID + ":ADMIN";
            request.addHeader("Authorization", "Bearer " + token);

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(1, principal.roles().size());
            assertTrue(principal.isAdmin());
        }

        @Test
        void devTokenTakesPriorityOverXHeaders() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            UUID differentOrg = UUID.fromString("11111111-1111-1111-1111-111111111111");
            String token = "dev:" + USER_ID + ":" + ORG_ID + ":IC";
            request.addHeader("Authorization", "Bearer " + token);
            // These should be ignored when a dev token is present
            request.addHeader("X-Org-Id", differentOrg.toString());
            request.addHeader("X-User-Id", "22222222-2222-2222-2222-222222222222");
            request.addHeader("X-Roles", "ADMIN");

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(USER_ID, principal.userId());
            assertEquals(ORG_ID, principal.orgId());
            assertTrue(principal.hasRole("IC"));
        }

        @Test
        void throwsOnMalformedDevToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer dev:only-one-part");

            assertThrows(AuthenticationException.class,
                    () -> authenticator.authenticate(request));
        }

        @Test
        void throwsOnInvalidUserIdInDevToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer dev:not-a-uuid:" + ORG_ID + ":IC");

            assertThrows(AuthenticationException.class,
                    () -> authenticator.authenticate(request));
        }

        @Test
        void throwsOnInvalidOrgIdInDevToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer dev:" + USER_ID + ":not-a-uuid:IC");

            assertThrows(AuthenticationException.class,
                    () -> authenticator.authenticate(request));
        }
    }

    // ── Legacy X- header tests (backwards compatibility) ────────────

    @Nested
    class LegacyHeaders {

        @Test
        void extractsOrgIdUserIdRolesAndTimeZone() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Org-Id", ORG_ID.toString());
            request.addHeader("X-User-Id", USER_ID.toString());
            request.addHeader("X-Roles", "MANAGER, IC");
            request.addHeader("X-Timezone", "Europe/London");

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(ORG_ID, principal.orgId());
            assertEquals(USER_ID, principal.userId());
            assertEquals(2, principal.roles().size());
            assertTrue(principal.hasRole("MANAGER"));
            assertTrue(principal.hasRole("IC"));
            assertEquals("Europe/London", principal.timeZone());
        }

        @Test
        void defaultsUserIdToAnonymousWhenHeaderAbsent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Org-Id", ORG_ID.toString());

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(ORG_ID, principal.orgId());
            assertEquals(DevRequestAuthenticator.ANONYMOUS_USER_ID, principal.userId());
            assertTrue(principal.roles().isEmpty());
        }

        @Test
        void throwsWhenOrgIdHeaderIsAbsent() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            assertThrows(AuthenticationException.class,
                    () -> authenticator.authenticate(request));
        }

        @Test
        void throwsWhenOrgIdHeaderIsNotUuid() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Org-Id", "not-a-uuid");

            assertThrows(AuthenticationException.class,
                    () -> authenticator.authenticate(request));
        }

        @Test
        void throwsWhenUserIdHeaderIsNotUuid() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Org-Id", ORG_ID.toString());
            request.addHeader("X-User-Id", "invalid");

            assertThrows(AuthenticationException.class,
                    () -> authenticator.authenticate(request));
        }

        @Test
        void handlesEmptyRolesHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Org-Id", ORG_ID.toString());
            request.addHeader("X-User-Id", USER_ID.toString());
            request.addHeader("X-Roles", "");

            UserPrincipal principal = authenticator.authenticate(request);

            assertTrue(principal.roles().isEmpty());
        }

        @Test
        void parsesSingleRole() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Org-Id", ORG_ID.toString());
            request.addHeader("X-User-Id", USER_ID.toString());
            request.addHeader("X-Roles", "ADMIN");

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(1, principal.roles().size());
            assertTrue(principal.isAdmin());
        }

        @Test
        void fallsBackToXHeadersForNonDevBearerToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.fake.jwt");
            request.addHeader("X-Org-Id", ORG_ID.toString());
            request.addHeader("X-User-Id", USER_ID.toString());
            request.addHeader("X-Roles", "IC");

            UserPrincipal principal = authenticator.authenticate(request);

            assertEquals(USER_ID, principal.userId());
            assertEquals(ORG_ID, principal.orgId());
            assertTrue(principal.hasRole("IC"));
        }
    }
}
