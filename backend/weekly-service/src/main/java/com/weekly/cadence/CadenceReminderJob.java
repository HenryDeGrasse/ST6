package com.weekly.cadence;

import com.weekly.config.OrgPolicyService;
import com.weekly.notification.NotificationEntity;
import com.weekly.notification.NotificationRepository;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyPlanRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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
 * Scheduled job that emits cadence-based reminder notifications for plans
 * that have not progressed to the expected state by the org's configured
 * deadline (PRD §4 notification table).
 *
 * <p>The following four triggers are handled:
 * <ol>
 *   <li><b>PLAN_STILL_DRAFT</b> — plan is still DRAFT on lock day after lockTime.</li>
 *   <li><b>TIME_TO_RECONCILE</b> — plan is still LOCKED on reconcile day after reconcileTime.</li>
 *   <li><b>RECONCILIATION_OVERDUE</b> — plan was never reconciled and the week has passed.</li>
 *   <li><b>PLAN_STALE_MANAGER</b> — plan is still DRAFT after the week has passed (manager dashboard badge).</li>
 * </ol>
 *
 * <p>Idempotency: a notification is skipped if one of the same type for the same
 * plan/week scope was already sent to the same user within the current ISO week
 * (Monday 00:00 UTC).
 *
 * <p>Enabled via {@code notification.cadence.enabled=true} (worker profile only).
 * Runs every hour by default; the cron expression is configurable via
 * {@code notification.cadence.cron}.
 */
@Component
@ConditionalOnProperty(name = "notification.cadence.enabled", havingValue = "true")
public class CadenceReminderJob {

    private static final Logger LOG = LoggerFactory.getLogger(CadenceReminderJob.class);

    /** Notification type constants matching PRD §4. */
    public static final String TYPE_PLAN_STILL_DRAFT = "PLAN_STILL_DRAFT";
    public static final String TYPE_TIME_TO_RECONCILE = "TIME_TO_RECONCILE";
    public static final String TYPE_RECONCILIATION_OVERDUE = "RECONCILIATION_OVERDUE";
    public static final String TYPE_PLAN_STALE_MANAGER = "PLAN_STALE_MANAGER";

    private final OrgPolicyService orgPolicyService;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final NotificationRepository notificationRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Autowired
    public CadenceReminderJob(
            OrgPolicyService orgPolicyService,
            WeeklyPlanRepository weeklyPlanRepository,
            NotificationRepository notificationRepository,
            MeterRegistry meterRegistry
    ) {
        this(orgPolicyService, weeklyPlanRepository,
                notificationRepository, meterRegistry, Clock.systemUTC());
    }

    /** Package-private constructor for unit tests (injectable clock). */
    CadenceReminderJob(
            OrgPolicyService orgPolicyService,
            WeeklyPlanRepository weeklyPlanRepository,
            NotificationRepository notificationRepository,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.orgPolicyService = orgPolicyService;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.notificationRepository = notificationRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * Runs every hour (configurable via {@code notification.cadence.cron}).
     * Scans all orgs and emits reminder notifications for plans that are past
     * their expected state transition deadline.
     */
    @Scheduled(cron = "${notification.cadence.cron:0 0 * * * *}")
    @Transactional
    public void sendCadenceReminders() {
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);

        // Current week Monday (used to identify "this week's" plans)
        LocalDate currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Idempotency cutoff: start of this week (Monday midnight UTC)
        Instant weekStart = currentWeekMonday.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<UUID> orgIds = weeklyPlanRepository.findDistinctOrgIds();

        if (orgIds.isEmpty()) {
            LOG.debug("CadenceReminderJob: no orgs with plans found, skipping");
            return;
        }

        LOG.debug("CadenceReminderJob: scanning {} org(s) for cadence reminders", orgIds.size());

        for (UUID orgId : orgIds) {
            try {
                processOrg(orgId, today, nowTime, currentWeekMonday, weekStart);
            } catch (Exception e) {
                LOG.warn("CadenceReminderJob: error processing org {}: {}", orgId, e.getMessage(), e);
            }
        }
    }

    // ── Per-org processing ────────────────────────────────────

    private void processOrg(
            UUID orgId,
            LocalDate today,
            LocalTime nowTime,
            LocalDate currentWeekMonday,
            Instant idempotencyCutoff
    ) {
        OrgPolicyService.OrgPolicy policy = orgPolicyService.getPolicy(orgId);

        // ── Trigger 1: PLAN_STILL_DRAFT ───────────────────────────────────
        // If today is the lock day and current time >= lockTime, remind DRAFT plan owners.
        DayOfWeek lockDay = parseDayOfWeek(policy.lockDay());
        LocalTime lockTime = parseTime(policy.lockTime());
        if (today.getDayOfWeek() == lockDay && !nowTime.isBefore(lockTime)) {
            List<WeeklyPlanEntity> draftPlans = weeklyPlanRepository
                    .findByOrgIdAndStateAndWeekStartDate(orgId, PlanState.DRAFT, currentWeekMonday);
            for (WeeklyPlanEntity plan : draftPlans) {
                maybeCreateNotification(
                        orgId,
                        plan.getOwnerUserId(),
                        TYPE_PLAN_STILL_DRAFT,
                        Map.of(
                                "planId", plan.getId().toString(),
                                "weekStartDate", plan.getWeekStartDate().toString(),
                                "message", "Your weekly plan is still in draft. "
                                        + "Lock it to set your baseline."
                        ),
                        idempotencyCutoff
                );
            }
        }

        // ── Trigger 2: TIME_TO_RECONCILE ──────────────────────────────────
        // If today is the reconcile day and current time >= reconcileTime,
        // remind LOCKED plan owners.
        DayOfWeek reconcileDay = parseDayOfWeek(policy.reconcileDay());
        LocalTime reconcileTime = parseTime(policy.reconcileTime());
        if (today.getDayOfWeek() == reconcileDay && !nowTime.isBefore(reconcileTime)) {
            List<WeeklyPlanEntity> lockedPlans = weeklyPlanRepository
                    .findByOrgIdAndStateAndWeekStartDate(orgId, PlanState.LOCKED, currentWeekMonday);
            for (WeeklyPlanEntity plan : lockedPlans) {
                maybeCreateNotification(
                        orgId,
                        plan.getOwnerUserId(),
                        TYPE_TIME_TO_RECONCILE,
                        Map.of(
                                "planId", plan.getId().toString(),
                                "weekStartDate", plan.getWeekStartDate().toString(),
                                "message", "Time to reconcile your week."
                        ),
                        idempotencyCutoff
                );
            }
        }

        // ── Triggers 3 & 4 fire on Mondays (start of new week) ───────────
        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {

            // ── Trigger 3: RECONCILIATION_OVERDUE ────────────────────────
            // Find plans from previous weeks still in DRAFT, LOCKED, or RECONCILING.
            List<PlanState> incompleteStates = List.of(
                    PlanState.DRAFT, PlanState.LOCKED, PlanState.RECONCILING);
            List<WeeklyPlanEntity> overduePlans = weeklyPlanRepository
                    .findByOrgIdAndStateInAndWeekStartDateBefore(orgId, incompleteStates, currentWeekMonday);
            for (WeeklyPlanEntity plan : overduePlans) {
                maybeCreateNotification(
                        orgId,
                        plan.getOwnerUserId(),
                        TYPE_RECONCILIATION_OVERDUE,
                        Map.of(
                                "planId", plan.getId().toString(),
                                "weekStartDate", plan.getWeekStartDate().toString(),
                                "message", "Last week's reconciliation is overdue."
                        ),
                        idempotencyCutoff
                );
            }

            // ── Trigger 4: PLAN_STALE_MANAGER ─────────────────────────────
            // Plans still DRAFT from a previous week → STALE badge on manager dashboard.
            // A passive notification entry is created for the ownerUserId so the
            // manager dashboard query can surface the badge.
            List<WeeklyPlanEntity> staleDraftPlans = weeklyPlanRepository
                    .findByOrgIdAndStateInAndWeekStartDateBefore(
                            orgId, List.of(PlanState.DRAFT), currentWeekMonday);
            for (WeeklyPlanEntity plan : staleDraftPlans) {
                maybeCreateNotification(
                        orgId,
                        plan.getOwnerUserId(),
                        TYPE_PLAN_STALE_MANAGER,
                        Map.of(
                                "planId", plan.getId().toString(),
                                "weekStartDate", plan.getWeekStartDate().toString(),
                                "message", "Plan is stale — never locked after the week ended."
                        ),
                        idempotencyCutoff
                );
            }
        }
    }

    // ── Idempotent notification creation ─────────────────────

    /**
     * Creates a notification only if one of the same type for the same user and
     * reminder scope (plan/week) has not already been created since {@code idempotencyCutoff}.
     */
    private void maybeCreateNotification(
            UUID orgId,
            UUID userId,
            String type,
            Map<String, Object> payload,
            Instant idempotencyCutoff
    ) {
        List<NotificationEntity> existingNotifications = notificationRepository
                .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        orgId, userId, type, idempotencyCutoff);
        boolean alreadySent = existingNotifications.stream()
                .anyMatch(existing -> hasSameReminderScope(existing.getPayload(), payload));
        if (alreadySent) {
            LOG.debug("CadenceReminderJob: skipping duplicate {} notification for user {} in org {}",
                    type, userId, orgId);
            return;
        }

        notificationRepository.save(new NotificationEntity(orgId, userId, type, payload));
        meterRegistry.counter("cadence_reminders_sent_total", "reminder_type", type).increment();
        LOG.info("CadenceReminderJob: created {} notification for user {} in org {}",
                type, userId, orgId);
    }

    private boolean hasSameReminderScope(
            Map<String, Object> existingPayload,
            Map<String, Object> candidatePayload
    ) {
        return payloadValue(existingPayload, "planId").equals(payloadValue(candidatePayload, "planId"))
                && payloadValue(existingPayload, "weekStartDate")
                .equals(payloadValue(candidatePayload, "weekStartDate"));
    }

    private String payloadValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : "";
    }

    // ── Parsing helpers ───────────────────────────────────────

    /**
     * Parses a day-of-week string (e.g. "MONDAY") to {@link DayOfWeek}.
     * Case-insensitive.
     */
    static DayOfWeek parseDayOfWeek(String day) {
        return DayOfWeek.valueOf(day.toUpperCase());
    }

    /**
     * Parses a time string in {@code HH:mm} format to {@link LocalTime}.
     */
    static LocalTime parseTime(String time) {
        return LocalTime.parse(time);
    }
}
