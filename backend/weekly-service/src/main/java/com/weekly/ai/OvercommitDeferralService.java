package com.weekly.ai;

import com.weekly.issues.domain.IssueEntity;
import com.weekly.issues.domain.IssueStatus;
import com.weekly.issues.repository.IssueRepository;
import com.weekly.shared.CapacityProfileProvider;
import com.weekly.shared.CapacityProfileProvider.CapacityProfileSnapshot;
import com.weekly.shared.DeferralPlanDataProvider;
import com.weekly.shared.DeferralPlanDataProvider.PlanAssignmentSummary;
import com.weekly.shared.UrgencyDataProvider;
import com.weekly.shared.UrgencyInfo;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Overcommit deferral suggestion service (Phase 6, Step 14).
 *
 * <p>When a user's weekly plan exceeds their realistic capacity cap, this service
 * identifies the lowest-priority / lowest-urgency assignments to suggest deferring
 * back to the backlog. The goal is to help users focus on the work that matters most
 * and avoid chronic overcommitment.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Resolve the user's current plan assignments via {@link DeferralPlanDataProvider}.</li>
 *   <li>Load associated issues from {@link IssueRepository}.</li>
 *   <li>Compute total estimated hours across all assignments.</li>
 *   <li>Compare against the user's {@code realisticWeeklyCap} from their capacity profile.</li>
 *   <li>If total &gt; cap, sort assignments by deferral attractiveness:
 *     <ul>
 *       <li>Chess priority ascending (PAWN → KNIGHT → BISHOP → ROOK → QUEEN → KING);
 *           lower priority = better deferral candidate.</li>
 *       <li>Within the same priority, urgency ascending: ON_TRACK &lt; NEEDS_ATTENTION
 *           &lt; AT_RISK &lt; CRITICAL; lower urgency = better deferral candidate.</li>
 *       <li>Estimated hours descending (deferring larger items frees more capacity).</li>
 *     </ul>
 *   </li>
 *   <li>Greedily suggest deferrals until the projected remaining total is ≤ cap,
 *       or there are no more deferrable assignments.</li>
 * </ol>
 *
 * <p>KING-priority and CRITICAL-urgency assignments are never suggested for deferral.
 *
 * <p>Returns {@link DeferralResult#noOvercommit(BigDecimal, BigDecimal)} when within capacity.
 * Returns {@link DeferralResult#unavailable()} when a capacity profile is missing.
 *
 * <p>This service lives in the {@code ai} package and accesses plan data only through the
 * {@link DeferralPlanDataProvider} shared seam, satisfying the ArchUnit boundary rule
 * that AI must not depend on plan internals.
 */
@Service
public class OvercommitDeferralService {

    private static final Logger LOG = LoggerFactory.getLogger(OvercommitDeferralService.class);

    // ── Chess priority ordering (string-based, no plan.domain reference) ─────

    /**
     * Chess priority deferral ordinal — higher value = better deferral candidate.
     * Values are intentionally string-keyed to avoid depending on plan.domain.ChessPriority.
     */
    static final int CHESS_DEFERRAL_PAWN = 5;
    static final int CHESS_DEFERRAL_KNIGHT = 4;
    static final int CHESS_DEFERRAL_BISHOP = 3;
    static final int CHESS_DEFERRAL_ROOK = 2;
    static final int CHESS_DEFERRAL_QUEEN = 1;
    static final int CHESS_DEFERRAL_KING = 0; // never deferred

    // ── Urgency ordinals ──────────────────────────────────────────────────────

    static final int URGENCY_ON_TRACK = 0;
    static final int URGENCY_NEEDS_ATTENTION = 1;
    static final int URGENCY_AT_RISK = 2;
    static final int URGENCY_CRITICAL = 3; // never deferred

    private final DeferralPlanDataProvider planDataProvider;
    private final IssueRepository issueRepository;
    private final CapacityProfileProvider capacityProfileProvider;
    private final UrgencyDataProvider urgencyDataProvider;

    public OvercommitDeferralService(
            DeferralPlanDataProvider planDataProvider,
            IssueRepository issueRepository,
            CapacityProfileProvider capacityProfileProvider,
            UrgencyDataProvider urgencyDataProvider
    ) {
        this.planDataProvider = planDataProvider;
        this.issueRepository = issueRepository;
        this.capacityProfileProvider = capacityProfileProvider;
        this.urgencyDataProvider = urgencyDataProvider;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Computes deferral suggestions for the given user's plan.
     *
     * @param orgId     the organisation ID
     * @param userId    the plan owner's user ID
     * @param weekStart the Monday of the current planning week (defaults to current Monday)
     * @return a {@link DeferralResult}; never null
     */
    public DeferralResult suggestDeferrals(UUID orgId, UUID userId, LocalDate weekStart) {
        try {
            LocalDate monday = resolveMonday(weekStart);

            // 1. Load capacity profile
            Optional<CapacityProfileSnapshot> profileOpt =
                    capacityProfileProvider.getLatestProfile(orgId, userId);
            if (profileOpt.isEmpty()) {
                LOG.debug("No capacity profile for user {}; returning unavailable", userId);
                return DeferralResult.unavailable();
            }
            CapacityProfileSnapshot profile = profileOpt.get();
            BigDecimal cap = profile.realisticWeeklyCap();
            if (cap == null || cap.compareTo(BigDecimal.ZERO) <= 0) {
                return DeferralResult.unavailable();
            }

            // 2. Load plan assignments via shared seam (no plan.domain dependency)
            List<PlanAssignmentSummary> assignments =
                    planDataProvider.getCurrentPlanAssignments(orgId, userId, monday);
            if (assignments.isEmpty()) {
                return DeferralResult.noOvercommit(BigDecimal.ZERO, cap);
            }

            // 3. Enrich with issue data
            List<AssignmentWithIssue> enriched = loadIssues(orgId, assignments);
            if (enriched.isEmpty()) {
                return DeferralResult.noOvercommit(BigDecimal.ZERO, cap);
            }

            // 4. Compute total estimated hours
            BigDecimal totalHours = enriched.stream()
                    .map(a -> estimatedHours(a.issue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalHours.compareTo(cap) <= 0) {
                return DeferralResult.noOvercommit(totalHours, cap);
            }

            // 5. Filter to deferrable; sort by deferral attractiveness
            List<AssignmentWithIssue> deferrableAssignments = enriched.stream()
                    .filter(a -> isDeferrable(a, orgId))
                    .sorted(deferralOrder(orgId))
                    .toList();

            // 6. Greedy deferral until plan fits within cap
            BigDecimal remaining = totalHours;
            List<DeferralSuggestion> suggestions = new ArrayList<>();
            for (AssignmentWithIssue candidate : deferrableAssignments) {
                if (remaining.compareTo(cap) <= 0) {
                    break;
                }
                BigDecimal hours = estimatedHours(candidate.issue());
                String rationale = buildRationale(candidate, orgId, hours, remaining, cap);
                suggestions.add(new DeferralSuggestion(
                        candidate.summary().assignmentId(),
                        candidate.issue().getId(),
                        candidate.issue().getIssueKey(),
                        candidate.issue().getTitle(),
                        hours,
                        rationale
                ));
                remaining = remaining.subtract(hours);
            }

            String summary = buildSummary(totalHours, cap, suggestions.size());
            return new DeferralResult("ok", suggestions, totalHours, cap, summary);
        } catch (Exception e) {
            LOG.error("Unexpected error in suggestDeferrals for user {}", userId, e);
            return DeferralResult.unavailable();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private LocalDate resolveMonday(LocalDate weekStart) {
        return weekStart != null ? weekStart : LocalDate.now().with(DayOfWeek.MONDAY);
    }

    private List<AssignmentWithIssue> loadIssues(
            UUID orgId, List<PlanAssignmentSummary> summaries) {
        List<AssignmentWithIssue> result = new ArrayList<>(summaries.size());
        for (PlanAssignmentSummary s : summaries) {
            issueRepository.findByOrgIdAndId(orgId, s.issueId())
                    .filter(issue -> issue.getStatus() != IssueStatus.ARCHIVED)
                    .ifPresent(issue -> result.add(new AssignmentWithIssue(s, issue)));
        }
        return result;
    }

    private static BigDecimal estimatedHours(IssueEntity issue) {
        return issue.getEstimatedHours() != null
                ? issue.getEstimatedHours()
                : BigDecimal.ZERO;
    }

    /**
     * An assignment is deferrable if it is not KING priority and not CRITICAL urgency.
     * Chess priority is compared by string name to avoid importing plan.domain.ChessPriority.
     */
    private boolean isDeferrable(AssignmentWithIssue awi, UUID orgId) {
        String priority = resolveChessPriorityName(awi);
        if ("KING".equals(priority)) {
            return false;
        }
        int urgency = resolveUrgencyOrdinal(awi.issue(), orgId);
        return urgency < URGENCY_CRITICAL;
    }

    private Comparator<AssignmentWithIssue> deferralOrder(UUID orgId) {
        return Comparator
                // Lowest chess priority first (PAWN=5 reversed → largest ordinal first)
                .comparingInt((AssignmentWithIssue a) ->
                        chessDeferralOrdinal(resolveChessPriorityName(a)))
                .reversed()
                // Within same priority: lowest urgency first
                .thenComparingInt(a -> resolveUrgencyOrdinal(a.issue(), orgId))
                // Then largest hours first
                .thenComparing(
                        Comparator.comparing((AssignmentWithIssue a) -> estimatedHours(a.issue()))
                                .reversed()
                );
    }

    /**
     * Resolves the effective chess priority name (e.g. "ROOK") for an assignment.
     * Assignment-level override takes precedence over the issue's own chess priority.
     * Returns {@code null} when neither is set.
     */
    private static String resolveChessPriorityName(AssignmentWithIssue awi) {
        String override = awi.summary().chessPriorityOverride();
        if (override != null && !override.isBlank()) {
            return override.toUpperCase();
        }
        // IssueEntity.getChessPriority() returns a ChessPriority enum, but we only
        // need the name string — use Enum.name() via Object to avoid a type reference.
        Object cp = awi.issue().getChessPriority();
        if (cp instanceof Enum<?> e) {
            return e.name();
        }
        return null;
    }

    /**
     * Returns the deferral ordinal for a chess priority string.
     * Higher ordinal = better candidate for deferral.
     */
    static int chessDeferralOrdinal(String priorityName) {
        if (priorityName == null) {
            return CHESS_DEFERRAL_PAWN; // null treated as lowest priority
        }
        return switch (priorityName.toUpperCase()) {
            case "KING" -> CHESS_DEFERRAL_KING;
            case "QUEEN" -> CHESS_DEFERRAL_QUEEN;
            case "ROOK" -> CHESS_DEFERRAL_ROOK;
            case "BISHOP" -> CHESS_DEFERRAL_BISHOP;
            case "KNIGHT" -> CHESS_DEFERRAL_KNIGHT;
            default -> CHESS_DEFERRAL_PAWN; // PAWN and unknown
        };
    }

    private int resolveUrgencyOrdinal(IssueEntity issue, UUID orgId) {
        if (issue.getOutcomeId() == null) {
            return URGENCY_ON_TRACK;
        }
        try {
            UrgencyInfo info = urgencyDataProvider.getOutcomeUrgency(orgId, issue.getOutcomeId());
            if (info == null || info.urgencyBand() == null) {
                return URGENCY_ON_TRACK;
            }
            return switch (info.urgencyBand()) {
                case "CRITICAL" -> URGENCY_CRITICAL;
                case "AT_RISK" -> URGENCY_AT_RISK;
                case "NEEDS_ATTENTION" -> URGENCY_NEEDS_ATTENTION;
                default -> URGENCY_ON_TRACK;
            };
        } catch (Exception e) {
            LOG.debug("Could not resolve urgency for issue {}: {}", issue.getId(), e.getMessage());
            return URGENCY_ON_TRACK;
        }
    }

    private String buildRationale(
            AssignmentWithIssue awi,
            UUID orgId,
            BigDecimal hours,
            BigDecimal currentTotal,
            BigDecimal cap
    ) {
        String priority = resolveChessPriorityName(awi);
        int urgencyOrdinal = resolveUrgencyOrdinal(awi.issue(), orgId);
        String urgencyLabel = urgencyLabel(urgencyOrdinal);
        String priorityLabel = priority != null ? priority : "UNSET";
        return String.format(
                "Chess priority: %s, outcome urgency: %s. "
                + "Deferring frees %.1f h toward cap of %.1f h (currently at %.1f h).",
                priorityLabel, urgencyLabel, hours, cap, currentTotal
        );
    }

    private static String urgencyLabel(int ordinal) {
        return switch (ordinal) {
            case URGENCY_CRITICAL -> "CRITICAL";
            case URGENCY_AT_RISK -> "AT_RISK";
            case URGENCY_NEEDS_ATTENTION -> "NEEDS_ATTENTION";
            default -> "ON_TRACK";
        };
    }

    private static String buildSummary(
            BigDecimal totalHours, BigDecimal cap, int deferralCount) {
        if (deferralCount == 0) {
            return String.format(
                    "Total estimated hours: %.1f h exceeds capacity cap of %.1f h, "
                    + "but no deferrable items were found (all remaining items are KING-priority "
                    + "or CRITICAL urgency).",
                    totalHours, cap);
        }
        return String.format(
                "Total estimated hours: %.1f h exceeds capacity cap of %.1f h. "
                + "Suggesting %d deferral(s) to bring the plan within capacity.",
                totalHours, cap, deferralCount);
    }

    // ── Internal record ───────────────────────────────────────────────────────

    private record AssignmentWithIssue(PlanAssignmentSummary summary, IssueEntity issue) {}

    // ── Public result types ───────────────────────────────────────────────────

    /**
     * A single deferral suggestion.
     *
     * @param assignmentId   the weekly assignment ID to remove from the plan
     * @param issueId        the issue UUID
     * @param issueKey       the human-readable issue key (e.g. "PLAT-42")
     * @param title          issue title
     * @param estimatedHours hours that would be freed by deferring this issue
     * @param rationale      human-readable explanation for the suggestion
     */
    public record DeferralSuggestion(
            UUID assignmentId,
            UUID issueId,
            String issueKey,
            String title,
            BigDecimal estimatedHours,
            String rationale
    ) {}

    /**
     * Result returned by {@link #suggestDeferrals}.
     *
     * @param status      "ok" | "no_overcommit" | "unavailable"
     * @param suggestions ordered list of deferral suggestions (may be empty)
     * @param totalHours  total estimated hours across all assignments (null when unavailable)
     * @param cap         the user's realistic weekly cap (null when unavailable)
     * @param summary     human-readable overcommit summary
     */
    public record DeferralResult(
            String status,
            List<DeferralSuggestion> suggestions,
            BigDecimal totalHours,
            BigDecimal cap,
            String summary
    ) {
        static DeferralResult unavailable() {
            return new DeferralResult("unavailable", List.of(), null, null,
                    "Capacity profile unavailable; cannot compute deferral suggestions.");
        }

        static DeferralResult noOvercommit(BigDecimal totalHours, BigDecimal cap) {
            return new DeferralResult("no_overcommit", List.of(), totalHours, cap,
                    String.format("Plan is within capacity (%.1f h / %.1f h cap).",
                            totalHours, cap));
        }
    }
}
