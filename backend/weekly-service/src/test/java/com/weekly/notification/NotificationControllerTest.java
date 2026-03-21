package com.weekly.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link NotificationController}.
 */
class NotificationControllerTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(USER_ID, ORG_ID, Set.of());

    private NotificationRepository notificationRepository;
    private AuthenticatedUserContext authenticatedUserContext;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        authenticatedUserContext = new AuthenticatedUserContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );
        controller = new NotificationController(notificationRepository, authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUnreadNotifications() {
        NotificationEntity n1 = new NotificationEntity(
                ORG_ID, USER_ID, "RECONCILIATION_SUBMITTED",
                Map.of("planId", UUID.randomUUID().toString(), "name", "Alice")
        );

        when(notificationRepository.findByOrgIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                ORG_ID, USER_ID
        )).thenReturn(List.of(n1));

        ResponseEntity<List<NotificationResponse>> response =
                controller.getUnreadNotifications();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("RECONCILIATION_SUBMITTED", response.getBody().get(0).type());
        assertFalse(response.getBody().get(0).read());
    }

    @Test
    void returnsEmptyListWhenNoUnread() {
        when(notificationRepository.findByOrgIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                ORG_ID, USER_ID
        )).thenReturn(List.of());

        ResponseEntity<List<NotificationResponse>> response =
                controller.getUnreadNotifications();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void marksNotificationAsRead() {
        NotificationEntity n = new NotificationEntity(
                ORG_ID, USER_ID, "PLAN_STILL_DRAFT", Map.of()
        );

        when(notificationRepository.findById(n.getId()))
                .thenReturn(Optional.of(n));
        when(notificationRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Void> response = controller.markAsRead(n.getId());

        assertEquals(204, response.getStatusCode().value());
        assertNotNull(n.getReadAt());
    }

    @Test
    void returns404ForMissingNotification() {
        when(notificationRepository.findById(any())).thenReturn(Optional.empty());

        ResponseEntity<Void> response =
                controller.markAsRead(UUID.randomUUID());

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void marksAllAsRead() {
        NotificationEntity n1 = new NotificationEntity(ORG_ID, USER_ID, "TYPE_1", Map.of());
        NotificationEntity n2 = new NotificationEntity(ORG_ID, USER_ID, "TYPE_2", Map.of());

        when(notificationRepository.findByOrgIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                ORG_ID, USER_ID
        )).thenReturn(List.of(n1, n2));

        ResponseEntity<Void> response = controller.markAllAsRead();

        assertEquals(204, response.getStatusCode().value());
        verify(notificationRepository).saveAll(List.of(n1, n2));
        assertNotNull(n1.getReadAt());
        assertNotNull(n2.getReadAt());
    }
}
