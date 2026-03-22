# AI Eval: RCDO Auto-Suggest

## Purpose

This directory contains golden dataset fixtures for regression-testing the RCDO
(Rally Cry → Defining Objective → Outcome) auto-suggest prompt. The fixtures let
evaluators replay a known input against a live or stub LLM and compare the output
to an expected shape — catching regressions in prompt engineering without running
a full integration test.

## Relationship to Code

The prompts are built by:
```
PromptBuilder.buildRcdoSuggestMessages(
    String title,
    String description,
    List<PromptBuilder.CandidateOutcome> candidateOutcomes,
    List<PromptBuilder.TeamOutcomeUsage> topTeamOutcomes,
    List<String> zeroCoverageOutcomeNames,
    List<PromptBuilder.CandidateUrgencyContext> candidateUrgencies
)
```
(class: `com.weekly.ai.PromptBuilder`)

The JSON response schema is defined by `PromptBuilder.rcdoSuggestResponseSchema()`.
Responses are validated into `AiSuggestionService.SuggestionResult(String status, List<AiSuggestionService.RcdoSuggestion> suggestions)`.

## Files

| File | Description |
|------|-------------|
| `input-context-1.json` | A plan with 3 commits (2 outcome-linked, 1 unlinked), team RCDO tree with 3 rally cries / 5 objectives / 9 outcomes, urgency context, and team usage context. |
| `expected-prompt-shape-1.md` | Documents the expected structure of the prompt messages produced from `input-context-1.json`. |

## How to Use

1. Load `input-context-1.json` in a test harness.
2. Call `PromptBuilder.buildRcdoSuggestMessages(...)` with the fields from the JSON.
3. Compare the produced prompt messages against the structure described in
   `expected-prompt-shape-1.md`.
4. Optionally send the prompt to a real LLM and validate the JSON response against
   `PromptBuilder.rcdoSuggestResponseSchema()`.

## Adding New Fixtures

Name additional input files `input-context-N.json` and their corresponding shape
documents `expected-prompt-shape-N.md`. Increment N sequentially.
