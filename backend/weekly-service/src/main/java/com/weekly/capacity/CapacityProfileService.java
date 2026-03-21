package com.weekly.capacity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for computing and persisting capacity profiles for users.
 *
 * <p>A capacity profile aggregates historical estimated and actual hours data
 * across a configurable rolling window of weeks, producing:
 * <ul>
 *   <li>Average estimated and actual hours per week</li>
 *   <li>Estimation bias ratio (actual / estimated)</li>
 *   <li>Realistic weekly capacity cap (p50 of actual weekly totals)</li>
 *   <li>Per-category bias breakdown</li>
 *   <li>Per-priority completion rates</li>
 *   <li>Confidence level based on weeks of data available</li>
 * </ul>
 */
@Service
public class CapacityProfileService {

    private static final Logger LOG = LoggerFactory.getLogger(CapacityProfileService.class);

    static final int MIN_WEEKS = 1;
    static final int MAX_WEEKS = 26;
    private static final int INTERMEDIATE_SCALE = 4;

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository actualRepository;
    private final CapacityProfileRepository profileRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CapacityProfileService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository,
            CapacityProfileRepository profileRepository,
            ObjectMapper objectMapper) {
        this(
                planRepository,
                commitRepository,
                actualRepository,
                profileRepository,
                objectMapper,
                Clock.systemUTC());
    }

    CapacityProfileService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository,
            CapacityProfileRepository profileRepository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.actualRepository = actualRepository;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Computes (or recomputes) the capacity profile for the given user and persists it.
     *
     * <p>The rolling window covers the last {@code weeks} Mondays up to and including
     * the current week's Monday. For each plan found in that window the method totals
     * estimated and actual hours, then derives summary statistics.
     *
     * @param orgId  the organisation ID
     * @param userId the user whose profile to compute
     * @param weeks  number of weeks to look back (clamped to [{@value #MIN_WEEKS},
     *               {@value #MAX_WEEKS}])
     * @return the saved (upserted) {@link CapacityProfileEntity}
     */
    @Transactional
    public CapacityProfileEntity computeProfile(UUID orgId, UUID userId, int weeks) {
        int clampedWeeks = clampWeeks(weeks);
        LocalDate windowEnd = LocalDate.now(clock).with(DayOfWeek.MONDAY);
        LocalDate windowStart = windowEnd.minusWeeks(clampedWeeks - 1);

        // 1. Fetch user plans in window
        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, windowEnd);

        // 2. Load commits for all plans
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> allCommits = planIds.isEmpty()
                ? List.of()
                : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        Map<UUID, List<WeeklyCommitEntity>> commitsByPlan = allCommits.stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        // 3. Load actuals for all commits
        List<UUID> commitIds = allCommits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsByCommitId = commitIds.isEmpty()
                ? Map.of()
                : actualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                        .collect(Collectors.toMap(
                                WeeklyCommitActualEntity::getCommitId, Function.identity()));

        // 4. Compute per-week hour totals across all plans
        List<BigDecimal> weeklyEstimated = new ArrayList<>();
        List<BigDecimal> weeklyActual = new ArrayList<>();
        for (WeeklyPlanEntity plan : plans) {
            List<WeeklyCommitEntity> commits =
                    commitsByPlan.getOrDefault(plan.getId(), List.of());
            BigDecimal weekEst = BigDecimal.ZERO;
            BigDecimal weekAct = BigDecimal.ZERO;
            for (WeeklyCommitEntity commit : commits) {
                if (commit.getEstimatedHours() != null) {
                    weekEst = weekEst.add(commit.getEstimatedHours());
                }
                WeeklyCommitActualEntity actual = actualsByCommitId.get(commit.getId());
                if (actual != null && actual.getActualHours() != null) {
                    weekAct = weekAct.add(actual.getActualHours());
                }
            }
            weeklyEstimated.add(weekEst);
            weeklyActual.add(weekAct);
        }

        // 5. Derive summary metrics
        int weeksAnalyzed = plans.size();
        BigDecimal rawAvgEst = mean(weeklyEstimated);
        BigDecimal rawAvgAct = mean(weeklyActual);
        BigDecimal avgEst = rawAvgEst.setScale(1, RoundingMode.HALF_UP);
        BigDecimal avgAct = rawAvgAct.setScale(1, RoundingMode.HALF_UP);
        BigDecimal estimationBias = rawAvgEst.compareTo(BigDecimal.ZERO) > 0
                ? rawAvgAct.divide(rawAvgEst, 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal realisticCap = median(weeklyActual).setScale(1, RoundingMode.HALF_UP);

        // 6. Compute per-category bias JSON
        String categoryBiasJson = computeCategoryBiasJson(allCommits, actualsByCommitId);

        // 7. Compute per-priority completion JSON
        String priorityCompletionJson = computePriorityCompletionJson(allCommits, actualsByCommitId);

        // 8. Determine confidence level
        String confidenceLevel = determineConfidenceLevel(weeksAnalyzed);

        // 9. Upsert profile
        CapacityProfileEntity profile = profileRepository
                .findByOrgIdAndUserId(orgId, userId)
                .orElseGet(() -> new CapacityProfileEntity(orgId, userId));

        profile.setWeeksAnalyzed(weeksAnalyzed);
        profile.setAvgEstimatedHours(avgEst);
        profile.setAvgActualHours(avgAct);
        profile.setEstimationBias(estimationBias);
        profile.setRealisticWeeklyCap(realisticCap);
        profile.setCategoryBiasJson(categoryBiasJson);
        profile.setPriorityCompletionJson(priorityCompletionJson);
        profile.setConfidenceLevel(confidenceLevel);
        profile.setComputedAt(Instant.now(clock));

        return profileRepository.save(profile);
    }

    /**
     * Returns the most recently computed capacity profile for the given user, if any.
     *
     * @param orgId  the organisation ID
     * @param userId the user ID
     * @return optional capacity profile
     */
    @Transactional(readOnly = true)
    public Optional<CapacityProfileEntity> getProfile(UUID orgId, UUID userId) {
        return profileRepository.findByOrgIdAndUserId(orgId, userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String computeCategoryBiasJson(
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId) {

        List<CategoryBias> result = new ArrayList<>();
        for (CommitCategory category : CommitCategory.values()) {
            List<WeeklyCommitEntity> categoryCommits = commits.stream()
                    .filter(c -> c.getCategory() == category)
                    .toList();
            if (categoryCommits.isEmpty()) {
                continue;
            }
            List<BigDecimal> estimatedValues = categoryCommits.stream()
                    .filter(c -> c.getEstimatedHours() != null)
                    .map(WeeklyCommitEntity::getEstimatedHours)
                    .toList();
            List<BigDecimal> actualValues = categoryCommits.stream()
                    .map(c -> {
                        WeeklyCommitActualEntity a = actualsByCommitId.get(c.getId());
                        return (a != null) ? a.getActualHours() : null;
                    })
                    .filter(v -> v != null)
                    .toList();

            BigDecimal rawAvgEst = mean(estimatedValues);
            BigDecimal rawAvgAct = mean(actualValues);
            BigDecimal avgEst = rawAvgEst.setScale(1, RoundingMode.HALF_UP);
            BigDecimal avgAct = rawAvgAct.setScale(1, RoundingMode.HALF_UP);
            BigDecimal bias = rawAvgEst.compareTo(BigDecimal.ZERO) > 0
                    ? rawAvgAct.divide(rawAvgEst, 2, RoundingMode.HALF_UP)
                    : null;

            result.add(new CategoryBias(category.name(), avgEst, avgAct, bias));
        }
        return toJson(result);
    }

    private String computePriorityCompletionJson(
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId) {

        List<PriorityCompletion> result = new ArrayList<>();
        for (ChessPriority priority : ChessPriority.values()) {
            List<WeeklyCommitEntity> priorityCommits = commits.stream()
                    .filter(c -> c.getChessPriority() == priority)
                    .toList();
            if (priorityCommits.isEmpty()) {
                continue;
            }
            int sampleSize = priorityCommits.size();
            long doneCount = priorityCommits.stream()
                    .filter(c -> {
                        WeeklyCommitActualEntity a = actualsByCommitId.get(c.getId());
                        return a != null && a.getCompletionStatus() == CompletionStatus.DONE;
                    })
                    .count();
            double doneRate = (double) doneCount / sampleSize;

            List<BigDecimal> actualHourValues = priorityCommits.stream()
                    .map(c -> {
                        WeeklyCommitActualEntity a = actualsByCommitId.get(c.getId());
                        return (a != null) ? a.getActualHours() : null;
                    })
                    .filter(v -> v != null)
                    .toList();
            BigDecimal avgHours = mean(actualHourValues).setScale(1, RoundingMode.HALF_UP);

            result.add(new PriorityCompletion(
                    priority.name(),
                    BigDecimal.valueOf(doneRate).setScale(2, RoundingMode.HALF_UP),
                    avgHours,
                    sampleSize));
        }
        return toJson(result);
    }

    private static int clampWeeks(int weeks) {
        return Math.max(MIN_WEEKS, Math.min(MAX_WEEKS, weeks));
    }

    private static String determineConfidenceLevel(int weeksAnalyzed) {
        if (weeksAnalyzed < 4) {
            return "LOW";
        } else if (weeksAnalyzed <= 8) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    /**
     * Computes the arithmetic mean of the given values.
     * Returns {@link BigDecimal#ZERO} for an empty list.
     */
    private static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Computes the median (p50) of the given values.
     * Returns {@link BigDecimal#ZERO} for an empty list.
     */
    private static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(BigDecimal::compareTo);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            BigDecimal lo = sorted.get(size / 2 - 1);
            BigDecimal hi = sorted.get(size / 2);
            return lo.add(hi).divide(BigDecimal.valueOf(2), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize capacity JSON", e);
            return "[]";
        }
    }

    // ── Helper records ────────────────────────────────────────────────────────

    /**
     * Per-category estimation bias data.
     *
     * @param category          the commit category name
     * @param avgEstimatedHours mean estimated hours for commits in this category
     * @param avgActualHours    mean actual hours for commits in this category
     * @param bias              ratio of avgActual / avgEstimated, or {@code null} if uncomputable
     */
    record CategoryBias(
            String category,
            BigDecimal avgEstimatedHours,
            BigDecimal avgActualHours,
            BigDecimal bias) {
    }

    /**
     * Per-priority completion statistics.
     *
     * @param priority   the chess priority name
     * @param doneRate   fraction of commits with status DONE (0.00 – 1.00)
     * @param avgHours   mean actual hours for commits in this priority tier
     * @param sampleSize number of commits in the sample
     */
    record PriorityCompletion(
            String priority,
            BigDecimal doneRate,
            BigDecimal avgHours,
            int sampleSize) {
    }
}
