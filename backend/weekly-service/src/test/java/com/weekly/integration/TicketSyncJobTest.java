package com.weekly.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.repository.ProgressEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TicketSyncJob}.
 *
 * <p>Covers progress signal mapping (provider status → ProgressStatus) and
 * the fan-out logic that creates check-in entries for linked commits.
 */
class TicketSyncJobTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-03-10T09:00:00Z");

    private ExternalTicketAdapter jiraAdapter;
    private ExternalTicketLinkRepository linkRepository;
    private ProgressEntryRepository progressEntryRepository;
    private Clock fixedClock;
    private TicketSyncJob job;

    @BeforeEach
    void setUp() {
        jiraAdapter = mock(ExternalTicketAdapter.class);
        when(jiraAdapter.providerName()).thenReturn("JIRA");

        linkRepository = mock(ExternalTicketLinkRepository.class);
        progressEntryRepository = mock(ProgressEntryRepository.class);
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        when(progressEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job = new TicketSyncJob(
                List.of(jiraAdapter), linkRepository, progressEntryRepository, fixedClock);
    }

    // ── processAdapter ────────────────────────────────────────────────────────

    @Nested
    class ProcessAdapter {

        @Test
        void createsCheckInForTicketMovedToInReview() {
            UUID commitId = UUID.randomUUID();
            ExternalTicket ticket = ticketWithStatus("PROJ-1", "In Review");

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(ticket));
            when(jiraAdapter.mapToProgressStatus("In Review")).thenReturn(ProgressStatus.ON_TRACK);
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-1"))
                    .thenReturn(List.of(linkFor(commitId, "PROJ-1")));

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(1, created);
            verify(progressEntryRepository).save(argThat(entry ->
                    entry.getStatus() == ProgressStatus.ON_TRACK
                    && entry.getCommitId().equals(commitId)
                    && entry.getNote().contains("PROJ-1")
            ));
        }

        @Test
        void createsCheckInForTicketMovedToBlocked() {
            UUID commitId = UUID.randomUUID();
            ExternalTicket ticket = ticketWithStatus("PROJ-2", "Blocked");

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(ticket));
            when(jiraAdapter.mapToProgressStatus("Blocked")).thenReturn(ProgressStatus.AT_RISK);
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-2"))
                    .thenReturn(List.of(linkFor(commitId, "PROJ-2")));

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(1, created);
            verify(progressEntryRepository).save(argThat(entry ->
                    entry.getStatus() == ProgressStatus.AT_RISK
            ));
        }

        @Test
        void skipsTicketWhenStatusNotMappable() {
            UUID commitId = UUID.randomUUID();
            ExternalTicket ticket = ticketWithStatus("PROJ-3", "In Progress");
            ExternalTicketLinkEntity link = linkFor(commitId, "PROJ-3");

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(ticket));
            when(jiraAdapter.mapToProgressStatus("In Progress")).thenReturn(null);
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-3"))
                    .thenReturn(List.of(link));

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(0, created);
            assertEquals("In Progress", link.getExternalStatus());
            verify(linkRepository).save(link);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void skipsTicketWhenNoStatusPresent() {
            ExternalTicket ticket = new ExternalTicket(
                    "PROJ-4", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(ticket));
            when(jiraAdapter.mapToProgressStatus(null)).thenReturn(null);

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(0, created);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void skipsDuplicateCheckInWhenStatusUnchanged() {
            UUID commitId = UUID.randomUUID();
            ExternalTicket ticket = ticketWithStatus("PROJ-4A", "In Review");
            ExternalTicketLinkEntity link = linkFor(commitId, "PROJ-4A");
            link.setExternalStatus("In Review");

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(ticket));
            when(jiraAdapter.mapToProgressStatus("In Review")).thenReturn(ProgressStatus.ON_TRACK);
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-4A"))
                    .thenReturn(List.of(link));

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(0, created);
            verify(linkRepository).save(link);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void fanOutToMultipleLinkedCommits() {
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();
            ExternalTicket ticket = ticketWithStatus("PROJ-5", "In Review");

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(ticket));
            when(jiraAdapter.mapToProgressStatus("In Review")).thenReturn(ProgressStatus.ON_TRACK);
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-5"))
                    .thenReturn(List.of(linkFor(commitId1, "PROJ-5"), linkFor(commitId2, "PROJ-5")));

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(2, created);
            verify(progressEntryRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        void returnsZeroWhenNoTicketsLinked() {
            ExternalTicket ticket = ticketWithStatus("PROJ-6", "In Review");

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(ticket));
            when(jiraAdapter.mapToProgressStatus("In Review")).thenReturn(ProgressStatus.ON_TRACK);
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-6"))
                    .thenReturn(List.of());

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(0, created);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void returnsZeroWhenAdapterIsUnavailable() {
            when(jiraAdapter.syncTicketUpdates(any()))
                    .thenThrow(new ExternalTicketAdapter.ExternalTicketUnavailableException("Down"));

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            assertEquals(0, created);
            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void handlesMultipleTicketsInBatch() {
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();

            ExternalTicket t1 = ticketWithStatus("PROJ-10", "In Review");
            ExternalTicket t2 = ticketWithStatus("PROJ-11", "Blocked");
            ExternalTicket t3 = ticketWithStatus("PROJ-12", "In Progress"); // not mappable

            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of(t1, t2, t3));
            when(jiraAdapter.mapToProgressStatus("In Review")).thenReturn(ProgressStatus.ON_TRACK);
            when(jiraAdapter.mapToProgressStatus("Blocked")).thenReturn(ProgressStatus.AT_RISK);
            when(jiraAdapter.mapToProgressStatus("In Progress")).thenReturn(null);

            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-10"))
                    .thenReturn(List.of(linkFor(commitId1, "PROJ-10")));
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-11"))
                    .thenReturn(List.of(linkFor(commitId2, "PROJ-11")));
            when(linkRepository.findByProviderAndExternalTicketId("JIRA", "PROJ-12"))
                    .thenReturn(List.of()); // not linked

            int created = job.processAdapter(jiraAdapter, FIXED_NOW.minusSeconds(3600));

            // 1 (PROJ-10) + 1 (PROJ-11) + 0 (PROJ-12 not mappable) = 2
            assertEquals(2, created);
        }
    }

    // ── syncTicketStatuses (full job run) ─────────────────────────────────────

    @Nested
    class SyncTicketStatuses {

        @Test
        void runsWithoutErrorWhenAdaptersReturnEmpty() {
            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of());

            job.syncTicketStatuses();

            verify(progressEntryRepository, never()).save(any());
        }

        @Test
        void usesLastSyncedAtOnSubsequentRuns() {
            when(jiraAdapter.syncTicketUpdates(any())).thenReturn(List.of());

            // First run sets lastSyncedAt
            job.syncTicketStatuses();
            // Second run — should use the timestamp set during first run
            job.syncTicketStatuses();

            // Verify syncTicketUpdates was called twice
            verify(jiraAdapter, org.mockito.Mockito.times(2)).syncTicketUpdates(any());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ExternalTicket ticketWithStatus(String ticketId, String status) {
        return new ExternalTicket(
                ticketId,
                Optional.of("Test ticket"),
                Optional.empty(),
                Optional.of(status),
                Optional.empty()
        );
    }

    private ExternalTicketLinkEntity linkFor(UUID commitId, String ticketId) {
        return new ExternalTicketLinkEntity(
                UUID.randomUUID(), ORG_ID, commitId, "JIRA", ticketId, null, null);
    }
}
