package com.weekly.integration;

import java.util.Optional;

/**
 * Immutable snapshot of a ticket fetched from an external issue-tracker.
 *
 * <p>All fields except {@code ticketId} and {@code url} are optional —
 * not every provider exposes the same metadata.
 */
public record ExternalTicket(
        /** Provider-specific ticket identifier (e.g. "PROJ-123" for Jira). */
        String ticketId,

        /** Human-readable title / summary of the ticket. */
        Optional<String> title,

        /** Full description body of the ticket. */
        Optional<String> description,

        /** Current status label as returned by the provider (e.g. "In Progress"). */
        Optional<String> status,

        /** URL to the ticket in the provider's web UI. */
        Optional<String> url
) {
}
