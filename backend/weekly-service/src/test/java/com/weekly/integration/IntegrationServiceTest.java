package com.weekly.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultIntegrationService}.
 */
class IntegrationServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID COMMIT_ID = UUID.randomUUID();
    private static final String TICKET_ID = "PROJ-42";
    private static final String PROVIDER = "JIRA";

    private ExternalTicketLinkRepository linkRepository;
    private WeeklyCommitRepository commitRepository;
    private WeeklyPlanRepository planRepository;
    private ProgressEntryRepository progressEntryRepository;
    private StubExternalTicketAdapter stubAdapter;
    private DefaultIntegrationService service;

    @BeforeEach
    void setUp() {
        linkRepository = mock(ExternalTicketLinkRepository.class);
        commitRepository = mock(WeeklyCommitRepository.class);
        planRepository = mock(WeeklyPlanRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);
        stubAdapter = new StubExternalTicketAdapter("JIRA");
        service = new DefaultIntegrationService(
                linkRepository,
                commitRepository,
                planRepository,
                progressEntryRepository,
                List.of(stubAdapter)
        );
    }

    private WeeklyCommitEntity newCommit(String title, String description) {
        WeeklyCommitEntity commit = new WeeklyCommitEntity(COMMIT_ID, ORG_ID, UUID.randomUUID(), title);
        commit.setDescription(description != null ? description : "");
        return commit;
    }

    // ─── linkTicket ───────────────────────────────────────────────────────────

    @Nested
    class LinkTicket {

        @Test
        void createsNewLinkWhenNoneExists() {
            WeeklyCommitEntity commit = newCommit("Existing Title", "Existing description");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));
            when(linkRepository.findByOrgIdAndCommitIdAndProviderAndExternalTicketId(
                    ORG_ID, COMMIT_ID, PROVIDER, TICKET_ID))
                    .thenReturn(Optional.empty());
            when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LinkTicketRequest request = new LinkTicketRequest(COMMIT_ID, PROVIDER, TICKET_ID);
            ExternalTicketLinkResponse response = service.linkTicket(ORG_ID, request);

            assertNotNull(response);
            assertEquals(COMMIT_ID.toString(), response.commitId());
            assertEquals(PROVIDER, response.provider());
            assertEquals(TICKET_ID, response.externalTicketId());

            verify(linkRepository).save(any(ExternalTicketLinkEntity.class));
        }

        @Test
        void returnsExistingLinkIdempotently() {
            WeeklyCommitEntity commit = newCommit("Title", "Description");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));

            ExternalTicketLinkEntity existing = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, COMMIT_ID, PROVIDER, TICKET_ID,
                    "https://jira.example.com/PROJ-42", "In Progress");
            when(linkRepository.findByOrgIdAndCommitIdAndProviderAndExternalTicketId(
                    ORG_ID, COMMIT_ID, PROVIDER, TICKET_ID))
                    .thenReturn(Optional.of(existing));

            LinkTicketRequest request = new LinkTicketRequest(COMMIT_ID, PROVIDER, TICKET_ID);
            ExternalTicketLinkResponse response = service.linkTicket(ORG_ID, request);

            assertEquals(existing.getId().toString(), response.id());
            // Should not create a second link record
            verify(linkRepository, never()).save(any());
        }

        @Test
        void autoPopulatesEmptyCommitTitleFromTicket() {
            WeeklyCommitEntity commit = newCommit("", "");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));
            when(linkRepository.findByOrgIdAndCommitIdAndProviderAndExternalTicketId(
                    ORG_ID, COMMIT_ID, PROVIDER, TICKET_ID))
                    .thenReturn(Optional.empty());
            when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Configure the stub to return a ticket with title and description
            stubAdapter.setTicketToReturn(new ExternalTicket(
                    TICKET_ID,
                    Optional.of("Ticket Title from Jira"),
                    Optional.of("Ticket body from Jira"),
                    Optional.of("In Progress"),
                    Optional.of("https://jira.example.com/PROJ-42")
            ));

            LinkTicketRequest request = new LinkTicketRequest(COMMIT_ID, PROVIDER, TICKET_ID);
            service.linkTicket(ORG_ID, request);

            // Commit title and description should have been updated
            assertEquals("Ticket Title from Jira", commit.getTitle());
            assertEquals("Ticket body from Jira", commit.getDescription());
            verify(commitRepository).save(commit);
        }

        @Test
        void doesNotOverwriteNonEmptyCommitTitle() {
            WeeklyCommitEntity commit = newCommit("Already set title", "");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));
            when(linkRepository.findByOrgIdAndCommitIdAndProviderAndExternalTicketId(
                    ORG_ID, COMMIT_ID, PROVIDER, TICKET_ID))
                    .thenReturn(Optional.empty());
            when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            stubAdapter.setTicketToReturn(new ExternalTicket(
                    TICKET_ID,
                    Optional.of("Should Not Overwrite"),
                    Optional.of("New description"),
                    Optional.of("Done"),
                    Optional.empty()
            ));

            LinkTicketRequest request = new LinkTicketRequest(COMMIT_ID, PROVIDER, TICKET_ID);
            service.linkTicket(ORG_ID, request);

            // Title should remain unchanged; description was empty so should be updated
            assertEquals("Already set title", commit.getTitle());
            assertEquals("New description", commit.getDescription());
        }

        @Test
        void throwsCommitNotFoundWhenCommitMissing() {
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.empty());

            LinkTicketRequest request = new LinkTicketRequest(COMMIT_ID, PROVIDER, TICKET_ID);
            assertThrows(CommitNotFoundException.class,
                    () -> service.linkTicket(ORG_ID, request));
        }

        @Test
        void throwsCommitNotFoundWhenCommitBelongsToDifferentOrg() {
            UUID differentOrg = UUID.randomUUID();
            WeeklyCommitEntity commit = newCommit("Title", "Description");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));

            LinkTicketRequest request = new LinkTicketRequest(COMMIT_ID, PROVIDER, TICKET_ID);
            assertThrows(CommitNotFoundException.class,
                    () -> service.linkTicket(differentOrg, request));
        }

        @Test
        void stillCreatesLinkWhenProviderAdapterUnavailable() {
            WeeklyCommitEntity commit = newCommit("Title", "Description");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));
            when(linkRepository.findByOrgIdAndCommitIdAndProviderAndExternalTicketId(
                    ORG_ID, COMMIT_ID, "LINEAR", TICKET_ID))
                    .thenReturn(Optional.empty());
            when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // No LINEAR adapter registered — should still create a link without ticket data
            LinkTicketRequest request = new LinkTicketRequest(COMMIT_ID, "LINEAR", TICKET_ID);
            ExternalTicketLinkResponse response = service.linkTicket(ORG_ID, request);

            assertNotNull(response);
            assertEquals("LINEAR", response.provider());
            verify(linkRepository).save(any());
        }
    }

    // ─── getLinkedTickets ─────────────────────────────────────────────────────

    @Nested
    class GetLinkedTickets {

        @Test
        void returnsAllLinksForCommit() {
            WeeklyCommitEntity commit = newCommit("Title", "Description");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));

            ExternalTicketLinkEntity link1 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, COMMIT_ID, "JIRA", "PROJ-1",
                    "https://jira.example.com/PROJ-1", "Open");
            ExternalTicketLinkEntity link2 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, COMMIT_ID, "LINEAR", "lin-abc",
                    "https://linear.app/issue/lin-abc", "In Progress");
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, COMMIT_ID))
                    .thenReturn(List.of(link1, link2));

            LinkedTicketsResponse response = service.getLinkedTickets(ORG_ID, COMMIT_ID);

            assertEquals(COMMIT_ID.toString(), response.commitId());
            assertEquals(2, response.links().size());
            assertEquals("PROJ-1", response.links().get(0).externalTicketId());
            assertEquals("lin-abc", response.links().get(1).externalTicketId());
        }

        @Test
        void returnsEmptyListWhenNoLinksExist() {
            WeeklyCommitEntity commit = newCommit("Title", "Description");
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.of(commit));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, COMMIT_ID))
                    .thenReturn(List.of());

            LinkedTicketsResponse response = service.getLinkedTickets(ORG_ID, COMMIT_ID);

            assertEquals(COMMIT_ID.toString(), response.commitId());
            assertEquals(0, response.links().size());
        }

        @Test
        void throwsCommitNotFoundWhenCommitMissing() {
            when(commitRepository.findById(COMMIT_ID)).thenReturn(Optional.empty());

            assertThrows(CommitNotFoundException.class,
                    () -> service.getLinkedTickets(ORG_ID, COMMIT_ID));
        }
    }

    // ─── processWebhook ───────────────────────────────────────────────────────

    @Nested
    class ProcessWebhook {

        private Map<String, Object> jiraPayload(String key, String statusName) {
            return Map.of(
                    "issue", Map.of(
                            "key", key,
                            "fields", Map.of(
                                    "status", Map.of("name", statusName)
                            )
                    )
            );
        }

        @Test
        void createsCheckInWhenTicketIsLinkedAndStatusMappable() {
            UUID orgId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), orgId, commitId, "JIRA", "PROJ-42",
                    "https://jira.example.com/PROJ-42", "In Progress");

            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-42"))
                    .thenReturn(List.of(link));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int created = service.processWebhook("JIRA", jiraPayload("PROJ-42", "In Review"));

            assertEquals(1, created);
            assertEquals("In Review", link.getExternalStatus());
            verify(linkRepository).save(link);
            verify(progressEntryRepository).save(any(ProgressEntryEntity.class));
        }

        @Test
        void returnsZeroWhenTicketNotLinked() {
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-99"))
                    .thenReturn(List.of());

            int created = service.processWebhook("JIRA", jiraPayload("PROJ-99", "In Review"));

            assertEquals(0, created);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void returnsZeroWhenStatusNotMappable() {
            UUID orgId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), orgId, commitId, "JIRA", "PROJ-42",
                    "https://jira.example.com/PROJ-42", "In Progress");

            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-42"))
                    .thenReturn(List.of(link));

            int created = service.processWebhook("JIRA", jiraPayload("PROJ-42", "To Do"));

            assertEquals(0, created);
            assertEquals("To Do", link.getExternalStatus());
            verify(linkRepository).save(link);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void returnsZeroForUnknownProvider() {
            int created = service.processWebhook("UNKNOWN", Map.of("issue", Map.of("key", "X-1")));

            assertEquals(0, created);
            verify(progressEntryRepository, never()).save(any());
            verify(linkRepository, never()).findByProviderAndExternalTicketId(any(), any());
        }

        @Test
        void returnsZeroWhenPayloadHasNoTicketId() {
            int created = service.processWebhook("JIRA", Map.of("unexpected", "payload"));

            assertEquals(0, created);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void createsCheckInWithAtRiskForBlockedStatus() {
            UUID orgId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), orgId, commitId, "JIRA", "PROJ-5",
                    "https://jira.example.com/PROJ-5", "In Progress");

            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-5"))
                    .thenReturn(List.of(link));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> {
                ProgressEntryEntity saved = inv.getArgument(0);
                assertEquals(ProgressStatus.AT_RISK, saved.getStatus());
                return saved;
            });

            int created = service.processWebhook("JIRA", jiraPayload("PROJ-5", "Blocked"));
            assertEquals(1, created);
        }

        @Test
        void fanOutToMultipleLinkedCommits() {
            UUID orgId = UUID.randomUUID();
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();

            ExternalTicketLinkEntity link1 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), orgId, commitId1, "JIRA", "PROJ-42",
                    null, "In Progress");
            ExternalTicketLinkEntity link2 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), orgId, commitId2, "JIRA", "PROJ-42",
                    null, "In Progress");

            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-42"))
                    .thenReturn(List.of(link1, link2));
            when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            int created = service.processWebhook("JIRA", jiraPayload("PROJ-42", "In Review"));
            assertEquals(2, created);
        }

        @Test
        void skipsDuplicateCheckInWhenWebhookStatusUnchanged() {
            UUID orgId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), orgId, commitId, "JIRA", "PROJ-77",
                    null, "In Review");

            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-77"))
                    .thenReturn(List.of(link));

            int created = service.processWebhook("JIRA", jiraPayload("PROJ-77", "In Review"));

            assertEquals(0, created);
            verify(linkRepository).save(link);
            verify(progressEntryRepository, never()).save(any());
        }
    }

    // ─── isUnresolved helper ──────────────────────────────────────────────────

    @Nested
    class IsUnresolvedHelper {

        @Test
        void nullStatusIsConsideredUnresolved() {
            assertTrue(DefaultIntegrationService.isUnresolved(null));
        }

        @Test
        void blankStatusIsConsideredUnresolved() {
            assertTrue(DefaultIntegrationService.isUnresolved(""));
            assertTrue(DefaultIntegrationService.isUnresolved("   "));
        }

        @Test
        void inProgressStatusIsUnresolved() {
            assertTrue(DefaultIntegrationService.isUnresolved("In Progress"));
            assertTrue(DefaultIntegrationService.isUnresolved("In Review"));
            assertTrue(DefaultIntegrationService.isUnresolved("Blocked"));
        }

        @Test
        void doneStatussAreResolved() {
            assertFalse(DefaultIntegrationService.isUnresolved("Done"));
            assertFalse(DefaultIntegrationService.isUnresolved("DONE"));
            assertFalse(DefaultIntegrationService.isUnresolved("Closed"));
            assertFalse(DefaultIntegrationService.isUnresolved("Resolved"));
            assertFalse(DefaultIntegrationService.isUnresolved("Complete"));
            assertFalse(DefaultIntegrationService.isUnresolved("Merged"));
        }
    }

    // ─── getUnresolvedTicketsForUser ──────────────────────────────────────────

    @Nested
    class GetUnresolvedTicketsForUser {

        private static final UUID USER_ID = UUID.randomUUID();
        private static final LocalDate AS_OF = LocalDate.of(2026, 3, 17);

        @Test
        void returnsEmptyWhenNoPlansFound() {
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of());

            List<IntegrationService.UserTicketContext> result =
                    service.getUnresolvedTicketsForUser(ORG_ID, USER_ID, AS_OF, 4);

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyWhenNoStrategicCommitsFound() {
            UUID planId = UUID.randomUUID();
            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, AS_OF.minusWeeks(1));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan));

            // Commit with no outcomeId (non-strategic)
            WeeklyCommitEntity nonStrategicCommit = new WeeklyCommitEntity(
                    UUID.randomUUID(), ORG_ID, planId, "Non-strategic task");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(nonStrategicCommit));

            List<IntegrationService.UserTicketContext> result =
                    service.getUnresolvedTicketsForUser(ORG_ID, USER_ID, AS_OF, 4);

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsUnresolvedTicketsLinkedToStrategicCommits() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            UUID outcomeId = UUID.randomUUID();

            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, AS_OF.minusWeeks(1));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, planId, "Auth work");
            commit.setOutcomeId(outcomeId);
            commit.populateSnapshot(null, null, null, "Obj A", outcomeId, "Auth Outcome");
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit));

            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId, "JIRA", "PROJ-42",
                    "https://jira.example.com/PROJ-42", "In Progress");
            when(linkRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(link));

            List<IntegrationService.UserTicketContext> result =
                    service.getUnresolvedTicketsForUser(ORG_ID, USER_ID, AS_OF, 4);

            assertEquals(1, result.size());
            IntegrationService.UserTicketContext ctx = result.get(0);
            assertEquals("PROJ-42", ctx.externalTicketId());
            assertEquals("JIRA", ctx.provider());
            assertEquals("In Progress", ctx.externalStatus());
            assertEquals("https://jira.example.com/PROJ-42", ctx.externalTicketUrl());
            assertEquals(outcomeId.toString(), ctx.outcomeId());
            assertEquals("Auth Outcome", ctx.outcomeName());
        }

        @Test
        void filtersOutResolvedTickets() {
            UUID planId = UUID.randomUUID();
            UUID commitId = UUID.randomUUID();
            UUID outcomeId = UUID.randomUUID();

            WeeklyPlanEntity plan = new WeeklyPlanEntity(planId, ORG_ID, USER_ID, AS_OF.minusWeeks(1));
            when(planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                    eq(ORG_ID), eq(USER_ID), any(), any()))
                    .thenReturn(List.of(plan));

            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, planId, "Done work");
            commit.setOutcomeId(outcomeId);
            when(commitRepository.findByOrgIdAndWeeklyPlanIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(commit));

            ExternalTicketLinkEntity resolvedLink = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId, "JIRA", "PROJ-99",
                    "https://jira.example.com/PROJ-99", "Done");
            when(linkRepository.findByOrgIdAndCommitIdIn(eq(ORG_ID), any()))
                    .thenReturn(List.of(resolvedLink));

            List<IntegrationService.UserTicketContext> result =
                    service.getUnresolvedTicketsForUser(ORG_ID, USER_ID, AS_OF, 4);

            assertTrue(result.isEmpty(), "Done tickets should not be surfaced");
        }
    }

    // ─── StubExternalTicketAdapter ─────────────────────────────────────────────

    private static class StubExternalTicketAdapter implements ExternalTicketAdapter {

        private final String providerName;
        private ExternalTicket ticketToReturn;
        private boolean available = true;

        StubExternalTicketAdapter(String providerName) {
            this.providerName = providerName;
        }

        @Override
        public String providerName() {
            return providerName;
        }

        @Override
        public Optional<ExternalTicket> fetchTicket(String ticketId) {
            if (!available) {
                throw new ExternalTicketUnavailableException("Stub unavailable");
            }
            return Optional.ofNullable(ticketToReturn);
        }

        @Override
        public List<ExternalTicket> searchTickets(UUID userId, String status) {
            return List.of();
        }

        @Override
        public void linkTicketToCommit(UUID commitId, String ticketId) {
            // no-op
        }

        @Override
        public java.util.List<ExternalTicket> syncTicketUpdates(java.time.Instant since) {
            return List.of();
        }

        @Override
        public void postComment(String ticketId, String comment) {
            // no-op stub
        }

        @Override
        public com.weekly.plan.domain.ProgressStatus mapToProgressStatus(String providerStatus) {
            if ("In Review".equals(providerStatus)) {
                return com.weekly.plan.domain.ProgressStatus.ON_TRACK;
            }
            if ("Blocked".equals(providerStatus)) {
                return com.weekly.plan.domain.ProgressStatus.AT_RISK;
            }
            return null;
        }

        @Override
        public String extractTicketId(java.util.Map<String, Object> webhookPayload) {
            Object issue = webhookPayload.get("issue");
            if (issue instanceof java.util.Map<?, ?> m) {
                Object key = m.get("key");
                return key != null ? key.toString() : null;
            }
            return null;
        }

        @Override
        public String extractStatus(java.util.Map<String, Object> webhookPayload) {
            Object issue = webhookPayload.get("issue");
            if (issue instanceof java.util.Map<?, ?> m) {
                Object fields = m.get("fields");
                if (fields instanceof java.util.Map<?, ?> fm) {
                    Object status = fm.get("status");
                    if (status instanceof java.util.Map<?, ?> sm) {
                        Object name = sm.get("name");
                        return name != null ? name.toString() : null;
                    }
                }
            }
            return null;
        }

        void setTicketToReturn(ExternalTicket ticket) {
            this.ticketToReturn = ticket;
        }

        void setAvailable(boolean available) {
            this.available = available;
        }
    }
}
