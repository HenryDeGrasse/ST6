package com.weekly.integration;

import com.weekly.auth.AuthenticatedUserContext;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for external integration operations (Wave 4).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/integrations/link-ticket} — link a commit to an external ticket</li>
 *   <li>{@code GET  /api/v1/commits/{commitId}/linked-tickets} — list linked tickets for a commit</li>
 *   <li>{@code POST /api/v1/integrations/webhook/{provider}} — inbound webhook for real-time status updates</li>
 * </ul>
 *
 * <p>{@code orgId} and {@code userId} are sourced from the validated
 * {@link com.weekly.auth.UserPrincipal} via {@link AuthenticatedUserContext} (§9.1).
 *
 * <p>The webhook endpoint does not require a user session; it is secured by
 * provider-specific signature verification (stub in MVP).
 */
@RestController
@RequestMapping("/api/v1")
public class IntegrationController {

    private final IntegrationService integrationService;
    private final AuthenticatedUserContext authenticatedUserContext;

    public IntegrationController(
            IntegrationService integrationService,
            AuthenticatedUserContext authenticatedUserContext
    ) {
        this.integrationService = integrationService;
        this.authenticatedUserContext = authenticatedUserContext;
    }

    /**
     * Links a weekly commit to an external issue-tracker ticket.
     *
     * <p>If the commit's title or description is empty, the values are
     * auto-populated from the fetched ticket data.
     *
     * <p>Idempotent: if the link already exists, returns the existing record
     * with {@code 200 OK} instead of creating a duplicate.
     *
     * @param request link-ticket request body
     * @return 201 Created with the new link, or 200 OK if already linked
     */
    @PostMapping("/integrations/link-ticket")
    public ResponseEntity<ExternalTicketLinkResponse> linkTicket(
            @Valid @RequestBody LinkTicketRequest request
    ) {
        ExternalTicketLinkResponse link = integrationService.linkTicket(
                authenticatedUserContext.orgId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(link);
    }

    /**
     * Returns all external ticket links for the given commit.
     *
     * @param commitId the weekly commit UUID
     * @return 200 OK with the list of links
     */
    @GetMapping("/commits/{commitId}/linked-tickets")
    public ResponseEntity<LinkedTicketsResponse> getLinkedTickets(
            @PathVariable UUID commitId
    ) {
        LinkedTicketsResponse response = integrationService.getLinkedTickets(
                authenticatedUserContext.orgId(),
                commitId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Receives an inbound webhook from an external issue-tracker provider.
     *
     * <p>Extracts the ticket ID and current status from the provider-specific
     * payload and auto-creates a check-in entry for every commit linked to the
     * updated ticket. This is a real-time alternative to the {@link TicketSyncJob}
     * polling approach.
     *
     * <p>Returns {@code 200 OK} regardless of whether any check-ins were created
     * so the provider does not retry on empty payloads.
     *
     * @param provider the provider name from the path (e.g. "jira", "linear")
     * @param payload  the raw webhook body
     * @return 200 OK with the number of check-ins created
     */
    @PostMapping("/integrations/webhook/{provider}")
    public ResponseEntity<WebhookResponse> handleWebhook(
            @PathVariable String provider,
            @RequestBody Map<String, Object> payload
    ) {
        int created = integrationService.processWebhook(provider, payload);
        return ResponseEntity.ok(new WebhookResponse(provider.toUpperCase(), created));
    }
}
