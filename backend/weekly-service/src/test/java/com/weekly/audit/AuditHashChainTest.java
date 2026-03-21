package com.weekly.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the audit hash chain (§14.7).
 *
 * <p>Verifies: first-event anchor uses empty previous hash, subsequent events
 * chain correctly, hash is deterministic, and any field mutation produces
 * a different hash.
 */
class AuditHashChainTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID AGGREGATE = UUID.randomUUID();
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-03-18T12:00:00Z");

    private AuditEventEntity buildEvent(String action, String prev, String next, String reason) {
        AuditEventEntity event = new AuditEventEntity(
                ORG, ACTOR, action, "WeeklyPlan", AGGREGATE,
                prev, next, reason, "127.0.0.1", "corr-1"
        );
        ReflectionTestUtils.setField(event, "createdAt", FIXED_CREATED_AT);
        return event;
    }

    // ── (a) First event in chain uses empty string as previousHash ────────────

    @Test
    void firstEventHashIsNonNullAndSixtyFourChars() {
        AuditEventEntity event = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", null);
        String payload = event.buildPayload();
        String hash = AuditEventEntity.computeHash("", payload);

        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hex digest must be 64 characters");
    }

    @Test
    void firstEventWithEmptyPreviousHashProducesValidHex() {
        AuditEventEntity event = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", null);
        String hash = AuditEventEntity.computeHash("", event.buildPayload());

        assertTrue(hash.matches("[0-9a-f]{64}"),
                "Hash must be lowercase hex: " + hash);
    }

    // ── (b) Subsequent events chain correctly ─────────────────────────────────

    @Test
    void secondEventIncorporatesFirstHash() {
        AuditEventEntity first = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", null);
        String firstHash = AuditEventEntity.computeHash("", first.buildPayload());
        first.setHash(firstHash);

        AuditEventEntity second = buildEvent("PLAN_SUBMITTED", "LOCKED", "SUBMITTED", null);
        String secondHash = AuditEventEntity.computeHash(firstHash, second.buildPayload());
        second.setHash(secondHash);

        assertNotNull(secondHash);
        assertNotEquals(firstHash, secondHash,
                "Second hash must differ from first hash");

        // Recomputing with the same inputs must yield the same hash
        String recomputed = AuditEventEntity.computeHash(firstHash, second.buildPayload());
        assertEquals(secondHash, recomputed,
                "Chain hash must be reproducible from previousHash + payload");
    }

    @Test
    void chainBreaksWhenFirstHashIsAltered() {
        AuditEventEntity first = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", null);
        String originalFirstHash = AuditEventEntity.computeHash("", first.buildPayload());

        AuditEventEntity second = buildEvent("PLAN_SUBMITTED", "LOCKED", "SUBMITTED", null);
        String legitSecondHash = AuditEventEntity.computeHash(
                originalFirstHash, second.buildPayload());

        // Tamper: use a corrupted first hash
        String tamperedSecondHash = AuditEventEntity.computeHash(
                "tampered_hash", second.buildPayload());

        assertNotEquals(legitSecondHash, tamperedSecondHash,
                "Altering previousHash must produce a different chained hash");
    }

    // ── (c) Hash is deterministic for the same input ──────────────────────────

    @Test
    void computeHashIsDeterministicForSameInput() {
        String hash1 = AuditEventEntity.computeHash("prevHash", "payload");
        String hash2 = AuditEventEntity.computeHash("prevHash", "payload");

        assertEquals(hash1, hash2,
                "computeHash must return the same value for identical inputs");
    }

    @Test
    void buildPayloadIsDeterministicForSameEvent() {
        AuditEventEntity event = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", "reason");
        String payload1 = event.buildPayload();
        String payload2 = event.buildPayload();

        assertEquals(payload1, payload2,
                "buildPayload on the same entity must be stable");
    }

    // ── (d) Modifying any field produces a different hash ─────────────────────

    @Test
    void differentActionProducesDifferentHash() {
        AuditEventEntity original = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", null);
        AuditEventEntity modified = buildEvent("PLAN_DELETED", "DRAFT", "LOCKED", null);

        assertNotEquals(
                AuditEventEntity.computeHash("", original.buildPayload()),
                AuditEventEntity.computeHash("", modified.buildPayload()),
                "Different action must produce a different hash"
        );
    }

    @Test
    void differentReasonProducesDifferentHash() {
        AuditEventEntity original = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", "original");
        AuditEventEntity modified = buildEvent("PLAN_LOCKED", "DRAFT", "LOCKED", "tampered");

        assertNotEquals(
                AuditEventEntity.computeHash("", original.buildPayload()),
                AuditEventEntity.computeHash("", modified.buildPayload()),
                "Different reason must produce a different hash"
        );
    }

    @Test
    void differentOrgIdProducesDifferentHash() {
        AuditEventEntity event1 = new AuditEventEntity(
                UUID.randomUUID(), ACTOR, "PLAN_LOCKED", "WeeklyPlan", AGGREGATE,
                "DRAFT", "LOCKED", null, null, null
        );
        AuditEventEntity event2 = new AuditEventEntity(
                UUID.randomUUID(), ACTOR, "PLAN_LOCKED", "WeeklyPlan", AGGREGATE,
                "DRAFT", "LOCKED", null, null, null
        );
        ReflectionTestUtils.setField(event1, "createdAt", FIXED_CREATED_AT);
        ReflectionTestUtils.setField(event2, "createdAt", FIXED_CREATED_AT);

        assertNotEquals(
                AuditEventEntity.computeHash("", event1.buildPayload()),
                AuditEventEntity.computeHash("", event2.buildPayload()),
                "Different orgId must produce a different hash"
        );
    }

    @Test
    void differentActorProducesDifferentHash() {
        AuditEventEntity event1 = new AuditEventEntity(
                ORG, UUID.randomUUID(), "PLAN_LOCKED", "WeeklyPlan", AGGREGATE,
                "DRAFT", "LOCKED", null, null, null
        );
        AuditEventEntity event2 = new AuditEventEntity(
                ORG, UUID.randomUUID(), "PLAN_LOCKED", "WeeklyPlan", AGGREGATE,
                "DRAFT", "LOCKED", null, null, null
        );
        ReflectionTestUtils.setField(event1, "createdAt", FIXED_CREATED_AT);
        ReflectionTestUtils.setField(event2, "createdAt", FIXED_CREATED_AT);

        assertNotEquals(
                AuditEventEntity.computeHash("", event1.buildPayload()),
                AuditEventEntity.computeHash("", event2.buildPayload()),
                "Different actorUserId must produce a different hash"
        );
    }
}
