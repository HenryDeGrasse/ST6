package com.weekly.notification;

import com.weekly.auth.AuthenticatedUserContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for in-app notification endpoints.
 *
 * <p>Supports listing unread notifications and marking them as read.
 * MVP renders in-app banners on page load (PRD §4).
 *
 * <p>The caller's identity is sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} exposed through
 * {@link AuthenticatedUserContext} (§9.1).
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final AuthenticatedUserContext authenticatedUserContext;

    public NotificationController(
            NotificationRepository notificationRepository,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.notificationRepository = notificationRepository;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * GET /notifications/unread
     * Returns unread notifications for the current user, newest first.
     */
    @GetMapping("/notifications/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications() {
        List<NotificationEntity> unread = notificationRepository
                .findByOrgIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId()
                );
        List<NotificationResponse> responses = unread.stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * POST /notifications/{notificationId}/read
     * Marks a notification as read.
     */
    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID notificationId
    ) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getOrgId().equals(authenticatedUserContext.orgId())
                        && n.getUserId().equals(authenticatedUserContext.userId()))
                .orElse(null);

        if (notification == null) {
            return ResponseEntity.notFound().build();
        }

        notification.markRead();
        notificationRepository.save(notification);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /notifications/read-all
     * Marks all unread notifications for the current user as read.
     */
    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        List<NotificationEntity> unread = notificationRepository
                .findByOrgIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                        authenticatedUserContext.orgId(),
                        authenticatedUserContext.userId()
                );
        for (NotificationEntity n : unread) {
            n.markRead();
        }
        notificationRepository.saveAll(unread);
        return ResponseEntity.noContent().build();
    }
}
