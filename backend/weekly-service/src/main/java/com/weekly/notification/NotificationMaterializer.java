package com.weekly.notification;

import com.weekly.outbox.OutboxEventEntity;
import com.weekly.outbox.OutboxEventRepository;
import com.weekly.shared.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight worker that polls unpublished outbox events and materializes
 * MVP in-app notifications into the notifications table.
 *
 * <p>For MVP this runs from the same codebase as the API, but it should be
 * enabled only for a dedicated worker profile/process. Enabling it on every
 * API instance would allow multiple schedulers to race on the same outbox rows.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "notification.materializer.enabled",
        havingValue = "true"
)
public class NotificationMaterializer {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationMaterializer.class);

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationRepository notificationRepository;

    public NotificationMaterializer(
            OutboxEventRepository outboxEventRepository,
            NotificationRepository notificationRepository
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Polls for unpublished outbox events every 5 seconds and materializes
     * notifications. Marks events as published after processing.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> events = outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc();
        for (OutboxEventEntity event : events) {
            try {
                materializeNotification(event);
                event.markPublished();
                outboxEventRepository.save(event);
            } catch (Exception e) {
                LOG.warn("Failed to materialize notification for event {}: {}",
                        event.getEventId(), e.getMessage());
            }
        }
    }

    private void materializeNotification(OutboxEventEntity event) {
        String eventType = event.getEventType();
        Map<String, Object> payload = event.getPayload();
        UUID orgId = event.getOrgId();

        if (EventType.PLAN_RECONCILED.getValue().equals(eventType)) {
            // IC submits reconciliation → notify manager
            String managerUserId = extractString(payload, "managerUserId");
            String ownerName = extractString(payload, "ownerUserName");
            if (managerUserId != null) {
                notificationRepository.save(new NotificationEntity(
                        orgId,
                        UUID.fromString(managerUserId),
                        "RECONCILIATION_SUBMITTED",
                        Map.of(
                                "planId", event.getAggregateId().toString(),
                                "message", (ownerName != null ? ownerName : "A team member")
                                        + " submitted reconciliation for review."
                        )
                ));
            }
        } else if (EventType.REVIEW_SUBMITTED.getValue().equals(eventType)) {
            String decision = extractString(payload, "decision");
            String ownerUserId = extractString(payload, "ownerUserId");
            if ("CHANGES_REQUESTED".equals(decision) && ownerUserId != null) {
                // Manager requests changes → notify IC
                notificationRepository.save(new NotificationEntity(
                        orgId,
                        UUID.fromString(ownerUserId),
                        "CHANGES_REQUESTED",
                        Map.of(
                                "planId", event.getAggregateId().toString(),
                                "message", "Your manager requested changes to your reconciliation."
                        )
                ));
            }
        }
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }
}
