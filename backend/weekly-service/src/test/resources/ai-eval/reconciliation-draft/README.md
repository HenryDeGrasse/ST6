# AI Eval: Reconciliation Draft

## Purpose

This directory contains golden dataset fixtures for regression-testing the
reconciliation-draft prompt. The fixtures capture a locked weekly plan with
enriched mid-week context so that evaluators can replay the prompt against a live
or stub LLM and verify that the output matches expectations without running a full
integration test.

## Relationship to Code

The prompts are built by:
```
PromptBuilder.buildReconciliationDraftMessages(List<PromptBuilder.CommitContext> commits)
```
(class: `com.weekly.ai.PromptBuilder`)

Each `PromptBuilder.CommitContext` record carries:
- `commitId` — the commit UUID string
- `title` — commit title
- `expectedResult` — the expected result set at plan time
- `progressNotes` — free-form progress notes for the week
- `checkInHistory` — `List<CommitDataProvider.CheckInEntry>` (status + optional note per day)
- `priorCompletionStatuses` — completion statuses from carry-forward ancestors, most-recent first
- `categoryCompletionRateContext` — pre-formatted team category rate string, or null

The JSON response schema is defined by `PromptBuilder.reconciliationDraftResponseSchema()`.
Responses are validated into `AiSuggestionService.ReconciliationDraftResult(String status, List<AiSuggestionService.ReconciliationDraftItem> drafts)`.

## Files

| File | Description |
|------|-------------|
| `input-context-1.json` | A locked plan with 4 commits: 1 completed, 1 partial, 1 blocked carry-forward, 1 with no check-ins. |
| `expected-prompt-shape-1.md` | Documents the expected prompt message structure for `input-context-1.json`. |

## How to Use

1. Load `input-context-1.json` in a test harness.
2. Construct `List<PromptBuilder.CommitContext>` from the `commits` array.
3. Call `PromptBuilder.buildReconciliationDraftMessages(commits)`.
4. Compare the produced messages against `expected-prompt-shape-1.md`.
5. Optionally send the prompt to a real LLM and validate the response against
   `PromptBuilder.reconciliationDraftResponseSchema()`.

## Adding New Fixtures

Name additional input files `input-context-N.json` and shape docs `expected-prompt-shape-N.md`.
