package com.weekly.ai;

import com.weekly.integration.IntegrationService.UserTicketContext;
import com.weekly.shared.CommitDataProvider;
import com.weekly.shared.ManagerInsightDataProvider;
import com.weekly.shared.NextWorkDataProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Constructs structured prompts for AI suggestion calls.
 *
 * <p>Security: user-authored text (titles, descriptions) is placed in USER
 * role messages, never concatenated with system prompts. This mitigates
 * prompt injection per PRD §4.
 */
public final class PromptBuilder {

    private PromptBuilder() {}

    /**
     * Builds messages for RCDO auto-suggest (no team context).
     *
     * @param title             the commitment title (user input)
     * @param description       the commitment description (user input)
     * @param candidateOutcomes the narrowed candidate set of RCDO outcomes
     * @return ordered messages for the LLM
     */
    public static List<LlmClient.Message> buildRcdoSuggestMessages(
            String title,
            String description,
            List<CandidateOutcome> candidateOutcomes
    ) {
        return buildRcdoSuggestMessages(title, description, candidateOutcomes, List.of(), List.of());
    }

    /**
     * Builds messages for RCDO auto-suggest enriched with team-context signals.
     *
     * <p>Team-context lines are appended to the ASSISTANT context message (not the
     * USER message) so that user-authored text remains isolated in the USER role,
     * mitigating prompt-injection risk per PRD §4.
     *
     * @param title                    the commitment title (user input)
     * @param description              the commitment description (user input)
     * @param candidateOutcomes        the narrowed candidate set of RCDO outcomes
     * @param topTeamOutcomes          top outcomes the team linked to this week (may be empty)
     * @param zeroCoverageOutcomeNames RCDO outcome names with zero team commits this quarter (may be empty)
     * @return ordered messages for the LLM
     */
    public static List<LlmClient.Message> buildRcdoSuggestMessages(
            String title,
            String description,
            List<CandidateOutcome> candidateOutcomes,
            List<TeamOutcomeUsage> topTeamOutcomes,
            List<String> zeroCoverageOutcomeNames
    ) {
        return buildRcdoSuggestMessages(
                title,
                description,
                candidateOutcomes,
                topTeamOutcomes,
                zeroCoverageOutcomeNames,
                List.of()
        );
    }

    /**
     * Builds messages for RCDO auto-suggest enriched with team-context and
     * candidate-level urgency signals.
     *
     * <p>Urgency annotations are supplied by the caller so this static builder
     * stays side-effect free and does not reach into services directly.
     */
    public static List<LlmClient.Message> buildRcdoSuggestMessages(
            String title,
            String description,
            List<CandidateOutcome> candidateOutcomes,
            List<TeamOutcomeUsage> topTeamOutcomes,
            List<String> zeroCoverageOutcomeNames,
            List<CandidateUrgencyContext> candidateUrgencies
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        // System prompt — instructions and constraints
        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are an AI assistant that maps weekly commitments to strategic outcomes.
                Given a commitment title and description, suggest the most relevant RCDO \
                (Rally Cry → Defining Objective → Outcome) mappings from the provided candidate list.

                Rules:
                1. ONLY suggest outcomes from the candidate list provided. Never invent IDs.
                2. Return between 1 and 5 suggestions, ranked by confidence (highest first).
                3. Each suggestion must include outcomeId, rallyCryName, objectiveName, outcomeName, \
                confidence (0.0-1.0), and a brief rationale.
                4. If no candidates are relevant, return an empty suggestions array.
                5. Use the team context (if provided) to bias towards outcomes the team is actively \
                working toward or outcomes that need more coverage.
                6. When urgency context is provided, explicitly favour AT_RISK and CRITICAL outcomes \
                over lower-urgency candidates when relevance is otherwise similar.
                7. Respond ONLY with valid JSON matching the required schema.
                """
        ));

        Map<String, CandidateUrgencyContext> urgencyByOutcomeId = candidateUrgencies.stream()
                .collect(Collectors.toMap(
                        CandidateUrgencyContext::outcomeId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        // Context message — RCDO candidate set and team context (not user-authored)
        StringBuilder candidateContext = new StringBuilder("Available RCDO outcomes:\n");
        for (CandidateOutcome c : candidateOutcomes) {
            candidateContext.append(String.format(
                    "- outcomeId: %s | outcomeName: %s | objectiveName: %s | rallyCryName: %s",
                    c.outcomeId(), c.outcomeName(), c.objectiveName(), c.rallyCryName()
            ));
            CandidateUrgencyContext urgency = urgencyByOutcomeId.get(c.outcomeId());
            if (urgency != null) {
                candidateContext.append(String.format(
                        " | urgencyBand: %s | urgencyPreference: %s | targetDate: %s | actualProgressPct: %s | expectedProgressPct: %s | daysRemaining: %s",
                        urgency.urgencyBand(),
                        urgency.isEscalated() ? "FAVOR_HIGH_URGENCY" : "NORMAL",
                        formatText(urgency.targetDate()),
                        formatDecimal(urgency.progressPct()),
                        formatDecimal(urgency.expectedProgressPct()),
                        formatDaysRemaining(urgency.daysRemaining())
                ));
            }
            candidateContext.append(System.lineSeparator());
        }

        List<CandidateUrgencyContext> escalatedUrgencies = candidateUrgencies.stream()
                .filter(CandidateUrgencyContext::isEscalated)
                .toList();
        if (!escalatedUrgencies.isEmpty()) {
            candidateContext.append("\nUrgency preference guidance:\n");
            escalatedUrgencies.forEach(urgency -> candidateContext.append(String.format(
                    "- %s is %s and should be explicitly favoured when the commitment could plausibly support it.%n",
                    urgency.outcomeName(), urgency.urgencyBand()
            )));
        }

        // Append team context when available
        if (!topTeamOutcomes.isEmpty()) {
            candidateContext.append("\nTeam usage context:\n");
            candidateContext.append("Top 5 outcomes your team linked to this week:\n");
            topTeamOutcomes.stream().limit(5).forEach(o ->
                    candidateContext.append(String.format(
                            "- %s (%d commits)%n", o.outcomeName(), o.commitCount()
                    ))
            );
        }
        if (!zeroCoverageOutcomeNames.isEmpty()) {
            candidateContext.append("\nOutcomes with 0 commits from your team this quarter:\n");
            zeroCoverageOutcomeNames.stream().limit(10).forEach(name ->
                    candidateContext.append(String.format("- %s%n", name))
            );
        }

        messages.add(new LlmClient.Message(LlmClient.Role.ASSISTANT, candidateContext.toString()));

        // User message — the actual commitment (untrusted input, separate role)
        String userContent = "Commitment title: " + title;
        if (description != null && !description.isBlank()) {
            userContent += "\nCommitment description: " + description;
        }
        userContent += "\n\nSuggest the most relevant RCDO outcomes from the candidate list.";
        messages.add(new LlmClient.Message(LlmClient.Role.USER, userContent));

        return messages;
    }

    /**
     * Builds messages for reconciliation draft suggestions with enriched context.
     *
     * <p>In addition to the basic commit fields (title, expectedResult, progressNotes)
     * each commit block includes:
     * <ul>
     *   <li><b>Check-in history</b> — structured daily progress entries (ON_TRACK, AT_RISK,
     *       BLOCKED, DONE_EARLY) that give the LLM visibility into mid-week signals.</li>
     *   <li><b>Carry-forward context</b> — prior completion statuses from ancestor commits
     *       so the LLM can detect "this item was partially done last week too".</li>
     *   <li><b>Team category completion rate</b> — statistical baseline, e.g.
     *       "OPERATIONS: 85% DONE (team, last 4 wks)".</li>
     * </ul>
     *
     * <p>Security: all user-authored text is placed in the USER message role,
     * never concatenated into the SYSTEM prompt.
     */
    public static List<LlmClient.Message> buildReconciliationDraftMessages(
            List<CommitContext> commits
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are an AI assistant that helps with weekly reconciliation.
                Given a list of weekly commitments enriched with mid-week check-in history, \
                carry-forward context, and team category completion rates, suggest a completion \
                status and actual result for each.

                Rules:
                1. For each commit, suggest one of: DONE, PARTIALLY, NOT_DONE, DROPPED.
                2. If not DONE, provide a brief suggestedDeltaReason.
                3. Provide a suggestedActualResult summarizing what was accomplished.
                4. Use check-in history signals (AT_RISK, BLOCKED) as strong indicators of \
                   PARTIALLY or NOT_DONE.
                5. Use DONE_EARLY check-in signals as indicators of DONE.
                6. When a commit has prior carry-forward statuses (e.g. PARTIALLY twice), \
                   treat that as evidence of a chronic blocker and explain in the delta reason.
                7. Use team category completion rates as calibration context; do not override \
                   direct progress signals with statistical baselines.
                8. Be conservative — if progress notes are ambiguous, suggest PARTIALLY.
                9. Respond ONLY with valid JSON matching the required schema.
                """
        ));

        StringBuilder commitContext = new StringBuilder("Commitments to reconcile:\n");
        for (CommitContext c : commits) {
            commitContext.append(String.format(
                    "- commitId: %s | title: %s | expectedResult: %s | progressNotes: %s%n",
                    c.commitId(), c.title(), c.expectedResult(), c.progressNotes()
            ));
            // Check-in history — only append section when entries are present
            if (c.checkInHistory() != null && !c.checkInHistory().isEmpty()) {
                commitContext.append("  check-ins:");
                for (CommitDataProvider.CheckInEntry entry : c.checkInHistory()) {
                    if (entry.note() != null && !entry.note().isBlank()) {
                        commitContext.append(String.format(
                                " [%s: \"%s\"]", entry.status(), entry.note()));
                    } else {
                        commitContext.append(String.format(" [%s]", entry.status()));
                    }
                }
                commitContext.append('\n');
            }
            // Carry-forward context — only append when this is a carried item
            if (c.priorCompletionStatuses() != null && !c.priorCompletionStatuses().isEmpty()) {
                commitContext.append(String.format(
                        "  carry-forward: carried %d time(s); prior statuses: %s%n",
                        c.priorCompletionStatuses().size(),
                        String.join(", ", c.priorCompletionStatuses())
                ));
            }
            // Category completion rate — only append when data is available
            if (c.categoryCompletionRateContext() != null
                    && !c.categoryCompletionRateContext().isBlank()) {
                commitContext.append(String.format(
                        "  team rate: %s%n", c.categoryCompletionRateContext()
                ));
            }
        }
        messages.add(new LlmClient.Message(LlmClient.Role.USER, commitContext.toString()));

        return messages;
    }

    /**
     * Builds messages for manager insight summaries.
     *
     * <p>Includes the current-week snapshot as well as the multi-week historical
     * context (carry-forward streaks, outcome coverage trends, late-lock patterns,
     * and review-turnaround stats) when that data is present in {@code context}.
     */
    public static List<LlmClient.Message> buildManagerInsightsMessages(
            ManagerInsightDataProvider.ManagerWeekContext context
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are an AI assistant that summarizes a manager dashboard for weekly commitments.
                Given team summary metrics, strategic-focus rollups, and multi-week historical patterns,
                draft a concise headline and 2 to 4 insights about alignment gaps, review risk,
                urgency pressure, strategic slack, capacity strain, carry-forward patterns,
                or declining outcome coverage.

                Rules:
                1. Only use the data provided in the dashboard context.
                2. Prefer concrete statements over vague advice (e.g. name specific people or outcomes).
                3. Each insight must include a short title, a detail sentence, and a severity of INFO, WARNING, or POSITIVE.
                4. Prioritise multi-week patterns (carry streaks, declining coverage) over single-week anomalies.
                5. Do not mention unavailable or missing data unless it materially affects the summary.
                6. Respond ONLY with valid JSON matching the required schema.
                """
        ));

        StringBuilder dashboardContext = new StringBuilder();
        dashboardContext.append("Manager dashboard context for week ")
                .append(context.weekStart())
                .append(":\n");
        dashboardContext.append(String.format(
                "Review counts: pending=%d | approved=%d | changesRequested=%d%n",
                context.reviewCounts().pending(),
                context.reviewCounts().approved(),
                context.reviewCounts().changesRequested()
        ));
        dashboardContext.append("Team members:\n");
        for (ManagerInsightDataProvider.TeamMemberContext member : context.teamMembers()) {
            dashboardContext.append(String.format(
                    "- userId: %s | state: %s | reviewStatus: %s | commitCount: %d | incompleteCount: %d | "
                            + "issueCount: %d | nonStrategicCount: %d | kingCount: %d | queenCount: %d | "
                            + "stale: %s | lateLock: %s%n",
                    member.userId(), member.state(), member.reviewStatus(),
                    member.commitCount(), member.incompleteCount(), member.issueCount(),
                    member.nonStrategicCount(), member.kingCount(), member.queenCount(),
                    member.stale(), member.lateLock()
            ));
        }
        dashboardContext.append("Strategic focus rollup:\n");
        for (ManagerInsightDataProvider.RcdoFocusContext focus : context.rcdoFocuses()) {
            dashboardContext.append(String.format(
                    "- outcomeId: %s | outcomeName: %s | objectiveName: %s | rallyCryName: %s | "
                            + "commitCount: %d | kingCount: %d | queenCount: %d%n",
                    focus.outcomeId(), focus.outcomeName(), focus.objectiveName(), focus.rallyCryName(),
                    focus.commitCount(), focus.kingCount(), focus.queenCount()
            ));
        }

        // ── Analytics diagnostic context ─────────────────────────────────────
        appendDiagnosticContext(dashboardContext, context);

        // ── Urgency and slack context ────────────────────────────────────────
        appendUrgencyContext(dashboardContext, context);

        // ── Multi-week historical context ────────────────────────────────────
        appendHistoricalContext(dashboardContext, context);

        messages.add(new LlmClient.Message(LlmClient.Role.ASSISTANT, dashboardContext.toString()));
        return messages;
    }

    /**
     * Appends analytics diagnostic context for manager insights.
     */
    private static void appendDiagnosticContext(
            StringBuilder sb, ManagerInsightDataProvider.ManagerWeekContext context) {

        ManagerInsightDataProvider.DiagnosticContext diagnostics = context.diagnosticContext();
        if (diagnostics == null) {
            return;
        }

        boolean hasCategoryShifts = diagnostics.categoryShifts() != null
                && !diagnostics.categoryShifts().isEmpty();
        boolean hasOutcomeCoverage = diagnostics.outcomeCoverages() != null
                && !diagnostics.outcomeCoverages().isEmpty();
        boolean hasBlockerFrequency = diagnostics.blockerFrequencies() != null
                && !diagnostics.blockerFrequencies().isEmpty();

        if (!hasCategoryShifts && !hasOutcomeCoverage && !hasBlockerFrequency) {
            return;
        }

        sb.append("\nAnalytics diagnostics:\n");

        if (hasCategoryShifts) {
            sb.append("Category mix shifts:\n");
            for (ManagerInsightDataProvider.UserCategoryShiftContext shift : diagnostics.categoryShifts()) {
                sb.append(String.format(
                        "- userId: %s | currentPeriod: %s | priorPeriod: %s%n",
                        shift.userId(),
                        formatDoubleMap(shift.currentPeriod()),
                        formatDoubleMap(shift.priorPeriod())
                ));
            }
        }

        if (hasOutcomeCoverage) {
            sb.append("Per-user outcome coverage:\n");
            for (ManagerInsightDataProvider.UserOutcomeCoverageContext coverage : diagnostics.outcomeCoverages()) {
                String outcomeDetails = coverage.outcomes() == null ? "" : coverage.outcomes().stream()
                        .map(outcome -> outcome.outcomeId() + "@" + outcome.weekStart() + "=" + outcome.commitCount())
                        .collect(Collectors.joining(", "));
                sb.append(String.format(
                        "- userId: %s | outcomes: [%s]%n",
                        coverage.userId(),
                        outcomeDetails
                ));
            }
        }

        if (hasBlockerFrequency) {
            sb.append("Check-in blocker frequency:\n");
            for (ManagerInsightDataProvider.UserBlockerFrequencyContext blocker : diagnostics.blockerFrequencies()) {
                sb.append(String.format(
                        "- userId: %s | atRiskCount: %d | blockedCount: %d | totalCheckIns: %d%n",
                        blocker.userId(),
                        blocker.atRiskCount(),
                        blocker.blockedCount(),
                        blocker.totalCheckIns()
                ));
            }
        }
    }

    /**
     * Appends urgency-band and strategic-slack context for manager insights.
     */
    private static void appendUrgencyContext(
            StringBuilder sb, ManagerInsightDataProvider.ManagerWeekContext context) {

        boolean hasOutcomeUrgencies = context.outcomeUrgencies() != null
                && !context.outcomeUrgencies().isEmpty();
        boolean hasStrategicSlack = context.strategicSlackContext() != null;

        if (!hasOutcomeUrgencies && !hasStrategicSlack) {
            return;
        }

        sb.append("\nUrgency and strategic slack context:\n");

        if (hasStrategicSlack) {
            ManagerInsightDataProvider.StrategicSlackContext slack = context.strategicSlackContext();
            sb.append(String.format(
                    "Strategic slack: slackBand=%s | strategicFocusFloor=%s | atRiskCount=%d | criticalCount=%d%n",
                    slack.slackBand(),
                    formatDecimal(slack.strategicFocusFloor()),
                    slack.atRiskCount(),
                    slack.criticalCount()
            ));
        }

        if (hasOutcomeUrgencies) {
            sb.append("Outcome urgency snapshot:\n");
            for (ManagerInsightDataProvider.OutcomeUrgencyContext urgency : context.outcomeUrgencies()) {
                sb.append(String.format(
                        "- outcomeId: %s | outcomeName: %s | urgencyBand: %s | targetDate: %s | actualProgressPct: %s | expectedProgressPct: %s | progressGapPct: %s | daysRemaining: %s%n",
                        urgency.outcomeId(),
                        urgency.outcomeName(),
                        urgency.urgencyBand(),
                        formatText(urgency.targetDate()),
                        formatDecimal(urgency.progressPct()),
                        formatDecimal(urgency.expectedProgressPct()),
                        formatProgressGap(urgency.progressPct(), urgency.expectedProgressPct()),
                        formatDaysRemaining(urgency.daysRemaining())
                ));
            }
        }
    }

    /**
     * Appends the multi-week historical context section to {@code sb} when any
     * historical signals are present in {@code context}.
     */
    private static void appendHistoricalContext(
            StringBuilder sb, ManagerInsightDataProvider.ManagerWeekContext context) {

        boolean hasStreaks = context.carryForwardStreaks() != null
                && !context.carryForwardStreaks().isEmpty();
        boolean hasTrends = context.outcomeCoverageTrends() != null
                && !context.outcomeCoverageTrends().isEmpty();
        boolean hasLateLock = context.lateLockPatterns() != null
                && !context.lateLockPatterns().isEmpty();
        boolean hasTurnaround = context.reviewTurnaroundStats() != null;

        if (!hasStreaks && !hasTrends && !hasLateLock && !hasTurnaround) {
            return;
        }

        sb.append("\nMulti-week historical context:\n");

        if (hasStreaks) {
            sb.append("Carry-forward streaks (consecutive weeks with 2+ carried items):\n");
            for (ManagerInsightDataProvider.CarryForwardStreak streak : context.carryForwardStreaks()) {
                sb.append(String.format(
                        "- userId: %s | streakWeeks: %d | carriedItems: [%s]%n",
                        streak.userId(),
                        streak.streakWeeks(),
                        String.join(", ", streak.carriedItemTitles())
                ));
            }
        }

        if (hasTrends) {
            sb.append("Outcome coverage trends (commit counts per week, oldest→newest):\n");
            for (ManagerInsightDataProvider.OutcomeCoverageTrend trend : context.outcomeCoverageTrends()) {
                String weekData = trend.weekCounts().stream()
                        .map(wc -> wc.weekStart() + ":" + wc.commitCount())
                        .collect(java.util.stream.Collectors.joining(", "));
                sb.append(String.format(
                        "- outcomeName: %s | %s%n",
                        trend.outcomeName(), weekData
                ));
            }
        }

        if (hasLateLock) {
            sb.append("Late-lock frequency (over rolling window):\n");
            for (ManagerInsightDataProvider.LateLockPattern pattern : context.lateLockPatterns()) {
                sb.append(String.format(
                        "- userId: %s | lateLockWeeks: %d out of %d%n",
                        pattern.userId(), pattern.lateLockWeeks(), pattern.windowWeeks()
                ));
            }
        }

        if (hasTurnaround) {
            ManagerInsightDataProvider.ReviewTurnaroundStats stats = context.reviewTurnaroundStats();
            sb.append(String.format(
                    "Review turnaround: avg %.1f days from lock to first review (based on %d plans)%n",
                    stats.avgDaysToReview(), stats.sampleSize()
            ));
        }
    }

    private static String formatDecimal(BigDecimal value) {
        return value == null ? "n/a" : value.stripTrailingZeros().toPlainString();
    }

    private static String formatDoubleMap(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return "n/a";
        }
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + formatRatio(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String formatRatio(Double value) {
        if (value == null) {
            return "n/a";
        }
        BigDecimal decimal = BigDecimal.valueOf(value);
        return decimal.stripTrailingZeros().toPlainString();
    }

    private static String formatProgressGap(BigDecimal actualProgressPct, BigDecimal expectedProgressPct) {
        if (actualProgressPct == null || expectedProgressPct == null) {
            return "n/a";
        }
        return actualProgressPct.subtract(expectedProgressPct).stripTrailingZeros().toPlainString();
    }

    private static String formatDaysRemaining(long daysRemaining) {
        return daysRemaining == Long.MIN_VALUE ? "no-target" : Long.toString(daysRemaining);
    }

    private static String formatText(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    /**
     * A candidate outcome from the narrowed candidate set.
     */
    public record CandidateOutcome(
            String outcomeId,
            String outcomeName,
            String objectiveName,
            String rallyCryName
    ) {}

    /**
     * Candidate-level urgency annotation for the RCDO suggestion prompt.
     */
    public record CandidateUrgencyContext(
            String outcomeId,
            String outcomeName,
            String urgencyBand,
            String targetDate,
            BigDecimal progressPct,
            BigDecimal expectedProgressPct,
            long daysRemaining
    ) {
        boolean isEscalated() {
            return "AT_RISK".equals(urgencyBand) || "CRITICAL".equals(urgencyBand);
        }
    }

    /**
     * Team-level outcome usage entry used for prompt enrichment.
     *
     * @param outcomeName the snapshot outcome name
     * @param commitCount number of team commits linked to this outcome in the week
     */
    public record TeamOutcomeUsage(
            String outcomeName,
            int commitCount
    ) {}

    /**
     * Enriched context for a single commit in reconciliation draft.
     *
     * @param commitId                     the commit UUID string
     * @param title                        the commit title
     * @param expectedResult               the expected result defined at plan time
     * @param progressNotes                free-form progress notes for the week
     * @param checkInHistory               structured daily check-in entries (may be empty)
     * @param priorCompletionStatuses      completion statuses from carry-forward ancestors,
     *                                     most-recent first (empty if not a carried item)
     * @param categoryCompletionRateContext pre-formatted team category rate context, or
     *                                      {@code null} if unavailable
     */
    public record CommitContext(
            String commitId,
            String title,
            String expectedResult,
            String progressNotes,
            List<CommitDataProvider.CheckInEntry> checkInHistory,
            List<String> priorCompletionStatuses,
            String categoryCompletionRateContext
    ) {}

    /**
     * The JSON schema that RCDO suggestion responses must conform to.
     */
    public static String rcdoSuggestResponseSchema() {
        return """
                {
                  "type": "object",
                  "required": ["suggestions"],
                  "properties": {
                    "suggestions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["outcomeId", "rallyCryName", "objectiveName", "outcomeName", "confidence", "rationale"],
                        "properties": {
                          "outcomeId": { "type": "string" },
                          "rallyCryName": { "type": "string" },
                          "objectiveName": { "type": "string" },
                          "outcomeName": { "type": "string" },
                          "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
                          "rationale": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
    }

    /**
     * The JSON schema for reconciliation draft responses.
     */
    public static String reconciliationDraftResponseSchema() {
        return """
                {
                  "type": "object",
                  "required": ["drafts"],
                  "properties": {
                    "drafts": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["commitId", "suggestedStatus", "suggestedActualResult"],
                        "properties": {
                          "commitId": { "type": "string" },
                          "suggestedStatus": { "type": "string", "enum": ["DONE", "PARTIALLY", "NOT_DONE", "DROPPED"] },
                          "suggestedDeltaReason": { "type": "string" },
                          "suggestedActualResult": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
    }

    /**
     * The JSON schema for manager insight responses.
     */
    public static String managerInsightsResponseSchema() {
        return """
                {
                  "type": "object",
                  "required": ["headline", "insights"],
                  "properties": {
                    "headline": { "type": "string" },
                    "insights": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["title", "detail", "severity"],
                        "properties": {
                          "title": { "type": "string" },
                          "detail": { "type": "string" },
                          "severity": { "type": "string", "enum": ["INFO", "WARNING", "POSITIVE"] }
                        }
                      }
                    }
                  }
                }
                """;
    }

    // ── Next-Work Suggestion Phase 2 ─────────────────────────────────────────

    /**
     * Builds messages for LLM-based next-work suggestion re-ranking (Phase 2/3).
     *
     * <p>Convenience overload without external ticket context — delegates to the
     * full overload with an empty ticket list.
     *
     * @param candidates         Phase-1/Phase-3 data-driven suggestions as the candidate set
     * @param recentCommitHistory user's recent 4-week commit history with outcomes/statuses
     * @param carriedForwardItems user's active carry-forward items for urgency context
     * @param teamCoverageGaps    team-level outcome coverage gaps for strategic context
     * @param weekStart          the Monday being planned for
     * @return ordered messages for the LLM
     */
    public static List<LlmClient.Message> buildNextWorkSuggestMessages(
            List<NextWorkSuggestionService.NextWorkSuggestion> candidates,
            List<NextWorkDataProvider.RecentCommitContext> recentCommitHistory,
            List<NextWorkDataProvider.CarryForwardItem> carriedForwardItems,
            List<NextWorkDataProvider.RcdoCoverageGap> teamCoverageGaps,
            LocalDate weekStart
    ) {
        return buildNextWorkSuggestMessages(
                candidates, recentCommitHistory, carriedForwardItems,
                teamCoverageGaps, List.of(), weekStart);
    }

    /**
     * Builds messages for LLM-based next-work suggestion re-ranking (Phase 2/3).
     *
     * <p>The system prompt instructs the LLM to re-rank the provided candidate
     * suggestions by strategic impact and generate precise rationales.  When
     * {@code linkedTickets} is non-empty, a "Linked external tickets" section is
     * appended to the ASSISTANT context so the LLM can account for ticket state.
     *
     * <p>Security: all user-authored text (commit titles) is placed in the ASSISTANT
     * context message, not the system prompt, mitigating injection per PRD §4.
     *
     * @param candidates         Phase-1/Phase-3 data-driven suggestions as the candidate set
     * @param recentCommitHistory user's recent 4-week commit history with outcomes/statuses
     * @param carriedForwardItems user's active carry-forward items for urgency context
     * @param teamCoverageGaps    team-level outcome coverage gaps for strategic context
     * @param linkedTickets       unresolved external tickets linked to strategic commits (may be empty)
     * @param weekStart          the Monday being planned for
     * @return ordered messages for the LLM
     */
    public static List<LlmClient.Message> buildNextWorkSuggestMessages(
            List<NextWorkSuggestionService.NextWorkSuggestion> candidates,
            List<NextWorkDataProvider.RecentCommitContext> recentCommitHistory,
            List<NextWorkDataProvider.CarryForwardItem> carriedForwardItems,
            List<NextWorkDataProvider.RcdoCoverageGap> teamCoverageGaps,
            List<UserTicketContext> linkedTickets,
            LocalDate weekStart
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        // System prompt — role, rules, and security constraints
        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are a strategic work advisor that helps users identify their highest-impact \
                weekly commitments.
                Given a candidate set of suggested commitments derived from the user's work history \
                and team coverage patterns, re-rank them by strategic impact and generate a specific \
                rationale for each.

                Rules:
                1. ONLY rank suggestions whose suggestionId appears in the candidate list. \
                   Never invent or modify IDs.
                2. Rank ALL candidates from highest to lowest strategic impact.
                3. Respect declined-history suppression already reflected in the candidate set: \
                   do not infer or re-introduce work that is not present in the provided list.
                4. suggestedChessPriority must be one of: KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN \
                   (omit the field if you have no strong recommendation).
                5. confidence must be in range [0.0, 1.0].
                6. Generate a specific, actionable rationale (1–2 sentences) explaining WHY this \
                   item is high-impact for this particular week.
                7. Consider: team outcome coverage gaps, carry-forward urgency, strategic alignment, \
                   and recency of the underlying data.
                8. Respond ONLY with valid JSON matching the required schema.
                """
        ));

        // ASSISTANT context block — candidate set, recent history, and team gaps
        // (not user-authored; safe to include in ASSISTANT role)
        StringBuilder ctx = new StringBuilder();
        ctx.append("Next-work planning context for week of ").append(weekStart).append(":\n\n");

        // ── Candidate suggestions (Phase-1 output) ────────────────────────────
        ctx.append("Candidate suggestions to re-rank (").append(candidates.size()).append(" items):\n");
        for (NextWorkSuggestionService.NextWorkSuggestion s : candidates) {
            ctx.append(String.format(
                    "- suggestionId: %s | type: %s | title: %s",
                    s.suggestionId(), s.source(), s.title()
            ));
            if (s.suggestedOutcomeId() != null) {
                ctx.append(String.format(" | outcomeId: %s", s.suggestedOutcomeId()));
            }
            if (s.sourceDetail() != null) {
                ctx.append(String.format(" | context: %s", s.sourceDetail()));
            }
            if (s.suggestedChessPriority() != null) {
                ctx.append(String.format(" | currentPriority: %s", s.suggestedChessPriority()));
            }
            ctx.append(String.format(" | dataConfidence: %.2f", s.confidence()));
            ctx.append("\n");
        }

        // ── User's recent commit history ───────────────────────────────────────
        if (!recentCommitHistory.isEmpty()) {
            ctx.append("\nUser's last 4 weeks of commits:\n");
            recentCommitHistory.stream().limit(12).forEach(item -> {
                ctx.append(String.format(
                        "- week: %s | title: %s | status: %s",
                        item.weekStart(),
                        item.title(),
                        item.completionStatus()
                ));
                if (item.outcomeName() != null) {
                    ctx.append(" | outcome: ").append(item.outcomeName());
                } else if (item.outcomeId() != null) {
                    ctx.append(" | outcomeId: ").append(item.outcomeId());
                }
                if (item.objectiveName() != null) {
                    ctx.append(" | objective: ").append(item.objectiveName());
                }
                if (item.rallyCryName() != null) {
                    ctx.append(" | rallyCry: ").append(item.rallyCryName());
                }
                ctx.append('\n');
            });
        }

        // ── Active carry-forward items ────────────────────────────────────────
        if (!carriedForwardItems.isEmpty()) {
            ctx.append("\nCarried-forward items needing attention:\n");
            carriedForwardItems.stream().limit(10).forEach(item ->
                    ctx.append(String.format(
                            "- week: %s | title: %s | carryForwardWeeks: %d%s%n",
                            item.sourceWeekStart(),
                            item.title(),
                            item.carryForwardWeeks(),
                            item.outcomeName() != null
                                    ? " | outcome: " + item.outcomeName() : ""
                    ))
            );
        }

        // ── Team outcome coverage gaps ─────────────────────────────────────────
        if (!teamCoverageGaps.isEmpty()) {
            ctx.append("\nTeam outcome coverage gaps (outcomes not worked on recently):\n");
            teamCoverageGaps.stream().limit(8).forEach(gap ->
                    ctx.append(String.format(
                            "- outcomeId: %s | outcomeName: %s | rallyCry: %s | weeksMissing: %d | prevCommits: %d%n",
                            gap.outcomeId(), gap.outcomeName(), gap.rallyCryName(),
                            gap.weeksMissing(), gap.teamCommitsPrevWindow()
                    ))
            );
        }

        // ── Linked external tickets (Phase 3) ─────────────────────────────────
        if (!linkedTickets.isEmpty()) {
            ctx.append("\nLinked external tickets (unresolved, tied to strategic outcomes):\n");
            linkedTickets.stream().limit(10).forEach(ticket -> {
                ctx.append(String.format(
                        "- ticketId: %s | provider: %s",
                        ticket.externalTicketId(), ticket.provider()
                ));
                if (ticket.externalStatus() != null && !ticket.externalStatus().isBlank()) {
                    ctx.append(String.format(" | status: %s", ticket.externalStatus()));
                }
                if (ticket.lastSyncedAt() != null) {
                    ctx.append(String.format(" | lastSynced: %s",
                            ticket.lastSyncedAt().toString().substring(0, 10)));
                }
                if (ticket.outcomeName() != null) {
                    ctx.append(String.format(" | outcome: %s", ticket.outcomeName()));
                }
                if (ticket.rallyCryName() != null) {
                    ctx.append(String.format(" | rallyCry: %s", ticket.rallyCryName()));
                }
                ctx.append('\n');
            });
        }

        messages.add(new LlmClient.Message(LlmClient.Role.ASSISTANT, ctx.toString()));

        // User message — the actual planning request
        messages.add(new LlmClient.Message(
                LlmClient.Role.USER,
                "Suggest highest-impact commitments for this user for week of " + weekStart + "."
        ));

        return messages;
    }

    // ── Effort Type Suggestion ────────────────────────────────────────────────

    /**
     * Builds messages for AI effort type classification (Phase 6, Step 9).
     *
     * <p>Few-shot examples are embedded in the system prompt to guide classification.
     * User-authored text (title, description) is isolated in the USER message.
     *
     * @param title       issue title (required)
     * @param description issue description (may be null)
     * @param outcomeId   optional RCDO outcome ID for additional context
     * @return ordered messages for the LLM
     */
    public static List<LlmClient.Message> buildEffortTypeSuggestMessages(
            String title,
            String description,
            String outcomeId
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are a work classification assistant. Given an issue title and optional description, \
                classify it into exactly one of the four effort types:

                - BUILD: Creating something new — features, tools, content, infrastructure, new services.
                - MAINTAIN: Keeping things running — bug fixes, incidents, ops, tech debt, upgrades.
                - COLLABORATE: Working with or for others — reviews, meetings, hiring, mentoring, customer work.
                - LEARN: Investing in growth — spikes, training, research, experiments, documentation.

                Few-shot examples:
                title: "Implement OAuth2 login flow" → BUILD (confidence: 0.92)
                title: "Fix production memory leak in payment service" → MAINTAIN (confidence: 0.95)
                title: "Review PRs for new team member onboarding" → COLLABORATE (confidence: 0.88)
                title: "Research GraphQL federation options" → LEARN (confidence: 0.90)
                title: "Deploy new recommendation service to staging" → BUILD (confidence: 0.78)
                title: "Patch CVE-2024-1234 in auth library" → MAINTAIN (confidence: 0.93)
                title: "1:1 with direct reports" → COLLABORATE (confidence: 0.97)
                title: "Spike: evaluate vector DB options for semantic search" → LEARN (confidence: 0.91)

                Rules:
                1. Return exactly one effortType from: BUILD, MAINTAIN, COLLABORATE, LEARN.
                2. Return confidence as a number between 0.0 and 1.0.
                3. Respond ONLY with valid JSON matching the required schema.
                4. If the title is ambiguous, use the description to disambiguate.
                """
        ));

        // ASSISTANT context — outcome context if provided (not user-authored)
        if (outcomeId != null && !outcomeId.isBlank()) {
            messages.add(new LlmClient.Message(
                    LlmClient.Role.ASSISTANT,
                    "RCDO outcome context (for disambiguation): outcomeId=" + outcomeId
            ));
        }

        // USER message — untrusted input in separate role
        String userContent = "Issue title: " + title;
        if (description != null && !description.isBlank()) {
            userContent += "\nIssue description: " + description;
        }
        userContent += "\n\nClassify this issue into one of: BUILD, MAINTAIN, COLLABORATE, LEARN.";
        messages.add(new LlmClient.Message(LlmClient.Role.USER, userContent));

        return messages;
    }

    // ── HyDE (Hypothetical Document Embeddings) ───────────────────────────────

    /**
     * Builds messages for HyDE-based issue recommendation.
     *
     * <p>The LLM is asked to write a hypothetical ideal issue document that would
     * perfectly match the user's current capacity, plan state, and outcome context.
     * That hypothetical document is then embedded and used as a query vector against
     * Pinecone — this is the core of the HyDE technique.
     *
     * <p>Security: user-authored text (plan item titles, carried forward titles) is
     * placed in the USER message; structured context goes in the ASSISTANT role.
     *
     * @param userContext    user capacity and current plan state
     * @param riskContext    at-risk outcomes and coverage gaps
     * @return ordered messages for the LLM
     */
    public static List<LlmClient.Message> buildHydeRecommendationPrompt(
            com.weekly.ai.rag.UserWorkContext userContext,
            com.weekly.ai.rag.OutcomeRiskContext riskContext
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are a work planning assistant that generates hypothetical ideal issue documents.
                Given a user's current work context (capacity, plan state, at-risk outcomes, coverage gaps),
                write a short description of an ideal backlog issue that would be most valuable for them
                to pick up this week.

                The document should read like a real issue title + description, not a recommendation.
                It will be used as a semantic search query to find actual matching issues in the backlog.

                Rules:
                1. Write ONE hypothetical issue document: a title line followed by 2-4 sentences of description.
                2. The document must reflect the user's remaining capacity, strategic focus, and risk context.
                3. Prefer issues that address at-risk outcomes or fill coverage gaps when relevant.
                4. Do NOT include any preamble, explanation, or metadata — just the issue document itself.
                5. Keep the total output under 200 words.
                """
        ));

        // ASSISTANT context block — structured planning context (not user-authored free text)
        StringBuilder ctx = new StringBuilder();
        ctx.append("User planning context for week of ").append(userContext.weekStart()).append(":\n\n");
        ctx.append(String.format("Remaining capacity: %.1f hours (cap=%.1f, committed=%.1f)%n",
                userContext.remainingCapacityHours(),
                userContext.realisticWeeklyCapHours(),
                userContext.alreadyCommittedHours()));

        if (!userContext.recentOutcomeIds().isEmpty()) {
            ctx.append("Recently contributing to ").append(userContext.recentOutcomeIds().size())
                    .append(" outcome(s).\n");
        }

        if (riskContext != null && !riskContext.isEmpty()) {
            if (riskContext.atRiskOutcomes() != null && !riskContext.atRiskOutcomes().isEmpty()) {
                ctx.append("\nAt-risk outcomes needing attention:\n");
                riskContext.atRiskOutcomes().stream().limit(5).forEach(o ->
                        ctx.append(String.format("- %s [%s]%s%n",
                                o.outcomeName(),
                                o.urgencyBand(),
                                o.daysRemaining() != null
                                        ? " (" + o.daysRemaining() + " days remaining)" : ""
                        ))
                );
            }
            if (riskContext.coverageGaps() != null && !riskContext.coverageGaps().isEmpty()) {
                ctx.append("\nOutcomes with no recent team coverage:\n");
                riskContext.coverageGaps().stream().limit(5).forEach(g ->
                        ctx.append(String.format("- %s (no commits for %d week(s))%n",
                                g.outcomeName(), g.weeksMissing()))
                );
            }
        }

        messages.add(new LlmClient.Message(LlmClient.Role.ASSISTANT, ctx.toString()));

        // USER message — user-authored context (plan items, carry-forward)
        StringBuilder userMsg = new StringBuilder();
        userMsg.append("My current plan already includes:\n");
        if (userContext.currentPlanItemTitles() != null
                && !userContext.currentPlanItemTitles().isEmpty()) {
            userContext.currentPlanItemTitles().forEach(t ->
                    userMsg.append("- ").append(t).append("\n"));
        } else {
            userMsg.append("(nothing yet)\n");
        }
        if (userContext.carriedForwardTitles() != null
                && !userContext.carriedForwardTitles().isEmpty()) {
            userMsg.append("\nI'm carrying forward:\n");
            userContext.carriedForwardTitles().forEach(t ->
                    userMsg.append("- ").append(t).append("\n"));
        }
        userMsg.append("\nWrite a hypothetical ideal issue document I should work on next.");
        messages.add(new LlmClient.Message(LlmClient.Role.USER, userMsg.toString()));

        return messages;
    }

    /**
     * The JSON schema for effort type suggestion responses.
     */
    public static String effortTypeSuggestResponseSchema() {
        return """
                {
                  "type": "object",
                  "required": ["effortType", "confidence"],
                  "properties": {
                    "effortType": {
                      "type": "string",
                      "enum": ["BUILD", "MAINTAIN", "COLLABORATE", "LEARN"]
                    },
                    "confidence": {
                      "type": "number",
                      "minimum": 0,
                      "maximum": 1
                    }
                  }
                }
                """;
    }

    /**
     * The JSON schema for next-work LLM re-ranking responses.
     *
     * <p>Each item in {@code rankedSuggestions} must reference a {@code suggestionId}
     * from the candidate set. The validator rejects items whose IDs are not in the
     * candidate set to prevent hallucinated suggestions.
     */
    public static String nextWorkSuggestResponseSchema() {
        return """
                {
                  "type": "object",
                  "required": ["rankedSuggestions"],
                  "properties": {
                    "rankedSuggestions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "required": ["suggestionId", "confidence", "rationale"],
                        "properties": {
                          "suggestionId": { "type": "string" },
                          "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
                          "suggestedChessPriority": {
                            "type": "string",
                            "enum": ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"]
                          },
                          "rationale": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
    }
}
