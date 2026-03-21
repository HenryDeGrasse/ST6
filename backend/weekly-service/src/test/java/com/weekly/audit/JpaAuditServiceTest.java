package com.weekly.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JpaAuditService}: verifies that the service
 * fetches the previous event's hash and computes the new hash correctly.
 */
class JpaAuditServiceTest {

    private AuditEventRepository repository;
    private JpaAuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        service = new JpaAuditService(repository);
    }

    @Test
    void firstEventUsesEmptyStringAsPreviousHash() {
        UUID orgId = UUID.randomUUID();
        when(repository.findTopByOrgIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Optional.empty());
        when(repository.save(any(AuditEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.record(orgId, UUID.randomUUID(), "PLAN_LOCKED", "WeeklyPlan",
                UUID.randomUUID(), "DRAFT", "LOCKED", null, null, null);

        verify(repository).findTopByOrgIdOrderByCreatedAtDesc(orgId);
        verify(repository).save(argThat(event -> {
            // Must have a non-empty hash
            if (event.getHash() == null || event.getHash().isEmpty()) {
                return false;
            }
            // Must equal computeHash("", payload)
            String expected = AuditEventEntity.computeHash("", event.buildPayload());
            return expected.equals(event.getHash());
        }));
    }

    @Test
    void subsequentEventChainsToPreviousHash() {
        UUID orgId = UUID.randomUUID();
        String knownPreviousHash = "abc123knownprevioushash";

        AuditEventEntity previousEvent = new AuditEventEntity(
                orgId, UUID.randomUUID(), "PLAN_LOCKED", "WeeklyPlan",
                UUID.randomUUID(), "DRAFT", "LOCKED", null, null, null
        );
        previousEvent.setHash(knownPreviousHash);

        when(repository.findTopByOrgIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Optional.of(previousEvent));
        when(repository.save(any(AuditEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.record(orgId, UUID.randomUUID(), "PLAN_SUBMITTED", "WeeklyPlan",
                UUID.randomUUID(), "LOCKED", "SUBMITTED", null, null, null);

        verify(repository).save(argThat(event -> {
            String expected = AuditEventEntity.computeHash(
                    knownPreviousHash, event.buildPayload());
            return expected.equals(event.getHash());
        }));
    }

    @Test
    void hashIsAlways64HexChars() {
        UUID orgId = UUID.randomUUID();
        when(repository.findTopByOrgIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Optional.empty());
        when(repository.save(any(AuditEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.record(orgId, UUID.randomUUID(), "ACTION", "AggType",
                UUID.randomUUID(), null, null, null, null, null);

        verify(repository).save(argThat(event ->
                event.getHash() != null && event.getHash().matches("[0-9a-f]{64}")
        ));
    }

    @Test
    void previousHashNullFallsBackToEmptyString() {
        UUID orgId = UUID.randomUUID();

        // Previous event exists but its hash field is null (pre-migration data)
        AuditEventEntity previousEvent = new AuditEventEntity(
                orgId, UUID.randomUUID(), "PLAN_LOCKED", "WeeklyPlan",
                UUID.randomUUID(), "DRAFT", "LOCKED", null, null, null
        );
        // hash is not set → null

        when(repository.findTopByOrgIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Optional.of(previousEvent));
        when(repository.save(any(AuditEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.record(orgId, UUID.randomUUID(), "PLAN_SUBMITTED", "WeeklyPlan",
                UUID.randomUUID(), "LOCKED", "SUBMITTED", null, null, null);

        verify(repository).save(argThat(event -> {
            String expected = AuditEventEntity.computeHash("", event.buildPayload());
            return expected.equals(event.getHash());
        }));
    }

    @Test
    void savedEventHasCorrectOrgAndAction() {
        UUID orgId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        when(repository.findTopByOrgIdOrderByCreatedAtDesc(orgId))
                .thenReturn(Optional.empty());
        when(repository.save(any(AuditEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.record(orgId, actorId, "PLAN_LOCKED", "WeeklyPlan",
                aggregateId, "DRAFT", "LOCKED", "reason", "10.0.0.1", "corr-42");

        verify(repository).save(argThat(event -> {
            assertEquals(orgId, event.getOrgId());
            assertEquals(actorId, event.getActorUserId());
            assertEquals("PLAN_LOCKED", event.getAction());
            assertNotNull(event.getHash());
            return true;
        }));
    }
}
