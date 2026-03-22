# AI Eval: Executive Briefing

## Purpose

This directory contains golden dataset fixtures for regression-testing the executive
briefing prompt. The fixtures capture org-level aggregate strategic-health metrics so
that evaluators can replay the prompt against a live or stub LLM and verify the output
structure without running a full integration test.

## Relationship to Code

The prompts are built by `ExecutiveBriefingService.buildMessages(ExecutiveDashboardService.ExecutiveDashboardResult dashboard)`
(class: `com.weekly.executive.ExecutiveBriefingService`).

`ExecutiveDashboardResult` is produced by `ExecutiveDashboardService.getStrategicHealth(UUID orgId, LocalDate weekStart)`.
It contains:
- `weekStart` — plan week start date
- `summary` — `ExecutiveSummary` with aggregate forecast health, capacity, and planning coverage
- `rallyCryRollups` — `List<RallyCryHealthRollup>` (up to 6, sorted by health risk descending)
- `teamBuckets` — `List<TeamBucketComparison>` (per-bucket member count, capacity, utilization)
- `teamGroupingAvailable` — whether meaningful team bucketing was available for the org

`ExecutiveSummary.planningCoveragePct`, `ExecutiveSummary.strategicCapacityUtilizationPct`,
and `TeamBucketComparison.planCoveragePct` / `strategicCapacityUtilizationPct` are stored as
0–100 percentage `BigDecimal` values, matching `ExecutiveDashboardService.percentage(...)`.

The JSON response schema is defined by `ExecutiveBriefingService.responseSchema()` (a static package-private method).

Response schema shape:
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

Parsed into: `ExecutiveBriefingResult(String status, String headline, List<ExecutiveBriefingItem> insights)`
with `ExecutiveBriefingItem(String title, String detail, String severity)`.

## Files

| File | Description |
|------|-------------|
| `input-context-1.json` | Org-level data: 3 rally cries with mixed health, forecast confidence distribution, planning coverage, team bucket comparisons, and grouping availability. |
| `expected-prompt-shape-1.md` | Documents the expected prompt message structure for `input-context-1.json`. |

## How to Use

1. Load `input-context-1.json` in a test harness.
2. Construct `ExecutiveDashboardResult` from the JSON.
3. Call `ExecutiveBriefingService.buildMessages(dashboard)` (package-private method, accessible in tests).
4. Compare the produced messages against `expected-prompt-shape-1.md`.
5. Optionally send to a real LLM and validate the response is parseable by
   `ExecutiveBriefingService.parse(rawResponse)`.

## Adding New Fixtures

Name additional input files `input-context-N.json` and shape docs `expected-prompt-shape-N.md`.
