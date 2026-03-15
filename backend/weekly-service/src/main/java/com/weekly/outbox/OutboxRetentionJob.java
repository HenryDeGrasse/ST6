package com.weekly.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job that deletes outbox events older than a configurable retention
 * period. Prevents unbounded growth of the {@code outbox_events} table without
 * incorrectly marking events as published.
 *
 * <p>Runs once per hour. Retention period defaults to 30 days and is
 * configurable via {@code weekly.outbox.retention-days}.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "weekly.outbox.retention.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class OutboxRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final OutboxEventRepository outboxEventRepository;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public OutboxRetentionJob(
            OutboxEventRepository outboxEventRepository,
            @Value("${weekly.outbox.retention-days:30}") int retentionDays
    ) {
        this(outboxEventRepository, retentionDays, Clock.systemUTC());
    }

    OutboxRetentionJob(
            OutboxEventRepository outboxEventRepository,
            int retentionDays,
            Clock clock
    ) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("weekly.outbox.retention-days must be greater than 0");
        }
        this.outboxEventRepository = outboxEventRepository;
        this.retentionDays = retentionDays;
        this.clock = clock;
    }

    /**
     * Deletes outbox events whose {@code occurred_at} is older than the
     * configured retention period. Runs every hour.
     */
    @Scheduled(fixedRate = 3600_000, initialDelay = 60_000)
    @Transactional
    public void purgeExpiredEvents() {
        Instant cutoff = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxEventRepository.deleteByOccurredAtBefore(cutoff);
        if (deleted > 0) {
            LOG.info("Outbox retention: deleted {} events older than {} days (cutoff={})",
                    deleted, retentionDays, cutoff);
        } else {
            LOG.debug("Outbox retention: no expired events to delete (cutoff={})", cutoff);
        }
    }

    /** Visible for testing. */
    int getRetentionDays() {
        return retentionDays;
    }
}
