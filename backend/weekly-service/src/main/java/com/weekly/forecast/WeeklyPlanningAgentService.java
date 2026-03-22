package com.weekly.forecast;

import com.weekly.auth.OrgGraphClient;
import com.weekly.auth.OrgRosterEntry;
import com.weekly.notification.NotificationEntity;
import com.weekly.notification.NotificationRepository;
import com.weekly.notification.NotificationService;
import com.weekly.plan.domain.CompletionStatus;
import com.weekly.plan.domain.PlanState;
import com.weekly.plan.domain.WeeklyCommitActualEntity;
import com.weekly.plan.domain.WeeklyCommitEntity;
import com.weekly.plan.domain.WeeklyPlanEntity;
import com.weekly.plan.repository.WeeklyCommitActualRepository;
import com.weekly.plan.repository.WeeklyCommitRepository;
import com.weekly.plan.repository.WeeklyPlanRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.PredictionDataProvider;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * Proactive weekly-planning agent that pre-drafts realistic weekly plans from history,
 * urgency, and capacity signals.
 */
@Service
public class WeeklyPlanningAgentService {

    static final String NOTIFICATION_TYPE_WEEKLY_PLAN_DRAFT_READY = "WEEKLY_PLAN_DRAFT_READY";
    static final int HISTORY_WEEKS = 4;
    static final BigDecimal DEFAULT_CAPACITY_HOURS = new BigDecimal("24.0");
    static final BigDecimal DEFAULT_COMMIT_HOURS = new BigDecimal("4.0");
    static final int MAX_SUGGESTED_COMMITS = 5;
    static final double RECURRING_SIMILARITY_THRESHOLD = 0.3;

    private static final Logger LOG = LoggerFactory.getLogger(WeeklyPlanningAgentService.class);

    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklyCommitRepository weeklyCommitRepository;
    private final WeeklyCommitActualRepository weeklyCommitActualRepository;
    private final CapacityProfileProvider capacityProfileProvider;
    private final PredictionDataProvider predictionDataProvider;
    private final UrgencyDataProvider urgencyDataProvider;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final OrgGraphClient orgGraphClient;
    private final Clock clock;

    @Autowired
    public WeeklyPlanningAgentService(
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            WeeklyCommitActualRepository weeklyCommitActualRepository,
            CapacityProfileProvider capacityProfileProvider,
            PredictionDataProvider predictionDataProvider,
            UrgencyDataProvider urgencyDataProvider,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            OrgGraphClient orgGraphClient) {
        this(
                weeklyPlanRepository,
                weeklyCommitRepository,
                weeklyCommitActualRepository,
                capacityProfileProvider,
                predictionDataProvider,
                urgencyDataProvider,
                notificationService,
                notificationRepository,
                orgGraphClient,
                Clock.systemUTC());
    }

    WeeklyPlanningAgentService(
            WeeklyPlanRepository weeklyPlanRepository,
            WeeklyCommitRepository weeklyCommitRepository,
            WeeklyCommitActualRepository weeklyCommitActualRepository,
            CapacityProfileProvider capacityProfileProvider,
            PredictionDataProvider predictionDataProvider,
            UrgencyDataProvider urgencyDataProvider,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            OrgGraphClient orgGraphClient,
            Clock clock) {
        this.weeklyPlanRepository = weeklyPlanRepository;
        this.weeklyCommitRepository = weeklyCommitRepository;
        this.weeklyCommitActualRepository = weeklyCommitActualRepository;
        this.capacityProfileProvider = capacityProfileProvider;
        this.predictionDataProvider = predictionDataProvider;
        this.urgencyDataProvider = urgencyDataProvider;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.orgGraphClient = orgGraphClient;
        this.clock = clock;
    }

    /**
     * Creates bounded weekly-plan drafts for the current planning week.
     *
     * @return number of users who received a new draft notification
     */
    @Transactional
    public int createDraftsForCurrentWeek() {
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant notificationCutoff = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();

        int draftedCount = 0;
        for (UUID orgId : weeklyPlanRepository.findDistinctOrgIds()) {
            try {
                draftedCount += createDraftsForOrg(orgId, weekStart, notificationCutoff);
            } catch (Exception e) {
                LOG.warn("WeeklyPlanningAgentService: failed org {}: {}", orgId, e.getMessage(), e);
            }
        }
        return draftedCount;
    }

    @Transactional
    int createDraftsForOrg(UUID orgId, LocalDate weekStart, Instant notificationCutoff) {
        Map<UUID, String> rosterNames = orgGraphClient.getOrgRoster(orgId).stream()
                .collect(Collectors.toMap(
                        OrgRosterEntry::userId,
                        entry -> entry.displayName() != null ? entry.displayName() : entry.userId().toString(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        LocalDate historyStart = weekStart.minusWeeks(HISTORY_WEEKS);
        LocalDate historyEnd = weekStart.minusWeeks(1);
        List<WeeklyPlanEntity> recentPlans = weeklyPlanRepository.findByOrgIdAndWeekStartDateBetween(
                orgId, historyStart, historyEnd);

        Set<UUID> candidateUserIds = new LinkedHashSet<>(rosterNames.keySet());
        if (candidateUserIds.isEmpty()) {
            // Safe fallback when the org graph cannot supply a roster yet: use recent plan owners.
            recentPlans.stream().map(WeeklyPlanEntity::getOwnerUserId).forEach(candidateUserIds::add);
        }
        if (candidateUserIds.isEmpty()) {
            return 0;
        }

        Map<UUID, UrgencyInfo> urgencyByOutcomeId = Optional.ofNullable(urgencyDataProvider.getOrgUrgencySummary(orgId))
                .orElse(List.of())
                .stream()
                .collect(Collectors.toMap(UrgencyInfo::outcomeId, info -> info, (left, right) -> left));

        int draftedCount = 0;
        for (UUID userId : candidateUserIds) {
            String displayName = rosterNames.getOrDefault(userId, userId.toString());
            if (createDraftForUser(orgId, userId, displayName, weekStart, notificationCutoff, urgencyByOutcomeId)) {
                draftedCount++;
            }
        }
        return draftedCount;
    }

    private boolean createDraftForUser(
            UUID orgId,
            UUID userId,
            String displayName,
            LocalDate weekStart,
            Instant notificationCutoff,
            Map<UUID, UrgencyInfo> urgencyByOutcomeId) {
        Optional<WeeklyPlanEntity> existingPlanOpt = weeklyPlanRepository.findByOrgIdAndOwnerUserIdAndWeekStartDate(
                orgId, userId, weekStart);
        if (existingPlanOpt.isPresent()) {
            WeeklyPlanEntity existingPlan = existingPlanOpt.get();
            if (existingPlan.getState() != PlanState.DRAFT) {
                return false;
            }
            if (weeklyCommitRepository.countByOrgIdAndWeeklyPlanId(orgId, existingPlan.getId()) > 0) {
                return false;
            }
        }

        if (alreadyNotified(orgId, userId, weekStart, notificationCutoff)) {
            return false;
        }

        List<RankedCandidateCommit> candidates = buildCandidateCommitsForUser(orgId, userId, urgencyByOutcomeId, weekStart);
        if (candidates.isEmpty()) {
            return false;
        }

        BigDecimal realisticCapacity = capacityProfileProvider.getLatestProfile(orgId, userId)
                .map(CapacityProfileProvider.CapacityProfileSnapshot::realisticWeeklyCap)
                .filter(cap -> cap != null && cap.compareTo(BigDecimal.ZERO) > 0)
                .orElse(DEFAULT_CAPACITY_HOURS)
                .setScale(1, RoundingMode.HALF_UP);
        boolean carryRisk = Optional.ofNullable(predictionDataProvider.getUserPredictions(orgId, userId))
                .orElse(List.of())
                .stream()
                .anyMatch(signal -> signal.likely() && "CARRY_FORWARD".equalsIgnoreCase(signal.type()));
        BigDecimal targetHours = realisticCapacity
                .multiply(carryRisk ? new BigDecimal("0.75") : new BigDecimal("0.85"))
                .setScale(1, RoundingMode.HALF_UP);

        List<RankedCandidateCommit> selected = selectBoundedCandidates(candidates, targetHours, realisticCapacity);
        if (selected.isEmpty()) {
            return false;
        }

        WeeklyPlanEntity draftPlan = existingPlanOpt.orElseGet(() -> weeklyPlanRepository.save(
                new WeeklyPlanEntity(UUID.randomUUID(), orgId, userId, weekStart)));
        for (RankedCandidateCommit candidate : selected) {
            WeeklyCommitEntity commit = new WeeklyCommitEntity(UUID.randomUUID(), orgId, draftPlan.getId(), candidate.title());
            commit.setDescription(candidate.description());
            commit.setExpectedResult(candidate.expectedResult());
            if (candidate.category() != null) {
                commit.setCategory(candidate.category());
            }
            if (candidate.chessPriority() != null) {
                commit.setChessPriority(candidate.chessPriority());
            }
            if (candidate.outcomeId() != null) {
                commit.setOutcomeId(candidate.outcomeId());
            }
            if (candidate.nonStrategicReason() != null) {
                commit.setNonStrategicReason(candidate.nonStrategicReason());
            }
            commit.setEstimatedHours(candidate.estimatedHours());
            commit.setTagsFromArray(new String[]{
                    "draft_source:" + candidate.source(),
                    "draft_agent:weekly_planning"
            });
            weeklyCommitRepository.save(commit);
        }

        BigDecimal suggestedHours = selected.stream()
                .map(RankedCandidateCommit::estimatedHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);

        notificationService.notify(orgId, userId, NOTIFICATION_TYPE_WEEKLY_PLAN_DRAFT_READY, Map.of(
                "planId", draftPlan.getId().toString(),
                "weekStartDate", weekStart.toString(),
                "route", "weekly",
                "message", "A draft weekly plan is ready for " + displayName
                        + " with " + selected.size() + " suggested commit(s) totalling about "
                        + suggestedHours.toPlainString() + " hours.",
                "suggestedCommitCount", selected.size(),
                "suggestedHours", suggestedHours.toPlainString(),
                "capacityHours", realisticCapacity.toPlainString()
        ));
        return true;
    }

    private List<RankedCandidateCommit> buildCandidateCommitsForUser(
            UUID orgId,
            UUID userId,
            Map<UUID, UrgencyInfo> urgencyByOutcomeId,
            LocalDate weekStart) {
        LocalDate windowEnd = weekStart.minusWeeks(1);
        LocalDate windowStart = weekStart.minusWeeks(HISTORY_WEEKS);
        List<WeeklyPlanEntity> historicalPlans = weeklyPlanRepository
                .findByOrgIdAndOwnerUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(orgId, userId, windowStart, windowEnd);
        if (historicalPlans.isEmpty()) {
            return List.of();
        }

        List<UUID> historicalPlanIds = historicalPlans.stream().map(WeeklyPlanEntity::getId).toList();
        Map<UUID, List<WeeklyCommitEntity>> commitsByPlanId = weeklyCommitRepository
                .findByOrgIdAndWeeklyPlanIdIn(orgId, historicalPlanIds)
                .stream()
                .collect(Collectors.groupingBy(WeeklyCommitEntity::getWeeklyPlanId));

        List<RankedCandidateCommit> ranked = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        collectCarryForwardCandidates(orgId, historicalPlans, commitsByPlanId, urgencyByOutcomeId, ranked, seenKeys);
        collectRecurringCandidates(historicalPlans, commitsByPlanId, urgencyByOutcomeId, ranked, seenKeys);

        return ranked.stream()
                .sorted(Comparator.comparing(RankedCandidateCommit::score).reversed()
                        .thenComparing(RankedCandidateCommit::estimatedHours)
                        .thenComparing(RankedCandidateCommit::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void collectCarryForwardCandidates(
            UUID orgId,
            List<WeeklyPlanEntity> historicalPlans,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlanId,
            Map<UUID, UrgencyInfo> urgencyByOutcomeId,
            List<RankedCandidateCommit> ranked,
            Set<String> seenKeys) {
        Optional<WeeklyPlanEntity> latestLockedPlan = historicalPlans.stream()
                .filter(plan -> plan.getState() == PlanState.RECONCILED
                        || plan.getState() == PlanState.CARRY_FORWARD
                        || plan.getState() == PlanState.RECONCILING
                        || plan.getState() == PlanState.LOCKED)
                .max(Comparator.comparing(WeeklyPlanEntity::getWeekStartDate));
        if (latestLockedPlan.isEmpty()) {
            return;
        }

        List<WeeklyCommitEntity> commits = commitsByPlanId.getOrDefault(latestLockedPlan.get().getId(), List.of());
        if (commits.isEmpty()) {
            return;
        }
        Map<UUID, WeeklyCommitActualEntity> actualsByCommitId = weeklyCommitActualRepository
                .findByOrgIdAndCommitIdIn(orgId, commits.stream().map(WeeklyCommitEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(WeeklyCommitActualEntity::getCommitId, actual -> actual));

        for (WeeklyCommitEntity commit : commits) {
            WeeklyCommitActualEntity actual = actualsByCommitId.get(commit.getId());
            if (actual == null || actual.getCompletionStatus() == CompletionStatus.DONE) {
                continue;
            }
            addCandidate(commit, "CARRIED_FORWARD", 120, urgencyByOutcomeId, ranked, seenKeys);
        }
    }

    private void collectRecurringCandidates(
            List<WeeklyPlanEntity> historicalPlans,
            Map<UUID, List<WeeklyCommitEntity>> commitsByPlanId,
            Map<UUID, UrgencyInfo> urgencyByOutcomeId,
            List<RankedCandidateCommit> ranked,
            Set<String> seenKeys) {
        if (historicalPlans.size() < 2) {
            return;
        }
        WeeklyPlanEntity mostRecent = historicalPlans.get(historicalPlans.size() - 1);
        WeeklyPlanEntity previous = historicalPlans.get(historicalPlans.size() - 2);
        if (!mostRecent.getWeekStartDate().equals(previous.getWeekStartDate().plusWeeks(1))) {
            return;
        }

        List<WeeklyCommitEntity> recentCommits = commitsByPlanId.getOrDefault(mostRecent.getId(), List.of());
        List<WeeklyCommitEntity> previousCommits = commitsByPlanId.getOrDefault(previous.getId(), List.of());
        for (WeeklyCommitEntity recent : recentCommits) {
            boolean recurring = previousCommits.stream().anyMatch(previousCommit -> samePattern(recent, previousCommit));
            if (recurring) {
                addCandidate(recent, "RECURRING", 70, urgencyByOutcomeId, ranked, seenKeys);
            }
        }
    }

    private void addCandidate(
            WeeklyCommitEntity commit,
            String source,
            int baseScore,
            Map<UUID, UrgencyInfo> urgencyByOutcomeId,
            List<RankedCandidateCommit> ranked,
            Set<String> seenKeys) {
        String key = dedupeKey(commit.getTitle(), commit.getOutcomeId());
        if (!seenKeys.add(key)) {
            return;
        }

        BigDecimal estimatedHours = Optional.ofNullable(commit.getEstimatedHours())
                .filter(hours -> hours.compareTo(BigDecimal.ZERO) > 0)
                .orElse(DEFAULT_COMMIT_HOURS)
                .setScale(1, RoundingMode.HALF_UP);
        int score = baseScore + urgencyScore(commit.getOutcomeId(), urgencyByOutcomeId) + priorityScore(commit);
        ranked.add(new RankedCandidateCommit(
                commit.getTitle(),
                nullToEmpty(commit.getDescription()),
                nullToEmpty(commit.getExpectedResult()),
                commit.getCategory(),
                commit.getChessPriority(),
                commit.getOutcomeId(),
                commit.getNonStrategicReason(),
                estimatedHours,
                source,
                score));
    }

    private List<RankedCandidateCommit> selectBoundedCandidates(
            List<RankedCandidateCommit> candidates,
            BigDecimal targetHours,
            BigDecimal hardCap) {
        List<RankedCandidateCommit> selected = new ArrayList<>();
        BigDecimal totalHours = BigDecimal.ZERO;
        for (RankedCandidateCommit candidate : candidates) {
            if (selected.size() >= MAX_SUGGESTED_COMMITS) {
                break;
            }
            BigDecimal nextTotal = totalHours.add(candidate.estimatedHours());
            if (!selected.isEmpty() && nextTotal.compareTo(hardCap) > 0) {
                continue;
            }
            if (!selected.isEmpty() && nextTotal.compareTo(targetHours) > 0) {
                continue;
            }
            selected.add(candidate);
            totalHours = nextTotal;
        }

        if (selected.isEmpty() && !candidates.isEmpty()) {
            RankedCandidateCommit topCandidate = candidates.getFirst();
            if (topCandidate.estimatedHours().compareTo(hardCap.multiply(new BigDecimal("1.10"))) <= 0) {
                selected.add(topCandidate);
            }
        }
        return selected;
    }

    private boolean alreadyNotified(UUID orgId, UUID userId, LocalDate weekStart, Instant notificationCutoff) {
        return notificationRepository
                .findByOrgIdAndUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        orgId, userId, NOTIFICATION_TYPE_WEEKLY_PLAN_DRAFT_READY, notificationCutoff)
                .stream()
                .map(NotificationEntity::getPayload)
                .anyMatch(payload -> weekStart.toString().equals(String.valueOf(payload.get("weekStartDate"))));
    }

    private int urgencyScore(UUID outcomeId, Map<UUID, UrgencyInfo> urgencyByOutcomeId) {
        if (outcomeId == null) {
            return 0;
        }
        UrgencyInfo urgencyInfo = urgencyByOutcomeId.get(outcomeId);
        if (urgencyInfo == null || urgencyInfo.urgencyBand() == null) {
            return 0;
        }
        return switch (urgencyInfo.urgencyBand().toUpperCase()) {
            case "CRITICAL" -> 60;
            case "AT_RISK" -> 40;
            case "NEEDS_ATTENTION" -> 25;
            case "ON_TRACK" -> 10;
            default -> 0;
        };
    }

    private int priorityScore(WeeklyCommitEntity commit) {
        if (commit.getChessPriority() == null) {
            return commit.getOutcomeId() != null ? 10 : 0;
        }
        return switch (commit.getChessPriority()) {
            case KING -> 25;
            case QUEEN -> 18;
            case ROOK -> 12;
            case BISHOP -> 8;
            case KNIGHT -> 5;
            case PAWN -> 2;
        };
    }

    private boolean samePattern(WeeklyCommitEntity left, WeeklyCommitEntity right) {
        if (left.getOutcomeId() != null && left.getOutcomeId().equals(right.getOutcomeId())) {
            return true;
        }
        return normalizedLevenshteinDistance(left.getTitle(), right.getTitle()) < RECURRING_SIMILARITY_THRESHOLD;
    }

    static double normalizedLevenshteinDistance(String left, String right) {
        String normalizedLeft = normalizeTitleStatic(left);
        String normalizedRight = normalizeTitleStatic(right);
        if (normalizedLeft.equals(normalizedRight)) {
            return 0.0d;
        }
        int maxLength = Math.max(normalizedLeft.length(), normalizedRight.length());
        if (maxLength == 0) {
            return 0.0d;
        }
        return (double) levenshteinDistance(normalizedLeft, normalizedRight) / maxLength;
    }

    static int levenshteinDistance(String left, String right) {
        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0) {
            return rightLength;
        }
        if (rightLength == 0) {
            return leftLength;
        }

        int[] previous = new int[rightLength + 1];
        int[] current = new int[rightLength + 1];
        for (int index = 0; index <= rightLength; index++) {
            previous[index] = index;
        }

        for (int leftIndex = 1; leftIndex <= leftLength; leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= rightLength; rightIndex++) {
                if (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1)) {
                    current[rightIndex] = previous[rightIndex - 1];
                } else {
                    current[rightIndex] = 1 + Math.min(
                            previous[rightIndex - 1],
                            Math.min(previous[rightIndex], current[rightIndex - 1]));
                }
            }
            int[] temporary = previous;
            previous = current;
            current = temporary;
        }

        return previous[rightLength];
    }

    private String dedupeKey(String title, UUID outcomeId) {
        return outcomeId != null ? outcomeId.toString() : normalizeTitle(title);
    }

    private String normalizeTitle(String title) {
        return normalizeTitleStatic(title);
    }

    private static String normalizeTitleStatic(String title) {
        return title == null ? "" : title.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record RankedCandidateCommit(
            String title,
            String description,
            String expectedResult,
            com.weekly.plan.domain.CommitCategory category,
            com.weekly.plan.domain.ChessPriority chessPriority,
            UUID outcomeId,
            String nonStrategicReason,
            BigDecimal estimatedHours,
            String source,
            int score) {
    }
}
