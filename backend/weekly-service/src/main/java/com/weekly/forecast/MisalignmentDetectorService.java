package com.weekly.forecast;

import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.OrgTeamGroup;
import com.weekly.config.OrgPolicyService;
import com.weekly.notification.NotificationEntity;
import com.weekly.notification.NotificationRepository;
import com.weekly.notification.NotificationService;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects manager-facing planning misalignment after lock day and persists in-app briefings.
 */
@Service
public class MisalignmentDetectorService {

    static final String NOTIFICATION_TYPE_PLAN_MISALIGNMENT_BRIEFING = "PLAN_MISALIGNMENT_BRIEFING";
    static final BigDecimal DEFAULT_CAPACITY_HOURS = new BigDecimal("24.0");
    static final BigDecimal DEFAULT_COMMIT_HOURS = new BigDecimal("4.0");

    private static final Logger LOG = LoggerFactory.getLogger(MisalignmentDetectorService.class);
    private static final Set<PlanState> ALLOCATION_LOCKED_STATES = Set.of(
            PlanState.LOCKED, PlanState.RECONCILING, PlanState.RECONCILED, PlanState.CARRY_FORWARD);

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyCommitRepository weeklyCommitRepository;
    private final CapacityProfileProvider capacityProfileProvider;
    private final UrgencyDataProvider urgencyDataProvider;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final OrgGraphClient orgGraphClient;
    private final OrgPolicyService orgPolicyService;
    private final Clock clock;

    @Autowired
    public MisalignmentDetectorService(
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            CapacityProfileProvider capacityProfileProvider,
            UrgencyDataProvider urgencyDataProvider,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            OrgGraphClient orgGraphClient,
            OrgPolicyService orgPolicyService) {
        this(
                weeklyPlanRepository,
                weeklyCommitRepository,
                capacityProfileProvider,
                urgencyDataProvider,
                notificationService,
                notificationRepository,
                orgGraphClient,
                orgPolicyService,
                Clock.systemUTC());
    }

    MisalignmentDetectorService(
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            CapacityProfileProvider capacityProfileProvider,
            UrgencyDataProvider urgencyDataProvider,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            OrgGraphClient orgGraphClient,
            OrgPolicyService orgPolicyService,
            Clock clock) {
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.weeklyCommitRepository = weeklyCommitRepository;
        this.capacityProfileProvider = capacityProfileProvider;
        this.urgencyDataProvider = urgencyDataProvider;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.orgGraphClient = orgGraphClient;
        this.orgPolicyService = orgPolicyService;
        this.clock = clock;
    }

    /** Runs the daily detector for the current week once lock day/time has passed. */
    @Transactional
    public int detectCurrentWeekMisalignment() {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant dayCutoff = today.atStartOfDay(ZoneOffset.UTC).toInstant();

        int notifications = 0;
        for (UUID orgId : weeklyPlanRepository.findDistinctOrgIds()) {
            try {
                if (!isAfterLockThreshold(orgId, today, now)) {
                    continue;
                }
                notifications += detectMisalignmentForOrg(orgId, weekStart, dayCutoff);
            } catch (Exception e) {
                LOG.warn("MisalignmentDetectorService: failed org {}: {}", orgId, e.getMessage(), e);
            }
        }
        return notifications;
    }

    @Transactional
    int detectMisalignmentForOrg(UUID orgId, LocalDate weekStart, Instant dayCutoff) {
        Map<UUID, UrgencyInfo> urgencyByOutcomeId = Optional.ofNullable(urgencyDataProvider.getOrgUrgencySummary(orgId))
                .orElse(List.of())
                .stream()
                .collect(Collectors.toMap(UrgencyInfo::outcomeId, info -> info, (left, right) -> left));

        Map<UUID, OrgTeamGroup> teamGroups = orgGraphClient.getOrgTeamGroups(orgId);
        if (teamGroups.isEmpty()) {
            return 0;
        }

        int notifications = 0;
        for (OrgTeamGroup teamGroup : teamGroups.values()) {
            if (teamGroup.members().isEmpty()) {
                continue;
            }
            List<UUID> memberIds = teamGroup.members().stream().map(member -> member.userId()).toList();
            List<WeeklyPlanEntity> plans = weeklyPlanRepository.findByOrgIdAndWeekStartDateAndOwnerUserIdIn(
                    orgId, weekStart, memberIds).stream()
                    .filter(plan -> ALLOCATION_LOCKED_STATES.contains(plan.getState()))
                    .toList();
            if (plans.isEmpty()) {
                continue;
            }

            TeamBriefing briefing = buildBriefing(orgId, teamGroup, plans, urgencyByOutcomeId, weekStart);
            if (briefing == null || briefing.concernCount() == 0) {
                continue;
            }
            if (alreadyNotified(orgId, teamGroup.managerId(), weekStart, dayCutoff)) {
                continue;
            }

            notificationService.notify(orgId, teamGroup.managerId(), NOTIFICATION_TYPE_PLAN_MISALIGNMENT_BRIEFING, Map.ofEntries(
                    Map.entry("managerId", teamGroup.managerId().toString()),
                    Map.entry("teamName", teamGroup.managerDisplayName()),
                    Map.entry("weekStartDate", weekStart.toString()),
                    Map.entry("route", "weekly/team"),
                    Map.entry("message", briefing.message()),
                    Map.entry("overloadedMembers", briefing.overloadedMembers()),
                    Map.entry("urgentOutcomesNeedingAttention", briefing.urgentOutcomesNeedingAttention()),
                    Map.entry("highUrgencyHours", briefing.highUrgencyHours().toPlainString()),
                    Map.entry("nonUrgentHours", briefing.nonUrgentHours().toPlainString()),
                    Map.entry("flaggedPlanIds", briefing.flaggedPlanIds()),
                    Map.entry("concernCount", briefing.concernCount())
            ));
            notifications++;
        }
        return notifications;
    }

    private TeamBriefing buildBriefing(
            UUID orgId,
            OrgTeamGroup teamGroup,
            List<WeeklyPlanEntity> plans,
            Map<UUID, UrgencyInfo> urgencyByOutcomeId,
            LocalDate weekStart) {
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        Map<UUID, List<WeeklyCommitEntity>> commitsByPlanId = weeklyCommitRepository
                .findByOrgIdAndWeeklyPlanIdIn(orgId, planIds)
                .stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        List<String> overloadedMembers = new ArrayList<>();
        List<String> flaggedPlanIds = new ArrayList<>();
        BigDecimal highUrgencyHours = BigDecimal.ZERO;
        BigDecimal nonUrgentHours = BigDecimal.ZERO;

        Map<UUID, String> memberNames = teamGroup.members().stream()
                .collect(Collectors.toMap(member -> member.userId(),
                        member -> member.displayName() != null ? member.displayName() : member.userId().toString()));

        for (WeeklyPlanEntity plan : plans) {
            List<WeeklyCommitEntity> commits = commitsByPlanId.getOrDefault(plan.getId(), List.of());
            if (commits.isEmpty()) {
                continue;
            }

            BigDecimal plannedHours = BigDecimal.ZERO;
            BigDecimal urgentHoursForPlan = BigDecimal.ZERO;
            for (WeeklyCommitEntity commit : commits) {
                BigDecimal hours = Optional.ofNullable(commit.getEstimatedHours())
                        .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                        .orElse(DEFAULT_COMMIT_HOURS);
                plannedHours = plannedHours.add(hours);
                if (isHighUrgency(commit.getOutcomeId(), urgencyByOutcomeId)) {
                    urgentHoursForPlan = urgentHoursForPlan.add(hours);
                }
            }

            highUrgencyHours = highUrgencyHours.add(urgentHoursForPlan);
            nonUrgentHours = nonUrgentHours.add(plannedHours.subtract(urgentHoursForPlan));

            BigDecimal realisticCapacity = capacityProfileProvider.getLatestProfile(orgId, plan.getOwnerUserId())
                    .map(CapacityProfileProvider.CapacityProfileSnapshot::realisticWeeklyCap)
                    .filter(cap -> cap != null && cap.compareTo(BigDecimal.ZERO) > 0)
                    .orElse(DEFAULT_CAPACITY_HOURS);
            if (plannedHours.compareTo(realisticCapacity.multiply(new BigDecimal("1.15"))) > 0) {
                overloadedMembers.add(memberNames.getOrDefault(plan.getOwnerUserId(), plan.getOwnerUserId().toString()));
                flaggedPlanIds.add(plan.getId().toString());
            }
        }

        List<String> urgentOutcomesNeedingAttention = urgencyByOutcomeId.values().stream()
                .filter(this::isHighUrgency)
                .filter(info -> teamCoverageHours(info.outcomeId(), commitsByPlanId) < 1.0d)
                .map(info -> info.outcomeName() != null ? info.outcomeName() : info.outcomeId().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(3)
                .toList();

        boolean lowUrgencyBias = highUrgencyHours.compareTo(nonUrgentHours) < 0 && !urgentOutcomesNeedingAttention.isEmpty();
        int concernCount = overloadedMembers.size() + urgentOutcomesNeedingAttention.size() + (lowUrgencyBias ? 1 : 0);
        if (concernCount == 0) {
            return null;
        }

        StringBuilder message = new StringBuilder();
        message.append("Misalignment briefing for week of ").append(weekStart).append(": ");
        List<String> parts = new ArrayList<>();
        if (!overloadedMembers.isEmpty()) {
            parts.add(overloadedMembers.size() + " member(s) appear over realistic capacity: "
                    + String.join(", ", overloadedMembers));
        }
        if (!urgentOutcomesNeedingAttention.isEmpty()) {
            parts.add("high-urgency outcomes with thin coverage: "
                    + String.join(", ", urgentOutcomesNeedingAttention));
        }
        if (lowUrgencyBias) {
            parts.add("team hours skew more to lower-urgency work than urgent work");
        }
        message.append(String.join("; ", parts)).append('.');

        return new TeamBriefing(
                message.toString(),
                overloadedMembers.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                urgentOutcomesNeedingAttention,
                highUrgencyHours.setScale(1, RoundingMode.HALF_UP),
                nonUrgentHours.setScale(1, RoundingMode.HALF_UP),
                flaggedPlanIds.stream().sorted().toList(),
                concernCount);
    }

    private boolean alreadyNotified(UUID orgId, UUID managerId, LocalDate weekStart, Instant dayCutoff) {
        return notificationRepository
                .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        orgId, managerId, NOTIFICATION_TYPE_PLAN_MISALIGNMENT_BRIEFING, dayCutoff)
                .stream()
                .map(NotificationEntity::getPayload)
                .anyMatch(payload -> weekStart.toString().equals(String.valueOf(payload.get("weekStartDate"))));
    }

    private boolean isAfterLockThreshold(UUID orgId, LocalDate today, LocalTime now) {
        OrgPolicyService.OrgPolicy policy = orgPolicyService.getPolicy(orgId);
        DayOfWeek lockDay = DayOfWeek.valueOf(policy.lockDay().toUpperCase());
        LocalTime lockTime = LocalTime.parse(policy.lockTime());
        if (today.getDayOfWeek().getValue() > lockDay.getValue()) {
            return true;
        }
        return today.getDayOfWeek() == lockDay && !now.isBefore(lockTime);
    }

    private boolean isHighUrgency(UrgencyInfo info) {
        return info != null && isHighUrgencyBand(info.urgencyBand());
    }

    private boolean isHighUrgency(UUID outcomeId, Map<UUID, UrgencyInfo> urgencyByOutcomeId) {
        return outcomeId != null && isHighUrgency(urgencyByOutcomeId.get(outcomeId));
    }

    private boolean isHighUrgencyBand(String urgencyBand) {
        if (urgencyBand == null) {
            return false;
        }
        return switch (urgencyBand.toUpperCase()) {
            case "CRITICAL", "AT_RISK", "NEEDS_ATTENTION" -> true;
            default -> false;
        };
    }

    private double teamCoverageHours(UUID outcomeId, Map<UUID, List<WeeklyCommitEntity>> commitsByPlanId) {
        return commitsByPlanId.values().stream()
                .flatMap(List::stream)
                .filter(commit -> outcomeId.equals(commit.getOutcomeId()))
                .map(commit -> Optional.ofNullable(commit.getEstimatedHours())
                        .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                        .orElse(DEFAULT_COMMIT_HOURS))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();
    }

    record TeamBriefing(
            String message,
            List<String> overloadedMembers,
            List<String> urgentOutcomesNeedingAttention,
            BigDecimal highUrgencyHours,
            BigDecimal nonUrgentHours,
            List<String> flaggedPlanIds,
            int concernCount) {
    }
}
