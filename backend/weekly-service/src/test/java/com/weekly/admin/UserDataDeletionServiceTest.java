package com.weekly.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.audit.AuditEventEntity;
import com.weekly.audit.AuditEventRepository;
import com.weekly.audit.AuditService;
import com.weekly.idempotency.IdempotencyKeyRepository;
import com.weekly.notification.NotificationRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link UserDataDeletionService}.
 *
 * <p>Verifies that all GDPR deletion steps (soft-delete plans, soft-delete commits,
 * anonymise audit events, delete notifications, delete idempotency keys, audit the
 * deletion itself) are invoked correctly.
 */
class UserDataDeletionServiceTest {

    private WeeklyPlanRepository planRepository;
    private WeeklyCommitRepository commitRepository;
    private AuditEventRepository auditEventRepository;
    private NotificationRepository notificationRepository;
    private IdempotencyKeyRepository idempotencyKeyRepository;
    private AuditService auditService;
    private UserDataDeletionService service;

    private static final UUID ORG_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        planRepository = mock(WeeklyPlanRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        auditEventRepository = mock(AuditEventRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        idempotencyKeyRepository = mock(IdempotencyKeyRepository.class);
        auditService = mock(AuditService.class);

        service = new UserDataDeletionService(
                planRepository,
                commitRepository,
                auditEventRepository,
                notificationRepository,
                idempotencyKeyRepository,
                auditService
        );

        // Default stubs
        when(commitRepository.softDeleteCommitsByUser(any(), any())).thenReturn(0);
        when(planRepository.softDeletePlansByUser(any(), any())).thenReturn(0);
        when(auditEventRepository.anonymizeActorUserId(any(), any(), any())).thenReturn(0);
        when(notificationRepository.deleteByOrgIdAndUserId(any(), any())).thenReturn(0);
        when(idempotencyKeyRepository.deleteByOrgIdAndUserId(any(), any())).thenReturn(0);
    }

    private AuditEventEntity auditEvent(UUID actorUserId, String action, Instant createdAt) {
        AuditEventEntity event = new AuditEventEntity(
                ORG_ID,
                actorUserId,
                action,
                "WeeklyPlan",
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        ReflectionTestUtils.setField(event, "hash", "stale-hash");
        return event;
    }

    // ── Soft-delete plans ────────────────────────────────────

    @Test
    void softDeletesPlansForUser() {
        when(planRepository.softDeletePlansByUser(ORG_ID, USER_ID)).thenReturn(3);

        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        verify(planRepository).softDeletePlansByUser(ORG_ID, USER_ID);
    }

    @Test
    void softDeletesCommitsForUser() {
        when(commitRepository.softDeleteCommitsByUser(ORG_ID, USER_ID)).thenReturn(7);

        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        verify(commitRepository).softDeleteCommitsByUser(ORG_ID, USER_ID);
    }

    // ── Audit event anonymisation ────────────────────────────

    @Test
    void anonymisesAuditEventsNotDeletes() {
        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        // Must call anonymize, NOT delete
        verify(auditEventRepository).anonymizeActorUserId(eq(ORG_ID), eq(USER_ID), any(UUID.class));
    }

    @Test
    void anonymisedIdIsDeterministicForSameUserId() {
        UUID first = service.computeAnonymisedUserId(USER_ID);
        UUID second = service.computeAnonymisedUserId(USER_ID);

        assertEquals(first, second,
                "computeAnonymisedUserId must return the same UUID for the same input");
    }

    @Test
    void anonymisedIdDiffersFromOriginal() {
        UUID anonymised = service.computeAnonymisedUserId(USER_ID);

        assertNotEquals(USER_ID, anonymised,
                "The anonymised UUID must differ from the original user ID");
    }

    @Test
    void anonymisedIdsAreDifferentForDifferentUsers() {
        UUID other = UUID.fromString("99000000-0000-0000-0000-000000000099");

        UUID firstAnonymised = service.computeAnonymisedUserId(USER_ID);
        UUID secondAnonymised = service.computeAnonymisedUserId(other);

        assertNotEquals(firstAnonymised, secondAnonymised,
                "Different users must produce different anonymised IDs");
    }

    @Test
    void anonymisedIdIsNotNull() {
        UUID anonymised = service.computeAnonymisedUserId(USER_ID);
        assertNotNull(anonymised);
    }

    @Test
    void rehashesAuditChainAfterAnonymisationBeforeRecordingDeletionEvent() {
        when(auditEventRepository.anonymizeActorUserId(ORG_ID, USER_ID, service.computeAnonymisedUserId(USER_ID)))
                .thenReturn(2);

        AuditEventEntity event1 = auditEvent(
                service.computeAnonymisedUserId(USER_ID),
                "PLAN_CREATED",
                Instant.parse("2026-03-17T09:00:00Z")
        );
        AuditEventEntity event2 = auditEvent(
                UUID.randomUUID(),
                "PLAN_SUBMITTED",
                Instant.parse("2026-03-17T10:00:00Z")
        );
        when(auditEventRepository.findAllByOrgIdOrderByCreatedAtAsc(ORG_ID))
                .thenReturn(List.of(event1, event2));

        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        String expectedHash1 = event1.computeChainedHash("");
        String expectedHash2 = event2.computeChainedHash(expectedHash1);

        InOrder order = inOrder(auditEventRepository, auditService);
        order.verify(auditEventRepository).anonymizeActorUserId(
                ORG_ID,
                USER_ID,
                service.computeAnonymisedUserId(USER_ID)
        );
        order.verify(auditEventRepository).findAllByOrgIdOrderByCreatedAtAsc(ORG_ID);
        order.verify(auditEventRepository).updateHashById(event1.getId(), expectedHash1);
        order.verify(auditEventRepository).updateHashById(event2.getId(), expectedHash2);
        order.verify(auditService).record(
                eq(ORG_ID),
                eq(ADMIN_ID),
                eq("USER_DATA_DELETED"),
                eq("User"),
                eq(USER_ID),
                isNull(),
                isNull(),
                any(String.class),
                isNull(),
                isNull()
        );
    }

    @Test
    void skipsAuditRehashWhenNoAuditRowsWereAnonymised() {
        when(auditEventRepository.anonymizeActorUserId(any(), any(), any())).thenReturn(0);

        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        verify(auditEventRepository, never()).findAllByOrgIdOrderByCreatedAtAsc(any());
        verify(auditEventRepository, never()).updateHashById(any(), any());
    }

    // ── Notifications deletion ───────────────────────────────

    @Test
    void deletesNotificationsForUser() {
        when(notificationRepository.deleteByOrgIdAndUserId(ORG_ID, USER_ID)).thenReturn(4);

        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        verify(notificationRepository).deleteByOrgIdAndUserId(ORG_ID, USER_ID);
    }

    // ── Idempotency key deletion ─────────────────────────────

    @Test
    void deletesIdempotencyKeysForUser() {
        when(idempotencyKeyRepository.deleteByOrgIdAndUserId(ORG_ID, USER_ID)).thenReturn(2);

        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        verify(idempotencyKeyRepository).deleteByOrgIdAndUserId(ORG_ID, USER_ID);
    }

    // ── Self-audit of deletion request ──────────────────────

    @Test
    void recordsAuditEventForDeletion() {
        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, "10.0.0.1", "corr-123");

        verify(auditService).record(
                eq(ORG_ID),
                eq(ADMIN_ID),
                eq("USER_DATA_DELETED"),
                eq("User"),
                eq(USER_ID),
                isNull(),
                isNull(),
                any(String.class),
                eq("10.0.0.1"),
                eq("corr-123")
        );
    }

    @Test
    void auditEventRecordedWithAdminIdNotUserId() {
        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        // The actor in the audit event must be the admin, not the deleted user
        verify(auditService).record(
                eq(ORG_ID),
                eq(ADMIN_ID),          // actor = admin
                eq("USER_DATA_DELETED"),
                eq("User"),
                eq(USER_ID),           // aggregate = deleted user
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    // ── All steps execute ────────────────────────────────────

    @Test
    void allDeletionStepsAreInvoked() {
        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        verify(commitRepository).softDeleteCommitsByUser(ORG_ID, USER_ID);
        verify(planRepository).softDeletePlansByUser(ORG_ID, USER_ID);
        verify(auditEventRepository).anonymizeActorUserId(eq(ORG_ID), eq(USER_ID), any());
        verify(notificationRepository).deleteByOrgIdAndUserId(ORG_ID, USER_ID);
        verify(idempotencyKeyRepository).deleteByOrgIdAndUserId(ORG_ID, USER_ID);
        verify(auditService).record(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void commitsAreSoftDeletedBeforePlans() {
        // Commits must be soft-deleted first (while the plan→owner mapping is still queriable)
        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        InOrder order = inOrder(commitRepository, planRepository);
        order.verify(commitRepository).softDeleteCommitsByUser(ORG_ID, USER_ID);
        order.verify(planRepository).softDeletePlansByUser(ORG_ID, USER_ID);
    }

    @Test
    void handlesZeroRowsForEachOperationGracefully() {
        // All stubs return 0 — service must not throw
        service.deleteUserData(ORG_ID, USER_ID, ADMIN_ID, null, null);

        // Verify all operations were still called
        verify(commitRepository).softDeleteCommitsByUser(ORG_ID, USER_ID);
        verify(planRepository).softDeletePlansByUser(ORG_ID, USER_ID);
        verify(auditEventRepository).anonymizeActorUserId(eq(ORG_ID), eq(USER_ID), any());
        verify(notificationRepository).deleteByOrgIdAndUserId(ORG_ID, USER_ID);
        verify(idempotencyKeyRepository).deleteByOrgIdAndUserId(ORG_ID, USER_ID);
    }
}
