package com.weekly.integration;

import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.service.ReconciliationSubmittedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Default implementation of {@link OutboundCommentService}.
 *
 * <p>Listens for {@link ReconciliationSubmittedEvent} (published by
 * {@link com.weekly.plan.service.PlanService}) after a plan is reconciled, and:
 * <ol>
 *   <li>Fetches all commits for the plan.</li>
 *   <li>For each commit, finds its external ticket links.</li>
 *   <li>For each link, locates the appropriate {@link ExternalTicketAdapter}
 *       and calls {@link ExternalTicketAdapter#postComment}.</li>
 * </ol>
 *
 * <p>Spring's {@link TransactionalEventListener} ensures this runs after the
 * reconciliation transaction commits, so ticket comment failures never roll
 * back the plan state change.
 *
 * <p>All adapter calls are best-effort: exceptions are caught, logged, and
 * swallowed so that outbound comment failures never affect the main domain flow.
 */
@Service
public class DefaultOutboundCommentService implements OutboundCommentService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultOutboundCommentService.class);

    private final WeeklyCommitRepository commitRepository;
    private final ExternalTicketLinkRepository linkRepository;
    private final Map<String, ExternalTicketAdapter> adapters;

    public DefaultOutboundCommentService(
            WeeklyCommitRepository commitRepository,
            ExternalTicketLinkRepository linkRepository,
            @Qualifier("externalTicketAdapters") List<ExternalTicketAdapter> adapterList
    ) {
        this.commitRepository = commitRepository;
        this.linkRepository = linkRepository;
        this.adapters = new java.util.HashMap<>();
        for (ExternalTicketAdapter adapter : adapterList) {
            adapters.put(adapter.providerName().toUpperCase(), adapter);
        }
    }

    /**
     * Triggered after the reconciliation transaction commits.
     *
     * <p>Delegates to {@link #postReconciliationComment} with the event data.
     */
    @TransactionalEventListener
    public void onReconciliationSubmitted(ReconciliationSubmittedEvent event) {
        postReconciliationComment(event.orgId(), event.planId(), event.summary());
    }

    /**
     * Posts a reconciliation summary comment on every external ticket linked to
     * commits in the given plan.
     *
     * <p>If no adapters are configured or no tickets are linked, this is a no-op.
     *
     * @param orgId   the org owning the plan
     * @param planId  the plan whose commits should be commented on
     * @param summary the comment body summarising the reconciliation outcome
     */
    @Override
    public void postReconciliationComment(UUID orgId, UUID planId, String summary) {
        if (adapters.isEmpty()) {
            LOG.debug("OutboundCommentService: no adapters configured, skipping comment for plan {}",
                    planId);
            return;
        }

        var commits = commitRepository.findByOrgIdAndWeeklyPlanId(orgId, planId);
        if (commits.isEmpty()) {
            LOG.debug("OutboundCommentService: no commits found for plan {}", planId);
            return;
        }

        java.util.Set<String> commentedTickets = new java.util.HashSet<>();
        for (var commit : commits) {
            List<ExternalTicketLinkEntity> links =
                    linkRepository.findByOrgIdAndCommitId(orgId, commit.getId());
            for (ExternalTicketLinkEntity link : links) {
                String dedupeKey = link.getProvider().toUpperCase() + ":" + link.getExternalTicketId();
                if (commentedTickets.add(dedupeKey)) {
                    postCommentBestEffort(link, summary);
                }
            }
        }
    }

    // ── Internal helpers ─────────────────────────────────────

    private void postCommentBestEffort(ExternalTicketLinkEntity link, String comment) {
        ExternalTicketAdapter adapter = adapters.get(link.getProvider().toUpperCase());
        if (adapter == null) {
            LOG.debug("OutboundCommentService: no adapter for provider {}, skipping ticket {}",
                    link.getProvider(), link.getExternalTicketId());
            return;
        }
        try {
            adapter.postComment(link.getExternalTicketId(), comment);
            LOG.debug("OutboundCommentService: posted comment on {} {} for commit {}",
                    link.getProvider(), link.getExternalTicketId(), link.getCommitId());
        } catch (ExternalTicketAdapter.ExternalTicketUnavailableException e) {
            LOG.warn("OutboundCommentService: failed to post comment on {} {}: {}",
                    link.getProvider(), link.getExternalTicketId(), e.getMessage());
        } catch (Exception e) {
            LOG.warn("OutboundCommentService: unexpected error posting comment on {} {}: {}",
                    link.getProvider(), link.getExternalTicketId(), e.getMessage(), e);
        }
    }
}
