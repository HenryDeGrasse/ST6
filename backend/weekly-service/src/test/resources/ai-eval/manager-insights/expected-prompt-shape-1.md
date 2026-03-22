# Expected Prompt Shape: Manager Insights — Fixture 1

Describes the message structure produced by
`PromptBuilder.buildManagerInsightsMessages(ManagerInsightDataProvider.ManagerWeekContext context)`
when called with the data from `input-context-1.json`.

Fixture scope: 4 direct reports, 2 weeks of historical context, one stale plan,
one chronic carry-forward pattern, and mixed strategic alignment signals.

---

## Message 1 — Role: SYSTEM

### Content (invariant system instructions)

```
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
```

### Invariants
- Role MUST be `SYSTEM`.
- All 6 rules MUST be present.
- No user-authored text appears in this message.

---

## Message 2 — Role: ASSISTANT

The ASSISTANT message is the structured dashboard context. All content is machine-generated.

### Section 1: Header

```
Manager dashboard context for week 2026-03-16:
Review counts: pending=1 | approved=2 | changesRequested=1
```

### Section 2: Team members

One line per member:
```
Team members:
- userId: user-alex-chen | state: LOCKED | reviewStatus: APPROVED | commitCount: 5 | incompleteCount: 0 | issueCount: 0 | nonStrategicCount: 1 | kingCount: 2 | queenCount: 2 | stale: false | lateLock: false
- userId: user-brianna-watts | state: LOCKED | reviewStatus: CHANGES_REQUESTED | commitCount: 4 | incompleteCount: 1 | issueCount: 1 | nonStrategicCount: 2 | kingCount: 1 | queenCount: 1 | stale: false | lateLock: true
- userId: user-carlos-mendez | state: LOCKED | reviewStatus: APPROVED | commitCount: 6 | incompleteCount: 2 | issueCount: 2 | nonStrategicCount: 4 | kingCount: 0 | queenCount: 1 | stale: false | lateLock: false
- userId: user-dana-torres | state: PENDING | reviewStatus: null | commitCount: 0 | incompleteCount: 0 | issueCount: 0 | nonStrategicCount: 0 | kingCount: 0 | queenCount: 0 | stale: true | lateLock: false
```

### Section 3: Strategic focus rollup

```
Strategic focus rollup:
- outcomeId: out-005 | outcomeName: Achieve sub-200ms P95 latency across all core APIs | objectiveName: Achieve top-tier reliability and performance | rallyCryName: Build a world-class platform | commitCount: 7 | kingCount: 2 | queenCount: 3
- outcomeId: out-001 | outcomeName: Enable self-service data portability for all customers | objectiveName: Deliver enterprise-grade compliance features | rallyCryName: Win enterprise customers | commitCount: 4 | kingCount: 1 | queenCount: 2
- outcomeId: out-007 | outcomeName: Increase trial-to-paid conversion rate from 18% to 25% | objectiveName: Improve activation and retention rates | rallyCryName: Grow and retain customers | commitCount: 2 | kingCount: 0 | queenCount: 1
```

### Section 4: Analytics diagnostics (appended by `appendDiagnosticContext`)

Present when `diagnosticContext` is non-null and has at least one of:
`categoryShifts`, `outcomeCoverages`, `blockerFrequencies`.

```
Analytics diagnostics:
Category mix shifts:
- userId: user-carlos-mendez | currentPeriod: DELIVERY=0.33, OPERATIONS=0.17, TECH_DEBT=0.5 | priorPeriod: DELIVERY=0.6, OPERATIONS=0.25, TECH_DEBT=0.15
Per-user outcome coverage:
- userId: user-carlos-mendez | outcomes: [out-005@2026-03-09=2, out-005@2026-03-16=1]
Check-in blocker frequency:
- userId: user-brianna-watts | atRiskCount: 4 | blockedCount: 2 | totalCheckIns: 12
- userId: user-carlos-mendez | atRiskCount: 5 | blockedCount: 3 | totalCheckIns: 14
```

Notes on format:
- Category maps are sorted alphabetically by key and formatted as `KEY=value`.
- Double values are rendered via `BigDecimal.stripTrailingZeros().toPlainString()` (e.g. `0.5` not `0.50`).
- Outcome coverage entries are formatted as `<outcomeId>@<weekStart>=<commitCount>`.

### Section 5: Urgency and strategic slack (appended by `appendUrgencyContext`)

Present when `outcomeUrgencies` or `strategicSlackContext` is non-null.

```
Urgency and strategic slack context:
Strategic slack: slackBand=LOW_SLACK | strategicFocusFloor=0.75 | atRiskCount=1 | criticalCount=1
Outcome urgency snapshot:
- outcomeId: out-005 | outcomeName: Achieve sub-200ms P95 latency across all core APIs | urgencyBand: AT_RISK | targetDate: 2026-06-30 | actualProgressPct: 22 | expectedProgressPct: 35 | progressGapPct: -13 | daysRemaining: 106
- outcomeId: out-007 | outcomeName: Increase trial-to-paid conversion rate from 18% to 25% | urgencyBand: CRITICAL | targetDate: 2026-04-30 | actualProgressPct: 10 | expectedProgressPct: 45 | progressGapPct: -35 | daysRemaining: 45
- outcomeId: out-001 | outcomeName: Enable self-service data portability for all customers | urgencyBand: ON_TRACK | targetDate: 2026-09-30 | actualProgressPct: 40 | expectedProgressPct: 38 | progressGapPct: 2 | daysRemaining: 198
```

Notes:
- `progressGapPct` = `actualProgressPct - expectedProgressPct` (negative means behind target).
- BigDecimal values are formatted via `stripTrailingZeros().toPlainString()`.
- `strategicFocusFloor` renders as `0.75` (not `0.750`).

### Section 6: Multi-week historical context (appended by `appendHistoricalContext`)

Present when any of `carryForwardStreaks`, `outcomeCoverageTrends`, `lateLockPatterns`, or
`reviewTurnaroundStats` is non-null and non-empty.

```
Multi-week historical context:
Carry-forward streaks (consecutive weeks with 2+ carried items):
- userId: user-carlos-mendez | streakWeeks: 2 | carriedItems: [Complete GDPR data deletion audit for inactive accounts, Write performance testing runbook]
Outcome coverage trends (commit counts per week, oldest→newest):
- outcomeName: Increase trial-to-paid conversion rate from 18% to 25% | 2026-03-09:3, 2026-03-16:2
- outcomeName: Achieve sub-200ms P95 latency across all core APIs | 2026-03-09:6, 2026-03-16:7
Late-lock frequency (over rolling window):
- userId: user-brianna-watts | lateLockWeeks: 2 out of 2
Review turnaround: avg 1.8 days from lock to first review (based on 8 plans)
```

### Invariants
- Role MUST be `ASSISTANT`.
- No user-authored text appears in this message.
- Sections 4–6 are absent when the corresponding data fields are null/empty.

---

## Expected JSON Output Schema

Produced by `PromptBuilder.managerInsightsResponseSchema()`:

```json
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
          "title":    { "type": "string" },
          "detail":   { "type": "string" },
          "severity": { "type": "string", "enum": ["INFO", "WARNING", "POSITIVE"] }
        }
      }
    }
  }
}
```

Validated into `AiSuggestionService.ManagerInsightsResult(String status, String headline, List<AiSuggestionService.ManagerInsight> insights)`
with `AiSuggestionService.ManagerInsight(String title, String detail, String severity)`.

## Expected LLM Behavior (Fixture 1)

The response MUST include a `headline` and 2–4 `insights`. Based on fixture data:

| Priority | Expected Insight Theme | Severity | Evidence |
|---|---|---|---|
| 1 | Carlos Mendez carry-forward streak (2 weeks) | `WARNING` | `carryForwardStreaks[user-carlos-mendez].streakWeeks=2` |
| 2 | `out-007` CRITICAL urgency with declining coverage | `WARNING` | `urgencyBand=CRITICAL`, coverage trend 3→2 |
| 3 | Carlos Mendez low strategic alignment (4/6 non-strategic) | `WARNING` | `nonStrategicCount=4`, category shift DELIVERY→TECH_DEBT |
| 4 | Brianna Watts persistent late-lock pattern (2/2 weeks) | `WARNING` | `lateLockPatterns[user-brianna-watts]` |

- The LLM SHOULD name specific people and outcomes per Rule 2.
- `severity` MUST be one of `INFO`, `WARNING`, `POSITIVE`.
- Total insights MUST be between 2 and 4.
