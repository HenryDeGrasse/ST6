package com.weekly.plan.dto;

import java.util.List;

/**
 * API response DTO for the GET /api/v1/commits/{commitId}/check-ins endpoint.
 *
 * <p>Returns the complete, ordered (oldest-first) append-only history of
 * check-in entries for the given commit.
 */
public record CheckInHistoryResponse(
        String commitId,
        List<CheckInEntryResponse> entries
) {
}
