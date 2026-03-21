package com.weekly.ai;

import com.weekly.rcdo.RcdoClient;
import com.weekly.rcdo.RcdoTree;
import com.weekly.shared.CommitDataProvider;
import com.weekly.shared.ManagerInsightDataProvider;
import com.weekly.shared.TeamRcdoUsageProvider;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link AiSuggestionService}.
 *
 * <p>Pipeline for RCDO auto-suggest:
 * <ol>
 *   <li>Check cache (orgId + hash of input + rcdoTreeVersion)</li>
 *   <li>Fetch RCDO tree, narrow to candidate set (~50 outcomes)</li>
 *   <li>Build structured prompt with candidate IDs in context</li>
 *   <li>Call LLM with 5s hard timeout</li>
 *   <li>Validate response: reject any outcomeId not in candidate set</li>
 *   <li>Cache and return</li>
 * </ol>
 *
 * <p>On any failure, returns 200 with {@code status: "unavailable"} and
 * empty suggestions (PRD §4 fallback contract).
 */
@Service
public class DefaultAiSuggestionService implements AiSuggestionService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAiSuggestionService.class);

    private final LlmClient llmClient;
    private final RcdoClient rcdoClient;
    private final AiCacheService cacheService;
    private final CommitDataProvider commitDataProvider;
    private final ManagerInsightDataProvider managerInsightDataProvider;
    private final TeamRcdoUsageProvider teamRcdoUsageProvider;

    public DefaultAiSuggestionService(
            LlmClient llmClient,
            RcdoClient rcdoClient,
            AiCacheService cacheService,
            CommitDataProvider commitDataProvider,
            ManagerInsightDataProvider managerInsightDataProvider,
            TeamRcdoUsageProvider teamRcdoUsageProvider
    ) {
        this.llmClient = llmClient;
        this.rcdoClient = rcdoClient;
        this.cacheService = cacheService;
        this.commitDataProvider = commitDataProvider;
        this.managerInsightDataProvider = managerInsightDataProvider;
        this.teamRcdoUsageProvider = teamRcdoUsageProvider;
    }

    @Override
    public SuggestionResult suggestRcdo(UUID orgId, String title, String description) {
        try {
            // 1. Get RCDO tree
            RcdoTree tree = rcdoClient.getTree(orgId);
            if (tree.rallyCries().isEmpty()) {
                LOG.debug("Empty RCDO tree for org {}, returning unavailable", orgId);
                return SuggestionResult.unavailable();
            }

            // 2. Build cache key and check cache
            String rcdoVersion = computeRcdoFingerprint(tree);
            String cacheKey = AiCacheService.buildSuggestKey(orgId, title, description, rcdoVersion);
            Optional<SuggestionResult> cached = cacheService.get(orgId, cacheKey, SuggestionResult.class);
            if (cached.isPresent()) {
                LOG.debug("Cache hit for suggest-rcdo: {}", cacheKey);
                return cached.get();
            }

            // 3. Fetch team RCDO usage for the current week (cached for 1 hour)
            LocalDate currentWeekStart = LocalDate.now().with(DayOfWeek.MONDAY);
            TeamRcdoUsageProvider.TeamRcdoUsageResult teamUsage = getTeamRcdoUsageCached(orgId, currentWeekStart);

            // 4. Build high-usage outcome IDs for candidate boosting
            Set<String> highUsageOutcomeIds = teamUsage.outcomes().stream()
                    .limit(5)
                    .map(TeamRcdoUsageProvider.OutcomeUsage::outcomeId)
                    .collect(Collectors.toSet());

            // 5. Narrow to candidate set, boosting team-active outcomes
            List<PromptBuilder.CandidateOutcome> candidates = CandidateSelector.select(
                    tree, title, description, CandidateSelector.DEFAULT_MAX_CANDIDATES,
                    highUsageOutcomeIds
            );
            if (candidates.isEmpty()) {
                LOG.debug("No candidates found for title '{}', returning empty", title);
                return new SuggestionResult("ok", List.of());
            }

            // 6. Build valid outcome ID set for validation
            Set<String> validOutcomeIds = candidates.stream()
                    .map(PromptBuilder.CandidateOutcome::outcomeId)
                    .collect(Collectors.toSet());

            // 7. Compute team context for prompt enrichment
            List<PromptBuilder.TeamOutcomeUsage> topTeamOutcomes = teamUsage.outcomes().stream()
                    .limit(5)
                    .map(u -> new PromptBuilder.TeamOutcomeUsage(u.outcomeName(), u.commitCount()))
                    .toList();

            Set<String> coveredOutcomeIds = teamUsage.coveredOutcomeIdsThisQuarter();
            List<String> zeroCoverageOutcomeNames = tree.rallyCries().stream()
                    .flatMap(rc -> rc.objectives().stream()
                            .flatMap(obj -> obj.outcomes().stream()))
                    .filter(o -> !coveredOutcomeIds.contains(o.id()))
                    .map(RcdoTree.Outcome::name)
                    .sorted()
                    .toList();

            // 8. Build enriched prompt and call LLM
            List<LlmClient.Message> messages = PromptBuilder.buildRcdoSuggestMessages(
                    title, description, candidates, topTeamOutcomes, zeroCoverageOutcomeNames
            );
            String rawResponse = llmClient.complete(messages, PromptBuilder.rcdoSuggestResponseSchema());

            // 9. Validate response — reject hallucinated IDs
            List<RcdoSuggestion> suggestions = ResponseValidator.validateRcdoSuggestions(
                    rawResponse, validOutcomeIds
            );

            SuggestionResult result = new SuggestionResult("ok", suggestions);

            // 10. Cache result
            cacheService.put(orgId, cacheKey, result);

            return result;
        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("LLM unavailable for suggest-rcdo: {}", e.getMessage());
            return SuggestionResult.unavailable();
        } catch (Exception e) {
            LOG.error("Unexpected error in suggest-rcdo", e);
            return SuggestionResult.unavailable();
        }
    }

    /**
     * Returns the team RCDO usage for the current week, using the 1-hour cache.
     * On any error, returns an empty usage snapshot so the suggestion pipeline continues.
     */
    private TeamRcdoUsageProvider.TeamRcdoUsageResult getTeamRcdoUsageCached(
            UUID orgId, LocalDate weekStart) {
        String teamCacheKey = AiCacheService.buildTeamRcdoUsageKey(orgId, weekStart);
        Optional<TeamRcdoUsageProvider.TeamRcdoUsageResult> cachedTeam =
                cacheService.get(orgId, teamCacheKey, TeamRcdoUsageProvider.TeamRcdoUsageResult.class);
        if (cachedTeam.isPresent()) {
            LOG.debug("Cache hit for team-rcdo-usage: {}", teamCacheKey);
            return cachedTeam.get();
        }
        try {
            TeamRcdoUsageProvider.TeamRcdoUsageResult usage =
                    teamRcdoUsageProvider.getTeamRcdoUsage(orgId, weekStart);
            cacheService.put(orgId, teamCacheKey, usage);
            return usage;
        } catch (Exception e) {
            LOG.warn("Could not fetch team RCDO usage for org {}: {}", orgId, e.getMessage());
            return new TeamRcdoUsageProvider.TeamRcdoUsageResult(List.of(), Set.of());
        }
    }

    @Override
    public ReconciliationDraftResult draftReconciliation(UUID orgId, UUID planId) {
        try {
            // 1. Load commit summaries via provider (respects module boundary)
            if (!commitDataProvider.planExists(orgId, planId)) {
                LOG.debug("Plan not found for draft reconciliation: {}", planId);
                return ReconciliationDraftResult.unavailable();
            }

            List<CommitDataProvider.CommitSummary> summaries =
                    commitDataProvider.getCommitSummaries(orgId, planId);
            if (summaries.isEmpty()) {
                return new ReconciliationDraftResult("ok", List.of());
            }

            // 2. Build enriched commit context (includes check-in history, carry-forward,
            //    and category completion rates populated by PlanCommitDataProvider)
            List<PromptBuilder.CommitContext> commitContexts = summaries.stream()
                    .map(s -> new PromptBuilder.CommitContext(
                            s.commitId(),
                            s.title(),
                            s.expectedResult(),
                            s.progressNotes(),
                            s.checkInHistory(),
                            s.priorCompletionStatuses(),
                            s.categoryCompletionRateContext()
                    ))
                    .toList();

            Set<String> validCommitIds = summaries.stream()
                    .map(CommitDataProvider.CommitSummary::commitId)
                    .collect(Collectors.toSet());

            // 3. Check cache — fingerprint the full reconciliation context so cached
            //    drafts invalidate when any prompt-relevant field changes (including
            //    check-in note/status content, carry-forward history, or category rates)
            String contextFingerprint = computeReconciliationContextFingerprint(summaries);
            String cacheKey = AiCacheService.buildDraftKey(orgId, planId, contextFingerprint);
            Optional<ReconciliationDraftResult> cached =
                    cacheService.get(orgId, cacheKey, ReconciliationDraftResult.class);
            if (cached.isPresent()) {
                return cached.get();
            }

            // 4. Build prompt and call LLM
            List<LlmClient.Message> messages = PromptBuilder.buildReconciliationDraftMessages(commitContexts);
            String rawResponse = llmClient.complete(messages, PromptBuilder.reconciliationDraftResponseSchema());

            // 5. Validate response
            List<ReconciliationDraftItem> drafts = ResponseValidator.validateReconciliationDraft(
                    rawResponse, validCommitIds
            );

            ReconciliationDraftResult result = new ReconciliationDraftResult("ok", drafts);
            cacheService.put(orgId, cacheKey, result);

            return result;
        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("LLM unavailable for draft-reconciliation: {}", e.getMessage());
            return ReconciliationDraftResult.unavailable();
        } catch (Exception e) {
            LOG.error("Unexpected error in draft-reconciliation", e);
            return ReconciliationDraftResult.unavailable();
        }
    }

    @Override
    public ManagerInsightsResult draftManagerInsights(UUID orgId, UUID managerId, LocalDate weekStart) {
        try {
            ManagerInsightDataProvider.ManagerWeekContext context =
                    managerInsightDataProvider.getManagerWeekContext(
                            orgId, managerId, weekStart,
                            ManagerInsightDataProvider.DEFAULT_WINDOW_WEEKS);

            if (context.teamMembers().isEmpty()) {
                return new ManagerInsightsResult("ok", "No direct-report data available for this week.", List.of());
            }

            String contextHash = computeManagerContextFingerprint(context);
            String cacheKey = AiCacheService.buildManagerInsightsKey(orgId, managerId, weekStart, contextHash);
            Optional<ManagerInsightsResult> cached = cacheService.get(orgId, cacheKey, ManagerInsightsResult.class);
            if (cached.isPresent()) {
                return cached.get();
            }

            List<LlmClient.Message> messages = PromptBuilder.buildManagerInsightsMessages(context);
            String rawResponse = llmClient.complete(messages, PromptBuilder.managerInsightsResponseSchema());

            ManagerInsightsResult result = ResponseValidator.validateManagerInsights(rawResponse);
            if ("unavailable".equals(result.status())) {
                return result;
            }

            cacheService.put(orgId, cacheKey, result);
            return result;
        } catch (LlmClient.LlmUnavailableException e) {
            LOG.warn("LLM unavailable for manager-insights: {}", e.getMessage());
            return ManagerInsightsResult.unavailable();
        } catch (Exception e) {
            LOG.error("Unexpected error in manager-insights", e);
            return ManagerInsightsResult.unavailable();
        }
    }

    private String computeRcdoFingerprint(RcdoTree tree) {
        return tree.rallyCries().stream()
                .sorted(Comparator.comparing(RcdoTree.RallyCry::id))
                .flatMap(rc -> rc.objectives().stream()
                        .sorted(Comparator.comparing(RcdoTree.Objective::id))
                        .flatMap(obj -> obj.outcomes().stream()
                                .sorted(Comparator.comparing(RcdoTree.Outcome::id))
                                .map(outcome -> String.join("|",
                                        rc.id(), rc.name(),
                                        obj.id(), obj.name(),
                                        outcome.id(), outcome.name()))))
                .collect(Collectors.joining(";"));
    }

    private String computeReconciliationContextFingerprint(
            List<CommitDataProvider.CommitSummary> summaries) {
        return summaries.stream()
                .sorted(Comparator.comparing(CommitDataProvider.CommitSummary::commitId))
                .map(summary -> {
                    StringBuilder fingerprint = new StringBuilder();
                    appendFingerprintPart(fingerprint, summary.commitId());
                    appendFingerprintPart(fingerprint, summary.title());
                    appendFingerprintPart(fingerprint, summary.expectedResult());
                    appendFingerprintPart(fingerprint, summary.progressNotes());

                    List<CommitDataProvider.CheckInEntry> checkIns =
                            summary.checkInHistory() == null ? List.of() : summary.checkInHistory();
                    appendFingerprintPart(fingerprint, String.valueOf(checkIns.size()));
                    for (CommitDataProvider.CheckInEntry entry : checkIns) {
                        appendFingerprintPart(fingerprint, entry.status());
                        appendFingerprintPart(fingerprint, entry.note());
                    }

                    List<String> priorStatuses = summary.priorCompletionStatuses() == null
                            ? List.of()
                            : summary.priorCompletionStatuses();
                    appendFingerprintPart(fingerprint, String.valueOf(priorStatuses.size()));
                    for (String status : priorStatuses) {
                        appendFingerprintPart(fingerprint, status);
                    }

                    appendFingerprintPart(fingerprint, summary.categoryCompletionRateContext());
                    return fingerprint.toString();
                })
                .collect(Collectors.joining());
    }

    private void appendFingerprintPart(StringBuilder fingerprint, String value) {
        String safeValue = String.valueOf(value);
        fingerprint.append(safeValue.length()).append(':').append(safeValue);
    }

    private String computeManagerContextFingerprint(ManagerInsightDataProvider.ManagerWeekContext context) {
        String teamMembers = context.teamMembers().stream()
                .sorted(Comparator.comparing(ManagerInsightDataProvider.TeamMemberContext::userId))
                .map(member -> String.join("|",
                        member.userId(),
                        String.valueOf(member.state()),
                        String.valueOf(member.reviewStatus()),
                        String.valueOf(member.commitCount()),
                        String.valueOf(member.incompleteCount()),
                        String.valueOf(member.issueCount()),
                        String.valueOf(member.nonStrategicCount()),
                        String.valueOf(member.kingCount()),
                        String.valueOf(member.queenCount()),
                        String.valueOf(member.stale()),
                        String.valueOf(member.lateLock())))
                .collect(Collectors.joining(";"));

        String focuses = context.rcdoFocuses().stream()
                .sorted(Comparator.comparing(ManagerInsightDataProvider.RcdoFocusContext::outcomeId))
                .map(focus -> String.join("|",
                        String.valueOf(focus.outcomeId()),
                        String.valueOf(focus.outcomeName()),
                        String.valueOf(focus.objectiveName()),
                        String.valueOf(focus.rallyCryName()),
                        String.valueOf(focus.commitCount()),
                        String.valueOf(focus.kingCount()),
                        String.valueOf(focus.queenCount())))
                .collect(Collectors.joining(";"));

        // Historical context fingerprint components
        String streaks = context.carryForwardStreaks() == null ? "" :
                context.carryForwardStreaks().stream()
                        .sorted(Comparator.comparing(ManagerInsightDataProvider.CarryForwardStreak::userId))
                        .map(s -> s.userId() + ":" + s.streakWeeks() + ":"
                                + String.join(",", s.carriedItemTitles()))
                        .collect(Collectors.joining(";"));

        String trends = context.outcomeCoverageTrends() == null ? "" :
                context.outcomeCoverageTrends().stream()
                        .sorted(Comparator.comparing(ManagerInsightDataProvider.OutcomeCoverageTrend::outcomeId))
                        .map(t -> t.outcomeId() + ":" + t.weekCounts().stream()
                                .map(wc -> wc.weekStart() + "=" + wc.commitCount())
                                .collect(Collectors.joining(",")))
                        .collect(Collectors.joining(";"));

        String lateLocks = context.lateLockPatterns() == null ? "" :
                context.lateLockPatterns().stream()
                        .sorted(Comparator.comparing(ManagerInsightDataProvider.LateLockPattern::userId))
                        .map(p -> p.userId() + ":" + p.lateLockWeeks() + "/" + p.windowWeeks())
                        .collect(Collectors.joining(";"));

        String turnaround = context.reviewTurnaroundStats() == null ? "" :
                String.format("%.2f/%d",
                        context.reviewTurnaroundStats().avgDaysToReview(),
                        context.reviewTurnaroundStats().sampleSize());

        return String.join("#",
                context.weekStart(),
                String.valueOf(context.reviewCounts().pending()),
                String.valueOf(context.reviewCounts().approved()),
                String.valueOf(context.reviewCounts().changesRequested()),
                teamMembers,
                focuses,
                streaks,
                trends,
                lateLocks,
                turnaround);
    }
}
