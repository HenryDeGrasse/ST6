package com.weekly.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies ErrorCode enum aligns with the PRD Appendix A
 * and mirrors the TypeScript contracts ErrorCode.
 */
class ErrorCodeTest {

    @Test
    void hasExpectedNumberOfCodes() {
        assertEquals(21, ErrorCode.values().length);
    }

    @Test
    void internalServerErrorMapsTo500() {
        assertEquals(500, ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    }

    @Test
    void unauthorizedMapsTo401() {
        assertEquals(401, ErrorCode.UNAUTHORIZED.getHttpStatus());
    }

    @Test
    void notFoundMapsTo404() {
        assertEquals(404, ErrorCode.NOT_FOUND.getHttpStatus());
    }

    @Test
    void forbiddenMapsTo403() {
        assertEquals(403, ErrorCode.FORBIDDEN.getHttpStatus());
    }

    @Test
    void conflictCodesMaTo409() {
        assertEquals(409, ErrorCode.CONFLICT.getHttpStatus());
        assertEquals(409, ErrorCode.FIELD_FROZEN.getHttpStatus());
        assertEquals(409, ErrorCode.PLAN_NOT_IN_DRAFT.getHttpStatus());
        assertEquals(409, ErrorCode.CARRY_FORWARD_ALREADY_EXECUTED.getHttpStatus());
    }

    @Test
    void validationCodesMaTo422() {
        ErrorCode[] codes422 = {
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.MISSING_CHESS_PRIORITY,
                ErrorCode.MISSING_RCDO_OR_REASON,
                ErrorCode.CONFLICTING_LINK,
                ErrorCode.CHESS_RULE_VIOLATION,
                ErrorCode.MISSING_DELTA_REASON,
                ErrorCode.MISSING_COMPLETION_STATUS,
                ErrorCode.INVALID_WEEK_START,
                ErrorCode.PAST_WEEK_CREATION_BLOCKED,
                ErrorCode.RCDO_VALIDATION_STALE,
                ErrorCode.IDEMPOTENCY_KEY_REUSE,
        };
        for (ErrorCode code : codes422) {
            assertEquals(422, code.getHttpStatus(),
                    code.name() + " should map to 422");
        }
    }

    @Test
    void serviceUnavailableMapsTo503() {
        assertEquals(503, ErrorCode.SERVICE_UNAVAILABLE.getHttpStatus());
    }

    @Test
    void allStatusCodesAreStandardHttp() {
        for (ErrorCode code : ErrorCode.values()) {
            int status = code.getHttpStatus();
            assertTrue(status >= 400 && status < 600,
                    code.name() + " has status " + status + " outside 4xx/5xx range");
        }
    }
}
