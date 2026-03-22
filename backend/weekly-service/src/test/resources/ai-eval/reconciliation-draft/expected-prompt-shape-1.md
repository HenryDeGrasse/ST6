# Expected Prompt Shape: Reconciliation Draft — Fixture 1

Describes the message structure produced by
`PromptBuilder.buildReconciliationDraftMessages(List<PromptBuilder.CommitContext> commits)`
when called with the 4 commits from `input-context-1.json`.

---

## Message 1 — Role: SYSTEM

### Content (invariant system instructions)

```
You are an AI assistant that helps with weekly reconciliation.
Given a list of weekly commitments enriched with mid-week check-in history,
carry-forward context, and team category completion rates, suggest a completion
status and actual result for each.

Rules:
1. For each commit, suggest one of: DONE, PARTIALLY, NOT_DONE, DROPPED.
2. If not DONE, provide a brief suggestedDeltaReason.
3. Provide a suggestedActualResult summarizing what was accomplished.
4. Use check-in history signals (AT_RISK, BLOCKED) as strong indicators of
   PARTIALLY or NOT_DONE.
5. Use DONE_EARLY check-in signals as indicators of DONE.
6. When a commit has prior carry-forward statuses (e.g. PARTIALLY twice),
   treat that as evidence of a chronic blocker and explain in the delta reason.
7. Use team category completion rates as calibration context; do not override
   direct progress signals with statistical baselines.
8. Be conservative — if progress notes are ambiguous, suggest PARTIALLY.
9. Respond ONLY with valid JSON matching the required schema.
```

### Invariants
- Role MUST be `SYSTEM`.
- All 9 rules MUST be present.
- No user-authored text appears in this message.

---

## Message 2 — Role: USER

The single USER message contains a structured list of all commits. Each commit
occupies one block with optional sub-lines for check-ins, carry-forward, and team
rate context.

### Preamble

```
Commitments to reconcile:
```

### Commit Block Format

Primary line:
```
- commitId: <id> | title: <title> | expectedResult: <expectedResult> | progressNotes: <progressNotes>
```

When `progressNotes` is `null` or blank, the field renders as the literal string `null`.

#### Sub-line: check-ins (only when checkInHistory is non-empty)

```
  check-ins: [<STATUS>: "<note>"] [<STATUS>: "<note>"] ...
```

When a check-in entry has no note (null/blank), the entry renders as `[<STATUS>]` without quotes.

#### Sub-line: carry-forward (only when priorCompletionStatuses is non-empty)

```
  carry-forward: carried <N> time(s); prior statuses: <STATUS1>, <STATUS2>, ...
```

N equals the size of `priorCompletionStatuses`. Statuses are joined with `", "`, most-recent first.

#### Sub-line: team rate (only when categoryCompletionRateContext is non-null and non-blank)

```
  team rate: <categoryCompletionRateContext>
```

### Expected USER Message Content for Fixture 1

```
Commitments to reconcile:
- commitId: d2b3c4d5-0001-0000-0000-000000000001 | title: Deploy rate-limiting middleware to production | expectedResult: Rate limiting deployed on all public API endpoints; P99 abuse rejection rate > 99%. | progressNotes: Deployed to prod on Tuesday. Monitoring dashboards show abuse traffic dropping from 12% to 0.3% of requests. All acceptance tests passed.
  check-ins: [ON_TRACK: "Staging deployment complete, tests passing."] [ON_TRACK: "Production deploy in progress."] [DONE_EARLY: "Deployed and verified. Monitoring looks great."]
  team rate: OPERATIONS: 88% DONE (team, last 4 wks)
- commitId: d2b3c4d5-0002-0000-0000-000000000002 | title: Migrate legacy auth module to OAuth 2.0 | expectedResult: All legacy session-based auth endpoints replaced with OAuth 2.0 token flow. Zero regressions in auth test suite. | progressNotes: Completed the token issuance flow and access token validation. Refresh token rotation and revocation still not done. Auth test suite shows 18/24 tests passing.
  check-ins: [ON_TRACK: "Token issuance implemented and unit tested."] [AT_RISK: "Refresh token rotation taking longer than expected. May not finish all endpoints this week."] [AT_RISK: "Revocation endpoint still blocked on security review."]
  team rate: DELIVERY: 72% DONE (team, last 4 wks)
- commitId: d2b3c4d5-0003-0000-0000-000000000003 | title: Complete GDPR data deletion audit for inactive accounts | expectedResult: Audit report identifying all inactive accounts (> 2 years) with personal data still retained. Deletion scripts reviewed by legal. | progressNotes: Started the database query to find inactive accounts. Query is running but taking much longer than expected due to missing index. Have not yet produced the report or contacted legal.
  check-ins: [AT_RISK: "Query performance is poor; missing index on last_login column."] [BLOCKED: "DBA review of index creation request delayed until next sprint."] [BLOCKED: "Still waiting on DBA. No progress possible without the index."]
  carry-forward: carried 2 time(s); prior statuses: PARTIALLY, NOT_DONE
  team rate: CUSTOMER: 55% DONE (team, last 4 wks)
- commitId: d2b3c4d5-0004-0000-0000-000000000004 | title: Write runbook for database failover procedure | expectedResult: Runbook documented in Confluence covering failover triggers, step-by-step switchover commands, rollback procedure, and post-failover verification checklist. | progressNotes: null
```

### Invariants
- Role MUST be `USER`.
- All 4 commits MUST appear in input order.
- The `check-ins:` sub-line MUST be absent for commit 4 (empty checkInHistory).
- The `carry-forward:` sub-line MUST be absent for commits 1, 2, and 4.
- The `team rate:` sub-line MUST be absent for commit 4 (null categoryCompletionRateContext).
- User-authored text (titles, progress notes, expected results) appears in USER role, not SYSTEM role.

---

## Expected JSON Output Schema

Produced by `PromptBuilder.reconciliationDraftResponseSchema()`:

```json
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
          "commitId":             { "type": "string" },
          "suggestedStatus":      { "type": "string", "enum": ["DONE", "PARTIALLY", "NOT_DONE", "DROPPED"] },
          "suggestedDeltaReason": { "type": "string" },
          "suggestedActualResult":{ "type": "string" }
        }
      }
    }
  }
}
```

Validated into `AiSuggestionService.ReconciliationDraftResult(String status, List<AiSuggestionService.ReconciliationDraftItem> drafts)`
with `AiSuggestionService.ReconciliationDraftItem(String commitId, String suggestedStatus, String suggestedDeltaReason, String suggestedActualResult)`.

## Expected LLM Behavior (Fixture 1)

| commitId (suffix) | Expected `suggestedStatus` | Rationale |
|---|---|---|
| `...0001` | `DONE` | DONE_EARLY check-in, strong progress notes, team rate 88%. |
| `...0002` | `PARTIALLY` | Two AT_RISK check-ins, 18/24 tests passing, incomplete scope. |
| `...0003` | `NOT_DONE` | Two BLOCKED check-ins, prior PARTIALLY + NOT_DONE statuses indicating chronic blocker; minimal progress against expected result. |
| `...0004` | `NOT_DONE` | No check-ins, null progress notes; conservative default. |

- `suggestedDeltaReason` MUST be present for `...0002`, `...0003`, and `...0004` (not DONE).
- For `...0003`, `suggestedDeltaReason` SHOULD mention the carry-forward pattern / chronic blocker.
- `suggestedActualResult` MUST be present for all 4 commits.
