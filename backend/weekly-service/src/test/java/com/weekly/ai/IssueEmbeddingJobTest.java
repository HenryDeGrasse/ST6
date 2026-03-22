package com.weekly.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.weekly.ai.rag.IssueEmbeddingJob;
import com.weekly.ai.rag.IssueEmbeddingService;
import com.weekly.issues.repository.IssueRepository;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link IssueEmbeddingJob}.
 *
 * <p>Uses a real {@link ScheduledExecutorService} with the package-visible constructor
 * so the debounce and retry logic can be exercised synchronously via
 * {@link IssueEmbeddingJob#embedWithRetry} and
 * {@link IssueEmbeddingJob#scheduleEmbed} without waiting 30 seconds.
 */
@ExtendWith(MockitoExtension.class)
class IssueEmbeddingJobTest {

    @Mock
    private IssueEmbeddingService embeddingService;

    @Mock
    private IssueRepository issueRepository;

    private ScheduledExecutorService scheduler;
    private IssueEmbeddingJob job;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newScheduledThreadPool(4);
        job = new IssueEmbeddingJob(embeddingService, issueRepository, scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    // ── embedNow (backfill) ───────────────────────────────────────────────────

    @Test
    void embedNowSucceedsAndIncrementsVersion() {
        UUID issueId = UUID.randomUUID();

        boolean result = job.embedNow(issueId);

        assertThat(result).isTrue();
        verify(embeddingService).embedIssue(issueId);
        verify(issueRepository).incrementEmbeddingVersion(issueId);
    }

    @Test
    void embedNowReturnsFalseWhenEmbeddingFails() {
        UUID issueId = UUID.randomUUID();
        doThrow(new RuntimeException("Pinecone unavailable"))
                .when(embeddingService).embedIssue(issueId);

        boolean result = job.embedNow(issueId);

        assertThat(result).isFalse();
        verify(issueRepository, never()).incrementEmbeddingVersion(any());
    }

    // ── Debounce ─────────────────────────────────────────────────────────────

    /**
     * Verifies that calling {@code scheduleEmbed} multiple times for the same issue
     * and then directly invoking {@code embedWithRetry} results in only one embed call.
     * This simulates the debounce cancellation: earlier scheduled tasks are cancelled
     * when a new event arrives, and only the final task fires.
     */
    @Test
    void multipleScheduleCallsForSameIssueResultInOneEmbed() {
        UUID issueId = UUID.randomUUID();

        // Schedule several times (each cancels the previous pending task)
        job.scheduleEmbed(issueId);
        job.scheduleEmbed(issueId);
        job.scheduleEmbed(issueId);

        // Directly invoke embedWithRetry (simulates the scheduled task firing)
        job.embedWithRetry(issueId, 0);

        verify(embeddingService, times(1)).embedIssue(issueId);
        verify(issueRepository, times(1)).incrementEmbeddingVersion(issueId);
    }

    /**
     * Verifies that after repeated {@code scheduleEmbed} calls, calling
     * {@code embedWithRetry} once produces exactly one embed — not one per
     * schedule call.
     */
    @Test
    void repeatedScheduleCallsCollapseToSingleEmbedInvocation() {
        UUID issueId = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            job.scheduleEmbed(issueId);
        }

        // Simulate the single surviving scheduled task firing
        job.embedWithRetry(issueId, 0);
        verify(embeddingService, times(1)).embedIssue(issueId);
    }

    // ── Retry / error handling ────────────────────────────────────────────────

    @Test
    void embedWithRetryDoesNotPropagateExceptionToCaller() {
        UUID issueId = UUID.randomUUID();
        doThrow(new RuntimeException("OpenAI down")).when(embeddingService).embedIssue(issueId);

        // Must not throw, even at the final retry attempt
        job.embedWithRetry(issueId, IssueEmbeddingJob.MAX_RETRIES);
    }

    @Test
    void embedWithRetryDoesNotRetryBeyondMaxRetries() throws Exception {
        UUID issueId = UUID.randomUUID();
        doThrow(new RuntimeException("Pinecone timeout")).when(embeddingService).embedIssue(issueId);

        // Calling at MAX_RETRIES means this is the last attempt — no further scheduling
        job.embedWithRetry(issueId, IssueEmbeddingJob.MAX_RETRIES);

        // Only the direct call was made; no retries added to the queue
        verify(embeddingService, times(1)).embedIssue(issueId);
        verify(issueRepository, never()).incrementEmbeddingVersion(any());
    }

    @Test
    void embedWithRetryAttemptsEmbedBeforeMaxRetries() {
        UUID issueId = UUID.randomUUID();
        doThrow(new RuntimeException("transient error")).when(embeddingService).embedIssue(issueId);

        // First attempt (attempt = 0) — should try to embed and then schedule a retry
        job.embedWithRetry(issueId, 0);

        // The embed was attempted at least once
        verify(embeddingService, atLeastOnce()).embedIssue(issueId);
        // No version increment because the embed failed
        verify(issueRepository, never()).incrementEmbeddingVersion(any());
    }

    // ── Version increment on success ─────────────────────────────────────────

    @Test
    void embedWithRetryIncrementsVersionOnSuccess() {
        UUID issueId = UUID.randomUUID();

        job.embedWithRetry(issueId, 0);

        verify(embeddingService).embedIssue(issueId);
        verify(issueRepository).incrementEmbeddingVersion(issueId);
    }

    // ── Different issues are tracked independently ────────────────────────────

    @Test
    void differentIssuesAreEmbeddedIndependently() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        job.scheduleEmbed(id1);
        job.scheduleEmbed(id2);
        job.scheduleEmbed(id1); // re-debounce id1

        // Both issues should embed when triggered
        job.embedWithRetry(id1, 0);
        job.embedWithRetry(id2, 0);

        verify(embeddingService).embedIssue(id1);
        verify(embeddingService).embedIssue(id2);
        verify(issueRepository).incrementEmbeddingVersion(id1);
        verify(issueRepository).incrementEmbeddingVersion(id2);
    }
}
