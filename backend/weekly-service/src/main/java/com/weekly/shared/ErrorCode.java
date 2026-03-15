package com.weekly.shared;

/**
 * Standardized API error codes from the PRD (Appendix A).
 * Mirrors the TypeScript ErrorCode enum in @weekly-commitments/contracts.
 */
public enum ErrorCode {
    // 400
    MISSING_IDEMPOTENCY_KEY(400),

    // 401
    UNAUTHORIZED(401),

    // 404
    NOT_FOUND(404),

    // 403
    FORBIDDEN(403),

    // 409
    CONFLICT(409),
    FIELD_FROZEN(409),
    PLAN_NOT_IN_DRAFT(409),
    CARRY_FORWARD_ALREADY_EXECUTED(409),

    // 422
    VALIDATION_ERROR(422),
    MISSING_CHESS_PRIORITY(422),
    MISSING_RCDO_OR_REASON(422),
    CONFLICTING_LINK(422),
    CHESS_RULE_VIOLATION(422),
    MISSING_DELTA_REASON(422),
    MISSING_COMPLETION_STATUS(422),
    INVALID_WEEK_START(422),
    PAST_WEEK_CREATION_BLOCKED(422),
    RCDO_VALIDATION_STALE(422),
    IDEMPOTENCY_KEY_REUSE(422),

    // 500
    INTERNAL_SERVER_ERROR(500),

    // 503
    SERVICE_UNAVAILABLE(503);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
