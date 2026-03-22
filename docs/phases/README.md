# Implementation Phases — Beyond-MVP Intelligence Roadmap

> Created 2026-03-20. Living document.
>
> These phases build on the existing MVP (weekly plan lifecycle, AI RCDO
> suggest, reconciliation drafts, manager insights, trends, check-ins,
> next-work suggestions). They extend the platform toward a system that
> **learns about users, forecasts strategic risk, and makes weekly planning
> a 3-minute habit backed by real intelligence**.

---

## Phase ordering rationale

```
Phase 1 ──► Phase 2 ──► Phase 3 ──► Phase 4 ──► Phase 5 ──► Phase 6
 data         use the     add time    model       synthesize   persistent
 generation   data        pressure    capacity    everything   work graph
```

| Phase | Title | Why this order |
|-------|-------|----------------|
| **1** | [Quick Update Flow & User Model](phase-1-quick-updates-and-user-model.md) | Generates the richest data and solves immediate friction. Everything downstream needs this data. |
| **2** | [Multi-Week Strategic Intelligence](phase-2-multi-week-strategic-intelligence.md) | Biggest unlock for managers. Uses Phase 1 data to surface patterns and trends that no human tracks manually. |
| **3** | [RCDO Target Dates & Urgency Modeling](phase-3-rcdo-target-dates-and-urgency.md) | Adds the time dimension. "Is this strategic?" becomes "How urgent is this strategy?" Enables forecasting. |
| **4** | [Capacity Planning & User Performance Model](phase-4-capacity-and-forecasting.md) | Benefits from weeks of accumulated Phase 1 data. Turns the user model into realistic delivery predictions. |
| **5** | [Predictive Intelligence & AI Manager Planning](phase-5-predictive-intelligence-and-manager-planning.md) | Synthesizes all prior phases into forward-looking planning. Most complex; needs the richest foundation. |
| **6** | [Issue Backlog, Teams & AI-Powered Work Intelligence](phase-6-issue-backlog-and-teams.md) | Replaces ephemeral commits with persistent issues, adds team backlogs, and enables AI to reason holistically over work history via RAG. |

---

## What already exists (current state)

Before these phases, the MVP already has:

| Capability | Status | Key files |
|------------|--------|-----------|
| Weekly plan lifecycle (DRAFT→LOCKED→RECONCILING→RECONCILED→CARRY_FORWARD) | ✅ Implemented | `plan/domain/`, `plan/service/` |
| Chess-layer prioritization | ✅ Implemented | `WeeklyCommitEntity`, `OrgPolicyService` |
| RCDO auto-suggest with team context | ✅ Implemented | `DefaultAiSuggestionService`, `PromptBuilder` |
| Reconciliation draft with check-in + carry-forward context | ✅ Implemented | `PromptBuilder.buildReconciliationDraftMessages` |
| Manager insights with multi-week history | ✅ Implemented | `ManagerInsightDataProvider`, `PromptBuilder.buildManagerInsightsMessages` |
| Cross-week trends for ICs | ✅ Implemented | `DefaultTrendsService`, `TrendsController` |
| Next-work suggestions (Phase 1 data + Phase 2 LLM ranking) | ✅ Implemented | `DefaultNextWorkSuggestionService` |
| Daily check-ins (structured progress entries) | ✅ Implemented | `CheckInController`, `ProgressEntryEntity` |
| Start-from-last-week plan drafting | ✅ Implemented | Frontend `WeeklyPlanPage`, backend endpoint |
| Plan quality nudge at lock time | ✅ Implemented | `DefaultPlanQualityService` |
| External integration stubs (Jira/Linear) | ⚠️ Stubbed | `JiraAdapter`, `LinearAdapter`, `IntegrationController` |
| Admin dashboard with AI usage + feature flags | ✅ Implemented | `AdminController`, `AdminDashboardPage` |
| Notification bell with digest support | ✅ Implemented | `NotificationBell`, cadence reminder job |

---

## Relationship to assignment

The [assignment](../assignment.md) defines the core product: weekly commit
CRUD, RCDO linking, chess prioritization, lifecycle state machine,
reconciliation, and manager dashboard. **That is the main entry point.**
External integrations (Jira/Linear) and the features below are enhancements
that increase adoption, reduce friction, and make the strategic intelligence
more useful — but they are built on top of a working core, not instead of it.

---

## Parallel implementation with agents

These phases can be parallelized across coding agents. See
[agent-prompts.md](agent-prompts.md) for ready-to-use prompts for each
agent, organized into three waves:

| Wave | Agents | Runs |
|------|--------|------|
| **Wave 1** | A (Quick Update), B (Strategic Intel), C (Urgency), D (Capacity) | In parallel |
| **Wave 2** | E (Integration wiring), F (Learning loop) | In parallel, after Wave 1 |
| **Wave 3** | G (Predictive + Planning Copilot) | Sequential, after Wave 2 |

---

## Relationship to sidelined items

See [sidelined.md](../sidelined.md) for topics deferred to later sessions:
- AI eval / observability deep-dive
- Platform surface / API / MCP exploration
- Multi-channel notifications (starting with email digests)
- Production hardening / polish pass
