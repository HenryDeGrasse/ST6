package com.weekly.integration;

import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.ProgressStatus;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.plan.service.CommitNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link IntegrationService}.
 *
 * <p>Orchestrates calls to the active {@link ExternalTicketAdapter}, stores
 * link records in {@link ExternalTicketLinkRepository}, and auto-populates
 * empty commit title/description from the fetched ticket data.
 */
@Service
public class DefaultIntegrationService implements IntegrationService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultIntegrationService.class);

    private final ExternalTicketLinkRepository linkRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyPlanRepository planRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final Map<String, ExternalTicketAdapter> adapters;

    public DefaultIntegrationService(
            ExternalTicketLinkRepository linkRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyPlanRepository planRepository,
            ProgressEntryRepository progressEntryRepository,
            @Qualifier("externalTicketAdapters") List<ExternalTicketAdapter> adapterList
    ) {
        this.linkRepository = linkRepository;
        this.commitRepository = commitRepository;
        this.planRepository = planRepository;
        this.progressEntryRepository = progressEntryRepository;
        // Build provider-name → adapter map (case-insensitive keys)
        this.adapters = new java.util.HashMap<>();
        for (ExternalTicketAdapter adapter : adapterList) {
            adapters.put(adapter.providerName().toUpperCase(), adapter);
        }
    }

    /**
     * Links a commit to an external ticket.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate commit exists and belongs to the org.</li>
     *   <li>Check for an existing link (idempotent — returns it if found).</li>
     *   <li>Fetch ticket from the provider adapter.</li>
     *   <li>Notify the provider that the link has been made.</li>
     *   <li>Auto-populate commit title/description from ticket if currently empty.</li>
     *   <li>Persist and return the link record.</li>
     * </ol>
     */
    @Override
    @Transactional
    public ExternalTicketLinkResponse linkTicket(UUID orgId, LinkTicketRequest request) {
        UUID commitId = request.commitId();
        String provider = request.provider().toUpperCase();
        String externalTicketId = request.externalTicketId();

        // 1. Verify the commit exists and belongs to this org
        WeeklyCommitEntity commit = commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));

        // 2. Idempotency — return existing link if already present
        Optional<ExternalTicketLinkEntity> existing =
                linkRepository.findByOrgIdAndCommitIdAndProviderAndExternalTicketId(
                        orgId, commitId, provider, externalTicketId);
        if (existing.isPresent()) {
            return ExternalTicketLinkResponse.from(existing.get());
        }

        // 3. Fetch ticket from the provider (best-effort — null if provider unavailable)
        ExternalTicketAdapter adapter = adapters.get(provider);
        Optional<ExternalTicket> ticketOpt = Optional.empty();
        if (adapter != null) {
            try {
                ticketOpt = adapter.fetchTicket(externalTicketId);
            } catch (ExternalTicketAdapter.ExternalTicketUnavailableException e) {
                // Proceed without ticket data — the link is still recorded
            }
        }

        // 4. Notify the provider (best-effort)
        if (adapter != null && ticketOpt.isPresent()) {
            try {
                adapter.linkTicketToCommit(commitId, externalTicketId);
            } catch (ExternalTicketAdapter.ExternalTicketUnavailableException e) {
                // Non-fatal — the local link is still created
            }
        }

        // 5. Auto-populate commit fields from ticket data if currently empty
        if (ticketOpt.isPresent()) {
            ExternalTicket ticket = ticketOpt.get();
            boolean commitUpdated = false;
            if (isNullOrEmpty(commit.getTitle()) && ticket.title().isPresent()) {
                commit.setTitle(ticket.title().get());
                commitUpdated = true;
            }
            if (isNullOrEmpty(commit.getDescription()) && ticket.description().isPresent()) {
                commit.setDescription(ticket.description().get());
                commitUpdated = true;
            }
            if (commitUpdated) {
                commitRepository.save(commit);
            }
        }

        // 6. Persist the link record
        String ticketUrl = ticketOpt.flatMap(ExternalTicket::url).orElse(null);
        String ticketStatus = ticketOpt.flatMap(ExternalTicket::status).orElse(null);
        ExternalTicketLinkEntity link = new ExternalTicketLinkEntity(
                UUID.randomUUID(),
                orgId,
                commitId,
                provider,
                externalTicketId,
                ticketUrl,
                ticketStatus
        );
        link.setLastSyncedAt(Instant.now());
        linkRepository.save(link);

        return ExternalTicketLinkResponse.from(link);
    }

    /**
     * Returns all external ticket links for the given commit.
     */
    @Override
    @Transactional(readOnly = true)
    public LinkedTicketsResponse getLinkedTickets(UUID orgId, UUID commitId) {
        // Verify commit exists and belongs to this org
        commitRepository.findById(commitId)
                .filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new CommitNotFoundException(commitId));

        List<ExternalTicketLinkResponse> links =
                linkRepository.findByOrgIdAndCommitId(orgId, commitId)
                        .stream()
                        .map(ExternalTicketLinkResponse::from)
                        .toList();

        return new LinkedTicketsResponse(commitId.toString(), links);
    }

    /**
     * Processes an inbound webhook payload from a provider.
     *
     * <p>Steps:
     * <ol>
     *   <li>Look up the adapter for the given provider.</li>
     *   <li>Extract the ticket ID and status from the payload.</li>
     *   <li>Find all {@link ExternalTicketLinkEntity} records for that ticket.</li>
     *   <li>Update the cached external status / sync timestamp on each link.</li>
     *   <li>Map the status to a {@link ProgressStatus} using the adapter.</li>
     *   <li>Create a {@link ProgressEntryEntity} check-in only when the ticket
     *       status actually changed and the mapped status is non-null.</li>
     * </ol>
     */
    @Override
    @Transactional
    public int processWebhook(String provider, Map<String, Object> webhookPayload) {
        String normalizedProvider = provider.toUpperCase();
        ExternalTicketAdapter adapter = adapters.get(normalizedProvider);
        if (adapter == null) {
            LOG.warn("processWebhook: no adapter registered for provider '{}', ignoring", provider);
            return 0;
        }

        String ticketId = adapter.extractTicketId(webhookPayload);
        if (ticketId == null || ticketId.isBlank()) {
            LOG.debug("processWebhook: could not extract ticket ID from {} payload", provider);
            return 0;
        }

        String rawStatus = adapter.extractStatus(webhookPayload);
        ProgressStatus progressStatus = adapter.mapToProgressStatus(rawStatus);

        List<ExternalTicketLinkEntity> links =
                linkRepository.findByProviderAndExternalTicketId(normalizedProvider, ticketId);
        if (links.isEmpty()) {
            LOG.debug("processWebhook: no commits linked to {} ticket {}", provider, ticketId);
            return 0;
        }

        int created = 0;
        Instant syncedAt = Instant.now();
        for (ExternalTicketLinkEntity link : links) {
            boolean statusChanged = !Objects.equals(link.getExternalStatus(), rawStatus);
            link.setExternalStatus(rawStatus);
            link.setLastSyncedAt(syncedAt);
            linkRepository.save(link);

            if (!statusChanged) {
                LOG.debug("processWebhook: {} ticket {} unchanged (status '{}'), skipping check-in",
                        link.getProvider(), link.getExternalTicketId(), rawStatus);
                continue;
            }
            if (progressStatus == null) {
                LOG.debug("processWebhook: status '{}' from {} not mappable to ProgressStatus, "
                                + "skipping check-in",
                        rawStatus, provider);
                continue;
            }

            String note = "Webhook update from " + link.getProvider()
                    + " ticket " + link.getExternalTicketId()
                    + ": status changed to " + rawStatus;

            ProgressEntryEntity entry = new ProgressEntryEntity(
                    UUID.randomUUID(),
                    link.getOrgId(),
                    link.getCommitId(),
                    progressStatus,
                    note
            );
            progressEntryRepository.save(entry);
            created++;

            LOG.debug("processWebhook: created check-in {} for commit {} via {} webhook",
                    progressStatus, link.getCommitId(), provider);
        }

        LOG.info("processWebhook: {} → {} ({}) created {} check-in(s)",
                provider, ticketId, progressStatus, created);
        return created;
    }

    /**
     * Returns unresolved external tickets linked to RCDO-strategic commits owned by
     * the given user within the look-back window.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load plans owned by the user in the {@code [asOf - weeksBack, asOf]} window.</li>
     *   <li>Load commits for those plans that have an RCDO outcome set.</li>
     *   <li>Batch-load ticket links for those commit IDs.</li>
     *   <li>Filter to links whose cached status is "unresolved" (not done/closed).</li>
     *   <li>Map each link to a {@link UserTicketContext} with RCDO metadata from the commit.</li>
     * </ol>
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserTicketContext> getUnresolvedTicketsForUser(
            UUID orgId, UUID userId, LocalDate asOf, int weeksBack) {
        LocalDate windowStart = asOf.minusWeeks(weeksBack);

        // 1. Load plans for the user in the window
        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, asOf);
        if (plans.isEmpty()) {
            return List.of();
        }

        // 2. Load commits with RCDO outcomes for those plans
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> strategicCommits =
                commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds)
                        .stream()
                        .filter(c -> c.getOutcomeId() != null)
                        .toList();
        if (strategicCommits.isEmpty()) {
            return List.of();
        }

        // 3. Batch-load ticket links for those commits
        Map<UUID, WeeklyCommitEntity> commitById = strategicCommits.stream()
                .collect(Collectors.toMap(WeeklyCommitEntity::getId, c -> c, (a, b) -> a));
        List<UUID> commitIds = strategicCommits.stream().map(WeeklyCommitEntity::getId).toList();
        List<ExternalTicketLinkEntity> links =
                linkRepository.findByOrgIdAndCommitIdIn(orgId, commitIds);

        // 4 & 5. Filter to unresolved and map to UserTicketContext
        return links.stream()
                .filter(link -> isUnresolved(link.getExternalStatus()))
                .map(link -> {
                    WeeklyCommitEntity commit = commitById.get(link.getCommitId());
                    if (commit == null) {
                        return null;
                    }
                    return new UserTicketContext(
                            link.getExternalTicketId(),
                            link.getProvider(),
                            link.getExternalStatus(),
                            link.getExternalTicketUrl(),
                            link.getLastSyncedAt(),
                            link.getCommitId(),
                            commit.getOutcomeId().toString(),
                            commit.getSnapshotOutcomeName(),
                            commit.getSnapshotObjectiveName(),
                            commit.getSnapshotRallyCryName()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Returns {@code true} when a ticket's cached status does not indicate a
     * completed/closed state.  Tickets with a {@code null} or blank status are
     * treated as unresolved since their current state is unknown.
     */
    static boolean isUnresolved(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        String s = status.toLowerCase(Locale.ROOT);
        return !s.contains("done") && !s.contains("closed")
                && !s.contains("resolved") && !s.contains("complete")
                && !s.contains("merged");
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.isBlank();
    }
}
