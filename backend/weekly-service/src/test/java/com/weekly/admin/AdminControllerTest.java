package com.weekly.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditEventRepository;
import com.weekly.audit.AuditService;
import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import com.weekly.config.CorrelationIdFilter;
import com.weekly.idempotency.IdempotencyKeyRepository;
import com.weekly.notification.NotificationRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

// suppress unused – AdminDashboardService is injected but not exercised in this test class

/**
 * Unit tests for {@link AdminController}.
 */
class AdminControllerTest {

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private CapturingUserDataDeletionService userDataDeletionService;
    private AuthenticatedUserContext authenticatedUserContext;
    private AdminDashboardService adminDashboardService;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        userDataDeletionService = new CapturingUserDataDeletionService();
        authenticatedUserContext = new AuthenticatedUserContext();
        adminDashboardService = mock(AdminDashboardService.class);
        controller = new AdminController(
                userDataDeletionService, adminDashboardService, authenticatedUserContext);
    }

    private static final class CapturingUserDataDeletionService extends UserDataDeletionService {

        private boolean invoked;
        private UUID orgId;
        private UUID userId;
        private UUID adminUserId;
        private String ipAddress;
        private String correlationId;

        private CapturingUserDataDeletionService() {
            super(
                    mock(WeeklyPlanRepository.class),
                    mock(WeeklyCommitRepository.class),
                    mock(AuditEventRepository.class),
                    mock(NotificationRepository.class),
                    mock(IdempotencyKeyRepository.class),
                    mock(AuditService.class)
            );
        }

        @Override
        public void deleteUserData(
                UUID orgId,
                UUID userId,
                UUID adminUserId,
                String ipAddress,
                String correlationId
        ) {
            this.invoked = true;
            this.orgId = orgId;
            this.userId = userId;
            this.adminUserId = adminUserId;
            this.ipAddress = ipAddress;
            this.correlationId = correlationId;
        }
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesDeletionForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(ADMIN_ID, ORG_ID, Set.of("ADMIN")),
                        null,
                        List.of()
                )
        );
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        ResponseEntity<?> response = controller.deleteUserData(USER_ID, request);

        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
        assertTrue(userDataDeletionService.invoked);
        assertEquals(ORG_ID, userDataDeletionService.orgId);
        assertEquals(USER_ID, userDataDeletionService.userId);
        assertEquals(ADMIN_ID, userDataDeletionService.adminUserId);
        assertEquals("10.0.0.1", userDataDeletionService.ipAddress);
        assertEquals("corr-123", userDataDeletionService.correlationId);
    }

    @Test
    void returnsForbiddenForNonAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(ADMIN_ID, ORG_ID, Set.of("MANAGER")),
                        null,
                        List.of()
                )
        );
        HttpServletRequest request = mock(HttpServletRequest.class);

        ResponseEntity<?> response = controller.deleteUserData(USER_ID, request);

        assertEquals(403, response.getStatusCode().value());
        assertFalse(userDataDeletionService.invoked);
    }
}
