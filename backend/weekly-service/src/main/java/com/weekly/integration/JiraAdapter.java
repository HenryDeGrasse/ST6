package com.weekly.integration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation of {@link ExternalTicketAdapter} for Jira.
 *
 * <p>This adapter is intentionally stubbed out — full OAuth2/API-token
 * integration with the Jira REST API (v3) will be wired in a follow-up sprint.
 *
 * <p>Configuration is read from environment variables / Spring properties:
 * <ul>
 *   <li>{@code integration.jira.base-url}  — e.g. {@code https://your-org.atlassian.net}</li>
 *   <li>{@code integration.jira.api-token} — Jira API token (personal or service account)</li>
 *   <li>{@code integration.jira.email}     — email address associated with the API token</li>
 * </ul>
 */
public class JiraAdapter implements ExternalTicketAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(JiraAdapter.class);

    private final String baseUrl;
    private final String apiToken;
    private final String email;

    public JiraAdapter(String baseUrl, String apiToken, String email) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.email = email;
    }

    @Override
    public String providerName() {
        return "JIRA";
    }

    /**
     * Stub: returns empty for any ticket ID.
     *
     * <p>Full implementation: GET /rest/api/3/issue/{ticketId}
     */
    @Override
    public Optional<ExternalTicket> fetchTicket(String ticketId) {
        LOG.info("[Jira stub] fetchTicket: ticketId={}", ticketId);
        return Optional.empty();
    }

    /**
     * Stub: returns an empty list.
     *
     * <p>Full implementation: POST /rest/api/3/issue/search (JQL)
     */
    @Override
    public List<ExternalTicket> searchTickets(UUID userId, String status) {
        LOG.info("[Jira stub] searchTickets: userId={}, status={}", userId, status);
        return List.of();
    }

    /**
     * Stub: logs the link request but does not call the Jira API.
     *
     * <p>Full implementation: POST /rest/api/3/issue/{ticketId}/remotelink
     */
    @Override
    public void linkTicketToCommit(UUID commitId, String ticketId) {
        LOG.info("[Jira stub] linkTicketToCommit: commitId={}, ticketId={}", commitId, ticketId);
    }

    /**
     * Stub: returns an empty list.
     *
     * <p>Full implementation: GET /rest/api/3/issue/search?jql=updated>=since
     */
    @Override
    public List<ExternalTicket> syncTicketUpdates(Instant since) {
        LOG.info("[Jira stub] syncTicketUpdates: since={}", since);
        return List.of();
    }

    /**
     * Stub: logs the comment request but does not call the Jira API.
     *
     * <p>Full implementation: POST /rest/api/3/issue/{ticketId}/comment
     */
    @Override
    public void postComment(String ticketId, String comment) {
        LOG.info("[Jira stub] postComment: ticketId={}, comment={}", ticketId, comment);
    }

    /**
     * Maps Jira status labels to internal {@link com.weekly.plan.domain.ProgressStatus}.
     *
     * <p>Jira typical workflow: To Do → In Progress → In Review → Done / Blocked.
     */
    @Override
    public com.weekly.plan.domain.ProgressStatus mapToProgressStatus(String providerStatus) {
        if (providerStatus == null) {
            return null;
        }
        return switch (providerStatus.trim().toLowerCase()) {
            case "in review", "review", "in qa" -> com.weekly.plan.domain.ProgressStatus.ON_TRACK;
            case "blocked", "impediment" -> com.weekly.plan.domain.ProgressStatus.AT_RISK;
            case "done", "closed", "resolved", "completed" ->
                    com.weekly.plan.domain.ProgressStatus.DONE_EARLY;
            default -> null;
        };
    }

    /**
     * Extracts the issue key (e.g. "PROJ-42") from a Jira webhook payload.
     *
     * <p>Jira webhook structure: {@code {"issue": {"key": "PROJ-42", ...}, ...}}
     */
    @Override
    public String extractTicketId(java.util.Map<String, Object> webhookPayload) {
        Object issue = webhookPayload.get("issue");
        if (issue instanceof java.util.Map<?, ?> issueMap) {
            Object key = issueMap.get("key");
            return key != null ? key.toString() : null;
        }
        return null;
    }

    /**
     * Extracts the current status name from a Jira webhook payload.
     *
     * <p>Jira webhook structure:
     * {@code {"issue": {"fields": {"status": {"name": "In Review"}}}}}
     */
    @Override
    public String extractStatus(java.util.Map<String, Object> webhookPayload) {
        Object issue = webhookPayload.get("issue");
        if (issue instanceof java.util.Map<?, ?> issueMap) {
            Object fields = issueMap.get("fields");
            if (fields instanceof java.util.Map<?, ?> fieldsMap) {
                Object status = fieldsMap.get("status");
                if (status instanceof java.util.Map<?, ?> statusMap) {
                    Object name = statusMap.get("name");
                    return name != null ? name.toString() : null;
                }
            }
        }
        return null;
    }
}
