package com.weekly.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for the external integration layer (ADR-010).
 *
 * <p>Orchestrates adapter calls, persists {@link ExternalTicketLinkEntity}
 * records, and auto-populates commit fields from ticket data when applicable.
 */
public interface IntegrationService {

    /**
     * Links a commit to an external ticket.
     *
     * <p>When the commit's title is empty, the ticket's title is used.
     * When the commit's description is empty, the ticket's description is used.
     *
     * @param orgId   the org from the auth context
     * @param request link-ticket request (commitId, provider, externalTicketId)
     * @return the created (or existing) link record
     */
    ExternalTicketLinkResponse linkTicket(UUID orgId, LinkTicketRequest request);

    /**
     * Returns all external ticket links for the given commit.
     *
     * @param orgId    the org from the auth context
     * @param commitId the commit to look up
     * @return aggregated response with the commit ID and its links
     */
    LinkedTicketsResponse getLinkedTickets(UUID orgId, UUID commitId);

    /**
     * Processes an inbound webhook payload from an external issue-tracker provider.
     *
     * <p>Extracts the ticket ID and status from the provider-specific payload,
     * maps the status to a {@link com.weekly.plan.domain.ProgressStatus}, and
     * auto-creates check-in entries for every commit linked to that ticket.
     *
     * @param provider       the provider name (e.g. "JIRA", "LINEAR") — case-insensitive
     * @param webhookPayload the raw webhook body as a key/value map
     * @return the number of check-in entries created (0 if the ticket is unknown or
     *         the status is not mappable)
     */
    int processWebhook(String provider, java.util.Map<String, Object> webhookPayload);

    /**
     * Returns unresolved external tickets linked to RCDO-strategic commits
     * owned by the given user.
     *
     * <p>Only tickets linked to commits that have an RCDO outcome set are returned.
     * A ticket is considered "unresolved" when its cached {@code externalStatus} does
     * not indicate a closed/done/resolved state.  Tickets with no cached status are
     * also surfaced because their state is unknown.
     *
     * <p>This is used by the next-work suggestion pipeline to surface tickets that
     * require the user's attention but have no corresponding weekly commit yet.
     *
     * @param orgId     the organisation ID
     * @param userId    the user whose commit-linked tickets are queried
     * @param asOf      the reference Monday; looks back {@code weeksBack} weeks
     * @param weeksBack number of weeks to scan for commit-linked tickets
     * @return list of unresolved ticket contexts; empty if none found
     */
    List<UserTicketContext> getUnresolvedTicketsForUser(
            UUID orgId, UUID userId, LocalDate asOf, int weeksBack);

    // ── Value objects ──────────────────────────────────────────────────────────

    /**
     * Snapshot of a potentially-unresolved external ticket linked to a user's commit.
     *
     * @param externalTicketId the provider-specific ticket identifier (e.g. "PROJ-123")
     * @param provider         the integration provider name (e.g. "JIRA", "LINEAR")
     * @param externalStatus   the last-synced status label from the provider (may be null)
     * @param externalTicketUrl URL to the ticket in the provider's web UI (may be null)
     * @param lastSyncedAt     timestamp of the last status sync; null if never synced
     * @param commitId         the weekly commit this ticket is linked to
     * @param outcomeId        RCDO outcome UUID string from the linked commit
     * @param outcomeName      snapshot outcome name from the linked commit (may be null)
     * @param objectiveName    snapshot objective name from the linked commit (may be null)
     * @param rallyCryName     snapshot rally cry name from the linked commit (may be null)
     */
    record UserTicketContext(
            String externalTicketId,
            String provider,
            String externalStatus,
            String externalTicketUrl,
            Instant lastSyncedAt,
            UUID commitId,
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName
    ) {}
}
