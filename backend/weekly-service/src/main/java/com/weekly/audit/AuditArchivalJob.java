package com.weekly.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that identifies audit events eligible for archival to S3 (PRD §14.7).
 *
 * <p>Audit events older than {@code weekly.audit.archival.archive-after-years} years (default: 5)
 * are identified and logged.  The actual export to S3 as Parquet files is post-MVP
 * infrastructure work and will be implemented when the S3 integration is available.
 *
 * <p>Enabled via {@code weekly.audit.archival.enabled=true}.
 * Runs daily at 04:00 UTC.
 *
 * <p><b>TODO (post-MVP):</b> Replace the log-and-count stub with a real S3 Parquet export:
 * <ol>
 *   <li>Iterate archival-eligible events page-by-page using
 *       {@link AuditEventRepository#findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc}.</li>
 *   <li>Serialize each page to Parquet using Apache Parquet / Arrow.</li>
 *   <li>Upload to {@code s3://&lt;bucket&gt;/audit-archive/&lt;orgId&gt;/&lt;year&gt;/}.</li>
 *   <li>Delete the events from the primary database after confirming the S3 upload.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(
        name = "weekly.audit.archival.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AuditArchivalJob {

    private static final Logger LOG = LoggerFactory.getLogger(AuditArchivalJob.class);

    /** Number of events scanned per page to bound heap usage. */
    static final int PAGE_SIZE = 500;

    private final AuditEventRepository auditEventRepository;
    private final int archiveAfterYears;
    private final Clock clock;

    @Autowired
    public AuditArchivalJob(
            AuditEventRepository auditEventRepository,
            @Value("${weekly.audit.archival.archive-after-years:5}") int archiveAfterYears
    ) {
        this(auditEventRepository, archiveAfterYears, Clock.systemUTC());
    }

    AuditArchivalJob(
            AuditEventRepository auditEventRepository,
            int archiveAfterYears,
            Clock clock
    ) {
        if (archiveAfterYears <= 0) {
            throw new IllegalArgumentException(
                    "weekly.audit.archival.archive-after-years must be greater than 0");
        }
        this.auditEventRepository = auditEventRepository;
        this.archiveAfterYears = archiveAfterYears;
        this.clock = clock;
    }

    /**
     * Identifies audit events eligible for archival and logs the count per organisation.
     * Runs daily at 04:00 UTC.
     *
     * <p><b>TODO (post-MVP):</b> Export identified events to S3 as Parquet and
     * delete them from the primary database after confirming the upload.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    public void identifyArchivableEvents() {
        Instant archivalCutoff = Instant.now(clock).minus(archiveAfterYears * 365L, ChronoUnit.DAYS);

        List<UUID> orgIds = auditEventRepository.findDistinctOrgIds();
        if (orgIds.isEmpty()) {
            LOG.debug("Audit archival: no organisations found, nothing to archive");
            return;
        }

        LOG.info("Audit archival: scanning {} organisation(s) for events older than {} year(s) "
                + "(cutoff={})", orgIds.size(), archiveAfterYears, archivalCutoff);

        long totalEligible = 0;
        for (UUID orgId : orgIds) {
            long count = countArchivableEvents(orgId, archivalCutoff);
            if (count > 0) {
                LOG.info("Audit archival: org={} has {} event(s) eligible for S3 archival "
                        + "[TODO: implement S3 export]", orgId, count);
                totalEligible += count;
            }
        }

        if (totalEligible > 0) {
            LOG.warn("Audit archival: {} total event(s) across all orgs are eligible for archival "
                    + "but S3 export is not yet implemented (post-MVP)", totalEligible);
        } else {
            LOG.debug("Audit archival: no events eligible for archival at cutoff={}", archivalCutoff);
        }
    }

    /**
     * Counts the number of archival-eligible events for a single organisation by
     * paginating through an age-filtered database query so recent events are
     * never loaded into memory.
     *
     * @param orgId          the organisation to scan
     * @param archivalCutoff only events created before this instant are eligible
     * @return count of archival-eligible events for this org
     */
    private long countArchivableEvents(UUID orgId, Instant archivalCutoff) {
        long count = 0;
        int pageNumber = 0;
        Page<AuditEventEntity> page;

        do {
            page = auditEventRepository.findByOrgIdAndCreatedAtBeforeOrderByCreatedAtAsc(
                    orgId, archivalCutoff, PageRequest.of(pageNumber, PAGE_SIZE));
            count += page.getNumberOfElements();
            pageNumber++;
        } while (page.hasNext());

        return count;
    }

    // ── Visible for testing ──────────────────────────────────

    int getArchiveAfterYears() {
        return archiveAfterYears;
    }
}
