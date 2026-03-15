package com.weekly.plan.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the result of lock-time validation across all commits in a plan.
 */
public record LockValidationResult(
        boolean valid,
        List<Map<String, Object>> details
) {

    /**
     * A successful validation with no errors.
     */
    public static LockValidationResult success() {
        return new LockValidationResult(true, List.of());
    }

    /**
     * A failed validation with error details.
     */
    public static LockValidationResult failure(List<Map<String, Object>> details) {
        return new LockValidationResult(false, details);
    }

    /**
     * Creates a single commit-level error detail.
     */
    public static Map<String, Object> commitError(UUID commitId, String code, String message) {
        return Map.of(
                "commitId", commitId.toString(),
                "code", code,
                "message", message
        );
    }

    /**
     * Creates a plan-level error detail (e.g., chess rule violations).
     */
    public static Map<String, Object> planError(String code, String message) {
        return Map.of("code", code, "message", message);
    }

    /**
     * Creates a plan-level error detail with extra fields.
     */
    public static Map<String, Object> planError(String code, String message, String rule, int expected, int actual) {
        return Map.of(
                "code", code,
                "message", message,
                "rule", rule,
                "expected", expected,
                "actual", actual
        );
    }
}
