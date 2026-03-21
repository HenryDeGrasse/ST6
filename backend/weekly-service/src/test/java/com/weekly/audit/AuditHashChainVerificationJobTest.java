package com.weekly.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AuditHashChainVerificationJob}.
 *
 * <p>Covers: valid chain (no breaks), broken chain detection, and empty
 * table handling. Uses {@link SimpleMeterRegistry} so counter values can
 * be asserted without mocking.
 */
class AuditHashChainVerificationJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-18T03:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final Instant EVENT_TIME_1 = Instant.parse("2026-03-17T10:00:00Z");
    private static final Instant EVENT_TIME_2 = Instant.parse("2026-03-17T11:00:00Z");

    private AuditEventRepository repository;
    private SimpleMeterRegistry meterRegistry;
    private AuditHashChainVerificationJob job;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        job = new AuditHashChainVerificationJob(repository, meterRegistry, FIXED_CLOCK);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private AuditEventEntity buildEvent(UUID orgId, String action, Instant createdAt) {
        AuditEventEntity event = new AuditEventEntity(
                orgId, UUID.randomUUID(), action, "WeeklyPlan", UUID.randomUUID(),
                "DRAFT", "LOCKED", null, null, null
        );
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        return event;
    }

    private double breakCount() {
        Counter counter = meterRegistry.find("audit_hash_chain_breaks_total").counter();
        return counter == null ? 0.0 : counter.count();
    }

    // ── Empty table ───────────────────────────────────────────────────────────

    @Test
    void emptyTableHandledGracefully() {
        when(repository.findDistinctOrgIds()).thenReturn(Collections.emptyList());

        job.verifyHashChains();

        verify(repository).findDistinctOrgIds();
        assertEquals(0.0, breakCount(), "No breaks expected for empty table");
    }

    // ── Valid chain ───────────────────────────────────────────────────────────

    @Test
    void validChainWithSingleEventProducesNoBreaks() {
        AuditEventEntity event1 = buildEvent(ORG_ID, "PLAN_LOCKED", EVENT_TIME_1);
        String hash1 = AuditEventEntity.computeHash("", event1.buildPayload());
        event1.setHash(hash1);

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event1));

        when(repository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(repository.findByOrgIdOrderByCreatedAtAsc(eq(ORG_ID), any(Pageable.class)))
                .thenReturn(page);

        job.verifyHashChains();

        assertEquals(0.0, breakCount(), "No breaks expected for a valid single-event chain");
    }

    @Test
    void validChainWithTwoEventsProducesNoBreaks() {
        AuditEventEntity event1 = buildEvent(ORG_ID, "PLAN_LOCKED", EVENT_TIME_1);
        String hash1 = AuditEventEntity.computeHash("", event1.buildPayload());
        event1.setHash(hash1);

        AuditEventEntity event2 = buildEvent(ORG_ID, "PLAN_SUBMITTED", EVENT_TIME_2);
        String hash2 = AuditEventEntity.computeHash(hash1, event2.buildPayload());
        event2.setHash(hash2);

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event1, event2));

        when(repository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(repository.findByOrgIdOrderByCreatedAtAsc(eq(ORG_ID), any(Pageable.class)))
                .thenReturn(page);

        job.verifyHashChains();

        assertEquals(0.0, breakCount(), "No breaks expected for a valid two-event chain");
    }

    // ── Broken chain ──────────────────────────────────────────────────────────

    @Test
    void brokenFirstEventHashIsDetected() {
        AuditEventEntity event1 = buildEvent(ORG_ID, "PLAN_LOCKED", EVENT_TIME_1);
        // Tamper: store a wrong hash on the first event
        event1.setHash("000000000000000000000000000000000000000000000000000000000000dead");

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event1));

        when(repository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(repository.findByOrgIdOrderByCreatedAtAsc(eq(ORG_ID), any(Pageable.class)))
                .thenReturn(page);

        job.verifyHashChains();

        assertEquals(1.0, breakCount(), "Tampered first event must be detected as a chain break");
    }

    @Test
    void brokenSecondEventHashIsDetected() {
        AuditEventEntity event1 = buildEvent(ORG_ID, "PLAN_LOCKED", EVENT_TIME_1);
        String hash1 = AuditEventEntity.computeHash("", event1.buildPayload());
        event1.setHash(hash1);

        AuditEventEntity event2 = buildEvent(ORG_ID, "PLAN_SUBMITTED", EVENT_TIME_2);
        // Tamper: store a wrong hash on the second event
        event2.setHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event1, event2));

        when(repository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(repository.findByOrgIdOrderByCreatedAtAsc(eq(ORG_ID), any(Pageable.class)))
                .thenReturn(page);

        job.verifyHashChains();

        assertEquals(1.0, breakCount(), "Tampered second event must be detected as a chain break");
    }

    @Test
    void multipleBreaksInSameOrgAreAllCounted() {
        AuditEventEntity event1 = buildEvent(ORG_ID, "PLAN_LOCKED", EVENT_TIME_1);
        event1.setHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        AuditEventEntity event2 = buildEvent(ORG_ID, "PLAN_SUBMITTED", EVENT_TIME_2);
        event2.setHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event1, event2));

        when(repository.findDistinctOrgIds()).thenReturn(List.of(ORG_ID));
        when(repository.findByOrgIdOrderByCreatedAtAsc(eq(ORG_ID), any(Pageable.class)))
                .thenReturn(page);

        job.verifyHashChains();

        assertEquals(2.0, breakCount(), "Both tampered events must be counted as breaks");
    }
}
