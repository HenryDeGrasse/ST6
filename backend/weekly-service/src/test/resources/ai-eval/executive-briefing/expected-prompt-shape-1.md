# Expected Prompt Shape: Executive Briefing — Fixture 1

Describes the message structure produced by
`ExecutiveBriefingService.buildMessages(ExecutiveDashboardService.ExecutiveDashboardResult dashboard)`
when called with the data from `input-context-1.json`.

Dashboard covers week 2026-03-16 with 24 forecasted outcomes across 3 rally cries,
3 team buckets, and 55% strategic utilization.

---

## Message 1 — Role: SYSTEM

### Content (invariant system instructions)

```
You are an executive strategy copilot. Summarize only the aggregate metrics provided.
Produce one headline and 2 to 4 briefing items.

Rules:
1. Use only the supplied metrics. Do not invent organisations, teams, people, or causes.
2. Emphasise forecast health, strategic-vs-non-strategic capacity allocation, and team-level outliers.
3. Keep each detail sentence concrete and metric-grounded.
4. Severity must be INFO, WARNING, or POSITIVE.
5. Respond ONLY with valid JSON matching the required schema.
```

### Invariants
- Role MUST be `SYSTEM`.
- All 5 rules MUST be present.
- No user-authored text appears in this message.

---

## Message 2 — Role: ASSISTANT

The ASSISTANT message is built by `ExecutiveBriefingService.buildDashboardContext(dashboard)`.
All content is machine-generated from aggregate metrics.

### Section 1: Header and summary line

```
Executive strategic health dashboard for week 2026-03-16:
Summary: totalForecasts=24 | onTrack=10 | needsAttention=8 | offTrack=4 | noData=2 | avgForecastConfidence=0.6200 | totalCapacityHours=840.0 | strategicHours=462.0 | nonStrategicHours=378.0 | strategicUtilizationPct=55.00 | nonStrategicUtilizationPct=45.00 | planningCoveragePct=78.00
```

Notes on format:
- `ExecutiveBriefingService` interpolates `BigDecimal` values directly from `ExecutiveDashboardResult`, so the rendered scale is whatever the record carries.
- In fixture 1, confidence values use 4 decimal places, capacity hours use 1 decimal place, and percentage fields use 2 decimal places.

### Section 2: Rally cry rollups (up to 6, sorted by health risk descending)

One line per rally cry:
```
Rally cry rollups:
- rallyCryId: rc-001 | rallyCryName: Win enterprise customers | forecastedOutcomeCount: 8 | onTrack: 2 | needsAttention: 4 | offTrack: 2 | noData: 0 | avgForecastConfidence: 0.4800 | strategicHours: 126.0
- rallyCryId: rc-002 | rallyCryName: Build a world-class platform | forecastedOutcomeCount: 10 | onTrack: 5 | needsAttention: 3 | offTrack: 2 | noData: 0 | avgForecastConfidence: 0.6500 | strategicHours: 210.0
- rallyCryId: rc-003 | rallyCryName: Grow and retain customers | forecastedOutcomeCount: 6 | onTrack: 3 | needsAttention: 1 | offTrack: 0 | noData: 2 | avgForecastConfidence: 0.7400 | strategicHours: 126.0
```

Ordering: `ExecutiveDashboardService` sorts rollups by `offTrackCount DESC`, then
`needsAttentionCount DESC`, then `strategicHours DESC`, then `rallyCryName ASC`.
In fixture 1, `rc-001` and `rc-002` are tied on offTrack (2 each); `rc-001` is first
because it has more needsAttention (4 vs 3). `rc-003` is last (0 off-track).

### Section 3: Aggregate team buckets

One line per bucket:
```
Aggregate team buckets:
- bucketId: engineering | memberCount: 24 | planCoveragePct: 88.00 | totalCapacityHours: 480.0 | strategicHours: 312.0 | nonStrategicHours: 168.0 | strategicUtilizationPct: 65.00 | avgForecastConfidence: 0.6800
- bucketId: product | memberCount: 8 | planCoveragePct: 75.00 | totalCapacityHours: 160.0 | strategicHours: 96.0 | nonStrategicHours: 64.0 | strategicUtilizationPct: 60.00 | avgForecastConfidence: 0.5900
- bucketId: go-to-market | memberCount: 10 | planCoveragePct: 60.00 | totalCapacityHours: 200.0 | strategicHours: 54.0 | nonStrategicHours: 146.0 | strategicUtilizationPct: 27.00 | avgForecastConfidence: 0.5200
```

Notes:
- `strategicCapacityUtilizationPct` renders as `strategicUtilizationPct` in the context block.
- `planCoveragePct` and utilization fields are 0–100 percentages, matching `ExecutiveDashboardService.percentage(...)`.
- `teamGroupingAvailable` is not printed into the prompt, but is part of the `ExecutiveDashboardResult` fixture and should remain present in the JSON.

### Invariants
- Role MUST be `ASSISTANT`.
- No user-authored text appears in this message.
- Summary line format uses `|` as delimiter with no trailing `|`.
- Rally cry rollups are capped at 6 entries (`.stream().limit(6)`).

---

## Expected JSON Output Schema

Produced by `ExecutiveBriefingService.responseSchema()`:

```json
{
  "type": "object",
  "required": ["headline", "insights"],
  "additionalProperties": false,
  "properties": {
    "headline": { "type": "string" },
    "insights": {
      "type": "array",
      "minItems": 2,
      "maxItems": 4,
      "items": {
        "type": "object",
        "required": ["title", "detail", "severity"],
        "additionalProperties": false,
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

Parsed by `ExecutiveBriefingService.parse()` into:
```
ExecutiveBriefingResult(
    status: "ok",
    headline: <non-blank string>,
    insights: List<ExecutiveBriefingItem> [2..4 items]
)
```

If `headline` is blank, or `insights` has fewer than 2 or more than 4 items, or any
`severity` is not `INFO`/`WARNING`/`POSITIVE`, the service returns
`ExecutiveBriefingResult.unavailable()` with status `"unavailable"`.

## Expected LLM Behavior (Fixture 1)

| Priority | Expected Briefing Item | Severity | Driving Metric |
|---|---|---|---|
| 1 | Low strategic capacity allocation org-wide, with go-to-market the biggest outlier | `WARNING` | `strategicUtilizationPct=55.00`; `go-to-market.strategicUtilizationPct=27.00` |
| 2 | `Win enterprise customers` rally cry at risk | `WARNING` | `rc-001.offTrackCount=2`, `rc-001.averageForecastConfidence=0.4800` |
| 3 | Planning coverage gap | `WARNING` | `planningCoveragePct=78.00`; `go-to-market.planCoveragePct=60.00` |
| 4 | `Build a world-class platform` is the largest strategic investment | `INFO` | `rc-002.strategicHours=210.0` |

- The `headline` SHOULD summarize the overall strategic health state in one sentence.
- The LLM MUST NOT invent team names, people, or causes not present in the data.
- All `detail` sentences SHOULD reference specific numeric metrics.
- `insights` count MUST be 2–4 inclusive.
