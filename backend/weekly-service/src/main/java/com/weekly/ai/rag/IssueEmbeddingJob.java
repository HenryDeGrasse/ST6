package com.weekly.ai.rag;

import com.weekly.issues.events.AssignmentReconciledEvent;
import com.weekly.issues.events.CommentAddedEvent;
import com.weekly.issues.events.IssueCreatedEvent;
import com.weekly.issues.events.IssueUpdatedEvent;
import com.weekly.issues.repository.IssueRepository;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Async embedding pipeline with debounced triggers (Phase 6, Step 12).
 *
 * <p>Listens for Spring application events published by {@link com.weekly.issues.service.IssueService}
 * and {@link com.weekly.issues.service.AssignmentService} and schedules a vector re-embed
 * for the affected issue(s).
 *
 * <h2>Debounce</h2>
 * <p>Rapid back-to-back changes (e.g. a user editing title then description within seconds)
 * are coalesced: if a new event arrives for the same issue within 30 seconds of a previous
 * event, the existing timer is cancelled and a fresh 30-second countdown begins. Only one
 * embed call is made per 30-second quiet period.
 *
 * <h2>Retry / error handling</h2>
 * <p>If the embed fails (e.g. Pinecone or OpenAI is temporarily unavailable), the job
 * schedules a retry with exponential back-off: 5 s, 10 s, 20 s (up to {@value #MAX_RETRIES}
 * retries). After exhausting retries a warning is logged and the issue is left for the
 * next triggering event or a manual backfill.  Failures are <em>never</em> propagated to
 * the write path — issue operations complete successfully regardless.
 *
 * <h2>Embedding version</h2>
 * <p>After each successful embed, {@link IssueRepository#incrementEmbeddingVersion(UUID)} is
 * called in its own transaction to bump {@code embedding_version}.  The backfill endpoint
 * uses this column to find issues that have never been embedded (version = 0).
 */
@Component
public class IssueEmbeddingJob {

    public static final int DEBOUNCE_SECONDS = 30;
    public static final int MAX_RETRIES = 3;
    /** Base back-off interval in seconds; doubles each attempt. */
    private static final int BACKOFF_BASE_SECONDS = 5;

    private static final Logger LOG = LoggerFactory.getLogger(IssueEmbeddingJob.class);

    private final IssueEmbeddingService embeddingService;
    private final IssueRepository issueRepository;
    private final ScheduledExecutorService scheduler;
    /**
     * Pending debounce tasks keyed by issue ID.  If a new event arrives for an
     * issue that already has a pending task, the pending task is cancelled and a
     * fresh one is submitted.
     */
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pendingTasks =
            new ConcurrentHashMap<>();

    @Autowired
    public IssueEmbeddingJob(
            IssueEmbeddingService embeddingService,
            IssueRepository issueRepository
    ) {
        this(embeddingService, issueRepository,
                Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "embedding-debounce");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /** Visible for testing — allows injecting a custom scheduler without 30-second delays. */
    public IssueEmbeddingJob(
            IssueEmbeddingService embeddingService,
            IssueRepository issueRepository,
            ScheduledExecutorService scheduler
    ) {
        this.embeddingService = embeddingService;
        this.issueRepository = issueRepository;
        this.scheduler = scheduler;
    }

    // ── Event listeners ───────────────────────────────────────────────────────

    /** Schedules an initial embed when a new issue is created. */
    @EventListener
    public void onIssueCreated(IssueCreatedEvent event) {
        scheduleEmbed(event.issueId());
    }

    /** Schedules a re-embed when an issue's content changes. */
    @EventListener
    public void onIssueUpdated(IssueUpdatedEvent event) {
        scheduleEmbed(event.issueId());
    }

    /** Schedules a re-embed when a comment is added (enriches embedding context). */
    @EventListener
    public void onCommentAdded(CommentAddedEvent event) {
        scheduleEmbed(event.issueId());
    }

    /**
     * Schedules a re-embed for every issue whose status changed during reconciliation.
     * Reconciliation adds rich context (completion status, actual result text) to the
     * embedding.
     */
    @EventListener
    public void onAssignmentReconciled(AssignmentReconciledEvent event) {
        for (UUID issueId : event.issueIds()) {
            scheduleEmbed(issueId);
        }
    }

    // ── Backfill entry-point ──────────────────────────────────────────────────

    /**
     * Immediately embeds the given issue without a debounce delay.
     *
     * <p>Used by the admin backfill endpoint to process issues that have
     * {@code embedding_version = 0} (never been embedded).
     *
     * @param issueId the UUID of the issue to embed
     * @return {@code true} if the embed succeeded, {@code false} otherwise
     */
    public boolean embedNow(UUID issueId) {
        try {
            embeddingService.embedIssue(issueId);
            issueRepository.incrementEmbeddingVersion(issueId);
            LOG.debug("Backfill embed succeeded for issue {}", issueId);
            return true;
        } catch (Exception e) {
            LOG.error("Backfill embed failed for issue {}: {}", issueId, e.getMessage(), e);
            return false;
        }
    }

    // ── Internal debounce logic ───────────────────────────────────────────────

    /**
     * Schedules an embed for {@code issueId} after {@value #DEBOUNCE_SECONDS} seconds.
     * If a task is already pending for this issue, it is cancelled and a fresh
     * countdown begins.
     */
    public void scheduleEmbed(UUID issueId) {
        // Cancel any existing pending task for this issue (debounce reset)
        ScheduledFuture<?> existing = pendingTasks.remove(issueId);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> task = scheduler.schedule(
                () -> embedWithRetry(issueId, 0),
                DEBOUNCE_SECONDS,
                TimeUnit.SECONDS
        );
        pendingTasks.put(issueId, task);
    }

    /**
     * Attempts to embed the issue, retrying with exponential back-off on failure.
     *
     * @param issueId the issue to embed
     * @param attempt zero-based attempt index
     */
    public void embedWithRetry(UUID issueId, int attempt) {
        // Remove from pending map so a new event can schedule a fresh debounce
        pendingTasks.remove(issueId);
        try {
            embeddingService.embedIssue(issueId);
            issueRepository.incrementEmbeddingVersion(issueId);
            LOG.debug("Embedded issue {} (attempt {})", issueId, attempt + 1);
        } catch (Exception e) {
            if (attempt < MAX_RETRIES) {
                long delaySeconds = BACKOFF_BASE_SECONDS * (1L << attempt); // 5, 10, 20
                LOG.warn(
                        "Embedding failed for issue {} (attempt {}/{}), retrying in {}s: {}",
                        issueId, attempt + 1, MAX_RETRIES, delaySeconds, e.getMessage());
                scheduler.schedule(
                        () -> embedWithRetry(issueId, attempt + 1),
                        delaySeconds,
                        TimeUnit.SECONDS
                );
            } else {
                LOG.error(
                        "Embedding permanently failed for issue {} after {} retries: {}",
                        issueId, MAX_RETRIES, e.getMessage(), e);
            }
        }
    }

    /** Shuts down the scheduler cleanly on application stop. */
    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
