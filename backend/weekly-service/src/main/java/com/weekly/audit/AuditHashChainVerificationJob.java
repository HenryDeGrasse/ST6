package com.weekly.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that verifies audit hash chain integrity daily (§14.7).
 *
 * <p>For each organisation, iterates all audit events in chronological order
 * (paginated to avoid OOM), recomputes the expected hash from the previous
 * hash and the event payload, and reports any mismatch as a chain break.
 *
 * <p>Chain breaks are logged at ERROR level with the event ID and org ID, and
 * the {@code audit_hash_chain_breaks_total} Micrometer counter is incremented
 * for each break so that alerts can be wired against it.
 *
 * <p>Enabled via {@code weekly.audit.hash-verification.enabled=true}.
 * Runs daily at 03:00 UTC.
 */
@Component
@ConditionalOnProperty(
        name = "weekly.audit.hash-verification.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AuditHashChainVerificationJob {

    private static final Logger LOG =
            LoggerFactory.getLogger(AuditHashChainVerificationJob.class);

    /** Number of events fetched per database page to bound heap usage. */
    static final int PAGE_SIZE = 1000;

    private final AuditEventRepository auditEventRepository;
    private final Counter chainBreaksCounter;
    private final Clock clock;

    @Autowired
    public AuditHashChainVerificationJob(
            AuditEventRepository auditEventRepository,
            MeterRegistry meterRegistry
    ) {
        this(auditEventRepository, meterRegistry, Clock.systemUTC());
    }

    AuditHashChainVerificationJob(
            AuditEventRepository auditEventRepository,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.auditEventRepository = auditEventRepository;
        this.chainBreaksCounter = Counter.builder("audit_hash_chain_breaks_total")
                .description("Number of audit hash chain integrity breaks detected")
                .register(meterRegistry);
        this.clock = clock;
    }

    /**
     * Iterates all organisations and verifies the hash chain of each.
     * Runs daily at 03:00 UTC.
     *
     * <p>Deliberately avoids a single long-lived transaction so pagination does
     * not accumulate every loaded entity in the persistence context.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void verifyHashChains() {
        List<UUID> orgIds = auditEventRepository.findDistinctOrgIds();
        if (orgIds.isEmpty()) {
            LOG.debug("Audit hash chain verification: no organisations found, nothing to verify");
            return;
        }

        LOG.info("Audit hash chain verification: starting for {} organisation(s)", orgIds.size());
        long totalBreaks = 0;
        for (UUID orgId : orgIds) {
            totalBreaks += verifyOrgChain(orgId);
        }

        if (totalBreaks > 0) {
            LOG.error("Audit hash chain verification: detected {} chain break(s) "
                    + "across all organisations", totalBreaks);
        } else {
            LOG.info("Audit hash chain verification: all chains intact");
        }
    }

    /**
     * Verifies the hash chain for a single organisation by iterating its
     * audit events in chronological order, page by page.
     *
     * @param orgId the organisation to verify
     * @return the number of chain breaks found for this organisation
     */
    private long verifyOrgChain(UUID orgId) {
        long breaks = 0;
        String previousHash = "";
        int pageNumber = 0;
        Page<AuditEventEntity> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE);
            page = auditEventRepository.findByOrgIdOrderByCreatedAtAsc(orgId, pageable);

            for (AuditEventEntity event : page.getContent()) {
                String expectedHash = AuditEventEntity.computeHash(
                        previousHash, event.buildPayload());
                if (!expectedHash.equals(event.getHash())) {
                    LOG.error("Audit hash chain break detected: "
                            + "eventId={}, orgId={}, expected={}, stored={}",
                            event.getId(), orgId, expectedHash, event.getHash());
                    chainBreaksCounter.increment();
                    breaks++;
                }
                // Advance chain: use stored hash so we can detect cascading breaks
                previousHash = event.getHash() != null ? event.getHash() : "";
            }
            pageNumber++;
        } while (page.hasNext());

        return breaks;
    }

    /** Visible for testing. */
    Clock getClock() {
        return clock;
    }
}
