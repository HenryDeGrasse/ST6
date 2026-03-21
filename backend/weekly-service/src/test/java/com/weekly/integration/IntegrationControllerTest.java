package com.weekly.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.auth.AuthenticatedUserContext;
import com.weekly.auth.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link IntegrationController}.
 */
class IntegrationControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL = new UserPrincipal(USER_ID, ORG_ID, Set.of());

    private IntegrationService integrationService;
    private AuthenticatedUserContext authenticatedUserContext;
    private IntegrationController controller;

    @BeforeEach
    void setUp() {
        integrationService = mock(IntegrationService.class);
        authenticatedUserContext = new AuthenticatedUserContext();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of())
        );

        controller = new IntegrationController(integrationService, authenticatedUserContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ExternalTicketLinkResponse stubLink(UUID commitId, String provider, String ticketId) {
        return new ExternalTicketLinkResponse(
                UUID.randomUUID().toString(),
                ORG_ID.toString(),
                commitId.toString(),
                provider,
                ticketId,
                "https://example.com/" + ticketId,
                "Open",
                null,
                Instant.now().toString()
        );
    }

    // ─── POST /integrations/link-ticket ───────────────────────────────────────

    @Nested
    class LinkTicket {

        @Test
        void returns201WithNewLink() {
            UUID commitId = UUID.randomUUID();
            LinkTicketRequest request = new LinkTicketRequest(commitId, "JIRA", "PROJ-42");
            ExternalTicketLinkResponse stub = stubLink(commitId, "JIRA", "PROJ-42");

            when(integrationService.linkTicket(eq(ORG_ID), any()))
                    .thenReturn(stub);

            ResponseEntity<ExternalTicketLinkResponse> response =
                    controller.linkTicket(request);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(commitId.toString(), response.getBody().commitId());
            assertEquals("JIRA", response.getBody().provider());
            assertEquals("PROJ-42", response.getBody().externalTicketId());
        }

        @Test
        void delegatesToServiceWithCorrectOrgId() {
            UUID commitId = UUID.randomUUID();
            LinkTicketRequest request = new LinkTicketRequest(commitId, "LINEAR", "lin-123");

            when(integrationService.linkTicket(any(), any()))
                    .thenReturn(stubLink(commitId, "LINEAR", "lin-123"));

            controller.linkTicket(request);

            verify(integrationService).linkTicket(eq(ORG_ID), eq(request));
        }

        @Test
        void usesOrgIdFromAuthContextNotRawHeader() {
            UUID commitId = UUID.randomUUID();
            LinkTicketRequest request = new LinkTicketRequest(commitId, "JIRA", "TICKET-1");

            when(integrationService.linkTicket(any(), any()))
                    .thenReturn(stubLink(commitId, "JIRA", "TICKET-1"));

            controller.linkTicket(request);

            // Verifies the orgId came from the security context
            verify(integrationService).linkTicket(eq(ORG_ID), any());
        }
    }

    // ─── GET /commits/{commitId}/linked-tickets ────────────────────────────────

    @Nested
    class GetLinkedTickets {

        @Test
        void returns200WithLinks() {
            UUID commitId = UUID.randomUUID();
            ExternalTicketLinkResponse link1 = stubLink(commitId, "JIRA", "PROJ-1");
            ExternalTicketLinkResponse link2 = stubLink(commitId, "LINEAR", "lin-abc");
            LinkedTicketsResponse stub = new LinkedTicketsResponse(
                    commitId.toString(), List.of(link1, link2));

            when(integrationService.getLinkedTickets(ORG_ID, commitId)).thenReturn(stub);

            ResponseEntity<LinkedTicketsResponse> response =
                    controller.getLinkedTickets(commitId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(commitId.toString(), response.getBody().commitId());
            assertEquals(2, response.getBody().links().size());
        }

        @Test
        void returnsEmptyListWhenNoLinks() {
            UUID commitId = UUID.randomUUID();
            LinkedTicketsResponse stub = new LinkedTicketsResponse(
                    commitId.toString(), List.of());

            when(integrationService.getLinkedTickets(ORG_ID, commitId)).thenReturn(stub);

            ResponseEntity<LinkedTicketsResponse> response =
                    controller.getLinkedTickets(commitId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(0, response.getBody().links().size());
        }

        @Test
        void delegatesToServiceWithCorrectArguments() {
            UUID commitId = UUID.randomUUID();
            when(integrationService.getLinkedTickets(any(), any()))
                    .thenReturn(new LinkedTicketsResponse(commitId.toString(), List.of()));

            controller.getLinkedTickets(commitId);

            verify(integrationService).getLinkedTickets(ORG_ID, commitId);
        }

        @Test
        void usesOrgIdFromAuthContext() {
            UUID commitId = UUID.randomUUID();
            when(integrationService.getLinkedTickets(any(), any()))
                    .thenReturn(new LinkedTicketsResponse(commitId.toString(), List.of()));

            controller.getLinkedTickets(commitId);

            verify(integrationService).getLinkedTickets(eq(ORG_ID), eq(commitId));
        }
    }

    // ─── POST /integrations/webhook/{provider} ────────────────────────────────

    @Nested
    class HandleWebhook {

        private Map<String, Object> jiraPayload() {
            return Map.of(
                    "issue", Map.of(
                            "key", "PROJ-42",
                            "fields", Map.of("status", Map.of("name", "In Review"))
                    )
            );
        }

        @Test
        void returns200WithCheckInsCreatedCount() {
            when(integrationService.processWebhook("jira", jiraPayload())).thenReturn(1);

            ResponseEntity<WebhookResponse> response =
                    controller.handleWebhook("jira", jiraPayload());

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("JIRA", response.getBody().provider());
            assertEquals(1, response.getBody().checkInsCreated());
        }

        @Test
        void returns200WithZeroWhenNothingCreated() {
            when(integrationService.processWebhook("linear", Map.of())).thenReturn(0);

            ResponseEntity<WebhookResponse> response =
                    controller.handleWebhook("linear", Map.of());

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("LINEAR", response.getBody().provider());
            assertEquals(0, response.getBody().checkInsCreated());
        }

        @Test
        void delegatesToServiceWithProviderAndPayload() {
            Map<String, Object> payload = jiraPayload();
            when(integrationService.processWebhook(any(), any())).thenReturn(2);

            controller.handleWebhook("JIRA", payload);

            verify(integrationService).processWebhook("JIRA", payload);
        }

        @Test
        void normalizesProviderCaseInResponse() {
            when(integrationService.processWebhook(any(), any())).thenReturn(0);

            ResponseEntity<WebhookResponse> lowerCase =
                    controller.handleWebhook("jira", Map.of());
            assertEquals("JIRA", lowerCase.getBody().provider());

            ResponseEntity<WebhookResponse> upperCase =
                    controller.handleWebhook("LINEAR", Map.of());
            assertEquals("LINEAR", upperCase.getBody().provider());
        }
    }
}
