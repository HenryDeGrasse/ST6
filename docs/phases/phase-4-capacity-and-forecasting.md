# Phase 4: Capacity Planning & User Performance Model

> **Priority:** Fourth. Benefits from weeks of accumulated Phase 1 data.
>
> **Core insight:** The system should know how much work a person and team
> can *actually* deliver, not just how much they *say* they'll deliver.
> This turns optimistic planning into realistic planning.

---

## 1. The Problem

### Planning without capacity is guessing

Currently, a user can create a plan with any number of commitments at any
priority level. There's no signal for:
- "this is more than you can handle this week"
- "you tend to take 40% longer than estimated on DELIVERY tasks"
- "this team planned 180 hours but has historically delivered 130"

### Estimation bias is invisible

People consistently over- or under-estimate. Without tracking this pattern,
the system can't calibrate:
- the AI's planning suggestions
- the manager's capacity expectations
- the user's own self-awareness

---

## 2. Data model additions

### Extend `weekly_commits`

```sql
ALTER TABLE weekly_commits ADD COLUMN estimated_hours NUMERIC(5,1);
```

### Extend `weekly_commit_actuals`

```sql
ALTER TABLE weekly_commit_actuals ADD COLUMN actual_hours NUMERIC(5,1);
```

### New: `user_capacity_profiles` (computed, not entered)

```sql
CREATE TABLE user_capacity_profiles (
    org_id                UUID NOT NULL,
    user_id               UUID NOT NULL,
    weeks_analyzed        INTEGER NOT NULL,
    -- Aggregate capacity metrics
    avg_estimated_hours   NUMERIC(5,1),
    avg_actual_hours      NUMERIC(5,1),
    estimation_bias       NUMERIC(4,2),  -- ratio: actual/estimated (>1 = underestimates)
    realistic_weekly_cap  NUMERIC(5,1),  -- derived: what they actually deliver
    -- Per-category estimation bias
    category_bias_json    JSONB,
    -- Per-priority completion rates
    priority_completion_json JSONB,
    -- Confidence
    confidence_level      VARCHAR(10),   -- LOW (<4 weeks data), MEDIUM (4-8), HIGH (>8)
    computed_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (org_id, user_id)
);
```

#### Example `category_bias_json`

```json
{
  "DELIVERY": { "avgEstimated": 5.2, "avgActual": 6.8, "bias": 1.31, "sampleSize": 18 },
  "OPERATIONS": { "avgEstimated": 2.0, "avgActual": 1.8, "bias": 0.90, "sampleSize": 12 },
  "TECH_DEBT": { "avgEstimated": 4.0, "avgActual": 5.5, "bias": 1.38, "sampleSize": 6 }
}
```

#### Example `priority_completion_json`

```json
{
  "KING": { "doneRate": 0.92, "avgHours": 8.5, "sampleSize": 12 },
  "QUEEN": { "doneRate": 0.75, "avgHours": 6.2, "sampleSize": 24 },
  "ROOK": { "doneRate": 0.82, "avgHours": 4.1, "sampleSize": 30 },
  "PAWN": { "doneRate": 0.95, "avgHours": 1.2, "sampleSize": 40 }
}
```

---

## 3. Capacity intelligence features

### A) Overcommitment detection (IC-facing)

When a user is about to lock their plan, the system checks:

```python
def detect_overcommitment(plan, user_profile):
    total_estimated = sum(c.estimated_hours for c in plan.commits if c.estimated_hours)
    realistic_cap = user_profile.realistic_weekly_cap

    # Adjust estimates by per-category bias
    adjusted_total = 0
    for commit in plan.commits:
        if commit.estimated_hours and commit.category:
            bias = user_profile.category_bias.get(commit.category, {}).get("bias", 1.0)
            adjusted_total += commit.estimated_hours * bias
        elif commit.estimated_hours:
            adjusted_total += commit.estimated_hours * user_profile.estimation_bias

    if adjusted_total > realistic_cap * 1.2:
        return OvercommitWarning(
            level="HIGH",
            message=f"Adjusted estimate is {adjusted_total:.0f}h "
                    f"(your typical delivery is {realistic_cap:.0f}h/week). "
                    f"Consider deferring lower-priority items.",
            adjusted_total=adjusted_total,
            realistic_cap=realistic_cap
        )
    elif adjusted_total > realistic_cap:
        return OvercommitWarning(level="MODERATE", ...)
    else:
        return None
```

### B) Estimation coaching (IC-facing)

After reconciliation, show a personal calibration card:

```
┌─────────────────────────────────────────────────┐
│  📊 Estimation accuracy this week               │
│                                                  │
│  You estimated 36 hours. You delivered 28 hours. │
│                                                  │
│  Pattern over 8 weeks:                           │
│  • DELIVERY: you underestimate by ~30%           │
│  • OPERATIONS: you're well calibrated (±10%)     │
│  • KING items: 92% completion rate ✅             │
│  • QUEEN items: 68% completion rate ⚠️           │
│                                                  │
│  💡 Consider adding 30% buffer to DELIVERY       │
│     estimates, or moving 1 QUEEN to next week.   │
└─────────────────────────────────────────────────┘
```

### C) Team capacity view (manager-facing)

```
┌──────────────────────────────────────────────────────┐
│  Team Capacity — Week of March 16                    │
│                                                      │
│  Name      Est.   Adj.Est  Realistic  Status         │
│  ─────     ────   ───────  ─────────  ──────         │
│  Alex      40h    48h      34h        ⛔ OVER        │
│  Sam       30h    33h      35h        ✅ OK          │
│  Jordan    25h    25h      30h        ✅ UNDER       │
│  Pat       45h    56h      32h        ⛔ WAY OVER    │
│                                                      │
│  Team total: 140h est → 162h adjusted → 131h realist │
│                                                      │
│  ⚠️ Team is planning 24% more than historical        │
│     delivery capacity.                               │
└──────────────────────────────────────────────────────┘
```

### D) AI-assisted realistic planning (manager tool)

When a manager sees the capacity view, they can ask:

> "Given team capacity and outcome urgency, what's a realistic allocation
> for this week?"

The AI would produce:

```json
{
  "suggestedAllocation": {
    "Scale Revenue (AT_RISK)": {
      "recommendedHours": 45,
      "suggestedAssignees": ["Alex (12h)", "Sam (18h)", "Jordan (15h)"],
      "rationale": "This outcome is AT_RISK with target in 6 weeks. Current pace is insufficient."
    },
    "Improve Monitoring (ON_TRACK)": {
      "recommendedHours": 20,
      "suggestedAssignees": ["Pat (20h)"],
      "rationale": "On track. Maintain current effort."
    },
    "Operations / Non-strategic": {
      "recommendedHours": 25,
      "suggestedAssignees": ["shared"],
      "rationale": "Strategic slack is LOW. Limit non-strategic to 19% of capacity."
    }
  }
}
```

This is a **suggestion**, not an assignment. The manager reviews, modifies,
and communicates with the team.

---

## 4. Building the capacity model from the user model (Phase 1)

Phase 1's user model already captures:
- completion reliability
- carry-forward tendency
- category completion rates
- check-in patterns

Phase 4 adds:
- estimated vs actual hours (the explicit capacity signal)
- per-category estimation bias (the calibration signal)
- realistic weekly capacity (the planning constraint)

### The capacity model is layered

```
Layer 1 (Phase 1): What the user does — commit counts, completion rates
Layer 2 (Phase 4): How long it takes — estimated vs actual hours
Layer 3 (Phase 4): What they can really do — realistic capacity band
Layer 4 (Phase 5): What should they do — AI-optimized allocation
```

---

## 5. What managers see vs what ICs see

### ICs see: self-improvement framing

- "Your estimation accuracy"
- "Your capacity band"
- "Where you tend to overcommit"
- "Your completion trends by category"

### Managers see: planning support framing

- "Team capacity summary"
- "Who is overcommitted this week"
- "Estimation accuracy distribution across team"
- "Capacity available for strategic reallocation"

### Sensitive data handling

| Data point | IC sees | Manager sees | Skip-level sees |
|-----------|---------|-------------|-----------------|
| Individual estimate accuracy | ✅ Full | ✅ Summary | ❌ Hidden |
| Individual actual hours | ✅ Full | ✅ Aggregate | ❌ Hidden |
| Individual estimation bias | ✅ Full | ⚠️ Category-level only | ❌ Hidden |
| Team total capacity | N/A | ✅ Full | ✅ Aggregate |
| Overcommitment flag | ✅ (self) | ✅ (per-team-member) | ❌ Hidden |
| Detailed hour logs | ✅ Full | ⚠️ Aggregate trends | ❌ Hidden |

### Framing principle

**Never:** "Sam takes 30% longer than estimated"
**Always:** "This plan's adjusted estimate exceeds Sam's recent delivery
capacity. Consider redistributing or deferring."

---

## 6. Integration with Phase 3 (urgency)

### Capacity-aware urgency assessment

Phase 3 determines urgency (ON_TRACK, AT_RISK, CRITICAL).
Phase 4 adds: **is there enough capacity to address the urgency?**

```python
def assess_outcome_feasibility(outcome, team_capacity):
    remaining_work = estimate_remaining_work(outcome)
    available_capacity = team_capacity.strategic_capacity_remaining
    weeks_remaining = (outcome.target_date - today).days / 7

    required_weekly_hours = remaining_work / weeks_remaining
    available_weekly_hours = available_capacity / weeks_remaining

    if required_weekly_hours > available_weekly_hours * 1.3:
        return "INFEASIBLE without reallocation"
    elif required_weekly_hours > available_weekly_hours:
        return "TIGHT — needs focus"
    else:
        return "FEASIBLE at current pace"
```

---

## 7. Success metrics

| Metric | Target |
|--------|--------|
| Users entering estimated hours | ≥ 60% of commitments within 60 days |
| Estimation accuracy improvement | Avg bias moves toward 1.0 by ≥ 15% over 12 weeks |
| Overcommitment detection rate | System flags overcommitment in ≥ 80% of weeks where carry-forward exceeds 3 items |
| Manager capacity view usage | ≥ 70% of managers view capacity summary weekly |

---

## 8. Implementation order within this phase

1. **Add `estimated_hours` / `actual_hours` columns** — migration + API
2. **Frontend: optional hour input** — in commit editor and reconciliation
3. **Capacity profile computation job** — weekly aggregate from historical data
4. **IC estimation coaching card** — post-reconciliation feedback
5. **Overcommitment detection** — lock-time warning
6. **Team capacity view** — manager dashboard panel
7. **Category-level estimation bias** — per-category adjustment
8. **Integration with Phase 3 urgency** — capacity-aware feasibility
9. **AI-assisted allocation suggestions** — manager planning tool
