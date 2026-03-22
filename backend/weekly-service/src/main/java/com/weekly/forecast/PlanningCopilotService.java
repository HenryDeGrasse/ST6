package com.weekly.forecast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.ai.AiCacheService;
import com.weekly.ai.LlmClient;
import com.weekly.ai.RateLimiter;
import com.weekly.auth.DirectReport;
import com.weekly.auth.OrgGraphClient;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.ForecastAnalyticsProvider;
import com.weekly.shared.PredictionDataProvider;
import com.weekly.shared.SlackInfo;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import com.weekly.shared.UserModelDataProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Generates manager planning-copilot suggestions for a team's upcoming week.
 *
 * <p>The service follows a transparent hybrid approach:
 * <ol>
 *   <li>Build deterministic member allocations from capacity, urgency, coverage,
 *       carry-forward backlog, and user strengths.</li>
 *   <li>Optionally ask the LLM to rewrite titles/rationales only.</li>
 *   <li>On rate limit, cache miss, or LLM failure, return the deterministic result unchanged.</li>
 * </ol>
 */
@Service
public class PlanningCopilotService {

    static final int HISTORY_WEEKS = 6;
    static final BigDecimal DEFAULT_CAPACITY_HOURS = new BigDecimal("24.0");
    static final BigDecimal MIN_BUFFER_RATIO = new BigDecimal("0.15");
    static final BigDecimal MAX_BUFFER_RATIO = new BigDecimal("0.30");
    static final String STATUS_OK = "ok";

    private static final Logger LOG = LoggerFactory.getLogger(PlanningCopilotService.class);
    private static final Set<PlanState> HISTORICAL_PLAN_STATES =
            Set.of(PlanState.RECONCILED, PlanState.CARRY_FORWARD, PlanState.RECONCILING, PlanState.LOCKED);

    private final OrgGraphClient orgGraphClient;
    private final CapacityProfileProvider capacityProfileProvider;
    private final UserModelDataProvider userModelDataProvider;
    private final UrgencyDataProvider urgencyDataProvider;
    private final ForecastAnalyticsProvider forecastAnalyticsProvider;
    private final PredictionDataProvider predictionDataProvider;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyCommitRepository weeklyCommitRepository;
    private final WeeklyCommitActualRepository weeklyCommitActualRepository;
    private final LlmClient llmClient;
    private final AiCacheService cacheService;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PlanningCopilotService(
            OrgGraphClient orgGraphClient,
            CapacityProfileProvider capacityProfileProvider,
            UserModelDataProvider userModelDataProvider,
            UrgencyDataProvider urgencyDataProvider,
            ForecastAnalyticsProvider forecastAnalyticsProvider,
            PredictionDataProvider predictionDataProvider,
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            WeeklyCommitActualRepository weeklyCommitActualRepository,
            LlmClient llmClient,
            AiCacheService cacheService,
            RateLimiter rateLimiter,
            ObjectMapper objectMapper) {
        this(
                orgGraphClient,
                capacityProfileProvider,
                userModelDataProvider,
                urgencyDataProvider,
                forecastAnalyticsProvider,
                predictionDataProvider,
                weeklyPlanRepository,
                weeklyCommitRepository,
                weeklyCommitActualRepository,
                llmClient,
                cacheService,
                rateLimiter,
                objectMapper,
                Clock.systemUTC());
    }

    PlanningCopilotService(
            OrgGraphClient orgGraphClient,
            CapacityProfileProvider capacityProfileProvider,
            UserModelDataProvider userModelDataProvider,
            UrgencyDataProvider urgencyDataProvider,
            ForecastAnalyticsProvider forecastAnalyticsProvider,
            PredictionDataProvider predictionDataProvider,
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            WeeklyCommitActualRepository weeklyCommitActualRepository,
            LlmClient llmClient,
            AiCacheService cacheService,
            RateLimiter rateLimiter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.orgGraphClient = orgGraphClient;
        this.capacityProfileProvider = capacityProfileProvider;
        this.userModelDataProvider = userModelDataProvider;
        this.urgencyDataProvider = urgencyDataProvider;
        this.forecastAnalyticsProvider = forecastAnalyticsProvider;
        this.predictionDataProvider = predictionDataProvider;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.weeklyCommitRepository = weeklyCommitRepository;
        this.weeklyCommitActualRepository = weeklyCommitActualRepository;
        this.llmClient = llmClient;
        this.cacheService = cacheService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Produces planning-copilot suggestions for a manager's direct reports.
     */
    @Transactional(readOnly = true)
    public TeamPlanSuggestionResult suggestTeamPlan(UUID orgId, UUID managerId, LocalDate weekStart) {
        validateWeekStart(weekStart);

        List<DirectReport> directReports = orgGraphClient.getDirectReportsWithNames(orgId, managerId);
        if (directReports.isEmpty()) {
            return new TeamPlanSuggestionResult(
                    STATUS_OK,
                    weekStart,
                    new TeamPlanSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, null, "No direct reports found."),
                    List.of(),
                    List.of(),
                    false);
        }

        SlackInfo slackInfo = urgencyDataProvider.getStrategicSlack(orgId, managerId);
        List<UrgencyInfo> urgencies = Optional.ofNullable(urgencyDataProvider.getOrgUrgencySummary(orgId)).orElse(List.of());

        Map<UUID, MemberPlanningContext> memberContexts = new LinkedHashMap<>();
        for (DirectReport report : directReports) {
            MemberPlanningContext context = buildMemberContext(orgId, report, weekStart);
            memberContexts.put(report.userId(), context);
        }

        Map<UUID, MutableMemberPlan> plansByMember = memberContexts.values().stream()
                .collect(Collectors.toMap(
                        MemberPlanningContext::userId,
                        context -> new MutableMemberPlan(context.userId(), context.displayName(), context.realisticCapacity()),
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<OutcomeDemand> demands = buildOutcomeDemands(orgId, urgencies);
        allocateCarryForwardBacklog(memberContexts, plansByMember);
        allocateStrategicWork(memberContexts, plansByMember, demands, slackInfo);

        List<MemberPlanSuggestion> members = plansByMember.values().stream()
                .map(plan -> toMemberSuggestion(plan, memberContexts.get(plan.userId())))
                .sorted(Comparator.comparing(MemberPlanSuggestion::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        TeamPlanSummary summary = buildSummary(members, demands, slackInfo);
        List<OutcomeAllocationSuggestion> allocations = buildOutcomeAllocations(members, demands);

        TeamPlanSuggestionResult deterministic = new TeamPlanSuggestionResult(
                STATUS_OK,
                weekStart,
                summary,
                members,
                allocations,
                false);

        return refineWithLlm(orgId, managerId, deterministic, memberContexts, demands);
    }

    private void validateWeekStart(LocalDate weekStart) {
        if (weekStart == null || weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart must be a Monday");
        }
    }

    private MemberPlanningContext buildMemberContext(UUID orgId, DirectReport report, LocalDate weekStart) {
        Optional<CapacityProfileProvider.CapacityProfileSnapshot> profile =
                capacityProfileProvider.getLatestProfile(orgId, report.userId());
        Optional<UserModelDataProvider.UserModelSnapshot> userModel =
                userModelDataProvider.getLatestSnapshot(orgId, report.userId());
        List<PredictionDataProvider.PredictionSignal> predictionSignals =
                Optional.ofNullable(predictionDataProvider.getUserPredictions(orgId, report.userId())).orElse(List.of());

        BigDecimal realisticCapacity = profile
                .map(CapacityProfileProvider.CapacityProfileSnapshot::realisticWeeklyCap)
                .filter(cap -> cap != null && cap.compareTo(BigDecimal.ZERO) > 0)
                .orElse(DEFAULT_CAPACITY_HOURS)
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal estimationBias = profile
                .map(CapacityProfileProvider.CapacityProfileSnapshot::estimationBias)
                .filter(bias -> bias != null && bias.compareTo(BigDecimal.ZERO) > 0)
                .orElse(BigDecimal.ONE)
                .setScale(2, RoundingMode.HALF_UP);
        UserStrengthProfile strengthProfile = parseStrengthProfile(userModel.orElse(null));
        List<CarryForwardItem> carryForwardItems = loadCarryForwardItems(orgId, report.userId(), weekStart, estimationBias);
        Map<UUID, Integer> recentOutcomeAffinity = loadRecentOutcomeAffinity(orgId, report.userId(), weekStart);

        boolean carryRisk = predictionSignals.stream().anyMatch(signal ->
                signal.likely() && "CARRY_FORWARD".equalsIgnoreCase(signal.type()));

        return new MemberPlanningContext(
                report.userId(),
                report.displayName(),
                realisticCapacity,
                estimationBias,
                carryRisk,
                strengthProfile,
                carryForwardItems,
                recentOutcomeAffinity);
    }

    private List<CarryForwardItem> loadCarryForwardItems(
            UUID orgId,
            UUID userId,
            LocalDate weekStart,
            BigDecimal estimationBias) {
        LocalDate windowStart = weekStart.minusWeeks(HISTORY_WEEKS);
        LocalDate windowEnd = weekStart.minusWeeks(1);
        List<WeeklyPlanEntity> plans = weeklyPlanRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, windowEnd)
                .stream()
                .filter(plan -> HISTORICAL_PLAN_STATES.contains(plan.getState()))
                .toList();

        Optional<WeeklyPlanEntity> latestPlan = plans.stream()
                .max(Comparator.comparing(WeeklyPlanEntity::getWeekStartDate));
        if (latestPlan.isEmpty()) {
            return List.of();
        }

        List<WeeklyCommitEntity> commits = weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(orgId, latestPlan.get().getId());
        if (commits.isEmpty()) {
            return List.of();
        }
        List<UUID> commitIds = commits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsByCommit = weeklyCommitActualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds)
                .stream()
                .collect(Collectors.toMap(WeeklyCommitActualEntity::getCommitId, actual -> actual));

        List<CarryForwardItem> carryForward = new ArrayList<>();
        for (WeeklyCommitEntity commit : commits) {
            WeeklyCommitActualEntity actual = actualsByCommit.get(commit.getId());
            if (actual == null || actual.getCompletionStatus() == CompletionStatus.DONE) {
                continue;
            }
            BigDecimal hours = adjustedHours(commit.getEstimatedHours(), estimationBias);
            carryForward.add(new CarryForwardItem(
                    commit.getId(),
                    commit.getTitle(),
                    commit.getOutcomeId(),
                    commit.getChessPriority(),
                    hours,
                    latestPlan.get().getWeekStartDate(),
                    actual.getCompletionStatus().name()));
        }
        return carryForward;
    }

    private Map<UUID, Integer> loadRecentOutcomeAffinity(UUID orgId, UUID userId, LocalDate weekStart) {
        LocalDate windowStart = weekStart.minusWeeks(HISTORY_WEEKS);
        LocalDate windowEnd = weekStart.minusWeeks(1);
        List<WeeklyPlanEntity> plans = weeklyPlanRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(orgId, userId, windowStart, windowEnd);
        if (plans.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Integer> counts = new HashMap<>();
        for (WeeklyPlanEntity plan : plans) {
            for (WeeklyCommitEntity commit : weeklyCommitRepository.findByOrgIdAndWeeklyPlanId(orgId, plan.getId())) {
                if (commit.getOutcomeId() != null) {
                    counts.merge(commit.getOutcomeId(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private void allocateCarryForwardBacklog(
            Map<UUID, MemberPlanningContext> memberContexts,
            Map<UUID, MutableMemberPlan> plansByMember) {
        for (MemberPlanningContext context : memberContexts.values()) {
            MutableMemberPlan plan = plansByMember.get(context.userId());
            for (CarryForwardItem item : context.carryForwardItems()) {
                plan.addSuggestion(new MutableSuggestedCommit(
                        item.title(),
                        item.outcomeId(),
                        chessPriorityOrDefault(item.chessPriority(), item.outcomeId() != null ? ChessPriority.QUEEN : ChessPriority.ROOK),
                        item.estimatedHours(),
                        "Carry forward from week of " + item.sourceWeekStart()
                                + " because it finished as " + item.priorStatus() + "."));
            }
        }
    }

    private List<OutcomeDemand> buildOutcomeDemands(UUID orgId, List<UrgencyInfo> urgencies) {
        return urgencies.stream()
                .filter(urgency -> urgency.outcomeId() != null)
                .map(urgency -> buildOutcomeDemand(orgId, urgency))
                .filter(demand -> demand.weight().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(OutcomeDemand::weight).reversed())
                .toList();
    }

    private OutcomeDemand buildOutcomeDemand(UUID orgId, UrgencyInfo urgency) {
        ForecastAnalyticsProvider.OutcomeCoverageHistory history =
                forecastAnalyticsProvider.getOutcomeCoverageHistory(orgId, urgency.outcomeId(), 4);

        BigDecimal weight = urgencyWeight(urgency.urgencyBand());
        BigDecimal progressGap = progressGap(urgency.progressPct(), urgency.expectedProgressPct());
        if (progressGap.compareTo(new BigDecimal("-10.0")) <= 0) {
            weight = weight.add(new BigDecimal("0.20"));
        }
        if (progressGap.compareTo(BigDecimal.ZERO) < 0) {
            weight = weight.add(new BigDecimal("0.10"));
        }
        if (history != null && "FALLING".equalsIgnoreCase(history.trendDirection())) {
            weight = weight.add(new BigDecimal("0.25"));
        }
        if (urgency.daysRemaining() >= 0 && urgency.daysRemaining() <= 21) {
            weight = weight.add(new BigDecimal("0.15"));
        }

        return new OutcomeDemand(
                urgency.outcomeId(),
                urgency.outcomeName() != null ? urgency.outcomeName() : urgency.outcomeId().toString(),
                urgency.urgencyBand(),
                history != null ? history.trendDirection() : "STABLE",
                progressGap,
                weight.setScale(2, RoundingMode.HALF_UP));
    }

    private void allocateStrategicWork(
            Map<UUID, MemberPlanningContext> memberContexts,
            Map<UUID, MutableMemberPlan> plansByMember,
            List<OutcomeDemand> demands,
            SlackInfo slackInfo) {
        if (demands.isEmpty()) {
            return;
        }

        BigDecimal totalCapacity = memberContexts.values().stream()
                .map(MemberPlanningContext::realisticCapacity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal alreadyAllocated = plansByMember.values().stream()
                .map(MutableMemberPlan::totalEstimated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal strategicFloor = slackInfo != null && slackInfo.strategicFocusFloor() != null
                ? slackInfo.strategicFocusFloor()
                : new BigDecimal("0.70");
        BigDecimal targetStrategicHours = totalCapacity.multiply(strategicFloor);
        BigDecimal bufferRatio = clampBufferRatio(new BigDecimal("1.0").subtract(strategicFloor).max(MIN_BUFFER_RATIO));
        BigDecimal maxAllocatable = totalCapacity.subtract(totalCapacity.multiply(bufferRatio));
        BigDecimal remainingStrategicBudget = targetStrategicHours.subtract(alreadyAllocated).max(BigDecimal.ZERO);
        BigDecimal totalWeight = demands.stream()
                .map(OutcomeDemand::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0 || remainingStrategicBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        for (OutcomeDemand demand : demands) {
            BigDecimal targetHours = remainingStrategicBudget
                    .multiply(demand.weight())
                    .divide(totalWeight, 1, RoundingMode.HALF_UP);
            BigDecimal remainingTeamRoom = maxAllocatable.subtract(totalAllocatedHours(plansByMember)).max(BigDecimal.ZERO);
            targetHours = targetHours.min(remainingTeamRoom);
            if (targetHours.compareTo(new BigDecimal("1.0")) < 0) {
                continue;
            }
            distributeOutcome(demand, targetHours, memberContexts, plansByMember);
        }
    }

    private void distributeOutcome(
            OutcomeDemand demand,
            BigDecimal targetHours,
            Map<UUID, MemberPlanningContext> memberContexts,
            Map<UUID, MutableMemberPlan> plansByMember) {
        List<MemberScore> rankedMembers = memberContexts.values().stream()
                .map(context -> new MemberScore(
                        context.userId(),
                        scoreMemberForOutcome(context, demand),
                        plansByMember.get(context.userId()).remainingCapacity()))
                .filter(score -> score.remainingCapacity().compareTo(BigDecimal.ZERO) > 0
                        && score.score().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(MemberScore::score).reversed())
                .toList();
        if (rankedMembers.isEmpty()) {
            return;
        }

        BigDecimal totalScore = rankedMembers.stream()
                .map(MemberScore::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = targetHours;
        for (int i = 0; i < rankedMembers.size() && remaining.compareTo(BigDecimal.ZERO) > 0; i++) {
            MemberScore score = rankedMembers.get(i);
            MutableMemberPlan plan = plansByMember.get(score.userId());
            BigDecimal share = i == rankedMembers.size() - 1
                    ? remaining
                    : targetHours.multiply(score.score()).divide(totalScore, 1, RoundingMode.HALF_UP);
            BigDecimal allocated = share.min(plan.remainingCapacity()).min(remaining);
            allocated = roundHalfDay(allocated);
            if (allocated.compareTo(new BigDecimal("1.0")) < 0) {
                continue;
            }

            plan.addSuggestion(new MutableSuggestedCommit(
                    demand.outcomeName() + ": advance next milestone",
                    demand.outcomeId(),
                    priorityForUrgency(demand.urgencyBand()),
                    allocated,
                    buildOutcomeRationale(memberContexts.get(score.userId()), demand, allocated)));
            remaining = remaining.subtract(allocated);
        }
    }

    private BigDecimal scoreMemberForOutcome(MemberPlanningContext context, OutcomeDemand demand) {
        BigDecimal score = new BigDecimal("1.00");
        int affinityCount = context.recentOutcomeAffinity().getOrDefault(demand.outcomeId(), 0);
        if (affinityCount > 0) {
            score = score.add(BigDecimal.valueOf(Math.min(0.75d, affinityCount * 0.20d)));
        }
        score = score.add(BigDecimal.valueOf(context.strengthProfile().completionReliability() * 0.50d));
        if (context.strengthProfile().hasStrategicStrength()) {
            score = score.add(new BigDecimal("0.15"));
        }
        if (context.carryRisk()) {
            score = score.subtract(new BigDecimal("0.20"));
        }
        if (context.remainingHistoricalCarryHours().compareTo(context.realisticCapacity().multiply(new BigDecimal("0.35"))) > 0) {
            score = score.subtract(new BigDecimal("0.15"));
        }
        return score.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildOutcomeRationale(MemberPlanningContext context, OutcomeDemand demand, BigDecimal hours) {
        List<String> parts = new ArrayList<>();
        parts.add(demand.outcomeName() + " is " + normalizeUrgencyLabel(demand.urgencyBand()).toLowerCase());
        int affinityCount = context.recentOutcomeAffinity().getOrDefault(demand.outcomeId(), 0);
        if (affinityCount > 0) {
            parts.add(context.displayName() + " touched this outcome in " + affinityCount + " recent week(s)");
        } else if (!context.strengthProfile().topCategories().isEmpty()) {
            parts.add(context.displayName() + "'s strongest categories are "
                    + String.join(", ", context.strengthProfile().topCategories().stream().limit(2).toList()));
        }
        if ("FALLING".equalsIgnoreCase(demand.coverageTrend())) {
            parts.add("coverage has been falling");
        }
        if (context.carryRisk()) {
            parts.add("keep the scope tight because carry-forward risk is elevated");
        }
        parts.add(hours.setScale(1, RoundingMode.HALF_UP) + "h fits within realistic capacity");
        return String.join("; ", parts) + ".";
    }

    private TeamPlanSummary buildSummary(
            List<MemberPlanSuggestion> members,
            List<OutcomeDemand> demands,
            SlackInfo slackInfo) {
        BigDecimal totalCapacity = members.stream()
                .map(MemberPlanSuggestion::realisticCapacity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal totalSuggested = members.stream()
                .map(MemberPlanSuggestion::totalEstimated)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal bufferHours = totalCapacity.subtract(totalSuggested).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
        long atRiskCount = demands.stream().filter(d -> "AT_RISK".equalsIgnoreCase(d.urgencyBand())).count();
        long criticalCount = demands.stream().filter(d -> "CRITICAL".equalsIgnoreCase(d.urgencyBand())).count();
        String headline = "Planned " + totalSuggested + "h against " + totalCapacity + "h realistic capacity"
                + "; strategic pressure is " + strategicPressureLabel((int) atRiskCount, (int) criticalCount) + ".";
        return new TeamPlanSummary(
                totalCapacity,
                totalSuggested,
                bufferHours,
                (int) atRiskCount,
                (int) criticalCount,
                slackInfo != null ? slackInfo.strategicFocusFloor() : null,
                headline);
    }

    private List<OutcomeAllocationSuggestion> buildOutcomeAllocations(
            List<MemberPlanSuggestion> members,
            List<OutcomeDemand> demands) {
        Map<UUID, OutcomeDemand> demandsById = demands.stream()
                .collect(Collectors.toMap(OutcomeDemand::outcomeId, demand -> demand, (left, right) -> left));
        Map<UUID, BigDecimal> hoursByOutcome = new LinkedHashMap<>();
        Map<UUID, List<MemberOutcomeSlice>> membersByOutcome = new LinkedHashMap<>();

        for (MemberPlanSuggestion member : members) {
            for (SuggestedCommit commit : member.suggestedCommits()) {
                if (commit.outcomeId() == null) {
                    continue;
                }
                UUID outcomeId = UUID.fromString(commit.outcomeId());
                hoursByOutcome.merge(outcomeId, commit.estimatedHours(), BigDecimal::add);
                membersByOutcome.computeIfAbsent(outcomeId, ignored -> new ArrayList<>())
                        .add(new MemberOutcomeSlice(member.userId(), member.displayName(), commit.estimatedHours(), commit.title()));
            }
        }

        return hoursByOutcome.entrySet().stream()
                .map(entry -> {
                    OutcomeDemand demand = demandsById.get(entry.getKey());
                    return new OutcomeAllocationSuggestion(
                            entry.getKey().toString(),
                            demand != null ? demand.outcomeName() : entry.getKey().toString(),
                            demand != null ? demand.urgencyBand() : "UNKNOWN",
                            entry.getValue().setScale(1, RoundingMode.HALF_UP),
                            membersByOutcome.getOrDefault(entry.getKey(), List.of()));
                })
                .sorted(Comparator.comparing(OutcomeAllocationSuggestion::recommendedHours).reversed())
                .toList();
    }

    private MemberPlanSuggestion toMemberSuggestion(MutableMemberPlan plan, MemberPlanningContext context) {
        BigDecimal totalEstimated = plan.totalEstimated().setScale(1, RoundingMode.HALF_UP);
        BigDecimal capacity = context.realisticCapacity().setScale(1, RoundingMode.HALF_UP);
        String risk = overcommitRisk(totalEstimated, capacity, context.carryRisk());
        return new MemberPlanSuggestion(
                plan.userId().toString(),
                plan.displayName(),
                plan.suggestions().stream()
                        .map(commit -> new SuggestedCommit(
                                commit.title(),
                                commit.outcomeId() != null ? commit.outcomeId().toString() : null,
                                commit.chessPriority().name(),
                                commit.estimatedHours().setScale(1, RoundingMode.HALF_UP),
                                commit.rationale(),
                                "AI_PLANNED"))
                        .toList(),
                totalEstimated,
                capacity,
                risk,
                context.strengthProfile().summary());
    }

    private TeamPlanSuggestionResult refineWithLlm(
            UUID orgId,
            UUID managerId,
            TeamPlanSuggestionResult deterministic,
            Map<UUID, MemberPlanningContext> memberContexts,
            List<OutcomeDemand> demands) {
        if (deterministic.members().isEmpty()) {
            return deterministic;
        }

        try {
            String fingerprint = computeFingerprint(deterministic, memberContexts, demands);
            String cacheKey = buildPlanningCopilotCacheKey(orgId, managerId, deterministic.weekStart(), fingerprint);
            Optional<TeamPlanSuggestionResult> cached = cacheService.get(orgId, cacheKey, TeamPlanSuggestionResult.class);
            if (cached.isPresent()) {
                return cached.get();
            }
            if (!rateLimiter.tryAcquire(managerId)) {
                LOG.debug("PlanningCopilotService: rate limit exceeded for manager {}, returning deterministic suggestions", managerId);
                return deterministic;
            }

            List<LlmClient.Message> messages = buildRefinementPrompt(deterministic, memberContexts, demands);
            String rawResponse = llmClient.complete(messages, refinementResponseSchema());
            TeamPlanSuggestionResult refined = applyRefinement(rawResponse, deterministic);
            cacheService.put(orgId, cacheKey, refined);
            return refined;
        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("PlanningCopilotService: LLM unavailable, returning deterministic suggestions: {}", e.getMessage());
            return deterministic;
        } catch (Exception e) {
            LOG.warn("PlanningCopilotService: unexpected LLM refinement error, returning deterministic suggestions", e);
            return deterministic;
        }
    }

    private List<LlmClient.Message> buildRefinementPrompt(
            TeamPlanSuggestionResult deterministic,
            Map<UUID, MemberPlanningContext> memberContexts,
            List<OutcomeDemand> demands) {
        List<LlmClient.Message> messages = new ArrayList<>();
        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are a planning copilot that rewrites weekly team-plan suggestions for clarity.
                You MUST preserve structure: do not change people, hours, outcomeIds, or priorities.
                Only rewrite the headline, commit titles, and rationales.

                Rules:
                1. Only reference memberId values and commitIndex values present in the context.
                2. Do not add or remove commits.
                3. Keep titles concise and action-oriented.
                4. Keep each rationale to one or two sentences and explain urgency, coverage, carry-forward, or user fit.
                5. Respond ONLY with JSON matching the required schema.
                """));
        StringBuilder context = new StringBuilder();
        context.append("Week: ").append(deterministic.weekStart()).append('\n');
        context.append("Current headline: ").append(deterministic.summary().headline()).append('\n');
        context.append("Outcome context:\n");
        for (OutcomeDemand demand : demands) {
            context.append(String.format(
                    "- outcomeId=%s | outcomeName=%s | urgency=%s | coverageTrend=%s | progressGap=%s%n",
                    demand.outcomeId(), demand.outcomeName(), demand.urgencyBand(), demand.coverageTrend(), demand.progressGap()));
        }
        context.append("Member context:\n");
        for (MemberPlanSuggestion member : deterministic.members()) {
            MemberPlanningContext memberContext = memberContexts.get(UUID.fromString(member.userId()));
            context.append(String.format(
                    "- memberId=%s | displayName=%s | realisticCapacity=%s | totalEstimated=%s | overcommitRisk=%s | strengths=%s%n",
                    member.userId(), member.displayName(), member.realisticCapacity(), member.totalEstimated(),
                    member.overcommitRisk(), memberContext != null ? memberContext.strengthProfile().summary() : "n/a"));
            for (int i = 0; i < member.suggestedCommits().size(); i++) {
                SuggestedCommit commit = member.suggestedCommits().get(i);
                context.append(String.format(
                        "  - commitIndex=%d | title=%s | outcomeId=%s | priority=%s | hours=%s | rationale=%s%n",
                        i, commit.title(), commit.outcomeId(), commit.chessPriority(), commit.estimatedHours(), commit.rationale()));
            }
        }
        messages.add(new LlmClient.Message(LlmClient.Role.ASSISTANT, context.toString()));
        messages.add(new LlmClient.Message(LlmClient.Role.USER,
                "Rewrite the headline and the commit wording while preserving all hours, priorities, and outcomeIds."));
        return messages;
    }

    private String refinementResponseSchema() {
        return """
                {
                  "type": "object",
                  "required": ["headline", "members"],
                  "properties": {
                    "headline": { "type": "string" },
                    "members": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["memberId", "commits"],
                        "properties": {
                          "memberId": { "type": "string" },
                          "commits": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "required": ["commitIndex", "title", "rationale"],
                              "properties": {
                                "commitIndex": { "type": "integer", "minimum": 0 },
                                "title": { "type": "string" },
                                "rationale": { "type": "string" }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
    }

    private TeamPlanSuggestionResult applyRefinement(String rawResponse, TeamPlanSuggestionResult deterministic)
            throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(rawResponse);
        if (root == null || !root.hasNonNull("headline") || !root.has("members")) {
            return deterministic;
        }

        Map<String, Map<Integer, RefinementCommit>> refinementsByMember = new HashMap<>();
        for (JsonNode memberNode : root.path("members")) {
            String memberId = memberNode.path("memberId").asText(null);
            if (memberId == null || memberId.isBlank()) {
                continue;
            }
            Map<Integer, RefinementCommit> commitMap = new HashMap<>();
            for (JsonNode commitNode : memberNode.path("commits")) {
                if (!commitNode.hasNonNull("commitIndex")) {
                    continue;
                }
                int commitIndex = commitNode.path("commitIndex").asInt(-1);
                String title = commitNode.path("title").asText(null);
                String rationale = commitNode.path("rationale").asText(null);
                if (commitIndex < 0 || title == null || title.isBlank() || rationale == null || rationale.isBlank()) {
                    continue;
                }
                commitMap.put(commitIndex, new RefinementCommit(title, rationale));
            }
            refinementsByMember.put(memberId, commitMap);
        }

        List<MemberPlanSuggestion> refinedMembers = new ArrayList<>();
        for (MemberPlanSuggestion member : deterministic.members()) {
            Map<Integer, RefinementCommit> commitRefinements = refinementsByMember.getOrDefault(member.userId(), Map.of());
            List<SuggestedCommit> refinedCommits = new ArrayList<>();
            for (int i = 0; i < member.suggestedCommits().size(); i++) {
                SuggestedCommit original = member.suggestedCommits().get(i);
                RefinementCommit refinement = commitRefinements.get(i);
                refinedCommits.add(refinement == null
                        ? original
                        : new SuggestedCommit(
                                refinement.title(),
                                original.outcomeId(),
                                original.chessPriority(),
                                original.estimatedHours(),
                                refinement.rationale(),
                                original.source()));
            }
            refinedMembers.add(new MemberPlanSuggestion(
                    member.userId(),
                    member.displayName(),
                    refinedCommits,
                    member.totalEstimated(),
                    member.realisticCapacity(),
                    member.overcommitRisk(),
                    member.strengthSummary()));
        }

        TeamPlanSummary refinedSummary = new TeamPlanSummary(
                deterministic.summary().teamCapacityHours(),
                deterministic.summary().suggestedHours(),
                deterministic.summary().bufferHours(),
                deterministic.summary().atRiskOutcomeCount(),
                deterministic.summary().criticalOutcomeCount(),
                deterministic.summary().strategicFocusFloor(),
                root.path("headline").asText(deterministic.summary().headline()));

        return new TeamPlanSuggestionResult(
                deterministic.status(),
                deterministic.weekStart(),
                refinedSummary,
                refinedMembers,
                deterministic.outcomeAllocations(),
                true);
    }

    private String computeFingerprint(
            TeamPlanSuggestionResult deterministic,
            Map<UUID, MemberPlanningContext> memberContexts,
            List<OutcomeDemand> demands) {
        StringBuilder builder = new StringBuilder();
        builder.append(deterministic.weekStart()).append('#');
        builder.append(deterministic.summary().headline()).append('#');
        for (OutcomeDemand demand : demands) {
            builder.append(demand.outcomeId()).append('|')
                    .append(demand.urgencyBand()).append('|')
                    .append(demand.coverageTrend()).append('|')
                    .append(demand.progressGap()).append('|')
                    .append(demand.weight()).append(';');
        }
        for (MemberPlanSuggestion member : deterministic.members()) {
            MemberPlanningContext context = memberContexts.get(UUID.fromString(member.userId()));
            builder.append(member.userId()).append('|')
                    .append(member.totalEstimated()).append('|')
                    .append(member.realisticCapacity()).append('|')
                    .append(member.overcommitRisk()).append('|')
                    .append(context != null ? context.strengthProfile().summary() : "").append('|')
                    .append(context != null && context.carryRisk()).append(';');
            for (SuggestedCommit commit : member.suggestedCommits()) {
                builder.append(commit.title()).append('|')
                        .append(commit.outcomeId()).append('|')
                        .append(commit.chessPriority()).append('|')
                        .append(commit.estimatedHours()).append('|')
                        .append(commit.rationale()).append(';');
            }
        }
        return sha256(builder.toString());
    }

    private String buildPlanningCopilotCacheKey(UUID orgId, UUID managerId, LocalDate weekStart, String fingerprint) {
        return "ai:planning-copilot:" + sha256(orgId + ":" + managerId + ":" + weekStart + ":" + fingerprint);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private BigDecimal urgencyWeight(String urgencyBand) {
        if (urgencyBand == null) {
            return new BigDecimal("0.20");
        }
        return switch (urgencyBand.toUpperCase()) {
            case "CRITICAL" -> new BigDecimal("1.00");
            case "AT_RISK" -> new BigDecimal("0.85");
            case "NEEDS_ATTENTION" -> new BigDecimal("0.65");
            case "ON_TRACK" -> new BigDecimal("0.35");
            default -> new BigDecimal("0.20");
        };
    }

    private BigDecimal progressGap(BigDecimal progressPct, BigDecimal expectedProgressPct) {
        BigDecimal actual = progressPct != null ? progressPct : BigDecimal.ZERO;
        BigDecimal expected = expectedProgressPct != null ? expectedProgressPct : actual;
        return actual.subtract(expected).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal adjustedHours(BigDecimal estimatedHours, BigDecimal bias) {
        BigDecimal base = estimatedHours != null && estimatedHours.compareTo(BigDecimal.ZERO) > 0
                ? estimatedHours
                : new BigDecimal("4.0");
        BigDecimal normalizedBias = bias != null && bias.compareTo(BigDecimal.ZERO) > 0 ? bias : BigDecimal.ONE;
        return roundHalfDay(base.multiply(normalizedBias));
    }

    private BigDecimal roundHalfDay(BigDecimal value) {
        return value.multiply(new BigDecimal("2"))
                .setScale(0, RoundingMode.HALF_UP)
                .divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal totalAllocatedHours(Map<UUID, MutableMemberPlan> plansByMember) {
        return plansByMember.values().stream()
                .map(MutableMemberPlan::totalEstimated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal clampBufferRatio(BigDecimal ratio) {
        if (ratio.compareTo(MIN_BUFFER_RATIO) < 0) {
            return MIN_BUFFER_RATIO;
        }
        if (ratio.compareTo(MAX_BUFFER_RATIO) > 0) {
            return MAX_BUFFER_RATIO;
        }
        return ratio.setScale(2, RoundingMode.HALF_UP);
    }

    private ChessPriority chessPriorityOrDefault(ChessPriority actual, ChessPriority fallback) {
        return actual != null ? actual : fallback;
    }

    private ChessPriority priorityForUrgency(String urgencyBand) {
        if (urgencyBand == null) {
            return ChessPriority.ROOK;
        }
        return switch (urgencyBand.toUpperCase()) {
            case "CRITICAL" -> ChessPriority.KING;
            case "AT_RISK" -> ChessPriority.QUEEN;
            case "NEEDS_ATTENTION" -> ChessPriority.ROOK;
            case "ON_TRACK" -> ChessPriority.BISHOP;
            default -> ChessPriority.KNIGHT;
        };
    }

    private String overcommitRisk(BigDecimal totalEstimated, BigDecimal capacity, boolean carryRisk) {
        if (capacity == null || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return carryRisk ? "MODERATE" : "LOW";
        }
        BigDecimal ratio = totalEstimated.divide(capacity, 2, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("1.15")) > 0) {
            return "HIGH";
        }
        if (ratio.compareTo(new BigDecimal("0.95")) > 0 || carryRisk) {
            return "MODERATE";
        }
        return "LOW";
    }

    private String strategicPressureLabel(int atRiskCount, int criticalCount) {
        if (criticalCount > 0 || atRiskCount >= 2) {
            return "HIGH";
        }
        if (atRiskCount == 1) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String normalizeUrgencyLabel(String urgencyBand) {
        return urgencyBand == null ? "TRACKED" : urgencyBand.replace('_', ' ');
    }

    private UserStrengthProfile parseStrengthProfile(UserModelDataProvider.UserModelSnapshot snapshot) {
        if (snapshot == null || snapshot.modelJson() == null || snapshot.modelJson().isBlank()) {
            return UserStrengthProfile.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(snapshot.modelJson());
            JsonNode performance = root.path("performanceProfile");
            List<String> topCategories = new ArrayList<>();
            for (JsonNode item : performance.path("topCategories")) {
                if (!item.asText().isBlank()) {
                    topCategories.add(item.asText());
                }
            }
            double completionReliability = performance.path("completionReliability").asDouble(0.0d);
            double estimationAccuracy = performance.path("estimationAccuracy").asDouble(0.0d);
            Map<String, Double> categoryRates = new LinkedHashMap<>();
            performance.path("categoryCompletionRates").fields().forEachRemaining(entry ->
                    categoryRates.put(entry.getKey(), entry.getValue().asDouble(0.0d)));
            String summary = topCategories.isEmpty()
                    ? "Generalist profile"
                    : "Strongest categories: " + String.join(", ", topCategories.stream().limit(3).toList());
            return new UserStrengthProfile(topCategories, categoryRates, completionReliability, estimationAccuracy, summary);
        } catch (JsonProcessingException e) {
            LOG.debug("PlanningCopilotService: could not parse user model snapshot", e);
            return UserStrengthProfile.empty();
        }
    }

    record TeamPlanSuggestionResult(
            String status,
            LocalDate weekStart,
            TeamPlanSummary summary,
            List<MemberPlanSuggestion> members,
            List<OutcomeAllocationSuggestion> outcomeAllocations,
            boolean llmRefined) {
    }

    record TeamPlanSummary(
            BigDecimal teamCapacityHours,
            BigDecimal suggestedHours,
            BigDecimal bufferHours,
            int atRiskOutcomeCount,
            int criticalOutcomeCount,
            BigDecimal strategicFocusFloor,
            String headline) {
    }

    record OutcomeAllocationSuggestion(
            String outcomeId,
            String outcomeName,
            String urgencyBand,
            BigDecimal recommendedHours,
            List<MemberOutcomeSlice> members) {
    }

    record MemberOutcomeSlice(
            String userId,
            String displayName,
            BigDecimal hours,
            String title) {
    }

    record MemberPlanSuggestion(
            String userId,
            String displayName,
            List<SuggestedCommit> suggestedCommits,
            BigDecimal totalEstimated,
            BigDecimal realisticCapacity,
            String overcommitRisk,
            String strengthSummary) {
    }

    record SuggestedCommit(
            String title,
            String outcomeId,
            String chessPriority,
            BigDecimal estimatedHours,
            String rationale,
            String source) {
    }

    private record MemberPlanningContext(
            UUID userId,
            String displayName,
            BigDecimal realisticCapacity,
            BigDecimal estimationBias,
            boolean carryRisk,
            UserStrengthProfile strengthProfile,
            List<CarryForwardItem> carryForwardItems,
            Map<UUID, Integer> recentOutcomeAffinity) {
        BigDecimal remainingHistoricalCarryHours() {
            return carryForwardItems.stream()
                    .map(CarryForwardItem::estimatedHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private record CarryForwardItem(
            UUID commitId,
            String title,
            UUID outcomeId,
            ChessPriority chessPriority,
            BigDecimal estimatedHours,
            LocalDate sourceWeekStart,
            String priorStatus) {
    }

    private record OutcomeDemand(
            UUID outcomeId,
            String outcomeName,
            String urgencyBand,
            String coverageTrend,
            BigDecimal progressGap,
            BigDecimal weight) {
    }

    private record MemberScore(UUID userId, BigDecimal score, BigDecimal remainingCapacity) {
    }

    private record RefinementCommit(String title, String rationale) {
    }

    private record UserStrengthProfile(
            List<String> topCategories,
            Map<String, Double> categoryRates,
            double completionReliability,
            double estimationAccuracy,
            String summary) {
        static UserStrengthProfile empty() {
            return new UserStrengthProfile(List.of(), Map.of(), 0.0d, 0.0d, "Generalist profile");
        }

        boolean hasStrategicStrength() {
            return completionReliability >= 0.70d || estimationAccuracy >= 0.70d;
        }
    }

    private static final class MutableMemberPlan {
        private final UUID userId;
        private final String displayName;
        private final BigDecimal realisticCapacity;
        private final List<MutableSuggestedCommit> suggestions = new ArrayList<>();
        private BigDecimal totalEstimated = BigDecimal.ZERO;

        private MutableMemberPlan(UUID userId, String displayName, BigDecimal realisticCapacity) {
            this.userId = userId;
            this.displayName = displayName;
            this.realisticCapacity = realisticCapacity;
        }

        UUID userId() {
            return userId;
        }

        String displayName() {
            return displayName;
        }

        List<MutableSuggestedCommit> suggestions() {
            return suggestions;
        }

        BigDecimal totalEstimated() {
            return totalEstimated;
        }

        BigDecimal remainingCapacity() {
            return realisticCapacity.subtract(totalEstimated).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
        }

        void addSuggestion(MutableSuggestedCommit suggestion) {
            suggestions.add(suggestion);
            totalEstimated = totalEstimated.add(suggestion.estimatedHours());
        }
    }

    private record MutableSuggestedCommit(
            String title,
            UUID outcomeId,
            ChessPriority chessPriority,
            BigDecimal estimatedHours,
            String rationale) {
    }
}
