package com.weekly.urgency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.rcdo.RcdoClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Core service for computing urgency bands and progress percentages for RCDO outcomes.
 *
 * <p>Implements the multi-signal progress model and urgency band algorithm from the
 * phase-3 spec:
 * <ul>
 *   <li>METRIC progress: {@code currentValue / targetValue}</li>
 *   <li>MILESTONE progress: weighted sum of completed milestones from JSONB</li>
 *   <li>ACTIVITY progress: commit-coverage velocity over the last 8 locked-plan weeks</li>
 *   <li>Composite weighting: METRIC 0.6/activity 0.4; MILESTONE 0.5/activity 0.5; ACTIVITY 1.0</li>
 * </ul>
 *
 * <p>Urgency bands in ascending severity: {@code NO_TARGET}, {@code ON_TRACK},
 * {@code NEEDS_ATTENTION}, {@code AT_RISK}, {@code CRITICAL}.
 *
 * <p>Uses an injectable {@link Clock} for testability, following the
 * {@link com.weekly.cadence.CadenceReminderJob} pattern.
 */
@Service
@Transactional
public class UrgencyComputeService {

    private static final Logger LOG = LoggerFactory.getLogger(UrgencyComputeService.class);

    /** Urgency band constants (phase-3 spec §3). */
    static final String BAND_NO_TARGET = "NO_TARGET";
    static final String BAND_ON_TRACK = "ON_TRACK";
    static final String BAND_NEEDS_ATTENTION = "NEEDS_ATTENTION";
    static final String BAND_AT_RISK = "AT_RISK";
    static final String BAND_CRITICAL = "CRITICAL";

    /** Plan states that count as "locked or better" for activity tracking. */
    private static final List<PlanState> LOCKED_PLUS_STATES = List.of(
            PlanState.LOCKED,
            PlanState.RECONCILING,
            PlanState.RECONCILED,
            PlanState.CARRY_FORWARD);

    /** Number of weeks in the activity tracking window (phase-3 spec §2). */
    private static final int ACTIVITY_WINDOW_WEEKS = 8;

    /**
     * Gap threshold below which an outcome is considered ON_TRACK
     * (expectedProgress − actualProgress &lt; 0.10).
     */
    private static final double GAP_ON_TRACK = 0.10;

    /**
     * Gap threshold below which an outcome is NEEDS_ATTENTION
     * (expectedProgress − actualProgress &lt; 0.25).
     */
    private static final double GAP_NEEDS_ATTENTION = 0.25;

    /** Days-remaining threshold that triggers CRITICAL when progress is low. */
    private static final long CRITICAL_DAYS_REMAINING = 30;

    /** Actual-progress threshold (ratio) below which the near-deadline rule fires. */
    private static final double CRITICAL_PROGRESS_THRESHOLD = 0.50;

    /** Velocity bonus awarded when recent coverage exceeds earlier coverage. */
    private static final double VELOCITY_BONUS = 0.10;

    private final OutcomeMetadataRepository metadataRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyPlanRepository planRepository;
    private final RcdoClient rcdoClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Production constructor — Spring auto-wires all dependencies and uses
     * {@link Clock#systemUTC()} for date arithmetic.
     */
    @Autowired
    public UrgencyComputeService(
            OutcomeMetadataRepository metadataRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyPlanRepository planRepository,
            RcdoClient rcdoClient,
            ObjectMapper objectMapper
    ) {
        this(metadataRepository, commitRepository, planRepository,
                rcdoClient, objectMapper, Clock.systemUTC());
    }

    /**
     * Package-private constructor for unit tests — allows injecting a fixed
     * {@link Clock} so date-sensitive assertions are deterministic.
     */
    UrgencyComputeService(
            OutcomeMetadataRepository metadataRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyPlanRepository planRepository,
            RcdoClient rcdoClient,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.metadataRepository = metadataRepository;
        this.commitRepository = commitRepository;
        this.planRepository = planRepository;
        this.rcdoClient = rcdoClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Recomputes progress and urgency for every tracked outcome in the given org.
     *
     * <p>Each entity is updated in-place: {@code progressPct}, {@code urgencyBand},
     * and {@code lastComputedAt} are refreshed and persisted.  Failures for individual
     * outcomes are logged and skipped so that a single bad record does not abort the
     * entire batch.
     *
     * @param orgId the organisation ID to process
     */
    public void computeUrgencyForOrg(UUID orgId) {
        List<OutcomeMetadataEntity> all = metadataRepository.findByOrgId(orgId);
        LOG.debug("UrgencyComputeService: recomputing {} outcome(s) for org {}", all.size(), orgId);

        for (OutcomeMetadataEntity entity : all) {
            try {
                computeAndSaveOutcome(entity);
            } catch (Exception ex) {
                LOG.warn(
                        "UrgencyComputeService: failed to compute urgency for outcome {} in org {}: {}",
                        entity.getOutcomeId(), orgId, ex.getMessage(), ex);
            }
        }
    }

    /**
     * Recomputes progress and urgency for a single tracked outcome.
     *
     * <p>Returns the freshly persisted entity when the outcome exists for the
     * supplied organisation; otherwise returns {@link Optional#empty()}.
     *
     * @param orgId the organisation ID that owns the outcome
     * @param outcomeId the outcome ID to recompute
     * @return the recomputed and persisted entity, if found
     */
    public Optional<OutcomeMetadataEntity> computeUrgencyForOutcome(UUID orgId, UUID outcomeId) {
        return metadataRepository.findByOrgIdAndOutcomeId(orgId, outcomeId)
                .map(this::computeAndSaveOutcome);
    }

    /**
     * Computes the composite progress percentage (0–100) for the given outcome metadata.
     *
     * <p>Progress model (phase-3 spec §2):
     * <ul>
     *   <li><b>METRIC</b>: {@code (currentValue / targetValue) × 100},
     *       blended with 60 % metric + 40 % activity.</li>
     *   <li><b>MILESTONE</b>: weighted completion of JSONB milestones (DONE = full,
     *       IN_PROGRESS = half), blended with 50 % milestone + 50 % activity.</li>
     *   <li><b>ACTIVITY</b> (default): pure commit-coverage velocity × 100.</li>
     * </ul>
     *
     * @param metadata the outcome metadata entity
     * @return progress as a percentage in [0, 100], scaled to 2 decimal places
     */
    public BigDecimal computeProgressPct(OutcomeMetadataEntity metadata) {
        String type = metadata.getProgressType();
        if (type == null) {
            type = "ACTIVITY";
        }

        double activityScore = computeActivityProgress(
                metadata.getOrgId(), metadata.getOutcomeId());

        switch (type) {
            case "METRIC" -> {
                double metricScore = computeMetricProgress(metadata);
                return toBigDecimalPct(metricScore * 0.6 + activityScore * 0.4);
            }
            case "MILESTONE" -> {
                double milestoneScore = computeMilestoneProgress(metadata);
                return toBigDecimalPct(milestoneScore * 0.5 + activityScore * 0.5);
            }
            default -> {
                return toBigDecimalPct(activityScore);
            }
        }
    }

    /**
     * Determines the urgency band for the given outcome metadata.
     *
     * <p>Algorithm (phase-3 spec §3):
     * <ol>
     *   <li><b>NO_TARGET</b> — {@code targetDate} is null.</li>
     *   <li><b>CRITICAL</b> — {@code targetDate} is today or earlier (due/past due).</li>
     *   <li>Compute linear {@code expectedProgress = daysElapsed / daysTotal}
     *       where the tracking period starts at {@code createdAt}.</li>
     *   <li>Compute {@code gap = expectedProgress − actualProgress} (both in [0, 1]).</li>
     *   <li><b>ON_TRACK</b> if {@code gap < 0.10}.</li>
     *   <li><b>NEEDS_ATTENTION</b> if {@code gap < 0.25}.</li>
     *   <li><b>CRITICAL</b> if {@code daysRemaining < 30} and
     *       {@code actualProgress < 0.50}.</li>
     *   <li><b>AT_RISK</b> otherwise.</li>
     * </ol>
     *
     * <p>Note: the {@code progressPct} stored on the entity should be refreshed by
     * {@link #computeProgressPct} before calling this method within the same
     * {@link #computeUrgencyForOrg} batch cycle.
     *
     * @param metadata the outcome metadata entity (with {@code progressPct} already set)
     * @return one of {@link #BAND_NO_TARGET}, {@link #BAND_ON_TRACK},
     *         {@link #BAND_NEEDS_ATTENTION}, {@link #BAND_AT_RISK}, or {@link #BAND_CRITICAL}
     */
    public String computeUrgencyBand(OutcomeMetadataEntity metadata) {
        LocalDate targetDate = metadata.getTargetDate();
        if (targetDate == null) {
            return BAND_NO_TARGET;
        }

        LocalDate today = LocalDate.now(clock);
        if (!targetDate.isAfter(today)) {
            return BAND_CRITICAL;
        }

        long daysRemaining = ChronoUnit.DAYS.between(today, targetDate);

        // Tracking period: from the date the row was created to the target date.
        LocalDate startDate = metadata.getCreatedAt()
                .atZone(clock.getZone())
                .toLocalDate();
        long daysTotal = ChronoUnit.DAYS.between(startDate, targetDate);

        // If the target is on or before the creation date, treat as critical.
        if (daysTotal <= 0) {
            return BAND_CRITICAL;
        }

        long daysElapsed = ChronoUnit.DAYS.between(startDate, today);
        double expectedProgress = clamp((double) daysElapsed / (double) daysTotal);

        double actualProgress = 0.0;
        if (metadata.getProgressPct() != null) {
            actualProgress = metadata.getProgressPct().doubleValue() / 100.0;
        }

        double gap = expectedProgress - actualProgress;

        if (gap < GAP_ON_TRACK) {
            return BAND_ON_TRACK;
        }
        if (gap < GAP_NEEDS_ATTENTION) {
            return BAND_NEEDS_ATTENTION;
        }
        if (daysRemaining < CRITICAL_DAYS_REMAINING && actualProgress < CRITICAL_PROGRESS_THRESHOLD) {
            return BAND_CRITICAL;
        }
        return BAND_AT_RISK;
    }

    /**
     * Computes a commit-coverage velocity score in [0, 1] for the given outcome,
     * based on locked-or-better plans over the last {@value ACTIVITY_WINDOW_WEEKS} weeks.
     *
     * <p>Steps:
     * <ol>
     *   <li>Fetch all plans for the org whose {@code weekStartDate} falls within
     *       the last 8 weeks.</li>
     *   <li>Keep only plans in a locked-or-better state (LOCKED, RECONCILING,
     *       RECONCILED, CARRY_FORWARD).</li>
     *   <li>Find commits in those plans that reference this {@code outcomeId}.</li>
     *   <li>Compute {@code coverageRatio = weeksWithOutcomeCommit / totalLockedWeeks}.</li>
     *   <li>Add a {@value VELOCITY_BONUS} bonus if the most recent half of the
     *       window had better coverage than the earlier half (improving trend).</li>
     * </ol>
     *
     * @param orgId     the organisation ID
     * @param outcomeId the outcome ID to track
     * @return activity score in [0, 1]
     */
    public double computeActivityProgress(UUID orgId, UUID outcomeId) {
        LocalDate windowEnd = LocalDate.now(clock).with(DayOfWeek.MONDAY);
        LocalDate windowStart = windowEnd.minusWeeks(ACTIVITY_WINDOW_WEEKS - 1L);

        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndWeekStartDateBetween(orgId, windowStart, windowEnd);

        List<UUID> lockedPlanIds = plans.stream()
                .filter(p -> LOCKED_PLUS_STATES.contains(p.getState()))
                .map(WeeklyPlanEntity::getId)
                .toList();

        if (lockedPlanIds.isEmpty()) {
            return 0.0;
        }

        List<WeeklyCommitEntity> commits =
                commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, lockedPlanIds);

        // Collect plan IDs that have at least one commit linked to this outcome.
        Set<UUID> planIdsWithOutcomeCommit = commits.stream()
                .filter(c -> outcomeId.equals(c.getOutcomeId()))
                .map(WeeklyCommitEntity::getWeeklyPlanId)
                .collect(Collectors.toSet());

        if (planIdsWithOutcomeCommit.isEmpty()) {
            return 0.0;
        }

        // Build a set of weekStartDates covered by this outcome across locked plans.
        Map<LocalDate, Boolean> coveredWeeks = plans.stream()
                .filter(p -> LOCKED_PLUS_STATES.contains(p.getState()))
                .filter(p -> planIdsWithOutcomeCommit.contains(p.getId()))
                .collect(Collectors.toMap(
                        WeeklyPlanEntity::getWeekStartDate,
                        p -> Boolean.TRUE,
                        (a, b) -> Boolean.TRUE));

        // All distinct locked-plan weeks in the window.
        List<LocalDate> sortedLockedWeeks = plans.stream()
                .filter(p -> LOCKED_PLUS_STATES.contains(p.getState()))
                .map(WeeklyPlanEntity::getWeekStartDate)
                .distinct()
                .sorted()
                .toList();

        long totalWeeks = sortedLockedWeeks.size();
        if (totalWeeks == 0) {
            return 0.0;
        }

        long coveredWeekCount = coveredWeeks.size();
        double coverageRatio = (double) coveredWeekCount / (double) totalWeeks;

        // Velocity bonus: recent half outperforms earlier half.
        double velocityBonus = computeVelocityBonus(sortedLockedWeeks, coveredWeeks);

        return clamp(coverageRatio + velocityBonus);
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Computes, stamps, and persists urgency fields for a single outcome entity.
     */
    private OutcomeMetadataEntity computeAndSaveOutcome(OutcomeMetadataEntity entity) {
        BigDecimal progressPct = computeProgressPct(entity);
        entity.setProgressPct(progressPct);

        String band = computeUrgencyBand(entity);
        entity.setUrgencyBand(band);
        entity.setLastComputedAt(Instant.now(clock));

        return metadataRepository.save(entity);
    }

    /**
     * Returns a metric progress ratio in [0, 1].
     * Returns 0.0 if {@code targetValue} is null, zero, or {@code currentValue} is null.
     */
    private double computeMetricProgress(OutcomeMetadataEntity metadata) {
        BigDecimal target = metadata.getTargetValue();
        BigDecimal current = metadata.getCurrentValue();
        if (target == null || current == null || target.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return clamp(current.doubleValue() / target.doubleValue());
    }

    /**
     * Returns a milestone progress ratio in [0, 1] by parsing the JSONB milestones field.
     *
     * <p>Expected JSON format: {@code [{"status":"DONE","weight":1.0}, ...]}.
     * Each milestone may omit {@code weight} (defaults to 1.0).
     * DONE = full weight contribution; IN_PROGRESS = half weight contribution.
     */
    private double computeMilestoneProgress(OutcomeMetadataEntity metadata) {
        String json = metadata.getMilestones();
        if (json == null || json.isBlank()) {
            return 0.0;
        }
        try {
            List<Map<String, Object>> milestones =
                    objectMapper.readValue(json, new TypeReference<>() {});
            double totalWeight = 0.0;
            double completedWeight = 0.0;
            for (Map<String, Object> ms : milestones) {
                Object rawWeight = ms.get("weight");
                Object rawStatus = ms.get("status");
                double weight = rawWeight instanceof Number n ? n.doubleValue() : 1.0;
                String status = rawStatus != null ? rawStatus.toString() : "";
                totalWeight += weight;
                if ("DONE".equalsIgnoreCase(status)) {
                    completedWeight += weight;
                } else if ("IN_PROGRESS".equalsIgnoreCase(status)) {
                    completedWeight += weight * 0.5;
                }
            }
            if (totalWeight <= 0.0) {
                return 0.0;
            }
            return clamp(completedWeight / totalWeight);
        } catch (JsonProcessingException ex) {
            LOG.warn("UrgencyComputeService: failed to parse milestones JSON for outcome {}: {}",
                    metadata.getOutcomeId(), ex.getMessage());
            return 0.0;
        }
    }

    /**
     * Computes a 0.0 or {@value VELOCITY_BONUS} bonus based on whether the second
     * half of the locked-week window has better outcome coverage than the first half.
     */
    private double computeVelocityBonus(
            List<LocalDate> sortedLockedWeeks,
            Map<LocalDate, Boolean> coveredWeeks
    ) {
        int total = sortedLockedWeeks.size();
        if (total < 4) {
            return 0.0;
        }
        int midpoint = total / 2;
        List<LocalDate> earlier = sortedLockedWeeks.subList(0, midpoint);
        List<LocalDate> recent = sortedLockedWeeks.subList(midpoint, total);

        double earlierRatio = coveredRatio(earlier, coveredWeeks);
        double recentRatio = coveredRatio(recent, coveredWeeks);

        return recentRatio > earlierRatio ? VELOCITY_BONUS : 0.0;
    }

    /** Returns the proportion of weeks in {@code weeks} that appear in {@code covered}. */
    private double coveredRatio(List<LocalDate> weeks, Map<LocalDate, Boolean> covered) {
        if (weeks.isEmpty()) {
            return 0.0;
        }
        long count = weeks.stream().filter(covered::containsKey).count();
        return (double) count / weeks.size();
    }

    /** Clamps a double value to [0.0, 1.0]. */
    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Converts a [0, 1] ratio to a BigDecimal percentage [0, 100] at scale 2. */
    private BigDecimal toBigDecimalPct(double ratio) {
        return BigDecimal.valueOf(ratio * 100.0).setScale(2, RoundingMode.HALF_UP);
    }
}
