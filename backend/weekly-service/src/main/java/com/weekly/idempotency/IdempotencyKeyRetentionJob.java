package com.weekly.idempotency;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that deletes idempotency keys older than a configurable
 * retention period. Prevents unbounded growth of the {@code idempotency_keys}
 * table while still protecting recent requests from duplicate processing.
 *
 * <p>Runs once per hour. Retention period defaults to 72 hours and is
 * configurable via {@code weekly.idempotency.retention-hours}.
 */
@Component
@ConditionalOnProperty(
        name = "weekly.idempotency.retention.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class IdempotencyKeyRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyKeyRetentionJob.class);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final int retentionHours;
    private final Clock clock;

    @Autowired
    public IdempotencyKeyRetentionJob(
            IdempotencyKeyRepository idempotencyKeyRepository,
            @Value("${weekly.idempotency.retention-hours:72}") int retentionHours
    ) {
        this(idempotencyKeyRepository, retentionHours, Clock.systemUTC());
    }

    IdempotencyKeyRetentionJob(
            IdempotencyKeyRepository idempotencyKeyRepository,
            int retentionHours,
            Clock clock
    ) {
        if (retentionHours <= 0) {
            throw new IllegalArgumentException("weekly.idempotency.retention-hours must be greater than 0");
        }
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.retentionHours = retentionHours;
        this.clock = clock;
    }

    /**
     * Deletes idempotency keys whose {@code created_at} is older than the
     * configured retention period. Runs every hour.
     */
    @Scheduled(fixedRate = 3600_000, initialDelay = 180_000)
    @Transactional
    public void purgeExpiredKeys() {
        Instant cutoff = Instant.now(clock).minus(retentionHours, ChronoUnit.HOURS);
        int deleted = idempotencyKeyRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            LOG.info("Idempotency key retention: deleted {} keys older than {} hours (cutoff={})",
                    deleted, retentionHours, cutoff);
        } else {
            LOG.debug("Idempotency key retention: no expired keys to delete (cutoff={})", cutoff);
        }
    }

    /** Visible for testing. */
    int getRetentionHours() {
        return retentionHours;
    }
}
