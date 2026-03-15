package com.weekly.notification;

import com.weekly.outbox.OutboxEventEntity;
import com.weekly.outbox.OutboxEventRepository;
import com.weekly.shared.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
