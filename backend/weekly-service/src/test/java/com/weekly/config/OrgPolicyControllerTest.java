package com.weekly.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link OrgPolicyController}.
 */
class OrgPolicyControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    private OrgPolicyService orgPolicyService;
    private AuthenticatedUserContext authenticatedUserContext;
    private OrgPolicyController controller;

    @BeforeEach
    void setUp() {
        orgPolicyService = mock(OrgPolicyService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        controller = new OrgPolicyController(orgPolicyService, authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── GET /admin/org-policy ─────────────────────────────────

    @Nested
    class GetOrgPolicy {

        @Test
        void returnsOrgPolicyForAdminUser() {
            setUpAdmin();
            OrgPolicyService.OrgPolicy policy = fridayPolicy();
            when(orgPolicyService.getPolicy(ORG_ID)).thenReturn(policy);

            ResponseEntity<?> response = controller.getOrgPolicy();

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            OrgPolicyController.OrgPolicyResponse body =
                    (OrgPolicyController.OrgPolicyResponse) response.getBody();
            assertEquals("FRIDAY", body.digestDay());
            assertEquals("17:00", body.digestTime());
            assertEquals(1, body.chessMaxKing());
        }

        @Test
        void returnsForbiddenForNonAdminUser() {
            setUpManager();

            ResponseEntity<?> response = controller.getOrgPolicy();

            assertEquals(403, response.getStatusCode().value());
            verify(orgPolicyService, never()).getPolicy(any());
        }
    }

    // ── PATCH /admin/org-policy/digest ────────────────────────

    @Nested
    class UpdateDigestConfig {

        @Test
        void updatesDigestConfigAndEvictsCacheForAdminUser() {
            setUpAdmin();
            OrgPolicyService.OrgPolicy updatedPolicy = mondayPolicy();
            when(orgPolicyService.getPolicy(ORG_ID)).thenReturn(updatedPolicy);

            OrgPolicyController.UpdateDigestConfigRequest request =
                    new OrgPolicyController.UpdateDigestConfigRequest("MONDAY", "08:00");

            ResponseEntity<?> response = controller.updateDigestConfig(request);

            assertEquals(200, response.getStatusCode().value());
            verify(orgPolicyService).updateDigestConfig(eq(ORG_ID), eq("MONDAY"), eq("08:00"));
            verify(orgPolicyService).evict(ORG_ID);
        }

        @Test
        void returnsForbiddenForNonAdminUser() {
            setUpManager();

            OrgPolicyController.UpdateDigestConfigRequest request =
                    new OrgPolicyController.UpdateDigestConfigRequest("FRIDAY", "17:00");

            ResponseEntity<?> response = controller.updateDigestConfig(request);

            assertEquals(403, response.getStatusCode().value());
            verify(orgPolicyService, never()).updateDigestConfig(any(), any(), any());
            verify(orgPolicyService, never()).evict(any());
        }

        @Test
        void returnsUpdatedPolicyInResponseBody() {
            setUpAdmin();
            OrgPolicyService.OrgPolicy updatedPolicy = mondayPolicy();
            when(orgPolicyService.getPolicy(ORG_ID)).thenReturn(updatedPolicy);

            OrgPolicyController.UpdateDigestConfigRequest request =
                    new OrgPolicyController.UpdateDigestConfigRequest("MONDAY", "08:00");

            ResponseEntity<?> response = controller.updateDigestConfig(request);

            OrgPolicyController.OrgPolicyResponse body =
                    (OrgPolicyController.OrgPolicyResponse) response.getBody();
            assertNotNull(body);
            assertEquals("MONDAY", body.digestDay());
            assertEquals("08:00", body.digestTime());
        }
    }

    // ── Request validation ───────────────────────────────────

    @Nested
    class UpdateDigestConfigValidation {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @Test
        void rejectsImpossibleDigestTimes() {
            OrgPolicyController.UpdateDigestConfigRequest request =
                    new OrgPolicyController.UpdateDigestConfigRequest("FRIDAY", "25:99");

            var violations = validator.validate(request);

            assertEquals(1, violations.size());
            assertEquals("digestTime", violations.iterator().next().getPropertyPath().toString());
        }

        @Test
        void acceptsValidDigestTimes() {
            OrgPolicyController.UpdateDigestConfigRequest request =
                    new OrgPolicyController.UpdateDigestConfigRequest("FRIDAY", "23:45");

            var violations = validator.validate(request);

            assertTrue(violations.isEmpty());
        }
    }

    // ── OrgPolicyResponse mapping ─────────────────────────────

    @Nested
    class OrgPolicyResponseMapping {

        @Test
        void fromMapsAllFieldsCorrectly() {
            OrgPolicyService.OrgPolicy policy = new OrgPolicyService.OrgPolicy(
                    false, 2, 3, "TUESDAY", "09:00", "THURSDAY", "15:00", false, 120, "WEDNESDAY", "12:00"
            );

            OrgPolicyController.OrgPolicyResponse response = OrgPolicyController.OrgPolicyResponse.from(policy);

            assertFalse(response.chessKingRequired());
            assertEquals(2, response.chessMaxKing());
            assertEquals(3, response.chessMaxQueen());
            assertEquals("TUESDAY", response.lockDay());
            assertEquals("09:00", response.lockTime());
            assertEquals("THURSDAY", response.reconcileDay());
            assertEquals("15:00", response.reconcileTime());
            assertFalse(response.blockLockOnStaleRcdo());
            assertEquals(120, response.rcdoStalenessThresholdMinutes());
            assertEquals("WEDNESDAY", response.digestDay());
            assertEquals("12:00", response.digestTime());
        }

        @Test
        void defaultPolicyMapsToDefaultResponse() {
            OrgPolicyService.OrgPolicy defaults = OrgPolicyService.defaultPolicy();
            OrgPolicyController.OrgPolicyResponse response = OrgPolicyController.OrgPolicyResponse.from(defaults);

            assertTrue(response.chessKingRequired());
            assertEquals("FRIDAY", response.digestDay());
            assertEquals("17:00", response.digestTime());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private void setUpAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(ADMIN_ID, ORG_ID, Set.of("ADMIN")),
                        null,
                        List.of()
                )
        );
    }

    private void setUpManager() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(ADMIN_ID, ORG_ID, Set.of("MANAGER")),
                        null,
                        List.of()
                )
        );
    }

    private static OrgPolicyService.OrgPolicy fridayPolicy() {
        return new OrgPolicyService.OrgPolicy(
                true, 1, 2,
                "MONDAY", "10:00",
                "FRIDAY", "16:00",
                true, 60,
                "FRIDAY", "17:00"
        );
    }

    private static OrgPolicyService.OrgPolicy mondayPolicy() {
        return new OrgPolicyService.OrgPolicy(
                true, 1, 2,
                "MONDAY", "10:00",
                "FRIDAY", "16:00",
                true, 60,
                "MONDAY", "08:00"
        );
    }
}
