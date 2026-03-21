# Phase 2: Multi-Week Strategic Intelligence

> **Priority:** Second. The biggest unlock for managers and leadership.
>
> **Core insight:** Single-week dashboards show what happened. Multi-week
> intelligence shows **what is happening** — patterns, drifts, and risks
> that no human tracks manually across 4–12 weeks of data.

---

## 1. The Problem

### What managers see today

The current manager dashboard shows:
- Team status grid (per user: state, review status, commit counts)
- Strategic focus rollup (commits grouped by RCDO)
- AI-generated headline + 2–4 insights

The AI insights (`PromptBuilder.buildManagerInsightsMessages`) already include
multi-week context:
- carry-forward streaks
- outcome coverage trends
- late-lock patterns
- review turnaround stats

**But the intelligence is shallow.** It reports patterns; it doesn't diagnose
causes, forecast consequences, or recommend actions.

### What managers actually need

1. **"Where is strategy slipping?"** — not just "Outcome X has fewer commits"
   but "Outcome X lost 3 engineers' focus over 4 weeks because ops load spiked"
2. **"Who needs help?"** — not just "Sam has carried items" but "Sam has been
   blocked on the same outcome for 3 weeks and their estimation accuracy is
   dropping"
3. **"What's going to happen?"** — not just "team alignment is 72%" but "if
   current patterns continue, Rally Cry Y will miss its target date"
4. **"Am I the bottleneck?"** — review turnaround, approval delays, feedback
   quality

---

## 2. Three layers of intelligence

### Layer 1: Descriptive — "What has been happening?"

Extend or deepen existing metrics with richer time-series views.

| Metric | Current state | Phase 2 improvement |
|--------|--------------|---------------------|
| Outcome coverage | Current week only in dashboard | 4/8/12-week coverage timelines per outcome |
| Strategic ratio | Single number | Trend line with week-over-week change + team comparison |
| Carry-forward | Count per user | Heatmap: user × week, colored by carry-forward count |
| Late-lock patterns | Badge on current week | Frequency chart: late-lock rate over rolling window |
| Review latency | Avg turnaround stat | Per-user and per-manager breakdown with trend |
| Confidence calibration | Avg confidence vs completion | Scatter plot: predicted confidence vs actual outcome, segmented by user |
| Category allocation | Current distribution | Stacked area chart: category share over time |
| Non-strategic load | Current count | Trend line with team average comparison |

#### New descriptive surfaces

**Outcome coverage timeline**
```
Rally Cry: Scale Revenue
  ├── Objective: Close enterprise deals
  │   ├── Outcome: Sign 3 healthcare pilots
  │   │   Week 1: ████████ 8 commits
  │   │   Week 2: ██████   6 commits
  │   │   Week 3: ██       2 commits  ← declining
  │   │   Week 4: █        1 commit   ← critical
  │   └── Outcome: Build pricing tool
  │       Week 1: ███      3 commits
  │       ...
```

**Carry-forward heatmap**
```
         W1  W2  W3  W4  W5  W6  W7  W8
Alex     0   1   2   2   3   1   0   1
Sam      0   0   0   3   3   4   2   2  ← chronic
Jordan   1   0   0   0   1   0   0   0
```

**Estimation accuracy scatter**
```
Confidence →  0.5    0.7    0.9
              │      │      │
DONE      ●   │  ●●  │ ●●●●●│
PARTIALLY     │ ●●   │  ●●  │
NOT_DONE  ●●  │●     │      │
              │      │      │
              Good calibration = points along diagonal
```

### Layer 2: Diagnostic — "Why is this happening?"

This is where AI adds the most value. Instead of restating numbers, the AI
should identify **causal relationships** and **contextual explanations**.

#### Diagnostic insight types

| Insight type | Example | Data needed |
|-------------|---------|-------------|
| **Capacity displacement** | "Outcome X coverage dropped because team shifted to incident response (Operations category spiked 3× in weeks 3-4)" | Category distribution + outcome coverage trends |
| **Chronic blocker** | "Sam has carried 'Legal review for Acme' for 4 weeks. This may be externally blocked, not under-prioritized." | Carry-forward lineage + check-in statuses |
| **Estimation drift** | "Alex's confidence scores haven't changed but completion rate dropped from 80% to 55% over 6 weeks — possible workload increase or scope creep" | Confidence vs actuals trend per user |
| **Review bottleneck** | "Your average review turnaround increased from 1.2 days to 3.8 days. 4 reconciliations are pending > 3 days." | Review timestamps + plan state history |
| **Strategic dilution** | "Team strategic ratio dropped from 88% to 65%. The increase is entirely in PAWN-priority non-strategic items. Consider whether those should be delegated." | Strategic ratio + chess priority distribution over time |
| **Coverage concentration** | "85% of commits for 'Scale Revenue' come from 2 of 8 team members. Single-point-of-failure risk." | Per-user outcome distribution |

#### AI prompt enrichment for diagnostics

Extend `PromptBuilder.buildManagerInsightsMessages` with:

1. **Category shift data**: week-over-week category allocation changes
2. **Per-user outcome coverage**: who is working on what, and how that changed
3. **Estimation accuracy per user**: confidence vs completion trend
4. **Blocker signals**: check-in AT_RISK/BLOCKED frequency per user per outcome
5. **Review timing**: per-plan time-to-review with trend

Add diagnostic-specific system prompt instructions:

```
When generating insights:
- Do not just restate metrics. Explain WHY a pattern exists if the data supports it.
- Distinguish between "under-invested" (capacity choice) and "blocked" (external dependency).
- When a metric is declining, check if another metric rose simultaneously (displacement).
- Name specific people and outcomes. Vague insights are useless.
- Suggest one concrete action per diagnostic insight.
```

### Layer 3: Predictive — "What is likely to happen?"

This layer is partially Phase 2 (rule-based predictions) and partially
Phase 5 (AI-synthesized forecasts). Phase 2 implements the foundation.

#### Rule-based predictions (Phase 2)

| Prediction | Rule | Confidence |
|-----------|------|------------|
| "Likely to carry forward ≥ 3 items" | User carried 3+ items 2 of last 3 weeks AND current week has ≥ 5 commits | High |
| "Outcome coverage will hit zero" | Coverage has declined for 3 consecutive weeks AND no new commits this week | Medium |
| "Plan will be late-locked" | User has late-locked 3 of last 4 weeks AND current plan is still DRAFT on lock day | High |
| "Reconciliation will be late" | User has submitted reconciliation after deadline 3 of last 5 weeks | Medium |
| "Review turnaround will exceed target" | Manager's rolling average is above target and trending upward | Medium |

#### Data needed for predictions

All data already exists or will exist after Phase 1:
- `weekly_plans` (state history, lock timing)
- `weekly_commits` (carry-forward, outcomes, priorities)
- `weekly_commit_actuals` (completion status)
- `progress_entries` (check-in signals)
- `user_model_snapshots` (Phase 1 — completion patterns)

---

## 3. Product surfaces

### Manager dashboard enhancements

| Surface | Description |
|---------|-------------|
| **Strategic health panel** | Rolling outcome coverage with trend indicators (↑↗→↘↓) |
| **Team attention heatmap** | User × signal matrix showing who needs attention and why |
| **AI diagnostic briefing** | 3-5 insights with causal explanations and action suggestions |
| **Prediction alerts** | "Likely outcomes this week" section with confidence levels |
| **Self-awareness panel** | Manager's own review turnaround, response quality, and team health trends |

### Skip-level / executive view

| Surface | Description |
|---------|-------------|
| **Rally Cry health** | Aggregate coverage, trend, and risk across all teams |
| **Team comparison** | Strategic ratio, completion rate, carry-forward rate by team (aggregate only) |
| **Outcome risk register** | Outcomes with declining coverage or zero recent activity |
| **AI executive briefing** | Natural-language summary of strategic posture |

---

## 4. Data model changes

### New materialized view: `mv_outcome_coverage_weekly`

```sql
CREATE MATERIALIZED VIEW mv_outcome_coverage_weekly AS
SELECT
    wc.org_id,
    wc.outcome_id,
    wp.week_start_date,
    COUNT(*) AS commit_count,
    COUNT(DISTINCT wp.owner_user_id) AS contributor_count,
    SUM(CASE WHEN wc.chess_priority IN ('KING', 'QUEEN') THEN 1 ELSE 0 END) AS high_priority_count
FROM weekly_commits wc
JOIN weekly_plans wp ON wc.weekly_plan_id = wp.id
WHERE wc.outcome_id IS NOT NULL
  AND wp.state IN ('LOCKED', 'RECONCILING', 'RECONCILED', 'CARRY_FORWARD')
GROUP BY wc.org_id, wc.outcome_id, wp.week_start_date;
```

### New materialized view: `mv_user_weekly_summary`

```sql
CREATE MATERIALIZED VIEW mv_user_weekly_summary AS
SELECT
    wp.org_id,
    wp.owner_user_id,
    wp.week_start_date,
    wp.state,
    wp.lock_type,
    COUNT(wc.id) AS total_commits,
    SUM(CASE WHEN wc.outcome_id IS NOT NULL THEN 1 ELSE 0 END) AS strategic_commits,
    SUM(CASE WHEN wc.carried_from_commit_id IS NOT NULL THEN 1 ELSE 0 END) AS carried_commits,
    AVG(wc.confidence) AS avg_confidence,
    -- Completion stats (only for reconciled plans)
    SUM(CASE WHEN a.completion_status = 'DONE' THEN 1 ELSE 0 END) AS done_count,
    SUM(CASE WHEN a.completion_status IS NOT NULL THEN 1 ELSE 0 END) AS reconciled_count
FROM weekly_plans wp
LEFT JOIN weekly_commits wc ON wc.weekly_plan_id = wp.id
LEFT JOIN weekly_commit_actuals a ON a.commit_id = wc.id
GROUP BY wp.org_id, wp.owner_user_id, wp.week_start_date, wp.state, wp.lock_type;
```

### Materialized view refresh

- Schedule: every 15 minutes or on significant event batch
- Lightweight: these are aggregations of existing data, not new tables
- Can run on read replica if ADR-001 is implemented

---

## 5. AI improvements for manager insights

### Enhanced `ManagerInsightDataProvider`

Add new context blocks to the data provider:

| Context | Data source | Purpose |
|---------|-------------|---------|
| `categoryShifts` | Per-user category distribution, current vs prior 4 weeks | Detect capacity displacement |
| `perUserOutcomeCoverage` | Who works on which outcomes, and how that changed | Detect concentration risk |
| `estimationAccuracyByUser` | Confidence vs completion trend per user | Detect calibration drift |
| `blockerFrequency` | AT_RISK/BLOCKED check-in rate per user per outcome | Distinguish blocked vs under-invested |
| `reviewTimeline` | Per-plan review timestamps and turnaround | Detect manager bottleneck |

### Enhanced system prompt

```
You are analyzing a manager's team dashboard with multi-week historical data.

Provide three types of insights:

DIAGNOSTIC insights (most valuable):
- Explain WHY a pattern exists, not just WHAT the pattern is.
- If coverage dropped on an outcome, check if another area absorbed the capacity.
- If a user has chronic carry-forward, check if their check-ins show BLOCKED status.
- Distinguish "under-invested by choice" from "blocked by dependency."

PREDICTIVE insights:
- Based on trends, what is likely to happen this coming week?
- Flag risks before they become problems.

SELF-REFLECTION insights:
- Is the manager's own review cadence affecting team velocity?
- Are there patterns the manager should adjust?

For each insight, provide:
- title (short)
- detail (one sentence explaining the situation)
- action (one concrete thing the manager could do)
- severity: POSITIVE, INFO, WARNING, or CRITICAL
```

---

## 6. Success metrics

| Metric | Target |
|--------|--------|
| Manager dashboard session duration | +30% (spending more time because it's more useful) |
| Manager-initiated interventions | Track: are managers reaching out to ICs based on dashboard signals? |
| Outcome coverage gap detection | System identifies gaps 2+ weeks before manual discovery |
| Insight action rate | ≥ 30% of WARNING/CRITICAL insights result in a concrete action within 5 days |
| Prediction accuracy | ≥ 70% of "likely to carry forward" predictions are correct |

---

## 7. Implementation order within this phase

1. **Materialized views** — coverage timeline, user weekly summary
2. **Descriptive dashboard extensions** — outcome timelines, heatmaps
3. **Enhanced data provider** — richer context for AI prompts
4. **Diagnostic AI prompt upgrade** — causal reasoning instructions
5. **Rule-based predictions** — carry-forward, late-lock, coverage decline
6. **Self-awareness panel** — manager's own metrics
7. **Skip-level aggregate view** — Rally Cry health, team comparison
