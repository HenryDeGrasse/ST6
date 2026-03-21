package com.weekly.urgency;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that recomputes urgency bands and progress percentages for all
 * tracked RCDO outcomes across every organisation.
 *
 * <p>Runs every 30 minutes by default (configurable via
 * {@code urgency.compute.cron}) so that the urgency indicators shown on the
 * frontend reflect reasonably current data without being recomputed on every
 * API request.
 *
 * <p>Enabled via {@code urgency.compute.enabled=true} (worker profile only).
 * Defaults to {@code false} in the base configuration so the job is never
 * active in local / test environments.
 *
 * <p>Follows the {@link com.weekly.cadence.CadenceReminderJob} and
 * {@link com.weekly.capacity.CapacityComputeJob} patterns:
 * per-org error isolation, a micrometer counter, and structured logging.
 */
@Component
@ConditionalOnProperty(name = "urgency.compute.enabled", havingValue = "true")
public class UrgencyComputeJob {

    private static final Logger LOG = LoggerFactory.getLogger(UrgencyComputeJob.class);

    /** Micrometer counter name incremented once per successfully processed org. */
    static final String COUNTER_URGENCY_RECOMPUTE_TOTAL = "urgency_recompute_total";

    private final UrgencyComputeService urgencyComputeService;
    private final OutcomeMetadataRepository outcomeMetadataRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Production constructor — Spring auto-wires all dependencies.
     */
    @Autowired
    public UrgencyComputeJob(
            UrgencyComputeService urgencyComputeService,
            OutcomeMetadataRepository outcomeMetadataRepository,
            MeterRegistry meterRegistry
    ) {
        this.urgencyComputeService = urgencyComputeService;
        this.outcomeMetadataRepository = outcomeMetadataRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Recomputes urgency bands for every tracked outcome in every organisation.
     *
     * <p>Scheduled to run every 30 minutes by default; the cron expression is
     * configurable via {@code urgency.compute.cron}.
     *
     * <p>Per-org failures are caught, logged, and skipped so that one bad
     * organisation does not abort the entire batch.  A micrometer counter
     * ({@value #COUNTER_URGENCY_RECOMPUTE_TOTAL}) is incremented for each
     * organisation processed successfully.
     */
    @Scheduled(cron = "${urgency.compute.cron:0 */30 * * * *}")
    public void recomputeAll() {
        List<UUID> orgIds = outcomeMetadataRepository.findDistinctOrgIds();

        if (orgIds.isEmpty()) {
            LOG.debug("UrgencyComputeJob: no orgs with outcome metadata found, skipping");
            return;
        }

        LOG.info("UrgencyComputeJob: starting urgency recomputation for {} org(s)", orgIds.size());

        int successCount = 0;

        for (UUID orgId : orgIds) {
            try {
                urgencyComputeService.computeUrgencyForOrg(orgId);
                meterRegistry.counter(COUNTER_URGENCY_RECOMPUTE_TOTAL).increment();
                successCount++;
                LOG.debug("UrgencyComputeJob: recomputed urgency for org {}", orgId);
            } catch (Exception e) {
                LOG.warn("UrgencyComputeJob: error recomputing urgency for org {}: {}",
                        orgId, e.getMessage(), e);
            }
        }

        LOG.info("UrgencyComputeJob: recomputation complete — {}/{} org(s) succeeded",
                successCount, orgIds.size());
    }
}
