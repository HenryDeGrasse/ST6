package com.weekly.notification;

import java.util.Map;

/**
 * API response DTO for an in-app notification.
 */
public record NotificationResponse(
        String id,
        String type,
        Map<String, Object> payload,
        boolean read,
        String createdAt
) {

    public static NotificationResponse from(NotificationEntity entity) {
        return new NotificationResponse(
                entity.getId().toString(),
                entity.getType(),
                entity.getPayload(),
                entity.getReadAt() != null,
                entity.getCreatedAt().toString()
        );
    }
}
