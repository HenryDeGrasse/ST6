package com.weekly.notification;

import com.weekly.outbox.OutboxEventEntity;
import com.weekly.outbox.OutboxEventRepository;
import com.weekly.shared.EventType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        } else if (EventType.WEEKLY_DIGEST.getValue().equals(eventType)) {
            materializeWeeklyDigest(orgId, payload);
        }
    }

    /**
     * Renders a {@code WEEKLY_DIGEST} outbox event into an in-app notification for
     * the target manager.
     *
     * <p>Template: surfaces the key metrics in the notification payload so the
     * frontend can render a rich digest card or fallback to the plain {@code message}
     * field for simple display.
     */
    private void materializeWeeklyDigest(UUID orgId, Map<String, Object> payload) {
        String managerId = extractString(payload, "managerId");
        if (managerId == null) {
            LOG.warn("NotificationMaterializer: WEEKLY_DIGEST event missing managerId");
            return;
        }

        String weekStart = extractString(payload, "weekStart");
        int totalMembers = extractInt(payload, "totalMemberCount");
        int reconciledCount = extractInt(payload, "reconciledCount");
        int reviewQueueSize = extractInt(payload, "reviewQueueSize");
        int staleCount = extractInt(payload, "staleCount");
        int doneEarlyCount = extractInt(payload, "doneEarlyCount");
        double rcdoAlignmentRate = extractDouble(payload, "rcdoAlignmentRate");
        Double previousRcdoAlignmentRate = extractNullableDouble(payload, "previousRcdoAlignmentRate");

        // Build a human-readable summary message (fallback for simple renderers)
        String message = buildDigestMessage(weekStart, totalMembers, reconciledCount,
                reviewQueueSize, staleCount, rcdoAlignmentRate, previousRcdoAlignmentRate, doneEarlyCount);

        // Include the full structured payload for rich frontend rendering
        Map<String, Object> notificationPayload = new java.util.HashMap<>(payload);
        notificationPayload.put("message", message);

        notificationRepository.save(new NotificationEntity(
                orgId,
                UUID.fromString(managerId),
                "WEEKLY_DIGEST",
                notificationPayload
        ));
    }

    private static String buildDigestMessage(
            String weekStart,
            int totalMembers,
            int reconciledCount,
            int reviewQueueSize,
            int staleCount,
            double rcdoAlignmentRate,
            Double previousRcdoAlignmentRate,
            int doneEarlyCount
    ) {
        StringBuilder sb = new StringBuilder("Weekly digest");
        if (weekStart != null) {
            sb.append(" (w/c ").append(weekStart).append(")");
        }
        sb.append(": ");
        sb.append(reconciledCount).append("/").append(totalMembers).append(" reconciled");
        if (reviewQueueSize > 0) {
            sb.append(", ").append(reviewQueueSize).append(" pending review");
        }
        if (staleCount > 0) {
            sb.append(", ").append(staleCount).append(" stale");
        }
        int alignmentPct = (int) Math.round(rcdoAlignmentRate * 100);
        sb.append(", ").append(alignmentPct).append("% RCDO aligned");
        if (previousRcdoAlignmentRate != null) {
            int previousAlignmentPct = (int) Math.round(previousRcdoAlignmentRate * 100);
            sb.append(" (vs ").append(previousAlignmentPct).append("% last week)");
        }
        if (doneEarlyCount > 0) {
            sb.append(", ").append(doneEarlyCount).append(" done early");
        }
        sb.append(".");
        return sb.toString();
    }

    private int extractInt(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private double extractDouble(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    private Double extractNullableDouble(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }
}
