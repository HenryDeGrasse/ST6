package com.weekly.digest;

import com.weekly.config.OrgPolicyService;
import com.weekly.notification.NotificationRepository;
import com.weekly.outbox.OutboxService;
import com.weekly.plan.repository.ManagerReviewRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.EventType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that builds and dispatches weekly digest notifications for
 * managers in every active org.
 *
 * <p>The job runs hourly and checks whether the current time matches the
 * org-configured digest schedule ({@code digest_day} / {@code digest_time}
 * columns in {@code org_policies}). This approach mirrors the existing
 * {@link com.weekly.cadence.CadenceReminderJob} pattern.
 *
 * <p>For each qualifying manager the job:
 * <ol>
 *   <li>Checks idempotency — skips if a {@code WEEKLY_DIGEST} notification
 *       was already created for this manager / week in the current 7-day
 *       period.</li>
 *   <li>Calls {@link DigestService} to aggregate the weekly data.</li>
 *   <li>Publishes a {@code WEEKLY_DIGEST} outbox event via
 *       {@link OutboxService} so the {@link com.weekly.notification.NotificationMaterializer}
 *       can render and persist the in-app notification.</li>
 * </ol>
 *
 * <p>Enabled via {@code notification.digest.enabled=true} (worker profile only).
 * The schedule defaults to every hour; the cron expression is configurable via
 * {@code notification.digest.cron}.
 *
 * <p><b>Manager discovery:</b> managers are identified as the distinct
 * {@code reviewer_user_id} values in {@code manager_reviews}. This is an MVP
 * approximation; managers who have never reviewed a plan will not receive digests
 * until they submit their first review.
 */
@Component
@ConditionalOnProperty(name = "notification.digest.enabled", havingValue = "true")
public class DigestJob {

    private static final Logger LOG = LoggerFactory.getLogger(DigestJob.class);

    /** Notification type constant for weekly digest. */
    public static final String NOTIFICATION_TYPE_WEEKLY_DIGEST = "WEEKLY_DIGEST";

    private final OrgPolicyService orgPolicyService;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final NotificationRepository notificationRepository;
    private final DigestService digestService;
    private final OutboxService outboxService;
    private final Clock clock;

    @Autowired
    public DigestJob(
            OrgPolicyService orgPolicyService,
            WeeklyPlanRepository weeklyPlanRepository,
            ManagerReviewRepository managerReviewRepository,
            NotificationRepository notificationRepository,
            DigestService digestService,
            OutboxService outboxService
    ) {
        this(orgPolicyService, weeklyPlanRepository, managerReviewRepository,
                notificationRepository, digestService, outboxService, Clock.systemUTC());
    }

    /** Package-private constructor for unit tests (injectable clock). */
    DigestJob(
            OrgPolicyService orgPolicyService,
            WeeklyPlanRepository weeklyPlanRepository,
            ManagerReviewRepository managerReviewRepository,
            NotificationRepository notificationRepository,
            DigestService digestService,
            OutboxService outboxService,
            Clock clock
    ) {
        this.orgPolicyService = orgPolicyService;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.managerReviewRepository = managerReviewRepository;
        this.notificationRepository = notificationRepository;
        this.digestService = digestService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    /**
     * Runs every hour (configurable via {@code notification.digest.cron}).
     * Scans all orgs, checks if the current time matches the org digest schedule,
     * and dispatches digest notifications to qualifying managers.
     */
    @Scheduled(cron = "${notification.digest.cron:0 0 * * * *}")
    @Transactional
    public void sendWeeklyDigests() {
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);

        List<UUID> orgIds = weeklyPlanRepository.findDistinctOrgIds();
        if (orgIds.isEmpty()) {
            LOG.debug("DigestJob: no orgs with plans found, skipping");
            return;
        }

        LOG.debug("DigestJob: scanning {} org(s) for digest schedule", orgIds.size());

        for (UUID orgId : orgIds) {
            try {
                processOrg(orgId, today, nowTime);
            } catch (Exception e) {
                LOG.warn("DigestJob: error processing org {}: {}", orgId, e.getMessage(), e);
            }
        }
    }

    // ── Per-org processing ────────────────────────────────────

    private void processOrg(UUID orgId, LocalDate today, LocalTime nowTime) {
        OrgPolicyService.OrgPolicy policy = orgPolicyService.getPolicy(orgId);

        DayOfWeek digestDay = parseDayOfWeek(policy.digestDay());
        LocalTime digestTime = parseTime(policy.digestTime());

        if (today.getDayOfWeek() != digestDay || nowTime.isBefore(digestTime)) {
            return;
        }

        // Determine the week to summarise:
        //   Monday digest → previous week's Monday (last week's outcome)
        //   Friday digest → current week's Monday (this week's status)
        LocalDate weekStart;
        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
            weekStart = today.minusWeeks(1);
        } else {
            weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        // Idempotency cutoff: 7 days ago (digests are weekly)
        Instant cutoff = today.minusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<UUID> managerIds = managerReviewRepository.findDistinctReviewerUserIdsByOrgId(orgId);
        if (managerIds.isEmpty()) {
            LOG.debug("DigestJob: no managers found for org {}", orgId);
            return;
        }

        for (UUID managerId : managerIds) {
            try {
                processManager(orgId, managerId, weekStart, cutoff);
            } catch (Exception e) {
                LOG.warn("DigestJob: error processing manager {} in org {}: {}",
                        managerId, orgId, e.getMessage(), e);
            }
        }
    }

    private void processManager(UUID orgId, UUID managerId, LocalDate weekStart, Instant cutoff) {
        // Idempotency: skip if a WEEKLY_DIGEST already exists for this manager/week
        boolean alreadySent = notificationRepository
                .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        orgId, managerId, NOTIFICATION_TYPE_WEEKLY_DIGEST, cutoff)
                .stream()
                .anyMatch(n -> weekStart.toString().equals(n.getPayload().get("weekStart")));

        if (alreadySent) {
            LOG.debug("DigestJob: skipping duplicate WEEKLY_DIGEST for manager {} week {}",
                    managerId, weekStart);
            return;
        }

        DigestPayload payload = digestService.buildDigestPayload(orgId, managerId, weekStart);

        // Publish outbox event — NotificationMaterializer creates the in-app notification
        Map<String, Object> eventPayload = toEventPayload(managerId, payload);
        outboxService.publish(
                EventType.WEEKLY_DIGEST,
                "WeeklyDigest",
                managerId,
                orgId,
                eventPayload
        );

        LOG.info("DigestJob: published WEEKLY_DIGEST for manager {} in org {} week {}",
                managerId, orgId, weekStart);
    }

    // ── Payload conversion ────────────────────────────────────

    static Map<String, Object> toEventPayload(UUID managerId, DigestPayload payload) {
        Map<String, Object> map = new HashMap<>();
        map.put("managerId", managerId.toString());
        map.put("weekStart", payload.weekStart());
        map.put("totalMemberCount", payload.totalMemberCount());
        map.put("reconciledCount", payload.reconciledCount());
        map.put("lockedCount", payload.lockedCount());
        map.put("draftCount", payload.draftCount());
        map.put("staleCount", payload.staleCount());
        map.put("reviewQueueSize", payload.reviewQueueSize());
        map.put("carryForwardStreakUserIds", payload.carryForwardStreakUserIds());
        map.put("stalePlanUserIds", payload.stalePlanUserIds());
        map.put("lateLockUserIds", payload.lateLockUserIds());
        map.put("rcdoAlignmentRate", payload.rcdoAlignmentRate());
        map.put("previousRcdoAlignmentRate", payload.previousRcdoAlignmentRate());
        map.put("doneEarlyCount", payload.doneEarlyCount());
        return map;
    }

    // ── Parsing helpers ───────────────────────────────────────

    /** Parses a day-of-week string (e.g. "FRIDAY") to {@link DayOfWeek}. Case-insensitive. */
    static DayOfWeek parseDayOfWeek(String day) {
        return DayOfWeek.valueOf(day.toUpperCase());
    }

    /** Parses a time string in {@code HH:mm} format to {@link LocalTime}. */
    static LocalTime parseTime(String time) {
        return LocalTime.parse(time);
    }
}
