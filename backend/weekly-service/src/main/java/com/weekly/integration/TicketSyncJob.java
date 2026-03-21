package com.weekly.integration;

import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.repository.ProgressEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that polls each configured external ticket adapter for status
 * changes and maps them to {@link ProgressEntryEntity} (daily check-in) records
 * (ADR-010, Wave 4).
 *
 * <p>Status mapping:
 * <ul>
 *   <li>Provider status "In Review" → {@link ProgressStatus#ON_TRACK}</li>
 *   <li>Provider status "Blocked"   → {@link ProgressStatus#AT_RISK}</li>
 *   <li>Provider status "Done" / "Completed" → {@link ProgressStatus#DONE_EARLY}</li>
 *   <li>All other statuses are ignored (no check-in created).</li>
 * </ul>
 *
 * <p>Mapping is delegated to {@link ExternalTicketAdapter#mapToProgressStatus} so
 * each provider can define its own status vocabulary.
 *
 * <p>Enabled via {@code integration.ticket-sync.enabled=true} (worker profile).
 * The polling interval defaults to 15 minutes and is configurable via
 * {@code integration.ticket-sync.interval-ms}.
 */
@Component
@ConditionalOnProperty(name = "integration.ticket-sync.enabled", havingValue = "true")
public class TicketSyncJob {

    private static final Logger LOG = LoggerFactory.getLogger(TicketSyncJob.class);

    /** How far back to look on the first poll (default: 24 hours). */
    private static final long INITIAL_LOOKBACK_HOURS = 24;

    private final List<ExternalTicketAdapter> adapters;
    private final ExternalTicketLinkRepository linkRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final Clock clock;

    /** Tracks the last successful sync instant so each poll covers only new changes. */
    private volatile Instant lastSyncedAt;

    @Autowired
    public TicketSyncJob(
            @Qualifier("externalTicketAdapters") List<ExternalTicketAdapter> adapters,
            ExternalTicketLinkRepository linkRepository,
            ProgressEntryRepository progressEntryRepository
    ) {
        this(adapters, linkRepository, progressEntryRepository, Clock.systemUTC());
    }

    /** Package-private constructor for unit tests (injectable clock). */
    TicketSyncJob(
            List<ExternalTicketAdapter> adapters,
            ExternalTicketLinkRepository linkRepository,
            ProgressEntryRepository progressEntryRepository,
            Clock clock
    ) {
        this.adapters = adapters;
        this.linkRepository = linkRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.clock = clock;
    }

    /**
     * Runs every 15 minutes by default (configurable via
     * {@code integration.ticket-sync.cron}).
     *
     * <p>For each registered adapter the job:
     * <ol>
     *   <li>Calls {@link ExternalTicketAdapter#syncTicketUpdates} with the
     *       last-sync timestamp.</li>
     *   <li>For each updated ticket, finds all {@link ExternalTicketLinkEntity}
     *       records that reference it.</li>
     *   <li>Compares the provider status with the last synced status stored on
     *       each {@link ExternalTicketLinkEntity}.</li>
     *   <li>Updates the cached external status / sync timestamp on the link.</li>
     *   <li>Maps the provider status to a {@link ProgressStatus} using
     *       {@link ExternalTicketAdapter#mapToProgressStatus}.</li>
     *   <li>Creates a new {@link ProgressEntryEntity} only when the ticket
     *       status actually changed and the mapped status is non-null.</li>
     * </ol>
     */
    @Scheduled(fixedDelayString = "${integration.ticket-sync.interval-ms:900000}",
               initialDelayString = "${integration.ticket-sync.initial-delay-ms:60000}")
    @Transactional
    public void syncTicketStatuses() {
        if (adapters.isEmpty()) {
            return;
        }

        Instant since = lastSyncedAt != null
                ? lastSyncedAt
                : Instant.now(clock).minus(INITIAL_LOOKBACK_HOURS, ChronoUnit.HOURS);

        Instant syncStart = Instant.now(clock);
        int totalCreated = 0;

        for (ExternalTicketAdapter adapter : adapters) {
            try {
                totalCreated += processAdapter(adapter, since);
            } catch (Exception e) {
                LOG.warn("TicketSyncJob: error syncing adapter {}: {}",
                        adapter.providerName(), e.getMessage(), e);
            }
        }

        lastSyncedAt = syncStart;
        LOG.info("TicketSyncJob: sync complete, {} check-in(s) created", totalCreated);
    }

    // ── Per-adapter processing ────────────────────────────────

    int processAdapter(ExternalTicketAdapter adapter, Instant since) {
        List<ExternalTicket> updates;
        try {
            updates = adapter.syncTicketUpdates(since);
        } catch (ExternalTicketAdapter.ExternalTicketUnavailableException e) {
            LOG.warn("TicketSyncJob: adapter {} unavailable: {}",
                    adapter.providerName(), e.getMessage());
            return 0;
        }

        int created = 0;
        for (ExternalTicket ticket : updates) {
            created += processTicket(adapter, ticket);
        }
        return created;
    }

    private int processTicket(ExternalTicketAdapter adapter, ExternalTicket ticket) {
        String rawStatus = ticket.status().orElse(null);
        ProgressStatus progressStatus = adapter.mapToProgressStatus(rawStatus);

        List<ExternalTicketLinkEntity> links =
                linkRepository.findByProviderAndExternalTicketId(
                        adapter.providerName().toUpperCase(), ticket.ticketId());
        if (links.isEmpty()) {
            return 0;
        }

        int created = 0;
        Instant syncedAt = Instant.now(clock);
        for (ExternalTicketLinkEntity link : links) {
            boolean statusChanged = !Objects.equals(link.getExternalStatus(), rawStatus);
            link.setExternalStatus(rawStatus);
            link.setLastSyncedAt(syncedAt);
            linkRepository.save(link);

            if (!statusChanged) {
                LOG.debug("TicketSyncJob: {} ticket {} unchanged (status '{}'), skipping check-in",
                        link.getProvider(), link.getExternalTicketId(), rawStatus);
                continue;
            }
            if (progressStatus == null) {
                LOG.debug("TicketSyncJob: no progress mapping for {} status '{}', skipping check-in",
                        adapter.providerName(), rawStatus);
                continue;
            }

            createCheckIn(link, progressStatus, rawStatus);
            created++;
        }
        return created;
    }

    private void createCheckIn(
            ExternalTicketLinkEntity link,
            ProgressStatus status,
            String rawStatus
    ) {
        String note = "Auto check-in from " + link.getProvider()
                + " ticket " + link.getExternalTicketId()
                + " status: " + rawStatus;

        ProgressEntryEntity entry = new ProgressEntryEntity(
                UUID.randomUUID(),
                link.getOrgId(),
                link.getCommitId(),
                status,
                note
        );
        progressEntryRepository.save(entry);

        LOG.debug("TicketSyncJob: created check-in {} for commit {} (ticket {}/{})",
                status, link.getCommitId(), link.getProvider(), link.getExternalTicketId());
    }
}
