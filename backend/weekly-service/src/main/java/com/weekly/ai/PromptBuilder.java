package com.weekly.ai;

import com.weekly.shared.ManagerInsightDataProvider;

import java.util.ArrayList;
import java.util.List;

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
     * Builds messages for RCDO auto-suggest.
     *
     * @param title           the commitment title (user input)
     * @param description     the commitment description (user input)
     * @param candidateOutcomes the narrowed candidate set of RCDO outcomes
     * @return ordered messages for the LLM
     */
    public static List<LlmClient.Message> buildRcdoSuggestMessages(
            String title,
            String description,
            List<CandidateOutcome> candidateOutcomes
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
                5. Respond ONLY with valid JSON matching the required schema.
                """
        ));

        // Context message — RCDO candidate set (not user-authored)
        StringBuilder candidateContext = new StringBuilder("Available RCDO outcomes:\n");
        for (CandidateOutcome c : candidateOutcomes) {
            candidateContext.append(String.format(
                    "- outcomeId: %s | outcomeName: %s | objectiveName: %s | rallyCryName: %s%n",
                    c.outcomeId(), c.outcomeName(), c.objectiveName(), c.rallyCryName()
            ));
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
     * Builds messages for reconciliation draft suggestions.
     */
    public static List<LlmClient.Message> buildReconciliationDraftMessages(
            List<CommitContext> commits
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are an AI assistant that helps with weekly reconciliation.
                Given a list of weekly commitments with their titles, descriptions, expected results, \
                and progress notes, suggest a completion status and actual result for each.
                
                Rules:
                1. For each commit, suggest one of: DONE, PARTIALLY, NOT_DONE, DROPPED.
                2. If not DONE, provide a brief suggestedDeltaReason.
                3. Provide a suggestedActualResult summarizing what was accomplished.
                4. Be conservative — if progress notes are ambiguous, suggest PARTIALLY.
                5. Respond ONLY with valid JSON matching the required schema.
                """
        ));

        StringBuilder commitContext = new StringBuilder("Commitments to reconcile:\n");
        for (CommitContext c : commits) {
            commitContext.append(String.format(
                    "- commitId: %s | title: %s | expectedResult: %s | progressNotes: %s%n",
                    c.commitId(), c.title(), c.expectedResult(), c.progressNotes()
            ));
        }
        messages.add(new LlmClient.Message(LlmClient.Role.USER, commitContext.toString()));

        return messages;
    }

    /**
     * Builds messages for manager insight summaries.
     */
    public static List<LlmClient.Message> buildManagerInsightsMessages(
            ManagerInsightDataProvider.ManagerWeekContext context
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();

        messages.add(new LlmClient.Message(
                LlmClient.Role.SYSTEM,
                """
                You are an AI assistant that summarizes a manager dashboard for weekly commitments.
                Given team summary metrics and strategic-focus rollups, draft a concise headline and
                2 to 4 insights about alignment gaps, review risk, capacity strain, or execution patterns.
                
                Rules:
                1. Only use the data provided in the dashboard context.
                2. Prefer concrete statements over vague advice.
                3. Each insight must include a short title, a detail sentence, and a severity of INFO, WARNING, or POSITIVE.
                4. Do not mention unavailable or missing data unless it materially affects the summary.
                5. Respond ONLY with valid JSON matching the required schema.
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

        messages.add(new LlmClient.Message(LlmClient.Role.ASSISTANT, dashboardContext.toString()));
        return messages;
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
     * Context for a single commit in reconciliation draft.
     */
    public record CommitContext(
            String commitId,
            String title,
            String expectedResult,
            String progressNotes
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
}
