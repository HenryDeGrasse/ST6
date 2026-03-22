# AI Eval: Manager Insights

## Purpose

This directory contains golden dataset fixtures for regression-testing the manager
insights prompt. The fixtures let evaluators replay a known team dashboard context
against a live or stub LLM and compare the output structure to expectations without
running a full integration test.

## Relationship to Code

The prompts are built by:
```
PromptBuilder.buildManagerInsightsMessages(
    ManagerInsightDataProvider.ManagerWeekContext context
)
```
(class: `com.weekly.ai.PromptBuilder`)

`ManagerWeekContext` includes:
- `weekStart` — the plan week start date
- `reviewCounts` — `ReviewCounts(pending, approved, changesRequested)`
- `teamMembers` — `List<TeamMemberContext>` with per-member state, commit counts, review status
- `rcdoFocuses` — `List<RcdoFocusContext>` with strategic focus rollup
- `diagnosticContext` — optional `DiagnosticContext` with category shifts, outcome coverages, blocker frequencies
- `outcomeUrgencies` — optional `List<OutcomeUrgencyContext>` (urgency bands per outcome)
- `strategicSlackContext` — optional `StrategicSlackContext` (slack band, at-risk/critical counts)
- `carryForwardStreaks` — optional `List<CarryForwardStreak>` (multi-week carry patterns)
- `outcomeCoverageTrends` — optional `List<OutcomeCoverageTrend>` (commit counts per week per outcome)
- `lateLockPatterns` — optional `List<LateLockPattern>` (late-lock frequency per user)
- `reviewTurnaroundStats` — optional `ReviewTurnaroundStats` (avg days to first review)

The JSON response schema is defined by `PromptBuilder.managerInsightsResponseSchema()`.
Responses are validated into `AiSuggestionService.ManagerInsightsResult(String status, String headline, List<AiSuggestionService.ManagerInsight> insights)`.

## Files

| File | Description |
|------|-------------|
| `input-context-1.json` | 4 direct reports with 2 weeks of history, mixed strategic-alignment signals, urgency context, strategic slack, and historical patterns. |
| `expected-prompt-shape-1.md` | Documents the expected prompt message structure for `input-context-1.json`. |

## How to Use

1. Load `input-context-1.json` in a test harness.
2. Construct `ManagerWeekContext` from the JSON fields.
3. Call `PromptBuilder.buildManagerInsightsMessages(context)`.
4. Compare the produced messages against `expected-prompt-shape-1.md`.
5. Optionally send the prompt to a real LLM and validate the response against
   `PromptBuilder.managerInsightsResponseSchema()`.

## Adding New Fixtures

Name additional input files `input-context-N.json` and shape docs `expected-prompt-shape-N.md`.
