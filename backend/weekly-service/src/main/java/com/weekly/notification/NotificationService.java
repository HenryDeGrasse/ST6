package com.weekly.notification;

import java.util.Map;
import java.util.UUID;

/**
 * Notification service interface (§4 notification table, §9.2 Container 5).
 *
 * <p>MVP renders in-app banners by querying a {@code notifications} table.
 * Push notifications (email/Slack) are post-MVP and use the same
 * outbox events consumed by the notification worker.
 */
public interface NotificationService {

    /**
     * Creates an in-app notification for a user.
     *
     * @param orgId   the organization ID
     * @param userId  the recipient user ID
     * @param type    the notification type
     * @param payload additional data for rendering the notification
     */
    void notify(UUID orgId, UUID userId, String type, Map<String, Object> payload);
}
