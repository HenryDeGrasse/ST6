# Phase 3: RCDO Target Dates & Urgency Modeling

> **Priority:** Third. Adds the time dimension to strategic intelligence.
>
> **Core insight:** "Is this work strategic?" is a useful question.
> "How urgent is this strategy, and are we on track?" is the question
> that actually changes behavior. This phase makes the system
> time-aware.

---

## 1. The Problem

### Strategy without deadlines is just aspiration

Currently, RCDO outcomes are static labels:
- they have names and parent objectives
- commits can be linked to them
- the system tracks coverage (how many commits per outcome)

But there's no sense of:
- **when** an outcome needs to be achieved
- **how much progress** has been made toward it
- **whether current effort is sufficient** to meet the deadline
- **how much slack** is available for non-strategic work

### Abstract goals are hard to measure

Many strategic outcomes are abstract:
- "Improve API uptime monitoring"
- "Scale enterprise pipeline"
- "Reduce customer churn in mid-market"

How do you discretize progress toward "reduce customer churn"?

This is the core modeling challenge.

---

## 2. Modeling progress toward abstract goals

### The discretization problem

We need a framework that works for:
- **Measurable outcomes**: "Sign 3 healthcare pilot customers" → countable
- **Milestone outcomes**: "Launch pricing tool" → binary, but with intermediate steps
- **Continuous outcomes**: "Improve API uptime" → metric-based, gradual

### Proposed approach: multi-signal progress model

Instead of forcing one progress type, support three complementary signals:

#### Signal 1: Metric-based progress (when quantifiable)

```json
{
  "progressType": "METRIC",
  "metricName": "Healthcare pilot customers signed",
  "targetValue": 3,
  "currentValue": 1,
  "unit": "customers"
}
```

Progress = `currentValue / targetValue` = 33%

**Who updates this:** Admin or outcome owner, periodically. Or integrated
from an external data source.

#### Signal 2: Milestone-based progress (when sequential)

```json
{
  "progressType": "MILESTONE",
  "milestones": [
    { "name": "Research complete", "status": "DONE", "weight": 0.15 },
    { "name": "Design approved", "status": "DONE", "weight": 0.20 },
    { "name": "MVP built", "status": "IN_PROGRESS", "weight": 0.30 },
    { "name": "Beta tested", "status": "NOT_STARTED", "weight": 0.20 },
    { "name": "Launched", "status": "NOT_STARTED", "weight": 0.15 }
  ]
}
```

Progress = sum of completed milestone weights = 35%
(IN_PROGRESS milestones can contribute partial weight)

**Who updates this:** The team, as part of monthly or quarterly reviews.
Could also be partially automated from commit reconciliation patterns.

#### Signal 3: Activity-based progress (always available, derived)

Even without explicit metrics or milestones, the system can infer
progress from commitment activity:

```json
{
  "progressType": "ACTIVITY",
  "weeklyCommitCounts": [8, 6, 2, 1],
  "completionRate": 0.72,
  "contributorCount": 3,
  "avgChessPriority": "ROOK",
  "carryForwardRate": 0.25
}
```

Activity-based progress is never as precise, but it's **always available**
and can serve as a signal even when no one has configured metrics.

### Composite progress score

Combine available signals:

```
if METRIC available:
    progress = metricProgress * 0.6 + activitySignal * 0.4
elif MILESTONE available:
    progress = milestoneProgress * 0.5 + activitySignal * 0.5
else:
    progress = activitySignal * 1.0  (lower confidence)
```

The system should always show which signals are contributing and at what
confidence level.

---

## 3. Target date and urgency model

### Data model additions

#### Outcome metadata (extend RCDO)

Since WC reads RCDO from an upstream service, these fields could be:
- **Option A:** added to the upstream RCDO service (ideal, if owned)
- **Option B:** stored locally as `outcome_metadata` overlay in WC's database

Option B is more realistic for MVP because it doesn't require upstream changes.

```sql
CREATE TABLE outcome_metadata (
    org_id          UUID NOT NULL,
    outcome_id      UUID NOT NULL,
    target_date     DATE,
    progress_type   VARCHAR(20) DEFAULT 'ACTIVITY',
    -- Metric-based fields (nullable)
    metric_name     VARCHAR(200),
    target_value    NUMERIC,
    current_value   NUMERIC,
    unit            VARCHAR(50),
    -- Milestones stored as JSONB
    milestones      JSONB,
    -- Derived/cached fields
    progress_pct    NUMERIC(5,2),
    urgency_band    VARCHAR(20),
    last_computed_at TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (org_id, outcome_id)
);
```

#### Urgency bands

| Band | Meaning | Derived from |
|------|---------|-------------|
| `ON_TRACK` | Progress is ahead or on pace for the target date | progress ≥ expected_progress_by_now |
| `NEEDS_ATTENTION` | Progress is slightly behind or coverage is declining | progress < expected - 10% OR coverage declining 2+ weeks |
| `AT_RISK` | Significant gap between expected and actual progress | progress < expected - 25% OR coverage hit zero |
| `CRITICAL` | Target date is near and progress is far behind | days_remaining < 30 AND progress < 50% |
| `NO_TARGET` | No target date set; urgency is undefined | target_date IS NULL |

### Computing urgency

```python
# Pseudocode for urgency computation
def compute_urgency(outcome):
    if outcome.target_date is None:
        return "NO_TARGET"

    days_total = (outcome.target_date - outcome.created_date).days
    days_elapsed = (today - outcome.created_date).days
    days_remaining = (outcome.target_date - today).days

    if days_remaining <= 0:
        return "CRITICAL"  # past due

    expected_progress = days_elapsed / days_total
    actual_progress = outcome.progress_pct / 100.0

    gap = expected_progress - actual_progress

    if gap < 0:  # ahead of schedule
        return "ON_TRACK"
    elif gap < 0.10:
        return "ON_TRACK"
    elif gap < 0.25:
        return "NEEDS_ATTENTION"
    elif days_remaining < 30 and actual_progress < 0.50:
        return "CRITICAL"
    else:
        return "AT_RISK"
```

### Expected progress curve

Linear expectation is a starting point, but not all work is linear. Options:

- **Linear**: simple, transparent, often wrong
- **S-curve**: slow start, fast middle, slow finish (common in projects)
- **Back-loaded**: most progress happens near the end (research-heavy work)

For MVP: use linear with a configuration option to set the expected curve
type per outcome. Default: linear.

---

## 4. Strategic slack concept

### The key insight

Instead of "non-strategic work is bad," the system should say:

> Given where we are relative to strategic targets, here's how much
> non-strategic work is tolerable this week.

### Recommended strategic focus floor

```python
def compute_strategic_focus_floor(team_outcomes):
    """Returns the minimum % of team commits that should be strategic."""

    # Base: always at least 50% strategic
    floor = 0.50

    # Increase floor for each AT_RISK or CRITICAL outcome
    for outcome in team_outcomes:
        if outcome.urgency_band == "CRITICAL":
            floor += 0.15
        elif outcome.urgency_band == "AT_RISK":
            floor += 0.10
        elif outcome.urgency_band == "NEEDS_ATTENTION":
            floor += 0.05

    # Cap at 95% — some non-strategic work is always necessary
    return min(floor, 0.95)
```

### Slack bands

| Band | Meaning | Strategic focus floor |
|------|---------|----------------------|
| `HIGH_SLACK` | All outcomes on-track or ahead | 50% |
| `MODERATE_SLACK` | Minor attention needed | 60-70% |
| `LOW_SLACK` | Significant strategic pressure | 75-85% |
| `NO_SLACK` | Critical outcomes at risk | 90-95% |

### Where slack surfaces

| Surface | How it appears |
|---------|----------------|
| **IC plan editor** | "Team strategic focus this week: 75% recommended (1 outcome AT_RISK)" |
| **Lock-time quality nudge** | "Your plan is 45% strategic. Given current outcome pressure, 75% is recommended." |
| **Manager dashboard** | "Strategic slack: LOW — 2 outcomes need attention before target dates" |
| **AI plan suggestions** | Next-work engine biases toward strategic outcomes when slack is LOW |

---

## 5. Integration with existing features

### Plan quality nudge (`DefaultPlanQualityService`)

Add urgency-aware nudges:

```java
// New nudge type
if (strategicFocusFloor > actualStrategicRatio) {
    nudges.add(new QualityNudge(
        "URGENCY_FOCUS_GAP",
        "Your plan is " + pct(actualStrategicRatio) + " strategic, but "
            + pct(strategicFocusFloor) + " is recommended due to "
            + atRiskCount + " outcome(s) approaching their target date.",
        "WARNING"
    ));
}
```

### Next-work suggestions (`DefaultNextWorkSuggestionService`)

Boost confidence scores for suggestions linked to AT_RISK or CRITICAL outcomes:

```java
// In addCoverageGapSuggestions:
double urgencyMultiplier = switch (outcomeUrgency) {
    case "CRITICAL" -> 1.4;
    case "AT_RISK" -> 1.2;
    case "NEEDS_ATTENTION" -> 1.1;
    default -> 1.0;
};
suggestion.confidence *= urgencyMultiplier;
```

### Manager insights prompt

Add to the context block:
```
Outcome urgency status:
- "Sign 3 healthcare pilots" — AT_RISK (target: June 30, progress: 33%, expected: 55%)
- "Build pricing tool" — ON_TRACK (target: Aug 15, progress: 40%, expected: 35%)
- "Reduce churn" — CRITICAL (target: May 1, progress: 20%, expected: 80%)
```

### Trends service (`DefaultTrendsService`)

Add outcome-level progress trends to the IC view:
```json
{
  "outcomeTrends": [
    {
      "outcomeName": "Sign 3 healthcare pilots",
      "urgencyBand": "AT_RISK",
      "progressPct": 33,
      "expectedProgressPct": 55,
      "targetDate": "2026-06-30",
      "weeklyCommitTrend": [8, 6, 2, 1]
    }
  ]
}
```

---

## 6. Who can set target dates and progress?

| Action | Who can do it |
|--------|--------------|
| Set/change target date | Admin, manager, outcome owner |
| Update metric current value | Admin, outcome owner, or automated integration |
| Update milestone status | Team members working on the outcome, manager |
| Override urgency band | Admin only (for edge cases where derived urgency is wrong) |

---

## 7. Edge cases

| Case | Handling |
|------|---------|
| No target date set | Urgency = `NO_TARGET`; system falls back to coverage-based signals only |
| Target date in the past | Urgency = `CRITICAL`; show "overdue" badge |
| Progress is ahead of target | Urgency = `ON_TRACK`; strategic slack is HIGH |
| Work is blocked externally | Check-in BLOCKED signals should distinguish from under-investment. AI diagnostic should note: "progress stalled despite active effort" |
| Outcome is archived upstream | Commit links remain (snapshot); urgency computation skips archived outcomes |
| Multiple teams contribute to one outcome | Progress is org-wide, not team-specific. Per-team contribution is shown separately. |

---

## 8. Success metrics

| Metric | Target |
|--------|--------|
| Outcomes with target dates set | ≥ 60% within 90 days of feature launch |
| Urgency-band accuracy (user-validated) | ≥ 75% of urgency bands perceived as correct |
| Strategic focus floor influence | Teams with LOW_SLACK increase strategic ratio by ≥ 10pp |
| AT_RISK outcome detection | Identified ≥ 3 weeks before target-date miss |

---

## 9. Implementation order within this phase

1. **`outcome_metadata` table + API** — target dates, progress type
2. **Metric-based progress tracking** — current value / target value
3. **Activity-based progress computation** — derived from commit data
4. **Urgency band computation** — scheduled job
5. **Strategic slack calculation** — per-team floor
6. **Plan quality nudge integration** — urgency-aware nudges
7. **Next-work suggestion boosting** — urgency-based confidence multiplier
8. **Manager insights integration** — urgency context in AI prompts
9. **Milestone-based progress** — JSONB milestones with partial completion
10. **IC-facing outcome progress view** — trends with target context
