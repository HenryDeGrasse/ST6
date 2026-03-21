package com.weekly.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.outbox.OutboxEventEntity;
import com.weekly.outbox.OutboxEventRepository;
import com.weekly.shared.EventType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NotificationMaterializer}: outbox event processing
 * and notification materialization.
 */
class NotificationMaterializerTest {

    private OutboxEventRepository outboxEventRepository;
    private NotificationRepository notificationRepository;
    private NotificationMaterializer materializer;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        materializer = new NotificationMaterializer(outboxEventRepository, notificationRepository);
    }

    @Test
    void materializesReconciliationSubmittedNotification() {
        UUID orgId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                EventType.PLAN_RECONCILED.getValue(),
                "WeeklyPlan",
                planId,
                orgId,
                Map.of("managerUserId", managerId.toString(),
                        "ownerUserName", "Alice")
        );
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        materializer.processOutboxEvents();

        verify(notificationRepository).save(argThat(n ->
                n.getType().equals("RECONCILIATION_SUBMITTED")
                        && n.getUserId().equals(managerId)
        ));
        verify(outboxEventRepository).save(argThat(e -> e.getPublishedAt() != null));
    }

    @Test
    void materializesChangesRequestedNotification() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                EventType.REVIEW_SUBMITTED.getValue(),
                "WeeklyPlan",
                planId,
                orgId,
                Map.of("decision", "CHANGES_REQUESTED",
                        "ownerUserId", ownerId.toString())
        );
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        materializer.processOutboxEvents();

        verify(notificationRepository).save(argThat(n ->
                n.getType().equals("CHANGES_REQUESTED")
                        && n.getUserId().equals(ownerId)
        ));
    }

    @Test
    void doesNotCreateNotificationForApprovalReview() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                EventType.REVIEW_SUBMITTED.getValue(),
                "WeeklyPlan",
                planId,
                orgId,
                Map.of("decision", "APPROVED",
                        "ownerUserId", ownerId.toString())
        );
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        materializer.processOutboxEvents();

        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }

    @Test
    void leavesEventUnpublishedWhenNotificationWriteFails() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                EventType.REVIEW_SUBMITTED.getValue(),
                "WeeklyPlan",
                UUID.randomUUID(),
                orgId,
                Map.of("decision", "CHANGES_REQUESTED",
                        "ownerUserId", ownerId.toString())
        );
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc())
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("db unavailable"))
                .when(notificationRepository)
                .save(any(NotificationEntity.class));

        materializer.processOutboxEvents();

        verify(outboxEventRepository, never()).save(any(OutboxEventEntity.class));
    }

    @Test
    void materializesWeeklyDigestNotificationWithTrendMessage() {
        UUID orgId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                EventType.WEEKLY_DIGEST.getValue(),
                "WeeklyDigest",
                managerId,
                orgId,
                Map.ofEntries(
                        Map.entry("managerId", managerId.toString()),
                        Map.entry("weekStart", "2026-03-16"),
                        Map.entry("totalMemberCount", 10),
                        Map.entry("reconciledCount", 8),
                        Map.entry("reviewQueueSize", 3),
                        Map.entry("staleCount", 1),
                        Map.entry("carryForwardStreakUserIds", List.of("user-a")),
                        Map.entry("stalePlanUserIds", List.of("user-stale")),
                        Map.entry("lateLockUserIds", List.of("user-b")),
                        Map.entry("rcdoAlignmentRate", 0.92),
                        Map.entry("previousRcdoAlignmentRate", 0.85),
                        Map.entry("doneEarlyCount", 4)
                )
        );
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        materializer.processOutboxEvents();

        verify(notificationRepository).save(argThat(n ->
                n.getType().equals("WEEKLY_DIGEST")
                        && n.getUserId().equals(managerId)
                        && n.getPayload().get("message").toString().contains("92% RCDO aligned")
                        && n.getPayload().get("message").toString().contains("vs 85% last week")
                        && n.getPayload().get("message").toString().contains("4 done early")
                        && n.getPayload().get("stalePlanUserIds").equals(List.of("user-stale"))
        ));
    }

    @Test
    void marksEventsAsPublishedEvenOnUnhandledType() {
        UUID orgId = UUID.randomUUID();

        OutboxEventEntity event = new OutboxEventEntity(
                EventType.PLAN_LOCKED.getValue(),
                "WeeklyPlan",
                UUID.randomUUID(),
                orgId,
                Map.of()
        );
        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByOccurredAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        materializer.processOutboxEvents();

        verify(outboxEventRepository).save(argThat(e -> e.getPublishedAt() != null));
        verify(notificationRepository, never()).save(any(NotificationEntity.class));
    }
}
