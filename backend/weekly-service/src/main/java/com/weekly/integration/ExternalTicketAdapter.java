package com.weekly.integration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter interface for external issue-tracker integrations (ADR-010).
 *
 * <p>Implementations exist for Jira ({@link JiraAdapter}) and Linear
 * ({@link LinearAdapter}). A concrete adapter is selected by the
 * {@code integration.provider} configuration property.
 *
 * <p>All methods are network calls to the upstream provider. Callers must
 * handle {@link ExternalTicketUnavailableException} for transient failures.
 */
public interface ExternalTicketAdapter {

    /**
     * Returns the name of the provider this adapter connects to
     * (e.g. {@code "JIRA"}, {@code "LINEAR"}).
     */
    String providerName();

    /**
     * Fetches a single ticket by its provider-specific ID.
     *
     * @param ticketId the provider-specific ticket identifier
     * @return the fetched ticket, or empty if not found
     * @throws ExternalTicketUnavailableException on network/auth failure
     */
    Optional<ExternalTicket> fetchTicket(String ticketId);

    /**
     * Searches for tickets assigned to or reported by the given user.
     *
     * @param userId the internal user UUID (used to resolve the external account)
     * @param status optional status filter (provider-specific label, e.g. "In Progress");
     *               pass {@code null} to return all statuses
     * @return list of matching tickets (may be empty)
     * @throws ExternalTicketUnavailableException on network/auth failure
     */
    List<ExternalTicket> searchTickets(UUID userId, String status);

    /**
     * Records a link between an internal commit and an external ticket in the
     * provider (e.g. posts a comment or sets a custom field).
     *
     * @param commitId the internal weekly-commit UUID
     * @param ticketId the provider-specific ticket identifier
     * @throws ExternalTicketUnavailableException on network/auth failure
     */
    void linkTicketToCommit(UUID commitId, String ticketId);

    /**
     * Pulls status updates for all tickets that have been modified since
     * {@code since} and returns the refreshed snapshots.
     *
     * @param since only return tickets updated at or after this instant
     * @return list of updated ticket snapshots
     * @throws ExternalTicketUnavailableException on network/auth failure
     */
    List<ExternalTicket> syncTicketUpdates(Instant since);

    /**
     * Posts a comment on the given external ticket.
     *
     * <p>Called during outbound status sync — e.g. when an IC submits
     * reconciliation, a comment summarising the outcome is posted on each
     * linked ticket.
     *
     * @param ticketId the provider-specific ticket identifier
     * @param comment  the comment body to post
     * @throws ExternalTicketUnavailableException on network/auth failure
     */
    void postComment(String ticketId, String comment);

    /**
     * Maps a provider-specific status label to the nearest internal
     * {@link com.weekly.plan.domain.ProgressStatus}.
     *
     * <p>Returns {@code null} when the provider status cannot be mapped to a
     * known progress state (the caller should skip creating a check-in).
     *
     * <p>Reference mappings:
     * <ul>
     *   <li>"In Review" (Jira / Linear) → {@code ON_TRACK}</li>
     *   <li>"Blocked"   (Jira / Linear) → {@code AT_RISK}</li>
     *   <li>"Done" / "Completed"        → {@code DONE_EARLY}</li>
     * </ul>
     *
     * @param providerStatus the raw status string returned by the provider
     * @return the mapped {@link com.weekly.plan.domain.ProgressStatus}, or {@code null}
     */
    com.weekly.plan.domain.ProgressStatus mapToProgressStatus(String providerStatus);

    /**
     * Extracts the provider-specific ticket identifier from an inbound webhook
     * payload.
     *
     * @param webhookPayload the raw webhook body as a key/value map
     * @return the ticket ID, or {@code null} if it cannot be extracted
     */
    String extractTicketId(java.util.Map<String, Object> webhookPayload);

    /**
     * Extracts the current status label from an inbound webhook payload.
     *
     * @param webhookPayload the raw webhook body as a key/value map
     * @return the raw status string, or {@code null} if it cannot be extracted
     */
    String extractStatus(java.util.Map<String, Object> webhookPayload);

    /** Thrown when the external provider is unreachable or returns an error. */
    class ExternalTicketUnavailableException extends RuntimeException {
        public ExternalTicketUnavailableException(String message) {
            super(message);
        }

        public ExternalTicketUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
