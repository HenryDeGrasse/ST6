package com.weekly.forecast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.forecast.ForecastDtos.ForecastFactorResponse;
import com.weekly.forecast.ForecastDtos.OutcomeForecastResponse;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoOutcomeDetail;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.ForecastAnalyticsProvider;
import com.weekly.shared.PredictionDataProvider;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes and persists target-date forecasts for tracked outcomes.
 */
@Service
public class TargetDateForecastService {

    static final int DEFAULT_HISTORY_WEEKS = 6;
    static final String STATUS_NO_DATA = "NO_DATA";
    static final String STATUS_NO_TARGET_DATE = "NO_TARGET_DATE";
    static final String STATUS_COMPLETE = "COMPLETE";
    static final String STATUS_ON_TRACK = "ON_TRACK";
    static final String STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION";
    static final String STATUS_AT_RISK = "AT_RISK";
    static final String CONFIDENCE_LOW = "LOW";
    static final String CONFIDENCE_MEDIUM = "MEDIUM";
    static final String CONFIDENCE_HIGH = "HIGH";
    static final String MODEL_VERSION = "phase5-target-date-v1";

    private static final Logger LOG = LoggerFactory.getLogger(TargetDateForecastService.class);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal MIN_EFFECTIVE_VELOCITY = new BigDecimal("0.2500");

    private final LatestForecastRepository latestForecastRepository;
    private final UrgencyDataProvider urgencyDataProvider;
    private final ForecastAnalyticsProvider forecastAnalyticsProvider;
    private final CapacityProfileProvider capacityProfileProvider;
    private final PredictionDataProvider predictionDataProvider;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final RcdoClient rcdoClient;
    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final Clock clock;

    @Autowired
    public TargetDateForecastService(
            LatestForecastRepository latestForecastRepository,
            UrgencyDataProvider urgencyDataProvider,
            ForecastAnalyticsProvider forecastAnalyticsProvider,
            CapacityProfileProvider capacityProfileProvider,
            PredictionDataProvider predictionDataProvider,
            WeeklyPlanRepository weeklyPlanRepository,
            RcdoClient rcdoClient,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this(
                latestForecastRepository,
                urgencyDataProvider,
                forecastAnalyticsProvider,
                capacityProfileProvider,
                predictionDataProvider,
                weeklyPlanRepository,
                rcdoClient,
                objectMapper,
                jdbcTemplate,
                Clock.systemUTC());
    }

    TargetDateForecastService(
            LatestForecastRepository latestForecastRepository,
            UrgencyDataProvider urgencyDataProvider,
            ForecastAnalyticsProvider forecastAnalyticsProvider,
            CapacityProfileProvider capacityProfileProvider,
            PredictionDataProvider predictionDataProvider,
            WeeklyPlanRepository weeklyPlanRepository,
            RcdoClient rcdoClient,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.latestForecastRepository = latestForecastRepository;
        this.urgencyDataProvider = urgencyDataProvider;
        this.forecastAnalyticsProvider = forecastAnalyticsProvider;
        this.capacityProfileProvider = capacityProfileProvider;
        this.predictionDataProvider = predictionDataProvider;
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.rcdoClient = rcdoClient;
        this.objectMapper = objectMapper;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.clock = clock;
    }

    /** Returns persisted forecasts for the authenticated org, computing missing ones on-demand. */
    @Transactional
    public List<OutcomeForecastResponse> getOrComputeOrgForecasts(UUID orgId) {
        return syncTrackedForecasts(orgId).stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(OutcomeForecastResponse::outcomeName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    /** Returns a persisted forecast for one outcome, computing it on-demand if needed. */
    @Transactional
    public Optional<OutcomeForecastResponse> getOrComputeOutcomeForecast(UUID orgId, UUID outcomeId) {
        UrgencyInfo urgencyInfo = urgencyDataProvider.getOutcomeUrgency(orgId, outcomeId);
        if (urgencyInfo == null) {
            latestForecastRepository.deleteByOrgIdAndOutcomeId(orgId, outcomeId);
            return Optional.empty();
        }

        return latestForecastRepository.findByOrgIdAndOutcomeId(orgId, outcomeId)
                .map(this::toResponse)
                .or(() -> computeAndPersistOutcomeForecast(orgId, urgencyInfo).map(this::toResponse));
    }

    /** Recomputes and persists forecasts for all tracked outcomes in an organisation. */
    @Transactional
    public List<LatestForecastEntity> recomputeForecastsForOrg(UUID orgId) {
        return syncTrackedForecasts(orgId, true);
    }

    /** Returns the orgs currently eligible for background forecast computation. */
    @Transactional(readOnly = true)
    public List<UUID> getForecastableOrgIds() {
        return weeklyPlanRepository.findDistinctOrgIds();
    }

    private List<LatestForecastEntity> syncTrackedForecasts(UUID orgId) {
        return syncTrackedForecasts(orgId, false);
    }

    private List<LatestForecastEntity> syncTrackedForecasts(UUID orgId, boolean forceRecompute) {
        List<UrgencyInfo> trackedOutcomes = urgencyDataProvider.getOrgUrgencySummary(orgId);
        if (trackedOutcomes.isEmpty()) {
            latestForecastRepository.deleteByOrgId(orgId);
            LOG.debug("TargetDateForecastService: no tracked outcomes found for org {}", orgId);
            return List.of();
        }

        Map<UUID, LatestForecastEntity> persistedByOutcomeId = latestForecastRepository.findByOrgId(orgId).stream()
                .collect(LinkedHashMap::new, (map, entity) -> map.put(entity.getOutcomeId(), entity), LinkedHashMap::putAll);
        Set<UUID> trackedOutcomeIds = trackedOutcomes.stream()
                .map(UrgencyInfo::outcomeId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        persistedByOutcomeId.keySet().stream()
                .filter(outcomeId -> !trackedOutcomeIds.contains(outcomeId))
                .toList()
                .forEach(outcomeId -> latestForecastRepository.deleteByOrgIdAndOutcomeId(orgId, outcomeId));

        List<LatestForecastEntity> synced = new ArrayList<>();
        for (UrgencyInfo urgencyInfo : trackedOutcomes) {
            LatestForecastEntity persisted = persistedByOutcomeId.get(urgencyInfo.outcomeId());
            if (!forceRecompute && persisted != null) {
                synced.add(persisted);
                continue;
            }
            computeAndPersistOutcomeForecast(orgId, urgencyInfo).ifPresent(synced::add);
        }
        return synced;
    }

    @Transactional
    Optional<LatestForecastEntity> computeAndPersistOutcomeForecast(UUID orgId, UUID outcomeId) {
        UrgencyInfo urgencyInfo = urgencyDataProvider.getOutcomeUrgency(orgId, outcomeId);
        if (urgencyInfo == null) {
            latestForecastRepository.deleteByOrgIdAndOutcomeId(orgId, outcomeId);
            return Optional.empty();
        }
        return computeAndPersistOutcomeForecast(orgId, urgencyInfo);
    }

    private Optional<LatestForecastEntity> computeAndPersistOutcomeForecast(UUID orgId, UrgencyInfo urgencyInfo) {
        ComputedForecast computed = computeForecast(orgId, urgencyInfo);
        LatestForecastEntity entity = latestForecastRepository.findByOrgIdAndOutcomeId(orgId, urgencyInfo.outcomeId())
                .orElseGet(() -> new LatestForecastEntity(orgId, urgencyInfo.outcomeId()));

        entity.setProjectedTargetDate(computed.projectedTargetDate());
        entity.setProjectedProgressPct(computed.projectedProgressPct());
        entity.setProjectedVelocity(computed.projectedVelocity());
        entity.setConfidenceScore(computed.confidenceScore());
        entity.setForecastStatus(computed.forecastStatus());
        entity.setModelVersion(MODEL_VERSION);
        entity.setForecastInputsJson(writeJson(computed.inputs()));
        entity.setForecastDetailsJson(writeJson(Map.of(
                "confidenceBand", computed.confidenceBand(),
                "contributingFactors", computed.factors(),
                "recommendations", computed.recommendations(),
                "outcomeName", computed.outcomeName())));
        entity.setComputedAt(Instant.now(clock));

        return Optional.of(latestForecastRepository.save(entity));
    }

    ComputedForecast computeForecast(UUID orgId, UrgencyInfo urgencyInfo) {
        LocalDate today = LocalDate.now(clock);
        String outcomeName = resolveOutcomeName(orgId, urgencyInfo);
        BigDecimal currentProgress = normalizedPercent(urgencyInfo.progressPct());
        BigDecimal expectedProgress = normalizedPercent(urgencyInfo.expectedProgressPct());

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("targetDate", urgencyInfo.targetDate());
        inputs.put("currentProgressPct", currentProgress);
        inputs.put("expectedProgressPct", expectedProgress);
        inputs.put("urgencyBand", urgencyInfo.urgencyBand());
        inputs.put("daysRemaining", urgencyInfo.daysRemaining());

        List<ForecastFactorResponse> factors = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (urgencyInfo.targetDate() == null) {
            factors.add(factor("urgency", "No target date", 0.10,
                    "Outcome is tracked but has no target date, so the forecast cannot project a completion date."));
            recommendations.add("Set a target date so the forecast can project whether the outcome is on track.");
            return new ComputedForecast(
                    outcomeId(urgencyInfo),
                    outcomeName,
                    null,
                    currentProgress,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    new BigDecimal("0.20"),
                    CONFIDENCE_LOW,
                    STATUS_NO_TARGET_DATE,
                    inputs,
                    factors,
                    recommendations);
        }

        if (currentProgress.compareTo(ONE_HUNDRED) >= 0) {
            factors.add(factor("progress", "Outcome already complete", 1.00,
                    "Current progress is at or above 100%, so the projected completion date is today."));
            return new ComputedForecast(
                    outcomeId(urgencyInfo),
                    outcomeName,
                    today,
                    ONE_HUNDRED,
                    ONE_HUNDRED.setScale(4, RoundingMode.HALF_UP),
                    new BigDecimal("0.95"),
                    CONFIDENCE_HIGH,
                    STATUS_COMPLETE,
                    inputs,
                    factors,
                    recommendations);
        }

        ForecastAnalyticsProvider.OutcomeCoverageHistory history =
                forecastAnalyticsProvider.getOutcomeCoverageHistory(orgId, urgencyInfo.outcomeId(), DEFAULT_HISTORY_WEEKS);
        List<UUID> contributorIds = findRecentContributorIds(orgId, urgencyInfo.outcomeId(), DEFAULT_HISTORY_WEEKS);

        inputs.put("coverageWeeks", history.weeks().size());
        inputs.put("coverageTrendDirection", history.trendDirection());
        inputs.put("contributorCount", contributorIds.size());

        BigDecimal scheduleMultiplier = scheduleMultiplier(currentProgress, expectedProgress, factors, recommendations);
        BigDecimal coverageMultiplier = coverageMultiplier(history, factors, recommendations);
        CapacitySignal capacitySignal = capacitySignal(orgId, contributorIds, factors, recommendations);
        PredictionSignalSummary predictionSummary = predictionSignal(orgId, contributorIds, factors, recommendations);

        BigDecimal observedVelocity = estimateObservedVelocity(
                currentProgress,
                urgencyInfo.targetDate(),
                urgencyInfo.daysRemaining(),
                expectedProgress,
                factors);
        BigDecimal effectiveVelocity = observedVelocity
                .multiply(scheduleMultiplier)
                .multiply(coverageMultiplier)
                .multiply(capacitySignal.multiplier())
                .multiply(predictionSummary.multiplier())
                .setScale(4, RoundingMode.HALF_UP);

        if (effectiveVelocity.compareTo(MIN_EFFECTIVE_VELOCITY) < 0) {
            effectiveVelocity = MIN_EFFECTIVE_VELOCITY;
        }

        BigDecimal remainingProgress = ONE_HUNDRED.subtract(currentProgress).max(BigDecimal.ZERO);
        long weeksToComplete = remainingProgress.compareTo(BigDecimal.ZERO) == 0
                ? 0L
                : Math.max(1L, remainingProgress.divide(effectiveVelocity, 0, RoundingMode.CEILING).longValue());
        LocalDate projectedTargetDate = today.plusWeeks(weeksToComplete);

        BigDecimal remainingDays = BigDecimal.valueOf(Math.max(0L, urgencyInfo.daysRemaining()));
        BigDecimal projectedProgressPct = urgencyInfo.daysRemaining() <= 0
                ? currentProgress
                : currentProgress.add(effectiveVelocity.multiply(remainingDays.divide(new BigDecimal("7.0"), 2, RoundingMode.HALF_UP)));
        projectedProgressPct = projectedProgressPct.min(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);

        BigDecimal confidenceScore = confidenceScore(urgencyInfo, history, contributorIds, capacitySignal, predictionSummary)
                .setScale(4, RoundingMode.HALF_UP);
        String confidenceBand = toConfidenceBand(confidenceScore);
        String forecastStatus = determineStatus(projectedTargetDate, urgencyInfo.targetDate());

        inputs.put("observedVelocity", observedVelocity);
        inputs.put("effectiveVelocity", effectiveVelocity);
        inputs.put("contributorsWithCapacityProfiles", capacitySignal.profileCount());
        inputs.put("contributorsWithCarryForwardRisk", predictionSummary.carryForwardRiskCount());

        if (recommendations.isEmpty()) {
            recommendations.add("Maintain the current delivery pattern and recheck the forecast after the next weekly planning cycle.");
        }

        return new ComputedForecast(
                urgencyInfo.outcomeId(),
                outcomeName,
                projectedTargetDate,
                projectedProgressPct,
                effectiveVelocity,
                confidenceScore,
                confidenceBand,
                forecastStatus,
                inputs,
                factors,
                recommendations.stream().distinct().toList());
    }

    private String resolveOutcomeName(UUID orgId, UrgencyInfo urgencyInfo) {
        if (urgencyInfo.outcomeName() != null && !urgencyInfo.outcomeName().isBlank()) {
            return urgencyInfo.outcomeName();
        }
        return rcdoClient.getOutcome(orgId, urgencyInfo.outcomeId())
                .map(RcdoOutcomeDetail::outcomeName)
                .orElse(urgencyInfo.outcomeId().toString());
    }

    private BigDecimal scheduleMultiplier(
            BigDecimal currentProgress,
            BigDecimal expectedProgress,
            List<ForecastFactorResponse> factors,
            List<String> recommendations) {
        if (expectedProgress == null || expectedProgress.compareTo(BigDecimal.ZERO) <= 0) {
            factors.add(factor("schedule", "Limited schedule baseline", 0.50,
                    "Expected progress is not yet available, so schedule adherence is estimated from sparse target-date data."));
            return new BigDecimal("0.95");
        }

        BigDecimal progressGap = currentProgress.subtract(expectedProgress).setScale(2, RoundingMode.HALF_UP);
        if (progressGap.compareTo(new BigDecimal("10.00")) >= 0) {
            factors.add(factor("schedule", "Ahead of expected progress", 0.90,
                    "Current progress is " + progressGap + " points ahead of the expected trajectory."));
            return new BigDecimal("1.10");
        }
        if (progressGap.compareTo(new BigDecimal("-15.00")) <= 0) {
            factors.add(factor("schedule", "Materially behind plan", 0.15,
                    "Current progress is " + progressGap.abs() + " points behind the expected trajectory."));
            recommendations.add("Reduce blockers or rescope the outcome because progress is materially behind the expected trajectory.");
            return new BigDecimal("0.70");
        }
        if (progressGap.compareTo(BigDecimal.ZERO) < 0) {
            factors.add(factor("schedule", "Slightly behind plan", 0.35,
                    "Current progress is " + progressGap.abs() + " points behind the expected trajectory."));
            recommendations.add("Review near-term milestones to close the schedule gap before it compounds.");
            return new BigDecimal("0.88");
        }

        factors.add(factor("schedule", "Tracking expected progress", 0.70,
                "Current progress is broadly aligned with the expected trajectory."));
        return BigDecimal.ONE;
    }

    private BigDecimal coverageMultiplier(
            ForecastAnalyticsProvider.OutcomeCoverageHistory history,
            List<ForecastFactorResponse> factors,
            List<String> recommendations) {
        if (history.weeks().isEmpty()) {
            factors.add(factor("coverage", "No coverage history", 0.20,
                    "No recent outcome coverage history is available from analytics materialized views."));
            recommendations.add("Reconfirm weekly commitments mapped to this outcome so the forecast has a usable delivery signal.");
            return new BigDecimal("0.90");
        }

        double avgCommits = history.weeks().stream()
                .mapToInt(ForecastAnalyticsProvider.OutcomeCoveragePoint::commitCount)
                .average()
                .orElse(0.0);
        String trend = history.trendDirection() == null ? "STABLE" : history.trendDirection();

        if ("FALLING".equalsIgnoreCase(trend)) {
            factors.add(factor("coverage", "Coverage trend is falling", 0.20,
                    "Recent outcome coverage is falling with an average of "
                            + round(avgCommits, 1) + " commits per week."));
            recommendations.add("Stabilize weekly coverage on this outcome; recent mapped work is declining.");
            return avgCommits < 1.5 ? new BigDecimal("0.70") : new BigDecimal("0.82");
        }
        if ("RISING".equalsIgnoreCase(trend)) {
            factors.add(factor("coverage", "Coverage trend is rising", 0.80,
                    "Recent outcome coverage is rising with an average of "
                            + round(avgCommits, 1) + " commits per week."));
            return avgCommits < 1.0 ? new BigDecimal("1.00") : new BigDecimal("1.08");
        }

        factors.add(factor("coverage", "Coverage trend is stable", avgCommits >= 1.0 ? 0.65 : 0.45,
                "Recent outcome coverage is stable with an average of "
                        + round(avgCommits, 1) + " commits per week."));
        if (avgCommits < 1.0) {
            recommendations.add("Consider assigning more explicit weekly work to this outcome; coverage is stable but thin.");
            return new BigDecimal("0.92");
        }
        return BigDecimal.ONE;
    }

    private CapacitySignal capacitySignal(
            UUID orgId,
            List<UUID> contributorIds,
            List<ForecastFactorResponse> factors,
            List<String> recommendations) {
        int profileCount = 0;
        int strainedCount = 0;

        for (UUID contributorId : contributorIds) {
            Optional<CapacityProfileProvider.CapacityProfileSnapshot> profile =
                    capacityProfileProvider.getLatestProfile(orgId, contributorId);
            if (profile.isEmpty()) {
                continue;
            }
            profileCount++;
            CapacityProfileProvider.CapacityProfileSnapshot snapshot = profile.get();
            BigDecimal bias = snapshot.estimationBias();
            if ((bias != null && bias.compareTo(new BigDecimal("1.20")) > 0)
                    || "LOW".equalsIgnoreCase(snapshot.confidenceLevel())) {
                strainedCount++;
            }
        }

        if (profileCount == 0) {
            factors.add(factor("capacity", "No contributor capacity profiles", 0.40,
                    "No recent capacity profiles were available for contributors mapped to this outcome."));
            return new CapacitySignal(new BigDecimal("0.96"), 0, 0);
        }

        double strainRatio = (double) strainedCount / (double) profileCount;
        if (strainRatio >= 0.5d) {
            factors.add(factor("capacity", "Contributor capacity risk", 0.25,
                    strainedCount + " of " + profileCount
                            + " contributors show low-confidence or overcommit-prone capacity profiles."));
            recommendations.add("Review contributor capacity; a large share of owners show overcommit risk signals.");
            return new CapacitySignal(new BigDecimal("0.82"), profileCount, strainedCount);
        }

        factors.add(factor("capacity", "Contributor capacity is broadly healthy", 0.70,
                profileCount + " contributor capacity profiles were available and only " + strainedCount
                        + " show elevated strain signals."));
        return new CapacitySignal(new BigDecimal("1.00"), profileCount, strainedCount);
    }

    private PredictionSignalSummary predictionSignal(
            UUID orgId,
            List<UUID> contributorIds,
            List<ForecastFactorResponse> factors,
            List<String> recommendations) {
        int carryForwardRiskCount = 0;
        int predictionCoverageCount = 0;

        for (UUID contributorId : contributorIds) {
            List<PredictionDataProvider.PredictionSignal> predictions =
                    predictionDataProvider.getUserPredictions(orgId, contributorId);
            if (!predictions.isEmpty()) {
                predictionCoverageCount++;
            }
            boolean risky = predictions.stream().anyMatch(signal ->
                    signal.likely() && "CARRY_FORWARD".equalsIgnoreCase(signal.type()));
            if (risky) {
                carryForwardRiskCount++;
            }
        }

        if (predictionCoverageCount == 0) {
            factors.add(factor("prediction", "No carry-forward prediction coverage", 0.45,
                    "No recent contributor prediction signals were available for this outcome."));
            return new PredictionSignalSummary(new BigDecimal("0.97"), 0, 0);
        }

        double riskRatio = (double) carryForwardRiskCount / (double) Math.max(1, contributorIds.size());
        if (riskRatio >= 0.34d) {
            factors.add(factor("prediction", "Carry-forward risk detected", 0.20,
                    carryForwardRiskCount + " contributor(s) on this outcome currently have likely carry-forward risk signals."));
            recommendations.add("Check whether this outcome's near-term work is slipping; contributor carry-forward risk is elevated.");
            return new PredictionSignalSummary(new BigDecimal("0.80"), predictionCoverageCount, carryForwardRiskCount);
        }

        factors.add(factor("prediction", "Carry-forward risk is limited", 0.65,
                "Contributor prediction coverage is available and only " + carryForwardRiskCount
                        + " contributor(s) show likely carry-forward risk."));
        return new PredictionSignalSummary(BigDecimal.ONE, predictionCoverageCount, carryForwardRiskCount);
    }

    private BigDecimal estimateObservedVelocity(
            BigDecimal currentProgress,
            LocalDate targetDate,
            long daysRemaining,
            BigDecimal expectedProgress,
            List<ForecastFactorResponse> factors) {
        if (expectedProgress == null || expectedProgress.compareTo(BigDecimal.ZERO) <= 0
                || expectedProgress.compareTo(ONE_HUNDRED) >= 0 || daysRemaining < 0) {
            BigDecimal fallback = new BigDecimal("2.5000");
            factors.add(factor("velocity", "Fallback velocity baseline", 0.40,
                    "Observed velocity used a conservative fallback because the target-date window is nearly complete or sparse."));
            return fallback;
        }

        BigDecimal expectedRatio = expectedProgress.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal remainingRatio = BigDecimal.ONE.subtract(expectedRatio);
        if (remainingRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("2.5000");
        }

        BigDecimal totalDays = BigDecimal.valueOf(daysRemaining)
                .divide(remainingRatio, 2, RoundingMode.HALF_UP);
        BigDecimal elapsedDays = totalDays.multiply(expectedRatio);
        BigDecimal elapsedWeeks = elapsedDays.divide(new BigDecimal("7.0"), 4, RoundingMode.HALF_UP);
        if (elapsedWeeks.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("2.5000");
        }

        BigDecimal observedVelocity = currentProgress.divide(elapsedWeeks, 4, RoundingMode.HALF_UP);
        factors.add(factor("velocity", "Observed delivery velocity", 0.60,
                "Estimated from progress against the target window at " + observedVelocity + " percentage points per week."));
        return observedVelocity.max(MIN_EFFECTIVE_VELOCITY);
    }

    private BigDecimal confidenceScore(
            UrgencyInfo urgencyInfo,
            ForecastAnalyticsProvider.OutcomeCoverageHistory history,
            List<UUID> contributorIds,
            CapacitySignal capacitySignal,
            PredictionSignalSummary predictionSummary) {
        BigDecimal score = new BigDecimal("0.30");
        if (urgencyInfo.targetDate() != null) {
            score = score.add(new BigDecimal("0.20"));
        }
        if (urgencyInfo.expectedProgressPct() != null) {
            score = score.add(new BigDecimal("0.10"));
        }
        if (history.weeks().size() >= 4) {
            score = score.add(new BigDecimal("0.15"));
        }
        if (contributorIds.size() >= 2) {
            score = score.add(new BigDecimal("0.10"));
        }
        if (capacitySignal.profileCount() > 0) {
            score = score.add(new BigDecimal("0.05"));
        }
        if (predictionSummary.predictionCoverageCount() > 0) {
            score = score.add(new BigDecimal("0.05"));
        }
        if (capacitySignal.strainedCount() == 0) {
            score = score.add(new BigDecimal("0.03"));
        }
        if (predictionSummary.carryForwardRiskCount() == 0) {
            score = score.add(new BigDecimal("0.02"));
        }
        return score.min(new BigDecimal("0.95"));
    }

    private String toConfidenceBand(BigDecimal confidenceScore) {
        if (confidenceScore.compareTo(new BigDecimal("0.75")) >= 0) {
            return CONFIDENCE_HIGH;
        }
        if (confidenceScore.compareTo(new BigDecimal("0.50")) >= 0) {
            return CONFIDENCE_MEDIUM;
        }
        return CONFIDENCE_LOW;
    }

    private String determineStatus(LocalDate projectedTargetDate, LocalDate targetDate) {
        if (projectedTargetDate == null) {
            return STATUS_NO_DATA;
        }
        long deltaDays = ChronoUnit.DAYS.between(targetDate, projectedTargetDate);
        if (deltaDays <= 0) {
            return STATUS_ON_TRACK;
        }
        if (deltaDays <= 14) {
            return STATUS_NEEDS_ATTENTION;
        }
        return STATUS_AT_RISK;
    }

    private List<UUID> findRecentContributorIds(UUID orgId, UUID outcomeId, int weeks) {
        LocalDate lookbackStart = LocalDate.now(clock).minusWeeks(Math.max(1, weeks - 1L));
        String sql = """
                SELECT DISTINCT wp.owner_user_id
                FROM weekly_commits wc
                JOIN weekly_plans wp
                  ON wp.id = wc.weekly_plan_id
                 AND wp.org_id = wc.org_id
                WHERE wc.org_id = :orgId
                  AND wc.outcome_id = :outcomeId
                  AND wp.week_start_date >= :lookbackStart
                  AND wc.deleted_at IS NULL
                  AND wp.deleted_at IS NULL
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", orgId)
                .addValue("outcomeId", outcomeId)
                .addValue("lookbackStart", lookbackStart);
        return namedJdbc.query(sql, params, (rs, rowNum) -> (UUID) rs.getObject("owner_user_id"));
    }

    private OutcomeForecastResponse toResponse(LatestForecastEntity entity) {
        Map<String, Object> details = readJson(entity.getForecastDetailsJson());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> factorMaps = (List<Map<String, Object>>) details.getOrDefault("contributingFactors", List.of());
        List<ForecastFactorResponse> factors = factorMaps.stream()
                .map(map -> new ForecastFactorResponse(
                        String.valueOf(map.get("type")),
                        String.valueOf(map.get("label")),
                        new BigDecimal(String.valueOf(map.get("score"))).setScale(2, RoundingMode.HALF_UP),
                        String.valueOf(map.get("detail"))))
                .toList();
        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) details.getOrDefault("recommendations", List.of());
        String outcomeName = String.valueOf(details.getOrDefault("outcomeName", entity.getOutcomeId().toString()));
        String confidenceBand = String.valueOf(details.getOrDefault("confidenceBand", toConfidenceBand(entity.getConfidenceScore())));
        Object rawTargetDate = readJson(entity.getForecastInputsJson()).get("targetDate");
        LocalDate targetDate = readLocalDate(rawTargetDate);
        return new OutcomeForecastResponse(
                entity.getOutcomeId().toString(),
                outcomeName,
                targetDate,
                entity.getProjectedTargetDate(),
                entity.getProjectedProgressPct(),
                entity.getProjectedVelocity(),
                entity.getConfidenceScore(),
                confidenceBand,
                entity.getForecastStatus(),
                entity.getModelVersion(),
                factors,
                recommendations,
                entity.getComputedAt() != null ? entity.getComputedAt().toString() : null);
    }

    private ForecastFactorResponse factor(String type, String label, double score, String detail) {
        return factor(type, label, BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP), detail);
    }

    private ForecastFactorResponse factor(String type, String label, BigDecimal score, String detail) {
        return new ForecastFactorResponse(type, label, score.setScale(2, RoundingMode.HALF_UP), detail);
    }

    private BigDecimal normalizedPercent(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.min(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private UUID outcomeId(UrgencyInfo urgencyInfo) {
        return urgencyInfo.outcomeId();
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize forecast payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (JsonProcessingException e) {
            LOG.warn("TargetDateForecastService: failed to deserialize persisted forecast payload", e);
            return Map.of();
        }
    }

    private LocalDate readLocalDate(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof String value && !value.isBlank()) {
            return LocalDate.parse(value);
        }
        try {
            return objectMapper.convertValue(rawValue, LocalDate.class);
        } catch (IllegalArgumentException e) {
            LOG.warn("TargetDateForecastService: failed to deserialize persisted target date value {}", rawValue, e);
            return null;
        }
    }

    private String round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    record ComputedForecast(
            UUID outcomeId,
            String outcomeName,
            LocalDate projectedTargetDate,
            BigDecimal projectedProgressPct,
            BigDecimal projectedVelocity,
            BigDecimal confidenceScore,
            String confidenceBand,
            String forecastStatus,
            Map<String, Object> inputs,
            List<ForecastFactorResponse> factors,
            List<String> recommendations) {
    }

    record CapacitySignal(BigDecimal multiplier, int profileCount, int strainedCount) {
    }

    record PredictionSignalSummary(BigDecimal multiplier, int predictionCoverageCount, int carryForwardRiskCount) {
    }
}
