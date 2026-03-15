package com.weekly.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies EventType enum aligns with the PRD Appendix B
 * and mirrors the TypeScript contracts EventType.
 */
class EventTypeTest {

    @Test
    void hasExpectedNumberOfEventTypes() {
        assertEquals(10, EventType.values().length);
    }

    @Test
    void planEventsUseDotNotation() {
        assertEquals("plan.created", EventType.PLAN_CREATED.getValue());
        assertEquals("plan.locked", EventType.PLAN_LOCKED.getValue());
        assertEquals("plan.reconciliation_started", EventType.PLAN_RECONCILIATION_STARTED.getValue());
        assertEquals("plan.reconciled", EventType.PLAN_RECONCILED.getValue());
        assertEquals("plan.carry_forward", EventType.PLAN_CARRY_FORWARD.getValue());
    }

    @Test
    void reviewEventsUseDotNotation() {
        assertEquals("review.submitted", EventType.REVIEW_SUBMITTED.getValue());
    }

    @Test
    void commitEventsUseDotNotation() {
        assertEquals("commit.created", EventType.COMMIT_CREATED.getValue());
        assertEquals("commit.updated", EventType.COMMIT_UPDATED.getValue());
        assertEquals("commit.deleted", EventType.COMMIT_DELETED.getValue());
        assertEquals("commit.actual_updated", EventType.COMMIT_ACTUAL_UPDATED.getValue());
    }

    @Test
    void allEventTypeValuesFollowNamingConvention() {
        for (EventType type : EventType.values()) {
            assertTrue(type.getValue().matches("[a-z]+\\.[a-z_]+"),
                    type.name() + " value '" + type.getValue()
                            + "' does not match expected pattern");
        }
    }
}
