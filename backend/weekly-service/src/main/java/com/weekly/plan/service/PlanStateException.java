package com.weekly.plan.service;

import com.weekly.shared.ErrorCode;

/**
 * Thrown when an operation is invalid for the current plan state.
 */
public class PlanStateException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String planState;

    public PlanStateException(ErrorCode errorCode, String message, String planState) {
        super(message);
        this.errorCode = errorCode;
        this.planState = planState;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getPlanState() {
        return planState;
    }
}
