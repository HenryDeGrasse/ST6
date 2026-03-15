package com.weekly.plan.domain;

/**
 * Plan lifecycle states (§6 of the PRD).
 */
public enum PlanState {
    DRAFT,
    LOCKED,
    RECONCILING,
    RECONCILED,
    CARRY_FORWARD
}
