package com.weekly.integration;

import java.util.List;

/**
 * Response body for {@code GET /api/v1/commits/{commitId}/linked-tickets}.
 *
 * <p>Returns all external ticket links associated with the given commit.
 */
public record LinkedTicketsResponse(
        String commitId,
        List<ExternalTicketLinkResponse> links
) {
}
