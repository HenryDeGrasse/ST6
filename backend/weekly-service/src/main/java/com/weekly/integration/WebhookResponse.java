package com.weekly.integration;

/**
 * Response DTO for the {@code POST /api/v1/integrations/webhook/{provider}} endpoint.
 *
 * <p>Acknowledges receipt of the webhook and reports how many check-in entries
 * were automatically created as a result.
 */
public record WebhookResponse(
        /** The normalized provider name (e.g. "JIRA", "LINEAR"). */
        String provider,

        /** Number of check-in entries created from this webhook payload (may be 0). */
        int checkInsCreated
) {
}
