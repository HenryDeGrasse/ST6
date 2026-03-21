# Phase 5: Predictive Intelligence & AI Manager Planning

> **Priority:** Fifth. Synthesizes all prior phases into forward-looking
> intelligence.
>
> **Core insight:** With rich data (Phase 1), pattern recognition (Phase 2),
> urgency awareness (Phase 3), and capacity realism (Phase 4), the system
> can now answer the most valuable question: **"What should we do next,
> and will it work?"**

---

## 1. What this phase builds on

| Phase | What it provides to Phase 5 |
|-------|----------------------------|
| **Phase 1** | User model: preferences, patterns, vocabulary, update cadence |
| **Phase 2** | Multi-week patterns: coverage trends, carry-forward, diagnostic intelligence |
| **Phase 3** | Urgency model: target dates, progress tracking, strategic slack |
| **Phase 4** | Capacity model: realistic throughput, estimation bias, team capacity |

Phase 5 doesn't add much new data. It **synthesizes** what exists into
forward-looking predictions and AI-assisted planning.

---

## 2. Predictive intelligence

### What the system should predict

| Prediction | Inputs | Value |
|-----------|--------|-------|
| **Target date miss risk** | Phase 3 progress + Phase 4 capacity + Phase 2 coverage trend | "Outcome X has a 35% chance of meeting its June 30 target at current pace" |
| **Carry-forward forecast** | Phase 1 user model + Phase 4 overcommitment detection | "Sam is likely to carry forward 3+ items this week (based on 6/8 recent weeks)" |
| **Strategic drift** | Phase 2 strategic ratio trend + Phase 3 urgency | "If current non-strategic load continues, Rally Cry Y will have insufficient coverage by April" |
| **Capacity crunch** | Phase 4 team capacity + Phase 3 upcoming target dates | "Team is overcommitted for the next 3 weeks given 2 approaching deadlines" |
| **Review bottleneck** | Phase 2 review turnaround trend | "At current pace, 5 reconciliations will be pending review by Friday" |
| **User burnout signal** | Phase 1 update frequency decline + Phase 4 chronic overcommitment | "Alex has been overcommitted 4 of last 6 weeks and check-in frequency has dropped" |

### Confidence levels for predictions

Predictions should always carry a confidence level based on data quality:

| Confidence | Criteria |
|-----------|----------|
| **HIGH** | ≥ 8 weeks of data; pattern repeated ≥ 4 times; low variance |
| **MEDIUM** | 4-8 weeks of data; pattern repeated 2-3 times |
| **LOW** | < 4 weeks of data; or high variance; or novel situation |

Low-confidence predictions are shown with clear caveats.
Below a minimum threshold, predictions are suppressed entirely.

---

## 3. Target date forecasting

### The forecasting model

```python
def forecast_target_date_risk(outcome, team_data):
    """Returns probability of meeting target date."""

    # Factor 1: Progress trajectory
    progress_velocity = compute_weekly_progress_velocity(outcome, weeks=6)
    remaining = 1.0 - outcome.progress_pct / 100.0
    weeks_remaining = (outcome.target_date - today).days / 7
    weeks_needed_at_current_pace = remaining / max(progress_velocity, 0.001)

    time_ratio = weeks_needed_at_current_pace / max(weeks_remaining, 0.1)

    # Factor 2: Coverage trend (are people still working on it?)
    coverage_trend = compute_coverage_trend(outcome, weeks=4)
    # INCREASING=1.1, STABLE=1.0, DECLINING=0.8, ZERO=0.5

    # Factor 3: Team capacity availability
    capacity_score = compute_capacity_availability(outcome, team_data)
    # 1.0 = team has slack; 0.5 = team is overcommitted

    # Factor 4: Historical carry-forward risk
    carry_risk = compute_carry_forward_risk(outcome, team_data)
    # How much of the work on this outcome gets carried week to week

    # Composite score (calibrate weights empirically)
    raw_score = (
        (1.0 / max(time_ratio, 0.1)) * 0.40  # pace factor
        + coverage_trend * 0.25               # engagement factor
        + capacity_score * 0.20               # capacity factor
        + (1.0 - carry_risk) * 0.15           # execution factor
    )

    # Clamp to [0, 1] and round
    return min(max(raw_score, 0.0), 1.0)
```

### Presentation

```
┌──────────────────────────────────────────────────┐
│  Outcome Risk Forecast                           │
│                                                  │
│  "Sign 3 healthcare pilots"                      │
│  Target: June 30 · Progress: 33% · Expected: 55% │
│                                                  │
│  📊 Forecast: 35% likely to meet target          │
│                                                  │
│  Contributing factors:                           │
│  ⚠️ Coverage declined 3 consecutive weeks        │
│  ⚠️ Team capacity is overcommitted               │
│  ✅ 2 active contributors still engaged          │
│  ⚠️ 40% carry-forward rate on related commits    │
│                                                  │
│  💡 Recommendation: Increase allocation by ~8h/wk │
│     Reassign 1 DELIVERY item from Alex to free   │
│     capacity for this outcome.                   │
└──────────────────────────────────────────────────┘
```

---

## 4. AI-assisted manager planning

### The planning copilot

This is the highest-leverage feature in the entire roadmap. It helps managers
answer: **"Given what I know about my team's capacity, our strategic urgency,
and historical patterns, what should the plan be for next week?"**

### Planning flow

```
Manager opens planning assistant for Week of March 23
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│  🤖 AI Planning Assistant                           │
│                                                      │
│  Team capacity: 131h realistic (5 people)            │
│  Strategic pressure: HIGH (2 AT_RISK outcomes)       │
│  Recommended strategic focus: ≥ 80%                  │
│                                                      │
│  ── Suggested team allocation ──                     │
│                                                      │
│  "Sign 3 healthcare pilots" [CRITICAL]               │
│    Recommended: 45h (34% of capacity)                │
│    Alex: 15h (follow-up calls, proposal draft)       │
│    Sam: 18h (legal coordination, contract prep)      │
│    Jordan: 12h (technical POC demo)                  │
│                                                      │
│  "Build pricing tool" [ON_TRACK]                     │
│    Recommended: 30h (23% of capacity)                │
│    Pat: 20h (frontend implementation)                │
│    Alex: 10h (API endpoints)                         │
│                                                      │
│  "Ops / non-strategic" [MODERATE SLACK]              │
│    Recommended: 25h (19% of capacity)                │
│    Shared across team                                │
│                                                      │
│  "Buffer / unplanned"                                │
│    31h remaining (24% — healthy buffer)              │
│                                                      │
│  [Accept & create draft plans] [Modify] [Dismiss]    │
└─────────────────────────────────────────────────────┘
```

### What the AI considers

| Input | Source | How it's used |
|-------|--------|---------------|
| Team member capacity profiles | Phase 4 | Realistic hours per person |
| Per-person estimation bias | Phase 4 | Adjust suggested hours |
| Per-person category strengths | Phase 1 | Match people to work types |
| Outcome urgency bands | Phase 3 | Prioritize allocation |
| Outcome progress + target dates | Phase 3 | Determine remaining work |
| Coverage trends | Phase 2 | Identify under-invested outcomes |
| Carry-forward items | Phase 1/2 | Include incomplete work |
| Blocked signals | Phase 1 | Avoid assigning blocked work |
| Historical completion patterns | Phase 1 | Predict likelihood of completion |

### Output: suggested commitments

The planning copilot can optionally create draft commitments:

```json
{
  "teamPlan": {
    "weekStart": "2026-03-23",
    "members": [
      {
        "userId": "alex-uuid",
        "suggestedCommits": [
          {
            "title": "Healthcare pilot: follow-up calls with 2 prospects",
            "outcomeId": "healthcare-outcome-uuid",
            "chessPriority": "KING",
            "estimatedHours": 8,
            "source": "AI_PLANNED",
            "rationale": "Outcome is CRITICAL; Alex has the relationship context"
          },
          {
            "title": "Healthcare pilot: draft proposal for Acme",
            "outcomeId": "healthcare-outcome-uuid",
            "chessPriority": "QUEEN",
            "estimatedHours": 7,
            "source": "AI_PLANNED",
            "rationale": "Required for pipeline progression"
          }
        ],
        "totalEstimated": 32,
        "realisticCapacity": 34,
        "overcommitRisk": "LOW"
      }
    ]
  }
}
```

### Important constraints

1. **Suggestions only.** The manager reviews and modifies. The AI never
   creates plans on behalf of users.
2. **ICs still own their plans.** Even if a manager uses the planning copilot,
   the IC must review, modify, and lock their own plan.
3. **Transparency.** Every suggestion includes rationale. No black-box
   allocation.
4. **Privacy.** The planning copilot uses aggregate capacity data, not
   individual hour logs.

---

## 5. Proactive agents

Building on PRD §17.4.3, these agents use Phase 1-4 data to operate
autonomously (but never write without human approval):

### Agent 1: Weekly planning assistant (enhanced)

**Trigger:** Sunday evening or Monday morning, per user timezone.

**What it does:**
1. Reviews user's carried-forward items
2. Reviews outcome urgency (Phase 3)
3. Reviews user's realistic capacity (Phase 4)
4. Reviews team coverage gaps (Phase 2)
5. Drafts a proposed plan within capacity bounds
6. Sends notification: "Your AI-drafted plan is ready for review"

**Enhancement over MVP:** Now capacity-aware. Won't suggest more than the
user can realistically deliver. Will flag if the user's typical pattern
suggests they'll need to carry forward.

### Agent 2: Misalignment detector (enhanced)

**Trigger:** Daily, after lock-day each week.

**What it does:**
1. Scans all team plans for the current week
2. Compares team allocation against outcome urgency bands
3. Identifies: underfunded CRITICAL/AT_RISK outcomes, overfunded ON_TRACK ones
4. Generates manager briefing with specific reallocation suggestions

**Enhancement:** Uses capacity data to make suggestions realistic.
"Move 8h from Pat's pricing work to healthcare pipeline" only if Pat
has the skills and capacity.

### Agent 3: Reconciliation assistant (enhanced)

**Trigger:** End of week, when reconciliation opens.

**What it does:**
1. Reviews each commitment's check-in history
2. Reviews linked ticket status (if integrated)
3. Reviews user's completion patterns for similar work
4. Pre-fills completion status, actual result, delta reason, and actual hours
5. For capacity data: compares estimated vs actual hours

**Enhancement:** Capacity-aware. Can say "This took 12h vs your 6h estimate.
Your DELIVERY tasks typically take 1.3× longer than estimated."

---

## 6. Executive intelligence surfaces

### Organizational strategic health dashboard

For skip-level managers, VPs, and executives:

```
┌──────────────────────────────────────────────────────┐
│  Strategic Health — Q1 2026                          │
│                                                      │
│  Rally Cry: Scale Revenue                            │
│  ├── 🔴 Sign healthcare pilots (35% → target June)  │
│  ├── 🟢 Build pricing tool (on track → target Aug)   │
│  └── 🟡 Enterprise pipeline (needs attention → Jul)  │
│                                                      │
│  Rally Cry: Operational Excellence                   │
│  ├── 🟢 API uptime (ahead of target)                │
│  └── 🟢 Reduce incidents (on track)                 │
│                                                      │
│  ── Organization capacity ──                         │
│  Total: 520h/week realistic capacity                 │
│  Strategic: 68% (target: 75% given urgency)          │
│  Non-strategic: 22%                                  │
│  Buffer: 10%                                         │
│                                                      │
│  ⚠️ 2 outcomes AT_RISK with current resource          │
│     allocation. See recommendations →                │
└──────────────────────────────────────────────────────┘
```

### AI executive briefing

Weekly or on-demand, a natural-language summary:

> "This week, 3 of 5 strategic outcomes are on track. The healthcare
> pilots outcome is at risk — progress has stalled for 3 weeks and
> the team's available capacity is split across too many non-strategic
> items. Recommend shifting 15h/week from operations to healthcare
> effort over the next 4 weeks.
>
> The pricing tool is ahead of schedule. Consider reassigning Pat
> to healthcare support for 2 weeks without risk to the August target.
>
> Team estimation accuracy has improved 12% since Phase 4 launch.
> Carry-forward rates are down 20% since the quick-update flow launched."

---

## 7. Success metrics

| Metric | Target |
|--------|--------|
| Target date forecast accuracy | ≥ 65% of forecasts within ±15% of actual outcome |
| Planning copilot usage | ≥ 40% of managers use it weekly within 60 days |
| Overcommitment reduction | Plans flagged as overcommitted decrease by 30% |
| Strategic alignment improvement | Team strategic ratio improves by ≥ 10pp over 12 weeks |
| AT_RISK outcome resolution | ≥ 50% of AT_RISK outcomes improve to ON_TRACK within 4 weeks of flagging |

---

## 8. Implementation order within this phase

1. **Rule-based predictions** — carry-forward, late-lock, coverage decline forecasts
2. **Target date forecasting model** — composite score from phases 2-4
3. **Forecast presentation** — outcome risk cards with contributing factors
4. **Enhanced weekly planning assistant agent** — capacity-aware drafts
5. **Enhanced misalignment detector agent** — urgency + capacity aware
6. **Enhanced reconciliation assistant** — capacity coaching integration
7. **Manager planning copilot** — team allocation suggestions
8. **Executive strategic health dashboard** — aggregate outcome + capacity view
9. **AI executive briefing** — weekly narrative summary
10. **Feedback loop** — track prediction accuracy, retune weights
