# AI & Product Improvement Roadmap

> Living document tracking UX improvements, AI-powered features, and product
> enhancements beyond the current PRD scope. Each item includes the user pain
> it addresses, the data/infrastructure it requires, and a rough priority.
>
> **Guiding principle:** Every improvement should reduce the minutes-per-week a
> person spends in this tool while increasing the quality of strategic signal it
> produces. The product wins when weekly planning feels like a 3-minute habit,
> not a 20-minute chore.

---

## Table of Contents

1. [AI-Powered Work Suggestions (RCDO-Driven Next-Work Engine)](#1-ai-powered-work-suggestions-rcdo-driven-next-work-engine)
2. ["Start My Week" — AI-Drafted Weekly Plan](#2-start-my-week--ai-drafted-weekly-plan)
3. [Cross-Week Trend Surfacing for ICs](#3-cross-week-trend-surfacing-for-ics)
4. [Enhanced Manager Anomaly Detection](#4-enhanced-manager-anomaly-detection)
5. [Quick Daily Check-In (Micro-Updates)](#5-quick-daily-check-in-micro-updates)
6. [Lock-Time AI Quality Nudge](#6-lock-time-ai-quality-nudge)
7. [Team-Context RCDO Suggestions](#7-team-context-rcdo-suggestions)
8. [Smarter Reconciliation with Richer Context](#8-smarter-reconciliation-with-richer-context)
9. [Admin / People Ops Dashboard](#9-admin--people-ops-dashboard)
10. [External Integration Signals (Jira/Linear/GitHub)](#10-external-integration-signals-jiralineargithub)
11. [Weekly Digest Notifications](#11-weekly-digest-notifications)
12. [Prioritization Matrix](#12-prioritization-matrix)

---

## 1. AI-Powered Work Suggestions (RCDO-Driven Next-Work Engine)

### The Pain

ICs open a blank plan on Monday and have to decide what to work on. They know
their day-to-day tasks, but they don't have visibility into which strategic
outcomes still need coverage, what incomplete work exists across the team, or
what the RCDO hierarchy is signaling as highest priority. They end up doing
what's in front of them rather than what's most strategically aligned.

Managers see the same problem from the other side: outcomes with zero commits
week after week, while other outcomes are over-saturated.

### The Feature

An AI-powered "Suggested Work" engine that proactively recommends commitments
based on strategic context. The system looks at:

- **RCDO coverage gaps** — which Outcomes have had low or zero commit coverage
  in the last 2-4 weeks across the team?
- **Incomplete / carried-forward work** — what has been started but not finished,
  across prior weeks?
- **Team context** — what are peers working on for the same Rally Cry? Where is
  there under-coverage?
- **Historical patterns** — what does this user typically work on? What recurring
  work is expected?
- **External ticket state** (if Jira/Linear integration exists) — which tickets
  are assigned to the user, unresolved, and linked to active Outcomes?

The AI produces a ranked list of suggested commitments, each pre-filled with:
- Title (descriptive, based on the context source)
- Suggested RCDO link
- Suggested chess priority (based on coverage gap severity)
- Source context ("This outcome has had 0 commits for 3 weeks", "Carried
  forward 2x from prior weeks", "Jira ticket PROJ-456 is in progress")

### User Interaction Model

Each suggestion presents three actions:

| Action | Behavior |
|---|---|
| **Accept** | Adds the commitment to the current week's DRAFT plan. User can edit all fields before locking. |
| **Defer** | Saves the suggestion to a personal "backlog" queue. Resurfaces next week with a "previously deferred" tag. Does not auto-add to any plan. |
| **Decline** | Dismisses the suggestion. The system records the decline (with optional reason) to improve future suggestions. The same suggestion won't resurface for 4 weeks unless the underlying signal changes. |

The suggestions are **never auto-committed**. They appear as a panel on the
plan creation page, clearly labeled as AI-generated, alongside the manual
"Add commitment" flow.

### Data Requirements

| Data Source | Currently Available? | Notes |
|---|---|---|
| RCDO tree + outcome IDs | ✅ Yes | Via `RcdoClient` cache |
| Historical commits (last 4-8 weeks) | ✅ Yes | `weekly_commits` table, query by `org_id + owner_user_id` |
| Carry-forward lineage | ✅ Yes | `carried_from_commit_id` field on commits |
| Team commits for same outcomes | ✅ Yes | `weekly_commits` joined on `outcome_id` |
| Jira/Linear ticket state | ❌ No | Requires integration (PRD §17.4.4, ADR-010) |
| Declined suggestion history | ❌ No | New table: `ai_suggestion_feedback` |

### API Design (Sketch)

```
POST /api/v1/ai/suggest-next-work
Request:
  { "weekStart": "2026-03-16" }
Response:
  {
    "status": "ok",
    "suggestions": [
      {
        "suggestionId": "uuid",
        "title": "Complete PagerDuty alerting integration",
        "description": "Finish the monitoring setup started last week",
        "suggestedOutcomeId": "uuid",
        "suggestedOutcomeName": "Improve API uptime monitoring",
        "suggestedChessPriority": "QUEEN",
        "confidence": 0.82,
        "source": "carried_forward",
        "sourceDetail": "Carried forward 2 weeks from plan 2026-03-02. Last status: PARTIALLY done.",
        "rationale": "This outcome has had partial completion for 2 consecutive weeks. Finishing it would close the gap."
      },
      {
        "suggestionId": "uuid",
        "title": "Draft enterprise pricing proposal for Q2",
        "suggestedOutcomeId": "uuid",
        "suggestedOutcomeName": "Close 3 enterprise deals by Q2",
        "suggestedChessPriority": "KING",
        "confidence": 0.75,
        "source": "coverage_gap",
        "sourceDetail": "This outcome had 0 commits from your team last week. 2 other teams have active work.",
        "rationale": "High-priority outcome with no recent coverage from this team."
      }
    ]
  }
```

```
POST /api/v1/ai/suggestion-feedback
Request:
  {
    "suggestionId": "uuid",
    "action": "accept" | "defer" | "decline",
    "reason": "Not relevant to my role this quarter"   // optional, for decline
  }
```

### Prompt Strategy

The LLM receives:
1. **System prompt**: role definition + rules (only suggest from real RCDO IDs,
   rank by strategic impact, respect declined history)
2. **Context block**: RCDO tree (or candidate set), user's last 4 weeks of
   commits with outcomes and statuses, team's outcome coverage for the current
   quarter, carried-forward items, and (if available) linked ticket statuses
3. **User message**: "Suggest the highest-impact commitments for this user for
   the week of {date}"

Anti-hallucination: same rules as existing RCDO suggest — `outcomeId` must
exist in the provided candidate set, schema-validated response, rejected IDs
silently filtered.

### Success Metrics

- Suggestion acceptance rate ≥ 40% (lower bar than RCDO suggest because these
  are full commitment suggestions, not just tags)
- Users who see suggestions complete plan creation 30% faster (measured by
  time from plan creation to lock)
- RCDO coverage gaps (outcomes with 0 commits) decrease by 25% within 60 days

### Implementation Phases

| Phase | Scope | Depends On |
|---|---|---|
| **Phase 1** | Carry-forward suggestions + RCDO coverage gap detection (no LLM needed — pure data queries) | Nothing new |
| **Phase 2** | LLM-ranked suggestions with rationale and confidence scores | Existing AI infrastructure |
| **Phase 3** | Jira/Linear ticket integration for context enrichment | ADR-010 integration adapter |
| **Phase 4** | Personal backlog/defer queue with resurfacing logic | New `ai_suggestion_feedback` table |

### Related PRD Sections

- §4 AI-assisted workflows ("core to the product thesis")
- §9.5 AI abstraction layer
- §17.4.3 Agentic AI workflows (Weekly Planning Assistant)

---

## 2. "Start My Week" — AI-Drafted Weekly Plan

### The Pain

Most people's weeks are 60-70% predictable. They have recurring meetings,
ongoing projects, and carried-forward items. Starting from a blank plan every
Monday is unnecessary friction. The carry-forward mechanism only copies
*incomplete* items — it doesn't account for recurring work that was DONE last
week and will be DONE again this week.

### The Feature

A "Start from last week" or "AI: Draft my plan" button on the empty plan page.
The system:

1. Takes last week's plan (all commits, not just incomplete ones)
2. Automatically includes all carried-forward items
3. Identifies recurring patterns: commits with similar titles/outcomes that
   appear 2+ weeks in a row
4. Pre-fills a DRAFT plan with suggested commits, each editable
5. Integrates with the Next-Work Engine (#1 above) to also include
   coverage-gap suggestions

### User Interaction

The IC sees:
```
┌─────────────────────────────────────────────────┐
│  Week of March 16, 2026 — No plan yet           │
│                                                  │
│  [Create Empty Plan]                             │
│                                                  │
│  ── or ──                                        │
│                                                  │
│  [✨ Start from Last Week]                       │
│  AI will draft a plan based on your history,     │
│  carried items, and strategic coverage gaps.     │
│  You review everything before locking.           │
└─────────────────────────────────────────────────┘
```

After clicking "Start from Last Week," the plan is created in DRAFT with
pre-filled commits. Each commit is tagged with its source:
- 🔄 Carried forward (from incomplete prior week)
- 📋 Recurring (similar commit appeared 2+ recent weeks)
- 🎯 Coverage gap (AI-suggested based on RCDO analysis)
- ✏️ New (user-added manually)

### Data Requirements

All data currently exists in `weekly_plans` + `weekly_commits`. No new
infrastructure needed for Phase 1.

### Success Metrics

- 50% of users choose "Start from Last Week" over blank plan within 30 days
- Average time from plan creation to lock decreases by 40%
- Plan creation abandonment rate (plan created but never locked) decreases

---

## 3. Cross-Week Trend Surfacing for ICs

### The Pain

The IC sees one week at a time. There's no self-awareness mechanism that says
"you've been doing X pattern for the last month." The manager has to notice
patterns by manually reviewing multiple weeks, and then have what can feel like
a punitive conversation. Surfacing trends directly to the IC turns
*management oversight* into *self-awareness*.

### The Feature

A "My Trends" panel (collapsible, non-intrusive) on the IC's plan page showing
rolling 4-8 week trends:

- **Strategic alignment ratio**: "72% of your commits were RCDO-linked this
  month (team avg: 85%)"
- **Carry-forward velocity**: "You've carried forward 2+ items for 4
  consecutive weeks"
- **Completion accuracy**: "Your average confidence score is 0.78, but your
  completion rate for those items is 52% — you might be overcommitting"
- **Priority distribution**: "68% of your work this month was PAWN/BISHOP —
  consider whether higher-leverage work is being displaced"
- **Category balance**: "You've spent 0% of time on Learning for 6 weeks"

### API Design (Sketch)

```
GET /api/v1/users/me/trends?weeks=8
Response:
  {
    "weeksAnalyzed": 8,
    "strategicAlignmentRate": 0.72,
    "teamStrategicAlignmentRate": 0.85,
    "avgCarryForwardPerWeek": 2.3,
    "consecutiveCarryForwardWeeks": 4,
    "avgConfidence": 0.78,
    "actualCompletionRate": 0.52,
    "priorityDistribution": { "KING": 0.08, "QUEEN": 0.15, "ROOK": 0.09, ... },
    "categoryDistribution": { "DELIVERY": 0.45, "OPERATIONS": 0.30, ... },
    "insights": [
      { "type": "carry_forward_streak", "message": "...", "severity": "WARNING" },
      { "type": "confidence_accuracy_gap", "message": "...", "severity": "INFO" }
    ]
  }
```

### Data Requirements

All data exists in `weekly_plans` + `weekly_commits` + `weekly_commit_actuals`.
Pure SQL aggregation, no AI needed for Phase 1. AI can be layered on for
natural-language insight generation in Phase 2.

---

## 4. Enhanced Manager Anomaly Detection

### The Pain

The AI Manager Insights panel currently summarizes *this week's* dashboard
numbers. That's a reformatting of data the manager can already see. The real
value is in **multi-week pattern detection** — things that require looking
across 4-8 weeks of history, which no human does regularly.

### The Feature

Extend the manager insights prompt to include:

- Last 4 weeks of team data (not just current week)
- Per-person carry-forward streaks
- Per-outcome coverage trends (is an outcome losing attention?)
- Plan state cadence patterns (who's consistently late-locking?)
- Review turnaround times (is the manager themselves a bottleneck?)

Example insights the AI should produce:
- "Alex has carried forward 'API monitoring setup' for 3 consecutive weeks.
  This may be blocked or under-scoped."
- "The 'Scale Revenue' rally cry had 12 commits last month but only 3 this
  week. Coverage is declining."
- "Sam's plans have been late-locked 3 of the last 4 weeks."
- "Your average review turnaround is 3.2 days — above the 2-day target."

### Data Requirements

Historical data exists. Requires querying `weekly_plans` and `weekly_commits`
across a rolling window and feeding a richer context block to the LLM.

---

## 5. Quick Daily Check-In (Micro-Updates)

### The Pain

`progressNotes` is a plain text field. Nobody updates it voluntarily because:
- It requires opening the full plan view
- It's one big text blob per commit (no structure)
- There's no reminder or feedback loop
- It feels like extra work with no personal benefit

But mid-week signals are what make reconciliation accurate and make AI drafts
useful. Garbage in, garbage out.

### The Feature

A lightweight "check-in" interaction:

**Option A — Structured micro-updates:**
```
Quick check-in for your KING:
  "Close enterprise deal with Acme Corp"

Status: [On Track] [At Risk] [Blocked] [Done Early]
Quick note (optional): ________________

[Save — takes 5 seconds]
```

**Option B — Append-only progress log:**
Instead of a single `progressNotes` text field, store a `progress_entries[]`
array with timestamped micro-notes:
```json
[
  { "timestamp": "2026-03-17T10:00Z", "status": "on_track", "note": "Meeting with Acme went well" },
  { "timestamp": "2026-03-19T14:00Z", "status": "at_risk", "note": "Legal review delayed" }
]
```

This structured history becomes rich context for the reconciliation AI draft.

**Option C — Notification-driven prompts:**
The cadence reminder system (now implemented) could include a mid-week
check-in prompt: "How's your KING going? [Quick update]" — links directly to
a minimal check-in form, not the full plan page.

### Data Requirements

New field or related table for structured progress entries. Alternatively,
append-only JSON in the existing `progress_notes` column.

---

## 6. Lock-Time AI Quality Nudge

### The Pain

The validation panel says "you have errors" or "you're good to lock." It's
binary. It doesn't help with *strategic quality* — a plan can pass all
validation (1 KING, all RCDO-linked) and still be badly aligned.

### The Feature

At lock time, before the confirmation dialog, show a non-blocking AI
assessment:

```
┌─────────────────────────────────────────────────┐
│  ✨ Plan Quality Check                          │
│                                                  │
│  ⚠️  Your plan has no commits linked to          │
│     "Scale Revenue" — your team's top rally cry  │
│                                                  │
│  ℹ️  4 of 6 commits are Operations/Tech Debt.    │
│     Last week was similar. Consider whether a    │
│     Delivery item is missing.                    │
│                                                  │
│  ✅ Chess distribution looks balanced.            │
│                                                  │
│  [Lock Anyway]  [Review Plan]                    │
└─────────────────────────────────────────────────┘
```

This is **advisory, not blocking**. The IC can always lock.

### Data Requirements

RCDO tree (for rally cry priorities), team's recent outcome distribution
(for comparison), current plan commits. All available.

---

## 7. Team-Context RCDO Suggestions

### The Pain

The current RCDO auto-suggest sends the commit title + RCDO tree to the LLM.
It has no idea what the *team* is working on. If 5 people on the team are all
linking similar work to outcome X, the AI doesn't know that and can't leverage
the signal.

### The Feature

Include team-level RCDO usage context in the suggestion prompt:
- "Top 5 outcomes your team linked to this week: [list with counts]"
- "Your manager's team has 0 commits on outcome Y this quarter"

This makes suggestions more contextually relevant without changing the API
contract or cache model. The team-context can be precomputed and cached
(TTL: 1 hour) to avoid per-request overhead.

---

## 8. Smarter Reconciliation with Richer Context

### The Pain

The AI reconciliation draft guesses at completion status based on commit
title + description + progressNotes. With thin `progressNotes`, it's mostly
guessing. Users end up overriding everything, which defeats the purpose.

### The Feature

Feed richer context into the reconciliation draft prompt:
- Structured check-in history (#5 above)
- Carry-forward history ("this item was partially done last week too")
- Category context ("Operations items in this team have a 90% DONE rate")
- If integrated: linked ticket status changes during the week

Target: ≥ 70% of AI-drafted reconciliation fields accepted without
modification (up from current baseline).

---

## 9. Admin / People Ops Dashboard

### The Pain

The PRD mentions Admin as a persona but nothing exists. As the tool scales
past the dogfood team, someone needs to configure cadence, view adoption,
and manage rollout. Currently all configuration requires code changes.

### The Feature

An admin view that surfaces:
- **Adoption metrics**: active users, plans created/locked/reconciled by week,
  adoption funnel (created → locked → reconciled → reviewed)
- **Cadence configuration**: lock day/time, reconciliation day/time per team
  (reads/writes `org_policies` table)
- **Chess rule configuration**: KING/QUEEN limits per team
- **RCDO health**: which outcomes have the most/least coverage, stale outcomes
- **AI usage**: suggestion acceptance rates, token spend, cache hit rates
- **Feature flag management**: visual toggle for AI features per team

### Data Requirements

Most data exists. `org_policies` table is now wired to a service (duet run 2).
Adoption metrics require new aggregate queries. Feature flag management
requires integration with the flag service.

---

## 10. External Integration Signals (Jira/Linear/GitHub)

### The Pain

Users maintain commitments in WC and tickets in Jira/Linear separately.
Double-entry friction, and the progress signals in the external tool never
flow back to inform WC.

### The Feature

Per PRD §17.4.4 (ADR-010):
- **Inbound**: Link a Jira/Linear ticket to a commit. Auto-populate title,
  description, and progress from ticket transitions.
- **Outbound**: When reconciliation status changes, post a comment on the
  linked ticket.
- **Progress signals**: Ticket moved to "In Review" → auto-update check-in
  status to "on_track." Ticket moved to "Blocked" → flag as "at_risk."

This is the highest-leverage integration for making reconciliation effortless
and making the Next-Work Engine (#1) dramatically more accurate.

---

## 11. Weekly Digest Notifications

### The Pain

Managers who don't check the dashboard daily miss signals. An end-of-week
summary delivered via Slack or email would surface the most important
information passively.

### The Feature

A scheduled digest (configurable: Friday evening or Monday morning) per
manager:

```
📊 Weekly Commitments Digest — Week of March 16

Team Status: 8/10 plans reconciled, 2 still in LOCKED state
Review Queue: 3 plans awaiting your review

⚠️ Attention Needed:
- Alex: KING item carried forward 3rd week
- Sam: Plan still in DRAFT (stale)

✅ Highlights:
- Team RCDO alignment: 92% (up from 85% last week)
- 4 items completed ahead of schedule

[Open Dashboard →]
```

### Data Requirements

All data exists. Requires the multi-channel notification system (ADR-003,
implemented in H1) and a scheduled digest job.

---

## 12. Prioritization Matrix

### Effort vs. Impact

| # | Feature | Impact | Effort | Data Ready? | Depends On |
|---|---|---|---|---|---|
| **1** | AI Next-Work Suggestions (Phase 1: data-driven) | 🔴 High | Medium | ✅ Yes | Nothing |
| **1** | AI Next-Work Suggestions (Phase 2: LLM-ranked) | 🔴 High | Medium | ✅ Yes | Phase 1 |
| **1** | AI Next-Work Suggestions (Phase 3: Jira context) | 🔴 High | High | ❌ No | ADR-010 |
| **2** | "Start My Week" plan draft | 🔴 High | Low-Med | ✅ Yes | Nothing |
| **3** | Cross-week trends for ICs | 🟡 Medium | Low | ✅ Yes | Nothing |
| **4** | Enhanced manager anomaly detection | 🟡 Medium | Low | ✅ Yes | Nothing |
| **5** | Quick daily check-in | 🔴 High | Medium | ⚠️ Partial | Schema change for structured entries |
| **6** | Lock-time AI quality nudge | 🟡 Medium | Low | ✅ Yes | Nothing |
| **7** | Team-context RCDO suggestions | 🟡 Medium | Low | ✅ Yes | Nothing |
| **8** | Smarter reconciliation context | 🟡 Medium | Low | ⚠️ Partial | #5 (check-ins) |
| **9** | Admin dashboard | 🟡 Medium | High | ⚠️ Partial | OrgPolicy service (done) |
| **10** | Jira/Linear integration | 🔴 High | High | ❌ No | ADR-010 |
| **11** | Weekly digest notifications | 🟡 Medium | Medium | ✅ Yes | ADR-003 multi-channel |

### Recommended Implementation Order

**Wave 1 — Quick wins, data already exists (weeks 1-3):**
1. Cross-week trends API + UI for ICs (#3)
2. Enhanced manager anomaly detection with multi-week context (#4)
3. Team-context RCDO suggestions (#7)
4. Lock-time AI quality nudge (#6)

**Wave 2 — Core new capabilities (weeks 4-8):**
5. "Start My Week" plan draft (#2)
6. AI Next-Work Suggestions, Phase 1: data-driven (#1)
7. Quick daily check-in (#5)

**Wave 3 — AI-enriched features (weeks 8-12):**
8. AI Next-Work Suggestions, Phase 2: LLM-ranked (#1)
9. Smarter reconciliation with richer context (#8)
10. Weekly digest notifications (#11)

**Wave 4 — Platform extensions (weeks 12+):**
11. Admin / People Ops dashboard (#9)
12. Jira/Linear integration (#10)
13. AI Next-Work Suggestions, Phase 3: external ticket context (#1)

---

## Appendix: Relationship to Existing PRD

| This Document | PRD Reference | Status |
|---|---|---|
| AI Next-Work Suggestions | §17.4.3 "Weekly Planning Assistant" agent | Expands scope significantly |
| "Start My Week" | §8.1 "Clone from previous week (optional)" | More opinionated, AI-enhanced version |
| Cross-week trends | Not in PRD | New capability |
| Manager anomaly detection | §4 "Manager insight summaries" | Enhancement of existing feature |
| Quick daily check-in | §6 "progressNotes (single mutable field)" | Structural evolution of existing field |
| Lock-time quality nudge | §7 "Validation panel" | Enhancement of existing feature |
| Team-context suggestions | §4 "RCDO auto-suggest" | Enhancement of existing feature |
| Smarter reconciliation | §4 "Reconciliation draft" | Enhancement of existing feature |
| Admin dashboard | §3 "Admin/People Ops (optional)" | New capability |
| Jira/Linear integration | §17.4.4 ADR-010 | Already planned |
| Weekly digest | §17.3.3 ADR-003 | Already planned |
