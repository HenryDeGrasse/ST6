package com.weekly.executive;

import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.OrgRosterEntry;
import com.weekly.auth.OrgTeamGroup;
import com.weekly.capacity.CapacityProfileEntity;
import com.weekly.capacity.CapacityProfileRepository;
import com.weekly.forecast.LatestForecastEntity;
import com.weekly.forecast.LatestForecastRepository;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoTree;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates org-wide executive strategic health from persisted forecasts and current planning data.
 *
 * <p>All outputs are aggregate-only. Team comparisons are emitted as opaque bucket IDs rather than
 * manager or individual names.
 */
@Service
public class ExecutiveDashboardService {

    private final LatestForecastRepository latestForecastRepository;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyCommitRepository weeklyCommitRepository;
    private final CapacityProfileRepository capacityProfileRepository;
    private final OrgGraphClient orgGraphClient;
    private final RcdoClient rcdoClient;
    private final Clock clock;

    @Autowired
    public ExecutiveDashboardService(
            LatestForecastRepository latestForecastRepository,
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            CapacityProfileRepository capacityProfileRepository,
            OrgGraphClient orgGraphClient,
            RcdoClient rcdoClient) {
        this(
                latestForecastRepository,
                weeklyPlanRepository,
                weeklyCommitRepository,
                capacityProfileRepository,
                orgGraphClient,
                rcdoClient,
                Clock.systemUTC());
    }

    ExecutiveDashboardService(
            LatestForecastRepository latestForecastRepository,
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            CapacityProfileRepository capacityProfileRepository,
            OrgGraphClient orgGraphClient,
            RcdoClient rcdoClient,
            Clock clock) {
        this.latestForecastRepository = latestForecastRepository;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.weeklyCommitRepository = weeklyCommitRepository;
        this.capacityProfileRepository = capacityProfileRepository;
        this.orgGraphClient = orgGraphClient;
        this.rcdoClient = rcdoClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExecutiveDashboardResult getStrategicHealth(UUID orgId, LocalDate weekStart) {
        LocalDate effectiveWeekStart = normalizeWeekStart(weekStart);

        List<LatestForecastEntity> forecasts = latestForecastRepository.findByOrgId(orgId);
        Map<UUID, LatestForecastEntity> forecastsByOutcome = forecasts.stream()
                .collect(Collectors.toMap(
                        LatestForecastEntity::getOutcomeId,
                        forecast -> forecast,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<WeeklyPlanEntity> plans = weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(
                orgId,
                effectiveWeekStart,
                effectiveWeekStart);
        Map<UUID, WeeklyPlanEntity> plansById = plans.stream()
                .collect(Collectors.toMap(WeeklyPlanEntity::getId, plan -> plan, (left, right) -> left));

        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> commits = planIds.isEmpty()
                ? List.of()
                : weeklyCommitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        Map<UUID, List<WeeklyCommitEntity>> commitsByOwner = commits.stream()
                .collect(Collectors.groupingBy(
                        commit -> plansById.get(commit.getWeeklyPlanId()).getOwnerUserId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<UUID, CapacityProfileEntity> capacityByUser = capacityProfileRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(CapacityProfileEntity::getUserId, profile -> profile, (left, right) -> left));

        List<OrgRosterEntry> roster = safeGetRoster(orgId);
        List<UUID> orgPopulationUserIds = resolveOrgPopulationUserIds(plans, capacityByUser, roster);
        StrategicCapacitySummary orgCapacity = buildStrategicCapacitySummary(
                plans,
                commits,
                capacityByUser,
                orgPopulationUserIds);

        RcdoTree rcdoTree = safeGetTree(orgId);
        Map<UUID, RallyCryRef> rallyCryByOutcome = buildRallyCryMap(rcdoTree);
        List<RallyCryHealthRollup> rallyCryRollups = buildRallyCryRollups(rallyCryByOutcome, forecasts, commits);

        Map<UUID, OrgTeamGroup> teamGroups = safeGetTeamGroups(orgId);
        boolean teamGroupingAvailable = !teamGroups.isEmpty();
        List<TeamBucketComparison> teamBuckets = buildTeamBuckets(
                teamGroups,
                plans,
                commitsByOwner,
                capacityByUser,
                forecastsByOutcome);

        return new ExecutiveDashboardResult(
                effectiveWeekStart,
                buildSummary(forecasts, orgCapacity, plans, orgPopulationUserIds.size()),
                rallyCryRollups,
                teamBuckets,
                teamGroupingAvailable);
    }

    private LocalDate normalizeWeekStart(LocalDate weekStart) {
        LocalDate resolved = weekStart != null
                ? weekStart
                : LocalDate.now(clock).with(DayOfWeek.MONDAY);
        if (resolved.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStart must be a Monday");
        }
        return resolved;
    }

    private RcdoTree safeGetTree(UUID orgId) {
        try {
            RcdoTree tree = rcdoClient.getTree(orgId);
            return tree != null ? tree : new RcdoTree(List.of());
        } catch (Exception ignored) {
            return new RcdoTree(List.of());
        }
    }

    private List<OrgRosterEntry> safeGetRoster(UUID orgId) {
        try {
            List<OrgRosterEntry> roster = orgGraphClient.getOrgRoster(orgId);
            return roster != null ? roster : List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<UUID, OrgTeamGroup> safeGetTeamGroups(UUID orgId) {
        try {
            Map<UUID, OrgTeamGroup> groups = orgGraphClient.getOrgTeamGroups(orgId);
            return groups != null ? groups : Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private ExecutiveSummary buildSummary(
            List<LatestForecastEntity> forecasts,
            StrategicCapacitySummary orgCapacity,
            List<WeeklyPlanEntity> plans,
            int populationSize) {
        int onTrack = 0;
        int needsAttention = 0;
        int offTrack = 0;
        int noData = 0;
        BigDecimal confidenceTotal = BigDecimal.ZERO;
        int confidenceCount = 0;

        for (LatestForecastEntity forecast : forecasts) {
            switch (toHealthBucket(forecast.getForecastStatus())) {
                case ON_TRACK -> onTrack++;
                case OFF_TRACK -> offTrack++;
                case NO_DATA -> noData++;
                case NEEDS_ATTENTION -> needsAttention++;
            }
            if (forecast.getConfidenceScore() != null) {
                confidenceTotal = confidenceTotal.add(forecast.getConfidenceScore());
                confidenceCount++;
            }
        }

        long plannedUsers = plans.stream().map(WeeklyPlanEntity::getOwnerUserId).distinct().count();
        BigDecimal planningCoveragePct = populationSize == 0
                ? BigDecimal.ZERO
                : percentage(BigDecimal.valueOf(plannedUsers), BigDecimal.valueOf(populationSize));

        return new ExecutiveSummary(
                forecasts.size(),
                onTrack,
                needsAttention,
                offTrack,
                noData,
                average(confidenceTotal, confidenceCount, 4),
                orgCapacity.totalCapacityHours(),
                orgCapacity.strategicHours(),
                orgCapacity.nonStrategicHours(),
                orgCapacity.strategicUtilizationPct(),
                orgCapacity.nonStrategicUtilizationPct(),
                planningCoveragePct);
    }

    private Map<UUID, RallyCryRef> buildRallyCryMap(RcdoTree tree) {
        Map<UUID, RallyCryRef> map = new LinkedHashMap<>();
        if (tree == null || tree.rallyCries() == null) {
            return map;
        }

        for (RcdoTree.RallyCry rallyCry : tree.rallyCries()) {
            List<RcdoTree.Objective> objectives = rallyCry.objectives() != null ? rallyCry.objectives() : List.of();
            for (RcdoTree.Objective objective : objectives) {
                List<RcdoTree.Outcome> outcomes = objective.outcomes() != null ? objective.outcomes() : List.of();
                for (RcdoTree.Outcome outcome : outcomes) {
                    UUID outcomeId = parseUuid(outcome.id());
                    UUID rallyCryId = parseUuid(rallyCry.id());
                    if (outcomeId != null) {
                        map.put(outcomeId, new RallyCryRef(
                                rallyCryId != null ? rallyCryId : outcomeId,
                                rallyCry.name() != null ? rallyCry.name() : "Unmapped Rally Cry"));
                    }
                }
            }
        }
        return map;
    }

    private List<RallyCryHealthRollup> buildRallyCryRollups(
            Map<UUID, RallyCryRef> rallyCryByOutcome,
            List<LatestForecastEntity> forecasts,
            List<WeeklyCommitEntity> commits) {
        Map<UUID, MutableRallyCryRollup> rollups = new LinkedHashMap<>();

        for (LatestForecastEntity forecast : forecasts) {
            RallyCryRef rallyCry = rallyCryByOutcome.getOrDefault(
                    forecast.getOutcomeId(),
                    new RallyCryRef(forecast.getOutcomeId(), "Unmapped Rally Cry"));
            MutableRallyCryRollup rollup = rollups.computeIfAbsent(
                    rallyCry.rallyCryId(),
                    ignored -> new MutableRallyCryRollup(rallyCry.rallyCryId(), rallyCry.rallyCryName()));
            rollup.forecastCount++;
            if (forecast.getConfidenceScore() != null) {
                rollup.confidenceTotal = rollup.confidenceTotal.add(forecast.getConfidenceScore());
                rollup.confidenceCount++;
            }
            switch (toHealthBucket(forecast.getForecastStatus())) {
                case ON_TRACK -> rollup.onTrackCount++;
                case OFF_TRACK -> rollup.offTrackCount++;
                case NO_DATA -> rollup.noDataCount++;
                case NEEDS_ATTENTION -> rollup.needsAttentionCount++;
            }
        }

        for (WeeklyCommitEntity commit : commits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }
            RallyCryRef rallyCry = rallyCryByOutcome.get(commit.getOutcomeId());
            if (rallyCry == null) {
                continue;
            }
            MutableRallyCryRollup rollup = rollups.computeIfAbsent(
                    rallyCry.rallyCryId(),
                    ignored -> new MutableRallyCryRollup(rallyCry.rallyCryId(), rallyCry.rallyCryName()));
            rollup.strategicHours = rollup.strategicHours.add(safeHours(commit.getEstimatedHours()));
        }

        return rollups.values().stream()
                .map(rollup -> new RallyCryHealthRollup(
                        rollup.rallyCryId().toString(),
                        rollup.rallyCryName(),
                        rollup.forecastCount,
                        rollup.onTrackCount,
                        rollup.needsAttentionCount,
                        rollup.offTrackCount,
                        rollup.noDataCount,
                        average(rollup.confidenceTotal, rollup.confidenceCount, 4),
                        rollup.strategicHours.setScale(1, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(RallyCryHealthRollup::offTrackCount).reversed()
                        .thenComparing(RallyCryHealthRollup::needsAttentionCount, Comparator.reverseOrder())
                        .thenComparing(RallyCryHealthRollup::strategicHours, Comparator.reverseOrder())
                        .thenComparing(RallyCryHealthRollup::rallyCryName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<TeamBucketComparison> buildTeamBuckets(
            Map<UUID, OrgTeamGroup> teamGroups,
            List<WeeklyPlanEntity> plans,
            Map<UUID, List<WeeklyCommitEntity>> commitsByOwner,
            Map<UUID, CapacityProfileEntity> capacityByUser,
            Map<UUID, LatestForecastEntity> forecastsByOutcome) {
        if (teamGroups.isEmpty()) {
            return List.of();
        }

        Set<UUID> plannedUsers = plans.stream().map(WeeklyPlanEntity::getOwnerUserId).collect(Collectors.toSet());
        List<OrgTeamGroup> orderedGroups = teamGroups.values().stream()
                .filter(group -> group.members() != null && !group.members().isEmpty())
                .sorted(Comparator.comparing(group -> group.managerId().toString()))
                .toList();

        List<TeamBucketComparison> buckets = new ArrayList<>();
        for (int index = 0; index < orderedGroups.size(); index++) {
            OrgTeamGroup group = orderedGroups.get(index);
            List<UUID> memberIds = group.members().stream()
                    .map(OrgRosterEntry::userId)
                    .filter(Objects::nonNull)
                    .toList();
            StrategicCapacitySummary summary = buildStrategicCapacitySummary(
                    plans,
                    flattenCommits(memberIds, commitsByOwner),
                    capacityByUser,
                    memberIds);
            BigDecimal planCoveragePct = memberIds.isEmpty()
                    ? BigDecimal.ZERO
                    : percentage(
                            BigDecimal.valueOf(memberIds.stream().filter(plannedUsers::contains).count()),
                            BigDecimal.valueOf(memberIds.size()));
            BigDecimal averageForecastConfidence = averageForecastConfidenceForTeam(
                    flattenCommits(memberIds, commitsByOwner),
                    forecastsByOutcome);
            buckets.add(new TeamBucketComparison(
                    "team-" + (index + 1),
                    memberIds.size(),
                    planCoveragePct,
                    summary.totalCapacityHours(),
                    summary.strategicHours(),
                    summary.nonStrategicHours(),
                    summary.strategicUtilizationPct(),
                    averageForecastConfidence));
        }
        return buckets;
    }

    private List<WeeklyCommitEntity> flattenCommits(
            List<UUID> memberIds,
            Map<UUID, List<WeeklyCommitEntity>> commitsByOwner) {
        return memberIds.stream()
                .flatMap(memberId -> commitsByOwner.getOrDefault(memberId, List.of()).stream())
                .toList();
    }

    private BigDecimal averageForecastConfidenceForTeam(
            List<WeeklyCommitEntity> commits,
            Map<UUID, LatestForecastEntity> forecastsByOutcome) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (WeeklyCommitEntity commit : commits) {
            if (commit.getOutcomeId() == null) {
                continue;
            }
            LatestForecastEntity forecast = forecastsByOutcome.get(commit.getOutcomeId());
            if (forecast == null || forecast.getConfidenceScore() == null) {
                continue;
            }
            total = total.add(forecast.getConfidenceScore());
            count++;
        }
        return average(total, count, 4);
    }

    private StrategicCapacitySummary buildStrategicCapacitySummary(
            List<WeeklyPlanEntity> plans,
            List<WeeklyCommitEntity> commits,
            Map<UUID, CapacityProfileEntity> capacityByUser,
            List<UUID> memberIds) {
        Set<UUID> scopedUsers = memberIds == null || memberIds.isEmpty()
                ? capacityByUser.keySet().isEmpty()
                        ? plans.stream().map(WeeklyPlanEntity::getOwnerUserId).collect(Collectors.toSet())
                        : Set.copyOf(capacityByUser.keySet())
                : Set.copyOf(memberIds);

        BigDecimal totalCapacity = scopedUsers.stream()
                .map(capacityByUser::get)
                .filter(Objects::nonNull)
                .map(CapacityProfileEntity::getRealisticWeeklyCap)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);

        BigDecimal strategicHours = BigDecimal.ZERO;
        BigDecimal nonStrategicHours = BigDecimal.ZERO;
        for (WeeklyCommitEntity commit : commits) {
            BigDecimal hours = safeHours(commit.getEstimatedHours());
            if (commit.getOutcomeId() != null) {
                strategicHours = strategicHours.add(hours);
            } else {
                nonStrategicHours = nonStrategicHours.add(hours);
            }
        }

        strategicHours = strategicHours.setScale(1, RoundingMode.HALF_UP);
        nonStrategicHours = nonStrategicHours.setScale(1, RoundingMode.HALF_UP);
        return new StrategicCapacitySummary(
                totalCapacity,
                strategicHours,
                nonStrategicHours,
                totalCapacity.compareTo(BigDecimal.ZERO) > 0
                        ? percentage(strategicHours, totalCapacity)
                        : BigDecimal.ZERO,
                totalCapacity.compareTo(BigDecimal.ZERO) > 0
                        ? percentage(nonStrategicHours, totalCapacity)
                        : BigDecimal.ZERO);
    }

    private List<UUID> resolveOrgPopulationUserIds(
            List<WeeklyPlanEntity> plans,
            Map<UUID, CapacityProfileEntity> capacityByUser,
            List<OrgRosterEntry> roster) {
        if (roster != null && !roster.isEmpty()) {
            return roster.stream()
                    .map(OrgRosterEntry::userId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (!capacityByUser.isEmpty()) {
            return capacityByUser.keySet().stream().toList();
        }
        return plans.stream()
                .map(WeeklyPlanEntity::getOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private BigDecimal safeHours(BigDecimal hours) {
        return hours == null ? BigDecimal.ZERO : hours;
    }

    private ForecastHealthBucket toHealthBucket(String status) {
        String normalized = status == null ? "NEEDS_ATTENTION" : status.trim().toUpperCase();
        return switch (normalized) {
            case "ON_TRACK", "COMPLETE" -> ForecastHealthBucket.ON_TRACK;
            case "AT_RISK", "OFF_TRACK" -> ForecastHealthBucket.OFF_TRACK;
            case "NO_DATA", "NO_TARGET_DATE" -> ForecastHealthBucket.NO_DATA;
            default -> ForecastHealthBucket.NEEDS_ATTENTION;
        };
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return numerator.multiply(new BigDecimal("100"))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(BigDecimal total, int count, int scale) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(count), scale, RoundingMode.HALF_UP);
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record ExecutiveDashboardResult(
            LocalDate weekStart,
            ExecutiveSummary summary,
            List<RallyCryHealthRollup> rallyCryRollups,
            List<TeamBucketComparison> teamBuckets,
            boolean teamGroupingAvailable) {
    }

    public record ExecutiveSummary(
            int totalForecasts,
            int onTrackForecasts,
            int needsAttentionForecasts,
            int offTrackForecasts,
            int noDataForecasts,
            BigDecimal averageForecastConfidence,
            BigDecimal totalCapacityHours,
            BigDecimal strategicHours,
            BigDecimal nonStrategicHours,
            BigDecimal strategicCapacityUtilizationPct,
            BigDecimal nonStrategicCapacityUtilizationPct,
            BigDecimal planningCoveragePct) {
    }

    public record RallyCryHealthRollup(
            String rallyCryId,
            String rallyCryName,
            int forecastedOutcomeCount,
            int onTrackCount,
            int needsAttentionCount,
            int offTrackCount,
            int noDataCount,
            BigDecimal averageForecastConfidence,
            BigDecimal strategicHours) {
    }

    public record TeamBucketComparison(
            String bucketId,
            int memberCount,
            BigDecimal planCoveragePct,
            BigDecimal totalCapacityHours,
            BigDecimal strategicHours,
            BigDecimal nonStrategicHours,
            BigDecimal strategicCapacityUtilizationPct,
            BigDecimal averageForecastConfidence) {
    }

    private record RallyCryRef(UUID rallyCryId, String rallyCryName) {
    }

    private record StrategicCapacitySummary(
            BigDecimal totalCapacityHours,
            BigDecimal strategicHours,
            BigDecimal nonStrategicHours,
            BigDecimal strategicUtilizationPct,
            BigDecimal nonStrategicUtilizationPct) {
    }

    private enum ForecastHealthBucket {
        ON_TRACK,
        NEEDS_ATTENTION,
        OFF_TRACK,
        NO_DATA
    }

    private static final class MutableRallyCryRollup {
        private final UUID rallyCryId;
        private final String rallyCryName;
        private int forecastCount;
        private int onTrackCount;
        private int needsAttentionCount;
        private int offTrackCount;
        private int noDataCount;
        private BigDecimal confidenceTotal = BigDecimal.ZERO;
        private int confidenceCount;
        private BigDecimal strategicHours = BigDecimal.ZERO;

        private MutableRallyCryRollup(UUID rallyCryId, String rallyCryName) {
            this.rallyCryId = rallyCryId;
            this.rallyCryName = rallyCryName;
        }

        private UUID rallyCryId() {
            return rallyCryId;
        }

        private String rallyCryName() {
            return rallyCryName;
        }
    }
}
