package com.weekly.notification;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * JPA-backed implementation of {@link NotificationService}.
 *
 * <p>Writes in-app notifications to the {@code notifications} table.
 * In production, this is called by the notification worker after
 * processing outbox events. For MVP, it can also be called directly
 * in the same transaction for immediate in-app notifications.
 */
@Service
public class JpaNotificationService implements NotificationService {

    private final NotificationRepository notificationRepository;

    public JpaNotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void notify(UUID orgId, UUID userId, String type, Map<String, Object> payload) {
        NotificationEntity notification = new NotificationEntity(orgId, userId, type, payload);
        notificationRepository.save(notification);
    }
}
