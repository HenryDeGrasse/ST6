package com.weekly.notification;

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
 * Scheduled job that deletes notifications older than a configurable retention
 * period. Prevents unbounded growth of the {@code notifications} table.
 *
 * <p>Runs once per hour. Retention period defaults to 90 days and is
 * configurable via {@code weekly.notification.retention-days}.
 */
@Component
@ConditionalOnProperty(
        name = "weekly.notification.retention.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class NotificationRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationRetentionJob.class);

    private final NotificationRepository notificationRepository;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public NotificationRetentionJob(
            NotificationRepository notificationRepository,
            @Value("${weekly.notification.retention-days:90}") int retentionDays
    ) {
        this(notificationRepository, retentionDays, Clock.systemUTC());
    }

    NotificationRetentionJob(
            NotificationRepository notificationRepository,
            int retentionDays,
            Clock clock
    ) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("weekly.notification.retention-days must be greater than 0");
        }
        this.notificationRepository = notificationRepository;
        this.retentionDays = retentionDays;
        this.clock = clock;
    }

    /**
     * Deletes notifications whose {@code created_at} is older than the
     * configured retention period. Runs every hour.
     */
    @Scheduled(fixedRate = 3600_000, initialDelay = 120_000)
    @Transactional
    public void purgeExpiredNotifications() {
        Instant cutoff = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
        int deleted = notificationRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            LOG.info("Notification retention: deleted {} notifications older than {} days (cutoff={})",
                    deleted, retentionDays, cutoff);
        } else {
            LOG.debug("Notification retention: no expired notifications to delete (cutoff={})", cutoff);
        }
    }

    /** Visible for testing. */
    int getRetentionDays() {
        return retentionDays;
    }
}
