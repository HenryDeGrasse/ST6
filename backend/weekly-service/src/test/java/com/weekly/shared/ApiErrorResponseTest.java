package com.weekly.shared;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the ApiErrorResponse record structure matches the PRD
 * error envelope: { "error": { "code": "...", "message": "...", "details": [...] } }
 */
class ApiErrorResponseTest {

    @Test
    void ofWithCodeAndMessageCreatesEmptyDetails() {
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.CONFLICT, "Version mismatch");
        assertNotNull(response.error());
        assertEquals("CONFLICT", response.error().code());
        assertEquals("Version mismatch", response.error().message());
        assertTrue(response.error().details().isEmpty());
    }

    @Test
    void ofWithDetailsIncludesDetailsList() {
        List<Map<String, Object>> details = List.of(
                Map.of("commitIds", List.of("id-1", "id-2"))
        );
        ApiErrorResponse response = ApiErrorResponse.of(
                ErrorCode.MISSING_CHESS_PRIORITY,
                "Commits missing chess priority",
                details
        );
        assertEquals(1, response.error().details().size());
        assertEquals("MISSING_CHESS_PRIORITY", response.error().code());
    }
}
