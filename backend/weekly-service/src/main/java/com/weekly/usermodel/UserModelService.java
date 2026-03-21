package com.weekly.usermodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weekly.plan.domain.ChessPriority;
import com.weekly.plan.domain.CommitCategory;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.ProgressEntryEntity;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.ProgressEntryRepository;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that computes and caches user model snapshots.
 *
 * <p>The user model is a structured, derived representation of a user's
 * planning behaviour over a rolling window of weeks. It is stored in the
 * {@code user_model_snapshots} table and exposed via the user profile API.
 */
@Service
public class UserModelService {

    static final String TREND_IMPROVING = "IMPROVING";
    static final String TREND_STABLE = "STABLE";
    static final String TREND_WORSENING = "WORSENING";

    private static final Logger LOG = LoggerFactory.getLogger(UserModelService.class);
    private static final double TREND_EPSILON = 0.05;

    private final WeeklyPlanRepository planRepository;
    private final WeeklyCommitRepository commitRepository;
    private final WeeklyCommitActualRepository actualRepository;
    private final ProgressEntryRepository progressEntryRepository;
    private final UserModelSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public UserModelService(
            WeeklyPlanRepository planRepository,
            WeeklyCommitRepository commitRepository,
            WeeklyCommitActualRepository actualRepository,
            ProgressEntryRepository progressEntryRepository,
            UserModelSnapshotRepository snapshotRepository,
            ObjectMapper objectMapper
    ) {
        this.planRepository = planRepository;
        this.commitRepository = commitRepository;
        this.actualRepository = actualRepository;
        this.progressEntryRepository = progressEntryRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Computes and upserts the user model snapshot for the given user over the
     * specified rolling window.
     *
     * @param orgId  the organisation ID
     * @param userId the user ID
     * @param weeks  the number of weeks to analyse (window size)
     * @return the saved snapshot entity
     */
    @Transactional
    public UserModelSnapshotEntity computeSnapshot(UUID orgId, UUID userId, int weeks) {
        int effectiveWeeks = Math.max(1, weeks);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate windowStart = today.minusDays((long) effectiveWeeks * 7);

        List<WeeklyPlanEntity> plans =
                planRepository.findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                        orgId, userId, windowStart, today);

        int weeksAnalyzed = plans.size();
        List<UUID> planIds = plans.stream().map(WeeklyPlanEntity::getId).toList();
        List<WeeklyCommitEntity> allCommits = planIds.isEmpty()
                ? List.of()
                : commitRepository.findByOrgIdAndWeeklyPlanIdIn(orgId, planIds);

        Map<UUID, List<WeeklyCommitEntity>> commitsByPlan = allCommits.stream()
                .collect(Collectors.groupingBy(
                        WeeklyCommitEntity::getWeeklyPlanId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<UUID> commitIds = allCommits.stream().map(WeeklyCommitEntity::getId).toList();
        Map<UUID, WeeklyCommitActualEntity> actualsByCommitId = commitIds.isEmpty()
                ? Map.of()
                : actualRepository.findByOrgIdAndCommitIdIn(orgId, commitIds).stream()
                        .collect(Collectors.toMap(
                                WeeklyCommitActualEntity::getCommitId,
                                Function.identity()));

        List<ProgressEntryEntity> allEntries = commitIds.isEmpty()
                ? List.of()
                : progressEntryRepository.findByOrgIdAndCommitIdInOrderByCreatedAtAsc(
                        orgId, commitIds);

        double estimationAccuracy = computeEstimationAccuracy(allCommits, actualsByCommitId);
        double completionReliability = computeCompletionReliability(allCommits, actualsByCommitId);
        double avgCommitsPerWeek = computeAvgCommitsPerWeek(plans, commitsByPlan);
        double avgCarryForwardPerWeek = computeAvgCarryForwardPerWeek(plans, commitsByPlan);
        Map<String, Double> categoryCompletionRates =
                computeCategoryCompletionRates(allCommits, actualsByCommitId);
        Map<String, Double> priorityCompletionRates =
                computePriorityCompletionRates(allCommits, actualsByCommitId);
        List<String> topCategories = computeTopCategories(categoryCompletionRates);
        String typicalPriorityPattern = computeTypicalPriorityPattern(plans, commitsByPlan);
        List<String> recurringCommitTitles = computeRecurringCommitTitles(allCommits);
        double avgCheckInsPerWeek = computeAvgCheckInsPerWeek(plans, allEntries);
        List<String> preferredUpdateDays = computePreferredUpdateDays(allEntries);
        UserProfileResponse.Trends trends =
                computeTrends(plans, commitsByPlan, actualsByCommitId);

        Map<String, Object> modelMap = buildModelMap(
                estimationAccuracy,
                completionReliability,
                avgCommitsPerWeek,
                avgCarryForwardPerWeek,
                topCategories,
                categoryCompletionRates,
                priorityCompletionRates,
                typicalPriorityPattern,
                recurringCommitTitles,
                avgCheckInsPerWeek,
                preferredUpdateDays,
                trends
        );

        String modelJson;
        try {
            modelJson = objectMapper.writeValueAsString(modelMap);
        } catch (JsonProcessingException e) {
            LOG.warn(
                    "Failed to serialise user model for orgId={} userId={}: {}",
                    orgId,
                    userId,
                    e.getMessage()
            );
            modelJson = "{}";
        }

        Optional<UserModelSnapshotEntity> existing =
                snapshotRepository.findByOrgIdAndUserId(orgId, userId);

        UserModelSnapshotEntity snapshot;
        if (existing.isPresent()) {
            snapshot = existing.get();
            snapshot.setWeeksAnalyzed(weeksAnalyzed);
            snapshot.setModelJson(modelJson);
            snapshot.setComputedAt(Instant.now());
        } else {
            snapshot = new UserModelSnapshotEntity(orgId, userId, weeksAnalyzed, modelJson);
        }

        return snapshotRepository.save(snapshot);
    }

    /**
     * Returns the most recently computed snapshot for the given user, mapped to a
     * {@link UserProfileResponse}.
     *
     * @param orgId  the organisation ID
     * @param userId the user ID
     * @return the profile response, or empty if no snapshot exists
     */
    @Transactional(readOnly = true)
    public Optional<UserProfileResponse> getSnapshot(UUID orgId, UUID userId) {
        return snapshotRepository.findByOrgIdAndUserId(orgId, userId)
                .map(entity -> toProfileResponse(entity, userId));
    }

    /**
     * Estimation accuracy is the total confidence attached to DONE outcomes,
     * divided by the number of reconciled commits.
     *
     * <p>Any DONE commit without confidence contributes zero to the numerator.
     */
    double computeEstimationAccuracy(
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId
    ) {
        List<WeeklyCommitEntity> reconciled = commits.stream()
                .filter(commit -> actualsByCommitId.containsKey(commit.getId()))
                .toList();

        if (reconciled.isEmpty()) {
            return 0.0;
        }

        double doneConfidenceTotal = reconciled.stream()
                .filter(commit -> actualsByCommitId.get(commit.getId()).getCompletionStatus()
                        == CompletionStatus.DONE)
                .mapToDouble(commit -> commit.getConfidence() == null
                        ? 0.0
                        : commit.getConfidence().doubleValue())
                .sum();

        return doneConfidenceTotal / reconciled.size();
    }

    /**
     * Completion reliability: DONE count / total reconciled commits.
     */
    double computeCompletionReliability(
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId
    ) {
        long reconciled = commits.stream()
                .filter(commit -> actualsByCommitId.containsKey(commit.getId()))
                .count();

        if (reconciled == 0) {
            return 0.0;
        }

        long done = commits.stream()
                .filter(commit -> actualsByCommitId.containsKey(commit.getId()))
                .filter(commit -> actualsByCommitId.get(commit.getId()).getCompletionStatus()
                        == CompletionStatus.DONE)
                .count();

        return (double) done / reconciled;
    }

    /**
     * Average commits per analysed week.
     */
    double computeAvgCommitsPerWeek(
            List<WeeklyPlanEntity> plans,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlan
    ) {
        if (plans.isEmpty()) {
            return 0.0;
        }
        int totalCommits = plans.stream()
                .mapToInt(plan -> commitsByPlan.getOrDefault(plan.getId(), List.of()).size())
                .sum();
        return (double) totalCommits / plans.size();
    }

    /**
     * Average carry-forward commits per analysed week.
     */
    double computeAvgCarryForwardPerWeek(
            List<WeeklyPlanEntity> plans,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlan
    ) {
        if (plans.isEmpty()) {
            return 0.0;
        }
        int totalCarryForward = plans.stream()
                .mapToInt(plan -> (int) commitsByPlan.getOrDefault(plan.getId(), List.of()).stream()
                        .filter(commit -> commit.getCarriedFromCommitId() != null)
                        .count())
                .sum();
        return (double) totalCarryForward / plans.size();
    }

    /**
     * DONE completion rate per {@link CommitCategory}, for categories that have
     * at least one reconciled commit.
     */
    Map<String, Double> computeCategoryCompletionRates(
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId
    ) {
        Map<String, Long> totalByCategory = new LinkedHashMap<>();
        Map<String, Long> doneByCategory = new LinkedHashMap<>();

        for (WeeklyCommitEntity commit : commits) {
            if (!actualsByCommitId.containsKey(commit.getId()) || commit.getCategory() == null) {
                continue;
            }
            String key = commit.getCategory().name();
            totalByCategory.merge(key, 1L, Long::sum);
            if (actualsByCommitId.get(commit.getId()).getCompletionStatus() == CompletionStatus.DONE) {
                doneByCategory.merge(key, 1L, Long::sum);
            }
        }

        Map<String, Double> rates = new LinkedHashMap<>();
        for (CommitCategory category : CommitCategory.values()) {
            String key = category.name();
            if (!totalByCategory.containsKey(key)) {
                continue;
            }
            long total = totalByCategory.get(key);
            long done = doneByCategory.getOrDefault(key, 0L);
            rates.put(key, total > 0 ? (double) done / total : 0.0);
        }
        return rates;
    }

    /**
     * DONE completion rate per {@link ChessPriority}, for priorities that have
     * at least one reconciled commit.
     */
    Map<String, Double> computePriorityCompletionRates(
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId
    ) {
        Map<String, Long> totalByPriority = new LinkedHashMap<>();
        Map<String, Long> doneByPriority = new LinkedHashMap<>();

        for (WeeklyCommitEntity commit : commits) {
            if (!actualsByCommitId.containsKey(commit.getId()) || commit.getChessPriority() == null) {
                continue;
            }
            String key = commit.getChessPriority().name();
            totalByPriority.merge(key, 1L, Long::sum);
            if (actualsByCommitId.get(commit.getId()).getCompletionStatus() == CompletionStatus.DONE) {
                doneByPriority.merge(key, 1L, Long::sum);
            }
        }

        Map<String, Double> rates = new LinkedHashMap<>();
        for (ChessPriority priority : ChessPriority.values()) {
            String key = priority.name();
            if (!totalByPriority.containsKey(key)) {
                continue;
            }
            long total = totalByPriority.get(key);
            long done = doneByPriority.getOrDefault(key, 0L);
            rates.put(key, total > 0 ? (double) done / total : 0.0);
        }
        return rates;
    }

    /**
     * Returns category names ordered by completion rate descending.
     */
    List<String> computeTopCategories(Map<String, Double> categoryCompletionRates) {
        return categoryCompletionRates.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Derives a compact priority-shape string such as {@code 1K-2Q-3R}.
     */
    String computeTypicalPriorityPattern(
            List<WeeklyPlanEntity> plans,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlan
    ) {
        if (plans.isEmpty()) {
            return "";
        }

        Map<ChessPriority, Long> counts = new LinkedHashMap<>();
        for (ChessPriority priority : ChessPriority.values()) {
            counts.put(priority, 0L);
        }

        for (WeeklyPlanEntity plan : plans) {
            for (WeeklyCommitEntity commit : commitsByPlan.getOrDefault(plan.getId(), List.of())) {
                if (commit.getChessPriority() != null) {
                    counts.merge(commit.getChessPriority(), 1L, Long::sum);
                }
            }
        }

        List<String> parts = new ArrayList<>();
        for (ChessPriority priority : ChessPriority.values()) {
            int roundedAverage = (int) Math.round((double) counts.get(priority) / plans.size());
            if (roundedAverage > 0) {
                parts.add(roundedAverage + priorityAbbreviation(priority));
            }
        }
        return String.join("-", parts);
    }

    /**
     * Finds recurring commit titles by normalized title frequency.
     */
    List<String> computeRecurringCommitTitles(List<WeeklyCommitEntity> commits) {
        Map<String, TitleFrequency> countsByNormalizedTitle = new HashMap<>();

        for (WeeklyCommitEntity commit : commits) {
            String title = commit.getTitle() == null ? "" : commit.getTitle().trim();
            if (title.isEmpty()) {
                continue;
            }
            String normalized = title.toLowerCase();
            TitleFrequency existing = countsByNormalizedTitle.get(normalized);
            if (existing == null) {
                countsByNormalizedTitle.put(normalized, new TitleFrequency(title, 1));
            } else {
                countsByNormalizedTitle.put(
                        normalized,
                        new TitleFrequency(existing.displayTitle(), existing.count() + 1)
                );
            }
        }

        return countsByNormalizedTitle.values().stream()
                .filter(titleFrequency -> titleFrequency.count() > 1)
                .sorted(Comparator.comparingLong(TitleFrequency::count).reversed()
                        .thenComparing(TitleFrequency::displayTitle))
                .limit(3)
                .map(TitleFrequency::displayTitle)
                .toList();
    }

    /**
     * Average progress-entry count per analysed week.
     */
    double computeAvgCheckInsPerWeek(
            List<WeeklyPlanEntity> plans,
            List<ProgressEntryEntity> entries
    ) {
        if (plans.isEmpty()) {
            return 0.0;
        }
        return (double) entries.size() / plans.size();
    }

    /**
     * Returns update days ordered by descending frequency, then natural weekday order.
     */
    List<String> computePreferredUpdateDays(List<ProgressEntryEntity> entries) {
        Map<DayOfWeek, Long> dayCounts = new HashMap<>();
        for (ProgressEntryEntity entry : entries) {
            DayOfWeek day = entry.getCreatedAt().atZone(ZoneOffset.UTC).getDayOfWeek();
            dayCounts.merge(day, 1L, Long::sum);
        }
        return dayCounts.entrySet().stream()
                .sorted(Map.Entry.<DayOfWeek, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().getValue()))
                .map(entry -> entry.getKey().name())
                .toList();
    }

    /**
     * Computes simple directional trends across active weeks in the window.
     */
    UserProfileResponse.Trends computeTrends(
            List<WeeklyPlanEntity> plans,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlan,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId
    ) {
        List<WeeklySignals> weeklySignals = plans.stream()
                .map(plan -> buildWeeklySignals(
                        commitsByPlan.getOrDefault(plan.getId(), List.of()),
                        actualsByCommitId))
                .filter(WeeklySignals::hasCommits)
                .toList();

        if (weeklySignals.size() < 2) {
            return new UserProfileResponse.Trends(
                    TREND_STABLE,
                    TREND_STABLE,
                    TREND_STABLE
            );
        }

        WeeklySignals first = weeklySignals.get(0);
        WeeklySignals last = weeklySignals.get(weeklySignals.size() - 1);

        return new UserProfileResponse.Trends(
                toTrendDirection(first.strategicAlignmentRate(), last.strategicAlignmentRate(), false),
                toTrendDirection(first.completionReliability(), last.completionReliability(), false),
                toTrendDirection(first.carryForwardRate(), last.carryForwardRate(), true)
        );
    }

    private WeeklySignals buildWeeklySignals(
            List<WeeklyCommitEntity> commits,
            Map<UUID, WeeklyCommitActualEntity> actualsByCommitId
    ) {
        if (commits.isEmpty()) {
            return new WeeklySignals(0, 0.0, 0.0, 0.0);
        }

        long strategicCount = commits.stream().filter(commit -> commit.getOutcomeId() != null).count();
        long carryForwardCount = commits.stream()
                .filter(commit -> commit.getCarriedFromCommitId() != null)
                .count();

        long reconciledCount = commits.stream()
                .filter(commit -> actualsByCommitId.containsKey(commit.getId()))
                .count();
        long doneCount = commits.stream()
                .filter(commit -> actualsByCommitId.containsKey(commit.getId()))
                .filter(commit -> actualsByCommitId.get(commit.getId()).getCompletionStatus()
                        == CompletionStatus.DONE)
                .count();

        double strategicAlignmentRate = (double) strategicCount / commits.size();
        double completionReliability = reconciledCount == 0 ? 0.0 : (double) doneCount / reconciledCount;
        double carryForwardRate = (double) carryForwardCount / commits.size();

        return new WeeklySignals(
                commits.size(),
                strategicAlignmentRate,
                completionReliability,
                carryForwardRate
        );
    }

    private String toTrendDirection(double baseline, double latest, boolean lowerIsBetter) {
        double delta = latest - baseline;
        if (Math.abs(delta) < TREND_EPSILON) {
            return TREND_STABLE;
        }
        if (lowerIsBetter) {
            return delta < 0 ? TREND_IMPROVING : TREND_WORSENING;
        }
        return delta > 0 ? TREND_IMPROVING : TREND_WORSENING;
    }

    private Map<String, Object> buildModelMap(
            double estimationAccuracy,
            double completionReliability,
            double avgCommitsPerWeek,
            double avgCarryForwardPerWeek,
            List<String> topCategories,
            Map<String, Double> categoryCompletionRates,
            Map<String, Double> priorityCompletionRates,
            String typicalPriorityPattern,
            List<String> recurringCommitTitles,
            double avgCheckInsPerWeek,
            List<String> preferredUpdateDays,
            UserProfileResponse.Trends trends
    ) {
        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("estimationAccuracy", estimationAccuracy);
        performance.put("completionReliability", completionReliability);
        performance.put("avgCommitsPerWeek", avgCommitsPerWeek);
        performance.put("avgCarryForwardPerWeek", avgCarryForwardPerWeek);
        performance.put("topCategories", topCategories);
        performance.put("categoryCompletionRates", categoryCompletionRates);
        performance.put("priorityCompletionRates", priorityCompletionRates);

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("typicalPriorityPattern", typicalPriorityPattern);
        preferences.put("recurringCommitTitles", recurringCommitTitles);
        preferences.put("avgCheckInsPerWeek", avgCheckInsPerWeek);
        preferences.put("preferredUpdateDays", preferredUpdateDays);

        Map<String, Object> trendMap = new LinkedHashMap<>();
        trendMap.put("strategicAlignmentTrend", trends.strategicAlignmentTrend());
        trendMap.put("completionTrend", trends.completionTrend());
        trendMap.put("carryForwardTrend", trends.carryForwardTrend());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("performanceProfile", performance);
        root.put("preferences", preferences);
        root.put("trends", trendMap);
        return root;
    }

    @SuppressWarnings("unchecked")
    private UserProfileResponse toProfileResponse(UserModelSnapshotEntity entity, UUID userId) {
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(entity.getModelJson(), Map.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to deserialise model JSON for userId={}: {}", userId, e.getMessage());
            raw = Map.of();
        }

        Map<String, Object> performanceMap =
                (Map<String, Object>) raw.getOrDefault("performanceProfile", Map.of());
        Map<String, Object> preferencesMap =
                (Map<String, Object>) raw.getOrDefault("preferences", Map.of());
        Map<String, Object> trendsMap =
                (Map<String, Object>) raw.getOrDefault("trends", Map.of());

        Map<String, Double> categoryRates = toDoubleMap(
                (Map<?, ?>) performanceMap.getOrDefault("categoryCompletionRates", Map.of()));
        Map<String, Double> priorityRates = toDoubleMap(
                (Map<?, ?>) performanceMap.getOrDefault("priorityCompletionRates", Map.of()));

        List<String> topCategories = toStringList(
                performanceMap.getOrDefault("topCategories", computeTopCategories(categoryRates)));

        UserProfileResponse.PerformanceProfile performanceProfile =
                new UserProfileResponse.PerformanceProfile(
                        toDouble(performanceMap.get("estimationAccuracy")),
                        toDouble(performanceMap.get("completionReliability")),
                        toDouble(performanceMap.get("avgCommitsPerWeek")),
                        toDouble(performanceMap.get("avgCarryForwardPerWeek")),
                        topCategories,
                        categoryRates,
                        priorityRates
                );

        UserProfileResponse.Preferences preferences = new UserProfileResponse.Preferences(
                toStringValue(preferencesMap.get("typicalPriorityPattern"), ""),
                toStringList(preferencesMap.get("recurringCommitTitles")),
                toDouble(preferencesMap.get("avgCheckInsPerWeek")),
                toStringList(preferencesMap.get("preferredUpdateDays"))
        );

        UserProfileResponse.Trends trends = new UserProfileResponse.Trends(
                toStringValue(trendsMap.get("strategicAlignmentTrend"), TREND_STABLE),
                toStringValue(trendsMap.get("completionTrend"), TREND_STABLE),
                toStringValue(trendsMap.get("carryForwardTrend"), TREND_STABLE)
        );

        return new UserProfileResponse(
                userId.toString(),
                entity.getWeeksAnalyzed(),
                performanceProfile,
                preferences,
                trends
        );
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private Map<String, Double> toDoubleMap(Map<?, ?> raw) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), toDouble(entry.getValue()));
        }
        return result;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream().map(String::valueOf).toList();
    }

    private String toStringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private String priorityAbbreviation(ChessPriority priority) {
        return switch (priority) {
            case KING -> "K";
            case QUEEN -> "Q";
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            case PAWN -> "P";
        };
    }

    private record WeeklySignals(
            int commitCount,
            double strategicAlignmentRate,
            double completionReliability,
            double carryForwardRate
    ) {
        boolean hasCommits() {
            return commitCount > 0;
        }
    }

    private record TitleFrequency(String displayTitle, long count) {
    }
}
