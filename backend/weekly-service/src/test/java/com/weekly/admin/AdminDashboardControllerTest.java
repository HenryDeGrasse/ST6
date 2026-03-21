package com.weekly.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.audit.AuditEventRepository;
import com.weekly.audit.AuditService;
import com.weekly.idempotency.IdempotencyKeyRepository;
import com.weekly.notification.NotificationRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.ApiErrorResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for the admin-dashboard endpoints in {@link AdminController}:
 * /adoption-metrics, /ai-usage, /rcdo-health.
 */
class AdminDashboardControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    private AdminDashboardService adminDashboardService;
    private AuthenticatedUserContext authenticatedUserContext;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        adminDashboardService = mock(AdminDashboardService.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        UserDataDeletionService deletionService = new UserDataDeletionService(
                mock(WeeklyPlanRepository.class),
                mock(WeeklyCommitRepository.class),
                mock(AuditEventRepository.class),
                mock(NotificationRepository.class),
                mock(IdempotencyKeyRepository.class),
                mock(AuditService.class)
        );
        controller = new AdminController(deletionService, adminDashboardService, authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(ADMIN_ID, ORG_ID, Set.of("ADMIN")),
                        null, List.of()
                )
        );
    }

    private void loginAsNonAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(ADMIN_ID, ORG_ID, Set.of("MANAGER")),
                        null, List.of()
                )
        );
    }

    private AdoptionMetrics stubAdoptionMetrics() {
        return new AdoptionMetrics(8, "2026-01-05", "2026-02-23",
                5, 0.9, List.of());
    }

    private AiUsageMetrics stubAiUsageMetrics() {
        return new AiUsageMetrics(8, "2026-01-05", "2026-02-23",
                100, 60, 20, 20, 0.6, 500, 100, 0.833, 100_000, 500_000);
    }

    private RcdoHealthReport stubRcdoHealthReport() {
        return new RcdoHealthReport("2026-03-19T10:00:00Z", 8, 10, 6, List.of(), List.of());
    }

    // ── Adoption Metrics ──────────────────────────────────────────────────────

    @Nested
    class AdoptionMetricsEndpoint {

        @Test
        void returnsOkForAdminUser() {
            loginAsAdmin();
            AdoptionMetrics stub = stubAdoptionMetrics();
            when(adminDashboardService.getAdoptionMetrics(eq(ORG_ID), eq(8))).thenReturn(stub);

            ResponseEntity<?> response = controller.getAdoptionMetrics(8);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stub, response.getBody());
        }

        @Test
        void returnsForbiddenForNonAdmin() {
            loginAsNonAdmin();

            ResponseEntity<?> response = controller.getAdoptionMetrics(8);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verifyNoInteractions(adminDashboardService);
        }

        @Test
        void returnsValidationErrorForInvalidWeeks() {
            loginAsAdmin();

            ResponseEntity<?> response = controller.getAdoptionMetrics(0);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
            ApiErrorResponse error = (ApiErrorResponse) response.getBody();
            assertNotNull(error);
            assertEquals("VALIDATION_ERROR", error.error().code());
        }

        @Test
        void rejectsWeeksAboveMax() {
            loginAsAdmin();

            ResponseEntity<?> response = controller.getAdoptionMetrics(27);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        }

        @Test
        void delegatesToServiceWithCorrectOrgId() {
            loginAsAdmin();
            when(adminDashboardService.getAdoptionMetrics(eq(ORG_ID), eq(4)))
                    .thenReturn(stubAdoptionMetrics());

            controller.getAdoptionMetrics(4);

            verify(adminDashboardService).getAdoptionMetrics(ORG_ID, 4);
        }
    }

    // ── AI Usage ──────────────────────────────────────────────────────────────

    @Nested
    class AiUsageEndpoint {

        @Test
        void returnsOkForAdminUser() {
            loginAsAdmin();
            AiUsageMetrics stub = stubAiUsageMetrics();
            when(adminDashboardService.getAiUsageMetrics(eq(ORG_ID), eq(8))).thenReturn(stub);

            ResponseEntity<?> response = controller.getAiUsage(8);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stub, response.getBody());
        }

        @Test
        void returnsForbiddenForNonAdmin() {
            loginAsNonAdmin();

            ResponseEntity<?> response = controller.getAiUsage(8);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verifyNoInteractions(adminDashboardService);
        }

        @Test
        void returnsValidationErrorForInvalidWeeks() {
            loginAsAdmin();

            ResponseEntity<?> response = controller.getAiUsage(-1);

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        }

        @Test
        void delegatesToServiceWithCorrectParams() {
            loginAsAdmin();
            when(adminDashboardService.getAiUsageMetrics(eq(ORG_ID), eq(12)))
                    .thenReturn(stubAiUsageMetrics());

            controller.getAiUsage(12);

            verify(adminDashboardService).getAiUsageMetrics(ORG_ID, 12);
        }
    }

    // ── RCDO Health ───────────────────────────────────────────────────────────

    @Nested
    class RcdoHealthEndpoint {

        @Test
        void returnsOkForAdminUser() {
            loginAsAdmin();
            RcdoHealthReport stub = stubRcdoHealthReport();
            when(adminDashboardService.getRcdoHealth(eq(ORG_ID))).thenReturn(stub);

            ResponseEntity<?> response = controller.getRcdoHealth();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(stub, response.getBody());
        }

        @Test
        void returnsForbiddenForNonAdmin() {
            loginAsNonAdmin();

            ResponseEntity<?> response = controller.getRcdoHealth();

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verifyNoInteractions(adminDashboardService);
        }

        @Test
        void delegatesToServiceWithCorrectOrgId() {
            loginAsAdmin();
            when(adminDashboardService.getRcdoHealth(eq(ORG_ID)))
                    .thenReturn(stubRcdoHealthReport());

            controller.getRcdoHealth();

            verify(adminDashboardService).getRcdoHealth(ORG_ID);
        }
    }
}
