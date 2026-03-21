package com.weekly.integration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation of {@link ExternalTicketAdapter} for Linear.
 *
 * <p>This adapter is intentionally stubbed out — full API-key integration
 * with the Linear GraphQL API will be wired in a follow-up sprint.
 *
 * <p>Configuration is read from environment variables / Spring properties:
 * <ul>
 *   <li>{@code integration.linear.api-key} — Linear personal or workspace API key</li>
 *   <li>{@code integration.linear.team-id} — optional team ID to scope queries</li>
 * </ul>
 */
public class LinearAdapter implements ExternalTicketAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(LinearAdapter.class);

    private final String apiKey;
    private final String teamId;

    public LinearAdapter(String apiKey, String teamId) {
        this.apiKey = apiKey;
        this.teamId = teamId;
    }

    @Override
    public String providerName() {
        return "LINEAR";
    }

    /**
     * Stub: returns empty for any ticket ID.
     *
     * <p>Full implementation: GraphQL query {@code issue(id: $ticketId)}
     */
    @Override
    public Optional<ExternalTicket> fetchTicket(String ticketId) {
        LOG.info("[Linear stub] fetchTicket: ticketId={}", ticketId);
        return Optional.empty();
    }

    /**
     * Stub: returns an empty list.
     *
     * <p>Full implementation: GraphQL query {@code issues(filter: {assignee: ...})}
     */
    @Override
    public List<ExternalTicket> searchTickets(UUID userId, String status) {
        LOG.info("[Linear stub] searchTickets: userId={}, status={}", userId, status);
        return List.of();
    }

    /**
     * Stub: logs the link request but does not call the Linear API.
     *
     * <p>Full implementation: GraphQL mutation {@code attachmentCreate}
     */
    @Override
    public void linkTicketToCommit(UUID commitId, String ticketId) {
        LOG.info("[Linear stub] linkTicketToCommit: commitId={}, ticketId={}", commitId, ticketId);
    }

    /**
     * Stub: returns an empty list.
     *
     * <p>Full implementation: GraphQL query {@code issues(filter: {updatedAt: {gte: $since}})}
     */
    @Override
    public List<ExternalTicket> syncTicketUpdates(Instant since) {
        LOG.info("[Linear stub] syncTicketUpdates: since={}", since);
        return List.of();
    }

    /**
     * Stub: logs the comment request but does not call the Linear API.
     *
     * <p>Full implementation: GraphQL mutation {@code commentCreate}
     */
    @Override
    public void postComment(String ticketId, String comment) {
        LOG.info("[Linear stub] postComment: ticketId={}, comment={}", ticketId, comment);
    }

    /**
     * Maps Linear state names to internal {@link com.weekly.plan.domain.ProgressStatus}.
     *
     * <p>Linear typical workflow: Backlog → Todo → In Progress → In Review → Done / Cancelled.
     */
    @Override
    public com.weekly.plan.domain.ProgressStatus mapToProgressStatus(String providerStatus) {
        if (providerStatus == null) {
            return null;
        }
        return switch (providerStatus.trim().toLowerCase()) {
            case "in review", "review" -> com.weekly.plan.domain.ProgressStatus.ON_TRACK;
            case "blocked" -> com.weekly.plan.domain.ProgressStatus.AT_RISK;
            case "done", "completed", "cancelled" ->
                    com.weekly.plan.domain.ProgressStatus.DONE_EARLY;
            default -> null;
        };
    }

    /**
     * Extracts the issue identifier from a Linear webhook payload.
     *
     * <p>Linear webhook structure: {@code {"data": {"identifier": "ENG-42", ...}, ...}}
     */
    @Override
    public String extractTicketId(java.util.Map<String, Object> webhookPayload) {
        Object data = webhookPayload.get("data");
        if (data instanceof java.util.Map<?, ?> dataMap) {
            Object identifier = dataMap.get("identifier");
            return identifier != null ? identifier.toString() : null;
        }
        return null;
    }

    /**
     * Extracts the current state name from a Linear webhook payload.
     *
     * <p>Linear webhook structure: {@code {"data": {"state": {"name": "In Review"}}}}
     */
    @Override
    public String extractStatus(java.util.Map<String, Object> webhookPayload) {
        Object data = webhookPayload.get("data");
        if (data instanceof java.util.Map<?, ?> dataMap) {
            Object state = dataMap.get("state");
            if (state instanceof java.util.Map<?, ?> stateMap) {
                Object name = stateMap.get("name");
                return name != null ? name.toString() : null;
            }
        }
        return null;
    }
}
