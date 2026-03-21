package com.weekly.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.service.ReconciliationSubmittedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultOutboundCommentService}.
 *
 * <p>Verifies that comments are posted on all external tickets linked to commits
 * in a plan, and that adapter failures are swallowed so they never block the
 * domain flow.
 */
class OutboundCommentServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private WeeklyCommitRepository commitRepository;
    private ExternalTicketLinkRepository linkRepository;
    private ExternalTicketAdapter jiraAdapter;
    private DefaultOutboundCommentService service;

    @BeforeEach
    void setUp() {
        commitRepository = mock(WeeklyCommitRepository.class);
        linkRepository = mock(ExternalTicketLinkRepository.class);
        jiraAdapter = mock(ExternalTicketAdapter.class);
        when(jiraAdapter.providerName()).thenReturn("JIRA");
        service = new DefaultOutboundCommentService(
                commitRepository, linkRepository, List.of(jiraAdapter));
    }

    // ── postReconciliationComment ─────────────────────────────────────────────

    @Nested
    class PostReconciliationComment {

        @Test
        void postsCommentOnLinkedTicket() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, PLAN_ID, "Task");

            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId, "JIRA", "PROJ-42",
                    "https://jira.example.com/PROJ-42", "In Progress");

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId))
                    .thenReturn(List.of(link));

            service.postReconciliationComment(ORG_ID, PLAN_ID, "Week reconciled: 3 done.");

            verify(jiraAdapter).postComment(eq("PROJ-42"), contains("Week reconciled: 3 done."));
        }

        @Test
        void postsCommentsAcrossMultipleCommitsAndTickets() {
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();

            WeeklyCommitEntity commit1 = new WeeklyCommitEntity(commitId1, ORG_ID, PLAN_ID, "Task 1");
            WeeklyCommitEntity commit2 = new WeeklyCommitEntity(commitId2, ORG_ID, PLAN_ID, "Task 2");

            ExternalTicketLinkEntity link1 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId1, "JIRA", "PROJ-1", null, null);
            ExternalTicketLinkEntity link2 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId2, "JIRA", "PROJ-2", null, null);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit1, commit2));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId1))
                    .thenReturn(List.of(link1));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId2))
                    .thenReturn(List.of(link2));

            service.postReconciliationComment(ORG_ID, PLAN_ID, "Summary");

            verify(jiraAdapter).postComment(eq("PROJ-1"), any());
            verify(jiraAdapter).postComment(eq("PROJ-2"), any());
        }

        @Test
        void deduplicatesCommentsWhenSameTicketLinkedAcrossMultipleCommits() {
            UUID commitId1 = UUID.randomUUID();
            UUID commitId2 = UUID.randomUUID();

            WeeklyCommitEntity commit1 = new WeeklyCommitEntity(commitId1, ORG_ID, PLAN_ID, "Task 1");
            WeeklyCommitEntity commit2 = new WeeklyCommitEntity(commitId2, ORG_ID, PLAN_ID, "Task 2");

            ExternalTicketLinkEntity link1 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId1, "JIRA", "PROJ-42", null, null);
            ExternalTicketLinkEntity link2 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId2, "JIRA", "PROJ-42", null, null);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit1, commit2));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId1))
                    .thenReturn(List.of(link1));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId2))
                    .thenReturn(List.of(link2));

            service.postReconciliationComment(ORG_ID, PLAN_ID, "Summary");

            verify(jiraAdapter, times(1)).postComment(eq("PROJ-42"), any());
        }

        @Test
        void isNoOpWhenNoCommitsExist() {
            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of());

            service.postReconciliationComment(ORG_ID, PLAN_ID, "Summary");

            verify(linkRepository, never()).findByOrgIdAndCommitId(any(), any());
            verify(jiraAdapter, never()).postComment(any(), any());
        }

        @Test
        void isNoOpWhenNoLinksExist() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, PLAN_ID, "Task");

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId))
                    .thenReturn(List.of());

            service.postReconciliationComment(ORG_ID, PLAN_ID, "Summary");

            verify(jiraAdapter, never()).postComment(any(), any());
        }

        @Test
        void skipsTicketWhenNoAdapterRegisteredForProvider() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, PLAN_ID, "Task");

            // Link to LINEAR — but only JIRA adapter is registered
            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId, "LINEAR", "ENG-99", null, null);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId))
                    .thenReturn(List.of(link));

            // Should not throw — no matching adapter
            assertDoesNotThrow(() ->
                    service.postReconciliationComment(ORG_ID, PLAN_ID, "Summary"));

            verify(jiraAdapter, never()).postComment(any(), any());
        }

        @Test
        void continuesWithRemainingTicketsWhenAdapterThrows() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, PLAN_ID, "Task");

            ExternalTicketLinkEntity link1 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId, "JIRA", "PROJ-FAIL", null, null);
            ExternalTicketLinkEntity link2 = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId, "JIRA", "PROJ-OK", null, null);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId))
                    .thenReturn(List.of(link1, link2));

            doThrow(new ExternalTicketAdapter.ExternalTicketUnavailableException("Network error"))
                    .when(jiraAdapter).postComment(eq("PROJ-FAIL"), any());

            // Should not throw — exception is swallowed
            assertDoesNotThrow(() ->
                    service.postReconciliationComment(ORG_ID, PLAN_ID, "Summary"));

            // Both tickets were attempted; PROJ-OK still received the comment
            verify(jiraAdapter).postComment(eq("PROJ-FAIL"), any());
            verify(jiraAdapter).postComment(eq("PROJ-OK"), any());
        }

        @Test
        void isNoOpWhenNoAdaptersConfigured() {
            DefaultOutboundCommentService noAdapterService = new DefaultOutboundCommentService(
                    commitRepository, linkRepository, List.of());

            // Should not call any repository (early-out guard)
            assertDoesNotThrow(() ->
                    noAdapterService.postReconciliationComment(ORG_ID, PLAN_ID, "Summary"));

            verify(commitRepository, never()).findByOrgIdAndWeeklyPlanId(any(), any());
        }
    }

    // ── onReconciliationSubmitted (event listener) ────────────────────────────

    @Nested
    class OnReconciliationSubmitted {

        @Test
        void delegatesToPostReconciliationComment() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, PLAN_ID, "Task");

            ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                    UUID.randomUUID(), ORG_ID, commitId, "JIRA", "PROJ-7", null, null);

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId))
                    .thenReturn(List.of(link));

            ReconciliationSubmittedEvent event =
                    new ReconciliationSubmittedEvent(this, ORG_ID, PLAN_ID, "3 done, 0 dropped.");

            // Calling the event listener method directly (bypasses Spring container)
            service.onReconciliationSubmitted(event);

            verify(jiraAdapter).postComment(eq("PROJ-7"), contains("3 done, 0 dropped."));
        }

        @Test
        void isNoOpWhenPlanHasNoLinkedTickets() {
            UUID commitId = UUID.randomUUID();
            WeeklyCommitEntity commit = new WeeklyCommitEntity(commitId, ORG_ID, PLAN_ID, "Task");

            when(commitRepository.findByOrgIdAndWeeklyPlanId(ORG_ID, PLAN_ID))
                    .thenReturn(List.of(commit));
            when(linkRepository.findByOrgIdAndCommitId(ORG_ID, commitId))
                    .thenReturn(List.of());

            ReconciliationSubmittedEvent event =
                    new ReconciliationSubmittedEvent(this, ORG_ID, PLAN_ID, "Summary");

            assertDoesNotThrow(() -> service.onReconciliationSubmitted(event));

            verify(jiraAdapter, never()).postComment(any(), any());
        }
    }
}
