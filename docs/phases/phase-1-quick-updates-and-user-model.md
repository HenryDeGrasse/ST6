# Phase 1: Quick Update Flow & User Model

> **Priority:** First. This is the data-generation foundation everything else
> depends on.
>
> **Core insight:** The system can only be as intelligent as the data it
> collects. The current check-in flow works but has too much friction. A faster,
> AI-assisted update flow generates richer data, and a structured user model
> gives every downstream feature a shared understanding of each person.

---

## 1. The Problem

### Check-in friction today

The current system has structured check-ins (`POST /commits/{commitId}/check-in`)
with `ProgressStatus` (ON_TRACK, AT_RISK, BLOCKED, DONE_EARLY) and a free-text
note. This is good infrastructure, but:

- Users must navigate to each commitment individually
- The free-text note field is blank — no suggestions, no structure
- There's no batch flow to update all commitments at once
- The system doesn't learn what the user typically says
- Updates feel like extra work, so they're skipped

### Missing user model

The system knows what a user *did* (commits, actuals, check-ins) but doesn't
maintain a structured model of:
- what the user is *like* (estimation patterns, work style, capacity)
- what the user *prefers* (recurring work, typical update language)
- how the user *performs* (accuracy, reliability, throughput)

---

## 2. Quick Update Flow — "Run Through My Week"

### Concept

When a user opens their locked plan mid-week, offer a **rapid-fire update mode**
that walks through each commitment with AI-generated contextual options.

### UX flow

```
┌─────────────────────────────────────────────────────┐
│  Quick Update — 5 of 7 commitments                  │
│                                                      │
│  "Complete API monitoring integration"               │
│   🏰 QUEEN · Delivery · Improve API uptime          │
│   Last check-in: ON_TRACK (2 days ago)               │
│                                                      │
│  Still working on this?                              │
│  ┌──────┐  ┌──────┐                                 │
│  │ Yes  │  │ Done │                                  │
│  └──────┘  └──────┘                                 │
│                                                      │
│  What's happened since last time?                    │
│  ┌─────────────────────────────────────────┐        │
│  │ 1. Deployed to staging                  │ ←      │
│  │ 2. PR under review                     │        │
│  │ 3. Blocked on infra access              │        │
│  │ 4. Waiting on code review               │        │
│  │ 5. [Type my own...]                     │        │
│  └─────────────────────────────────────────┘        │
│                                                      │
│  ← Previous                          Next →          │
└─────────────────────────────────────────────────────┘
```

### Key design principles

1. **One commitment at a time.** Card-based, swipeable or arrow-key navigable.
2. **Binary first question.** "Still working on this?" — Yes / Done / Blocked / Dropped.
3. **AI-generated answer options.** Based on:
   - Commitment title, description, category
   - Prior check-in notes for this commitment
   - User's typical update language patterns
   - External ticket status (if linked)
   - Common answers from similar commitments across the org
4. **Always offer free-text.** The generated options accelerate, never restrict.
5. **Learning loop.** When a user types a custom answer, the system records it.
   Over time, the AI surfaces that user's common phrases as options.
6. **Speed target.** A user with 6 commitments should finish updates in under
   90 seconds if most are on-track.

### AI option generation

#### Input context per commitment
```json
{
  "commitTitle": "Complete API monitoring integration",
  "category": "DELIVERY",
  "chessPriority": "QUEEN",
  "lastCheckInStatus": "ON_TRACK",
  "lastCheckInNote": "Set up Grafana dashboards",
  "lastCheckInDaysAgo": 2,
  "userPriorNotesForSimilar": ["Deployed to staging", "Waiting on infra team", ...],
  "linkedTicketStatus": "In Review",
  "outcomeName": "Improve API uptime monitoring"
}
```

#### Output
```json
{
  "statusQuestion": "Still working on this?",
  "statusOptions": ["Yes", "Done", "Blocked", "Dropped"],
  "progressQuestion": "What's happened since last time?",
  "progressOptions": [
    "Deployed to staging",
    "PR under review",
    "Blocked on infra access",
    "Waiting on code review"
  ]
}
```

#### Learning mechanism

Store a `user_update_patterns` table (or extend existing data):

| Field | Purpose |
|-------|---------|
| `org_id` | Tenant isolation |
| `user_id` | Per-user learning |
| `category` | Which kind of work |
| `note_text` | What the user actually typed |
| `frequency` | How often this phrase appears |
| `last_used_at` | Recency signal |

Over time, the AI's option generation prompt includes the user's top-N
historical notes for this category, so suggestions become increasingly
personalized.

### Backend changes needed

| Change | Scope |
|--------|-------|
| New endpoint: `POST /api/v1/plans/{planId}/quick-update` (batch check-in) | Backend |
| New endpoint: `POST /api/v1/ai/check-in-options` (generate contextual options) | Backend |
| New table: `user_update_patterns` | Migration V7 |
| Frontend: Quick Update card-based flow | Frontend |
| Learning job: aggregate typed notes into pattern table | Scheduled job |

### API sketch

```
POST /api/v1/ai/check-in-options
Request:
  {
    "commitId": "uuid",
    "currentStatus": "ON_TRACK",
    "lastNote": "Set up Grafana dashboards",
    "daysSinceLastCheckIn": 2
  }
Response:
  {
    "status": "ok",
    "statusOptions": ["ON_TRACK", "DONE_EARLY", "AT_RISK", "BLOCKED"],
    "progressOptions": [
      { "text": "Deployed to staging", "source": "user_history" },
      { "text": "PR under review", "source": "ai_generated" },
      { "text": "Blocked on infra access", "source": "ai_generated" },
      { "text": "Integration tests passing", "source": "team_common" }
    ]
  }
```

```
POST /api/v1/plans/{planId}/quick-update
Request:
  {
    "updates": [
      { "commitId": "uuid", "status": "ON_TRACK", "note": "PR under review" },
      { "commitId": "uuid", "status": "DONE_EARLY", "note": "Merged and deployed" },
      { "commitId": "uuid", "status": "BLOCKED", "note": "Waiting on legal review" }
    ]
  }
Response:
  {
    "updatedCount": 3,
    "entries": [ ... ]
  }
```

---

## 3. User Model — Structured Understanding of Each Person

### What the user model captures

The user model is **derived**, not manually entered. It's computed from
historical data that already exists (or will exist after the quick update flow).

#### Performance dimensions

| Dimension | Source data | Example derived metric |
|-----------|------------|----------------------|
| **Estimation accuracy** | `confidence` field vs `completion_status` | "User overestimates by ~25% on DELIVERY tasks" |
| **Completion reliability** | Plans locked vs reconciled; DONE rate | "85% DONE rate on ROOK items; 60% on QUEEN items" |
| **Throughput** | Commits per week, adjusted for priority | "Averages 5.2 commits/week; delivers 1 KING + 2 QUEEN reliably" |
| **Carry-forward tendency** | Carried items / total items per week | "Carries forward 1.8 items/week avg; 3-week streak currently" |
| **Category strengths** | Completion rate by category | "92% DONE on Operations; 58% DONE on Delivery" |
| **Strategic alignment** | % RCDO-linked, outcome coverage | "78% strategic (team avg 85%)" |
| **Update cadence** | Check-in frequency and timing | "Updates Mon + Wed consistently; rarely updates Friday" |
| **Response patterns** | Common check-in notes, typical language | "Usually says 'deployed to staging' for Delivery items" |

#### Preference dimensions

| Dimension | Source | Example |
|-----------|--------|---------|
| **Recurring work** | Title similarity across weeks | "Writes 'weekly ops review' 4 of last 5 weeks" |
| **Priority style** | Chess distribution over time | "Tends toward 1K + 1Q + 4R pattern" |
| **Category balance** | Category distribution trends | "70% Delivery, 20% Operations, 10% Learning" |
| **Check-in vocabulary** | Historical note analysis | Frequent phrases per category/status |

#### Capacity dimensions (added in Phase 4)

| Dimension | Source | Example |
|-----------|--------|---------|
| **Estimated hours** | `estimated_hours` field (Phase 4) | "Estimates avg 38 hours/week" |
| **Actual hours** | `actual_hours` field (Phase 4) | "Delivers avg 32 hours/week" |
| **Estimation bias** | Estimated vs actual over time | "Consistently underestimates DELIVERY by 30%" |
| **Realistic weekly capacity** | Derived from actuals | "Reliably delivers 30-35 hours/week of committed work" |

### Where the user model lives

**Not a single table.** The user model is a **computed view** assembled from:

1. **Raw data**: `weekly_plans`, `weekly_commits`, `weekly_commit_actuals`,
   `progress_entries`, `ai_suggestion_feedback`
2. **Derived metrics**: materialized or cached aggregates, recomputed weekly
3. **Learned patterns**: `user_update_patterns` table
4. **Profile cache**: `user_model_cache` (Redis or materialized view) for
   fast access during AI prompt construction

### API for the user model

```
GET /api/v1/users/me/profile
Response:
  {
    "userId": "uuid",
    "weeksAnalyzed": 12,
    "performanceProfile": {
      "estimationAccuracy": 0.75,
      "completionReliability": 0.82,
      "avgCommitsPerWeek": 5.2,
      "avgCarryForwardPerWeek": 1.8,
      "topCategories": ["DELIVERY", "OPERATIONS"],
      "categoryCompletionRates": { "DELIVERY": 0.72, "OPERATIONS": 0.92 },
      "priorityCompletionRates": { "KING": 0.90, "QUEEN": 0.75, "ROOK": 0.80 }
    },
    "preferences": {
      "typicalPriorityPattern": "1K-2Q-3R",
      "recurringCommitTitles": ["Weekly ops review", "Sprint planning prep"],
      "avgCheckInsPerWeek": 3.1,
      "preferredUpdateDays": ["MONDAY", "WEDNESDAY"]
    },
    "trends": {
      "strategicAlignmentTrend": "STABLE",
      "completionTrend": "IMPROVING",
      "carryForwardTrend": "WORSENING"
    }
  }
```

### User model visibility

| Audience | What they see |
|----------|--------------|
| **The user themselves** | Full profile, framed as self-awareness |
| **Their manager** | Performance profile + trends, framed as planning support |
| **Skip-level / admin** | Aggregate team patterns only; no individual detail without explicit access |

### Privacy principle

The user model is about **planning realism**, not **productivity surveillance**.
Framing matters:
- ✅ "You tend to overestimate DELIVERY tasks — consider adding buffer"
- ❌ "You are 25% less productive than you claim"
- ✅ "Sam's historical throughput suggests this plan is overcommitted"
- ❌ "Sam is slow"

---

## 4. Data model changes

### New table: `user_update_patterns` (V7 migration)

```sql
CREATE TABLE user_update_patterns (
    id          UUID PRIMARY KEY,
    org_id      UUID NOT NULL,
    user_id     UUID NOT NULL,
    category    VARCHAR(20),
    note_text   TEXT NOT NULL,
    frequency   INTEGER NOT NULL DEFAULT 1,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_update_patterns_user ON user_update_patterns (org_id, user_id, category);
```

### New table: `user_model_snapshots` (V7 migration)

```sql
CREATE TABLE user_model_snapshots (
    org_id          UUID NOT NULL,
    user_id         UUID NOT NULL,
    computed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    weeks_analyzed  INTEGER NOT NULL,
    model_json      JSONB NOT NULL,
    PRIMARY KEY (org_id, user_id)
);
```

---

## 5. How this feeds downstream phases

| Downstream phase | What it gets from Phase 1 |
|------------------|--------------------------|
| **Phase 2: Strategic Intelligence** | Richer check-in data for pattern detection; user model for calibrated insights |
| **Phase 3: Urgency Modeling** | More frequent progress signals per outcome; user reliability for progress forecasts |
| **Phase 4: Capacity Planning** | Throughput history, estimation accuracy, realistic capacity baselines |
| **Phase 5: Predictive Planning** | Complete user ontology for AI-assisted team planning |

---

## 6. Success metrics

| Metric | Target |
|--------|--------|
| Check-in frequency increase | 2× current rate within 30 days |
| Quick-update completion time (6 commitments) | < 90 seconds |
| AI option acceptance rate (vs free-text) | ≥ 50% within 60 days |
| User model coverage | 80% of active users have ≥ 4 weeks of derived profile data |

---

## 7. Implementation order within this phase

1. **Quick-update batch endpoint** — backend, no AI yet
2. **Quick-update frontend** — card-based rapid flow
3. **AI check-in option generation** — endpoint + prompt
4. **User update pattern tracking** — record custom notes
5. **Learning loop** — use patterns to personalize option generation
6. **User model computation** — derived metrics job
7. **User profile API** — expose model to frontend and AI prompts
