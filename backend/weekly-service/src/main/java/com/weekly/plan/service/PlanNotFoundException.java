package com.weekly.plan.service;

import java.util.UUID;

/**
 * Thrown when a plan is not found by ID.
 */
public class PlanNotFoundException extends RuntimeException {

    private final UUID planId;

    public PlanNotFoundException(UUID planId) {
        super("Plan not found: " + planId);
        this.planId = planId;
    }

    public UUID getPlanId() {
        return planId;
    }
}
