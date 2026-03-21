package com.weekly.integration;

/**
 * Response body representing a single external ticket link.
 *
 * <p>Returned by:
 * <ul>
 *   <li>{@code POST /api/v1/integrations/link-ticket} (the newly created link)</li>
 *   <li>{@code GET  /api/v1/commits/{commitId}/linked-tickets} (each item in the list)</li>
 * </ul>
 */
public record ExternalTicketLinkResponse(
        String id,
        String orgId,
        String commitId,
        String provider,
        String externalTicketId,
        String externalTicketUrl,
        String externalStatus,
        String lastSyncedAt,
        String createdAt
) {

    /** Maps a JPA entity to its API response shape. */
    public static ExternalTicketLinkResponse from(ExternalTicketLinkEntity entity) {
        return new ExternalTicketLinkResponse(
                entity.getId().toString(),
                entity.getOrgId().toString(),
                entity.getCommitId().toString(),
                entity.getProvider(),
                entity.getExternalTicketId(),
                entity.getExternalTicketUrl(),
                entity.getExternalStatus(),
                entity.getLastSyncedAt() != null ? entity.getLastSyncedAt().toString() : null,
                entity.getCreatedAt().toString()
        );
    }
}
