package com.weekly.plan.service;

import com.weekly.shared.ErrorCode;

import java.util.List;
import java.util.Map;

/**
 * Thrown when plan validation fails (lock validation, chess rules, etc.).
 */
public class PlanValidationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<Map<String, Object>> details;

    public PlanValidationException(ErrorCode errorCode, String message, List<Map<String, Object>> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<Map<String, Object>> getDetails() {
        return details;
    }
}
