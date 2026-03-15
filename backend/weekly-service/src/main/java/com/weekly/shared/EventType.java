package com.weekly.shared;

/**
 * Domain event types from the PRD (Appendix B).
 * Mirrors the TypeScript EventType enum.
 */
public enum EventType {
    PLAN_CREATED("plan.created"),
    PLAN_LOCKED("plan.locked"),
    PLAN_RECONCILIATION_STARTED("plan.reconciliation_started"),
    PLAN_RECONCILED("plan.reconciled"),
    PLAN_CARRY_FORWARD("plan.carry_forward"),
    REVIEW_SUBMITTED("review.submitted"),
    COMMIT_CREATED("commit.created"),
    COMMIT_UPDATED("commit.updated"),
    COMMIT_DELETED("commit.deleted"),
    COMMIT_ACTUAL_UPDATED("commit.actual_updated");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
