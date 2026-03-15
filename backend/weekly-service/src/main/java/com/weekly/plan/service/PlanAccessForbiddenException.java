package com.weekly.plan.service;

/**
 * Thrown when the authenticated user attempts to mutate a plan they do not own.
 */
public class PlanAccessForbiddenException extends RuntimeException {

    public PlanAccessForbiddenException(String message) {
        super(message);
    }
}
