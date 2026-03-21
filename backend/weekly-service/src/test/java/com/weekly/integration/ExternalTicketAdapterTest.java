package com.weekly.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the stub {@link ExternalTicketAdapter} implementations:
 * {@link JiraAdapter} and {@link LinearAdapter}.
 *
 * <p>These tests verify that the stubs behave predictably (return safe defaults,
 * do not throw) and that the adapter interface contract is satisfied.
 */
class ExternalTicketAdapterTest {

    // ─── JiraAdapter ──────────────────────────────────────────────────────────

    @Nested
    class JiraAdapterTests {

        private final JiraAdapter adapter =
                new JiraAdapter("https://org.atlassian.net", "token", "user@example.com");

        @Test
        void providerNameIsJira() {
            assertEquals("JIRA", adapter.providerName());
        }

        @Test
        void fetchTicketReturnsEmptyForStub() {
            Optional<ExternalTicket> result = adapter.fetchTicket("PROJ-1");
            assertTrue(result.isEmpty());
        }

        @Test
        void searchTicketsReturnsEmptyListForStub() {
            List<ExternalTicket> result = adapter.searchTickets(UUID.randomUUID(), "Open");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void linkTicketToCommitDoesNotThrow() {
            // Should be a no-op stub — must not throw
            adapter.linkTicketToCommit(UUID.randomUUID(), "PROJ-1");
        }

        @Test
        void syncTicketUpdatesReturnsEmptyListForStub() {
            List<ExternalTicket> result = adapter.syncTicketUpdates(Instant.now());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void searchTicketsWithNullStatusReturnsEmptyList() {
            List<ExternalTicket> result = adapter.searchTickets(UUID.randomUUID(), null);
            assertTrue(result.isEmpty());
        }

        @Test
        void postCommentDoesNotThrow() {
            adapter.postComment("PROJ-42", "Reconciliation submitted.");
        }

        @Test
        void mapsInReviewToOnTrack() {
            assertEquals(com.weekly.plan.domain.ProgressStatus.ON_TRACK,
                    adapter.mapToProgressStatus("In Review"));
        }

        @Test
        void mapsBlockedToAtRisk() {
            assertEquals(com.weekly.plan.domain.ProgressStatus.AT_RISK,
                    adapter.mapToProgressStatus("Blocked"));
        }

        @Test
        void mapsDoneToDataEarly() {
            assertEquals(com.weekly.plan.domain.ProgressStatus.DONE_EARLY,
                    adapter.mapToProgressStatus("Done"));
        }

        @Test
        void returnsNullForUnmappableStatus() {
            assertNull(adapter.mapToProgressStatus("In Progress"));
        }

        @Test
        void returnsNullForNullStatus() {
            assertNull(adapter.mapToProgressStatus(null));
        }

        @Test
        void extractsTicketIdFromJiraWebhookPayload() {
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "issue", java.util.Map.of("key", "PROJ-42")
            );
            assertEquals("PROJ-42", adapter.extractTicketId(payload));
        }

        @Test
        void returnsNullTicketIdWhenPayloadMissingIssue() {
            assertNull(adapter.extractTicketId(java.util.Map.of("foo", "bar")));
        }

        @Test
        void extractsStatusFromJiraWebhookPayload() {
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "issue", java.util.Map.of(
                            "key", "PROJ-42",
                            "fields", java.util.Map.of(
                                    "status", java.util.Map.of("name", "In Review")
                            )
                    )
            );
            assertEquals("In Review", adapter.extractStatus(payload));
        }

        @Test
        void returnsNullStatusWhenPayloadMissingFields() {
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "issue", java.util.Map.of("key", "PROJ-42")
            );
            assertNull(adapter.extractStatus(payload));
        }
    }

    // ─── LinearAdapter ────────────────────────────────────────────────────────

    @Nested
    class LinearAdapterTests {

        private final LinearAdapter adapter =
                new LinearAdapter("lin-api-key", "team-id-abc");

        @Test
        void providerNameIsLinear() {
            assertEquals("LINEAR", adapter.providerName());
        }

        @Test
        void fetchTicketReturnsEmptyForStub() {
            Optional<ExternalTicket> result = adapter.fetchTicket("ENG-42");
            assertTrue(result.isEmpty());
        }

        @Test
        void searchTicketsReturnsEmptyListForStub() {
            List<ExternalTicket> result = adapter.searchTickets(UUID.randomUUID(), "In Progress");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void linkTicketToCommitDoesNotThrow() {
            adapter.linkTicketToCommit(UUID.randomUUID(), "ENG-42");
        }

        @Test
        void syncTicketUpdatesReturnsEmptyListForStub() {
            List<ExternalTicket> result = adapter.syncTicketUpdates(Instant.now().minusSeconds(3600));
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void adapterWithBlankTeamIdDoesNotThrow() {
            LinearAdapter noTeam = new LinearAdapter("lin-api-key", "");
            Optional<ExternalTicket> result = noTeam.fetchTicket("ENG-1");
            assertTrue(result.isEmpty());
        }

        @Test
        void postCommentDoesNotThrow() {
            adapter.postComment("ENG-42", "Reconciliation submitted.");
        }

        @Test
        void mapsInReviewToOnTrack() {
            assertEquals(com.weekly.plan.domain.ProgressStatus.ON_TRACK,
                    adapter.mapToProgressStatus("In Review"));
        }

        @Test
        void mapsBlockedToAtRisk() {
            assertEquals(com.weekly.plan.domain.ProgressStatus.AT_RISK,
                    adapter.mapToProgressStatus("Blocked"));
        }

        @Test
        void mapsDoneToDataEarly() {
            assertEquals(com.weekly.plan.domain.ProgressStatus.DONE_EARLY,
                    adapter.mapToProgressStatus("Done"));
        }

        @Test
        void returnsNullForUnmappableLinearStatus() {
            assertNull(adapter.mapToProgressStatus("Todo"));
        }

        @Test
        void extractsTicketIdFromLinearWebhookPayload() {
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "data", java.util.Map.of("identifier", "ENG-42")
            );
            assertEquals("ENG-42", adapter.extractTicketId(payload));
        }

        @Test
        void returnsNullTicketIdWhenLinearPayloadMissingData() {
            assertNull(adapter.extractTicketId(java.util.Map.of("type", "Issue")));
        }

        @Test
        void extractsStatusFromLinearWebhookPayload() {
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "data", java.util.Map.of(
                            "identifier", "ENG-42",
                            "state", java.util.Map.of("name", "In Review")
                    )
            );
            assertEquals("In Review", adapter.extractStatus(payload));
        }

        @Test
        void returnsNullStatusWhenLinearPayloadMissingState() {
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "data", java.util.Map.of("identifier", "ENG-42")
            );
            assertNull(adapter.extractStatus(payload));
        }
    }

    // ─── ExternalTicket record ────────────────────────────────────────────────

    @Nested
    class ExternalTicketRecordTests {

        @Test
        void canConstructFullTicket() {
            ExternalTicket ticket = new ExternalTicket(
                    "PROJ-42",
                    Optional.of("Fix the bug"),
                    Optional.of("Detailed description"),
                    Optional.of("In Progress"),
                    Optional.of("https://jira.example.com/PROJ-42")
            );
            assertEquals("PROJ-42", ticket.ticketId());
            assertEquals("Fix the bug", ticket.title().orElseThrow());
            assertEquals("Detailed description", ticket.description().orElseThrow());
            assertEquals("In Progress", ticket.status().orElseThrow());
            assertEquals("https://jira.example.com/PROJ-42", ticket.url().orElseThrow());
        }

        @Test
        void canConstructMinimalTicket() {
            ExternalTicket ticket = new ExternalTicket(
                    "PROJ-1",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
            assertEquals("PROJ-1", ticket.ticketId());
            assertTrue(ticket.title().isEmpty());
            assertTrue(ticket.description().isEmpty());
            assertTrue(ticket.status().isEmpty());
            assertTrue(ticket.url().isEmpty());
        }
    }
}
