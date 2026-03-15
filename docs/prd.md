## PRD: Weekly Commitments Module (15Five Replacement) for Strategic Alignment

### Document meta

* **Product:** Weekly Commitments (WC) micro-frontend + backing service
* **Audience:** Engineering, Product, Design, QA
* **Status:** Draft
* **Target environment:** Embedded in existing PA host app (PM remote pattern)
* **Tech constraints:** TypeScript (strict), Java 21, SQL (Postgres assumed unless host mandates SQL Server)
* **Team size:** Small (1-10 engineers) - scope and phasing reflect this reality

---

## 1) Background and problem

### Problem statement

The org currently uses 15Five for weekly planning, but weekly commitments are not structurally connected to strategic goals (Rally Cries → Defining Objectives → Outcomes). Managers cannot reliably see whether weekly work aligns with strategy until after execution, which creates late discovery of misalignment and wasted capacity.

### Why now

* Scaling from ~$100M → $500M+ requires tight execution-to-strategy linkage and fast iteration.
* Weekly planning is a high-frequency workflow and the best lever for alignment and capacity steering.
* AI capabilities (LLMs, agents) are now mature enough to reduce the friction of manual tagging and reconciliation - this is a chance to build alignment tooling with AI at its core rather than bolting it on later.

---

## 2) Goals, non-goals, and success metrics

### Goals

1. **Enforce structural linkage** between each weekly commitment and the RCDO hierarchy (or explicitly classify as "non-strategic" with reason).
2. Support a **complete weekly lifecycle**: commit entry → prioritization → lock → reconcile planned vs actual → manager review → carry-forward.
3. Provide **manager visibility** into alignment, risk, and load via team roll-ups and status tracking.
4. Deliver as a **production-ready micro-frontend** integrated into the PA host via the PM remote pattern.
5. Provide **high confidence via tests** (unit/integration/contract/e2e) and operational readiness (logging/metrics/tracing).

### Non-goals (initial release)

* Full OKR authoring/management (RCDO is assumed to exist elsewhere; WC reads it).
* Compensation/performance review automation.
* Cross-org resource planning beyond manager's reporting chain (can be later).
* Replacing all 15Five features (e.g., 1:1 agenda, engagement surveys).

### Success metrics (first 60–90 days)

* **≥ 90%** of weekly commits have an RCDO link or an explicit "non-strategic" classification.
* **≥ 80%** of users complete reconciliation by end-of-week + 1 business day.
* **Manager review latency**: median < 2 business days after reconciliation.
* **Misalignment detection**: managers can identify top 3 off-strategy areas within < 5 minutes in dashboard usability tests.
* **AI suggestion acceptance rate**: ≥ 60% of RCDO auto-suggestions accepted without modification (indicates useful suggestions, not rubber-stamping).
* **System reliability**: 99.9% successful API requests; p95 UI load < 2s on internal networks.

---

## 3) Users and key use cases

### Personas

* **IC (Individual Contributor):** Plans weekly work, prioritizes, executes, reconciles, carries forward.
* **Manager:** Reviews direct reports' plans, monitors status, identifies misalignment, intervenes early.
* **Admin/People Ops (optional):** Configures cadence rules, access, and reporting (minimal in MVP).

### Primary user stories

**IC**

1. Create my weekly plan for the current week with a list of commitments.
2. For each commitment, link it to a Rally Cry / Objective / Outcome.
3. Prioritize commitments using the chess layer so I can express what "must happen" vs "nice to have."
4. Lock my plan once finalized.
5. At week end, reconcile: mark what happened vs what was planned, capture deltas, carry forward.

**Manager**

1. See which team members have drafted/locked/reconciled weeks.
2. View a roll-up of team commitments grouped by Rally Cry / Objective / Outcome.
3. Identify incomplete commits (in DRAFT), non-strategic work, low-priority overload, and capacity risk early.
4. Review and approve reconciliation, request changes, and comment.

---

## 4) Scope

### In-scope (MVP)

* Weekly plan CRUD (per user per week)
* Commitment CRUD with mandatory RCDO link or "Non-strategic" reason
* Chess-layer classification for commitment prioritization
* Weekly lifecycle state machine:

  * **DRAFT → LOCKED → RECONCILING → RECONCILED → CARRY_FORWARD**
  * Exception path: **DRAFT → RECONCILING** (late lock; see §6)
* Reconciliation view (planned vs actual, per commitment)
* Manager dashboard with team roll-up + filters
* Micro-frontend integration (PM remote pattern)
* Audit trail of lifecycle changes
* Notifications (in-app banners only in MVP; email/Slack post-MVP via outbox consumers)

**MVP notification triggers (in-app only):**

| Trigger | Recipient | Timing | Message |
|---|---|---|---|
| Plan still in DRAFT on lock day | IC | Monday 10:00 local | "Your weekly plan is still in draft. Lock it to set your baseline." |
| Plan still LOCKED on reconciliation day | IC | Friday 16:00 local | "Time to reconcile your week." |
| Plan not RECONCILED by Monday next week | IC | Monday 09:00 local | "Last week's reconciliation is overdue." |
| IC submits reconciliation | Manager | On submit | "{Name} submitted reconciliation for review." |
| Manager requests changes | IC | On action | "Your manager requested changes to your reconciliation." |
| Plan still in DRAFT, week has passed | Manager (dashboard) | Passive | STALE badge on dashboard (no push notification) |

Implementation: notification triggers are driven by outbox events consumed by a notification service. MVP renders in-app banners on page load by querying a `notifications` table. Push notifications (email/Slack) are post-MVP and use the same outbox events.

### AI-assisted workflows (MVP - not post-MVP)

ST6 builds systems where humans and AI work in tandem. These aren't nice-to-haves; they're core to the product thesis.

* **RCDO auto-suggest:** When a user types a commitment title/description, an LLM suggests the most likely Rally Cry → Objective → Outcome mapping. User confirms or overrides. This dramatically reduces friction and increases RCDO link rates.
* **Reconciliation draft:** At end-of-week, an agent pre-fills completion status and delta summaries based on commit descriptions and any linked context (e.g., status updates added during the week). User reviews and edits.
* **Manager insight summaries:** Dashboard surfaces a natural-language summary of alignment gaps, capacity risks, and patterns across the team - not just raw numbers.

**Implementation and guardrails:**

* Thin prompt layer calling an LLM API (e.g., Claude) behind internal `/api/v1/ai/*` endpoints.
* Responses are always suggestions, never auto-committed. The user confirms every AI output.
* **Structured output contract:** LLM must return schema-validated JSON. RCDO suggestions use:
  ```json
  {
    "suggestions": [
      { "outcomeId": "uuid", "rallyCryName": "...", "objectiveName": "...", "outcomeName": "...", "confidence": 0.87, "rationale": "..." }
    ]
  }
  ```
  The model can **only** select from RCDO IDs provided in the prompt context. Free-text IDs are rejected by schema validation. This eliminates hallucinated entities.
* **Scalability (token limits):** If the full RCDO tree exceeds a practical token budget (e.g., hundreds of outcomes), the service first retrieves a **candidate set** of ~50 outcomes using lexical search or lightweight embeddings over cached RCDO names/descriptions, then provides only that candidate set as LLM context. The "no hallucinated IDs" guarantee still holds because schema validation rejects any ID not in the candidate list.
* **Prompt security:** User-authored text (commit titles, descriptions) is treated as untrusted input. The system prompt and RCDO context are separated from user content using structured message roles — never string-concatenated. This mitigates prompt injection.
* **Reliability:** Hard timeout of 5s per request. On timeout or LLM error, the endpoint returns `200 OK` with `{ "status": "unavailable", "suggestions": [] }`. The UI treats an empty suggestions array as "no AI help available" and shows the manual picker without an error state. We use `200` (not `202`) because there is no async job to poll — the request is complete, there are just no results. If the service itself is unreachable (not the LLM), standard `503` applies.
* **Cost controls:** Cache suggestions keyed on `orgId + hash(commitTitle + commitDescription + rcdoTreeVersion)`. Rate limit: 20 AI requests per user per minute. Sufficient for active plan editing; prevents runaway costs.

**Phasing (scope control for a small team):**

* **MVP ship:** RCDO auto-suggest (synchronous, inline in commit editor). This is the highest-leverage feature — it directly drives the ≥ 90% RCDO link rate metric.
* **MVP beta (async, behind feature flag):** Reconciliation draft + manager insight summaries. These use the outbox/queue path and are clearly marked "AI-generated draft — review before submitting." Demo narrative: "Core workflow is fully functional without AI; AI accelerates it."

### Nice-to-have (post-MVP)

* Capacity planning (estimated hours/points vs available capacity)
* Cross-team rollups for skip-levels
* Integrations: Jira/Linear linking, Slack reminders
* Agent-driven weekly reminders via Slack (using SQS for delivery scheduling)

---

## 5) Core concepts and data model

### Key entities

* **RallyCry** (external/system-of-record)
* **DefiningObjective** (child of RallyCry)
* **Outcome** (child of DefiningObjective)
* **WeeklyPlan**

  * ownerUserId, weekStartDate (ISO Monday), state, reviewStatus, version, createdAt, updatedAt, lockedAt (nullable), **lockType** (`ON_TIME` | `LATE_LOCK`, nullable), **carryForwardExecutedAt** (nullable — set when carry-forward runs; gates manager review actions, see §6)
* **WeeklyCommit**

  * weeklyPlanId, title, description, chessPriority, category, outcomeId (nullable), nonStrategicReason (nullable), expectedResult, confidence, tags, **progressNotes** (text, editable after lock — the single mutable field for mid-week status updates), version, createdAt, updatedAt
  * **RCDO snapshot fields** (populated at lock time): `snapshotRallyCryId`, `snapshotRallyCryName`, `snapshotObjectiveId`, `snapshotObjectiveName`, `snapshotOutcomeId`, `snapshotOutcomeName`
* **WeeklyCommitActual**

  * commitId, actualResult, completionStatus, deltaReason, timeSpent (optional)
* **ManagerReview**

  * weeklyPlanId, reviewerUserId, decision (approved/changes_requested), comments, createdAt

### Plan state vs review status (two independent dimensions)

Plan lifecycle state and manager review status are **separate concerns**. Conflating them creates ambiguous states and blocks carry-forward on slow reviewers.

**Plan state** (lifecycle): `DRAFT → LOCKED → RECONCILING → RECONCILED → CARRY_FORWARD`

**Review status** (on WeeklyPlan, orthogonal):
* `REVIEW_NOT_APPLICABLE` — plan has not reached RECONCILED yet
* `REVIEW_PENDING` — user submitted reconciliation; awaiting manager
* `CHANGES_REQUESTED` — manager pushed back (plan returns to RECONCILING with reason)
* `APPROVED` — manager signed off

Carry-forward does **not** require APPROVED. A user can carry forward once RECONCILED regardless of review status. This prevents manager bottlenecks in an async/remote-first team. Manager approval is tracked for reporting but does not gate the user's next week.

### RCDO snapshots at lock time

RCDO entities can be renamed, re-parented, or archived over time. To preserve historical accuracy:

* At LOCK (or late lock), the system copies `rallyCryName`, `objectiveName`, and `outcomeName` into snapshot fields on each commit.
* UI renders the snapshot by default. A "refresh from source" option shows current names if they've drifted.

**Which field is used where:**
* **`outcomeId`** (the live foreign reference): used for **real-time roll-up queries** (manager dashboard grouping, filtering). This ensures roll-ups reflect the current RCDO hierarchy, even if names changed.
* **Snapshot fields** (`snapshotRallyCryName`, `snapshotObjectiveName`, `snapshotOutcomeName`): used for **historical reporting, audit, and display within a specific week's plan**. What the user saw that week is the truth for that week.
* If an `outcomeId` is archived/deleted upstream, the commit's live link becomes stale. The system detects this on read (RCDO cache miss) and renders the snapshot with an "archived" badge. The commit remains valid.

### Optimistic locking

All mutable entities (`WeeklyPlan`, `WeeklyCommit`) carry a `version` integer column.

* Every PATCH increments `version`.
* Clients must send `If-Match: {version}` header (or `version` in request body).
* Server returns `409 Conflict` with current `version` if stale.
* This prevents silent overwrites in concurrent editing (e.g., user editing on two tabs, or manager viewing while user edits).

**Actuals and the aggregate root:** `WeeklyCommitActual` does not carry its own `version`. The `WeeklyCommit` is the aggregate root — writing actuals (via `PATCH /commits/{id}/actual`) increments `WeeklyCommit.version`. The client sends `If-Match` with the commit's version, not the actual's. This keeps the concurrency model simple: one version counter per commit, covering both planning and reconciliation data.

### Chess layer (proposal)

A single, explicit priority signal that is easy to roll up and reason about.

* **KING:** Must happen; failure creates major risk
* **QUEEN:** High leverage; important
* **ROOK:** Strong execution item; can be traded if needed
* **BISHOP:** Support/enablement; situational
* **KNIGHT:** Exploration/learning; opportunistic
* **PAWN:** Small tasks; fill-ins / hygiene

Constraints (configurable):

* Exactly **1 KING** per week (default rule)
* Max **2 QUEEN**
* Unlimited others
  These rules create forced prioritization and enable manager comparisons.

**Where "configurable" lives (MVP):** Chess rules and cadence policies are **org-wide defaults** stored in a `org_policies` config object (AWS AppConfig or a `org_policies` table). There is no per-team admin UI in MVP. Values:

```json
{
  "chess": { "kingRequired": true, "maxKing": 1, "maxQueen": 2 },
  "cadence": { "lockDay": "MONDAY", "lockTime": "10:00", "reconcileDay": "FRIDAY", "reconcileTime": "16:00" },
  "validation": { "blockLockOnStaleRcdo": true, "rcdoStalenessThresholdMinutes": 60 }
}
```

Post-MVP: per-team overrides via a lightweight admin UI or API. Config changes are audited.

### Categories (example)

* Delivery, Operations, Customer, GTM, People, Learning, Tech Debt

### System invariants (single reference)

These are enforced server-side. The UI should prevent violations, but the backend is the source of truth.

1. **One plan per (orgId, userId, weekStartDate).** Unique DB constraint. `weekStartDate` must be an ISO Monday.
2. **Commits are incomplete until locked.** In DRAFT, `outcomeId`, `nonStrategicReason`, and `chessPriority` may all be null — the user is still drafting. The system computes a `validationErrors[]` array per commit (e.g., `MISSING_CHESS_PRIORITY`, `MISSING_RCDO_OR_REASON`) and exposes it on read so the UI can show inline warnings. At **lock time** (or late lock), the invariant is strict: every commit must have exactly one of `outcomeId` OR `nonStrategicReason` (never both, never neither), and `chessPriority` must be set. A commit with both `outcomeId` and `nonStrategicReason` is rejected (`422 CONFLICTING_LINK`). This "permissive draft, strict lock" model lets users build plans incrementally while guaranteeing data quality at the baseline snapshot.
3. **Chess constraints (evaluated at lock time, configurable per team):**
   * Exactly **1 KING** per plan (default).
   * At most **2 QUEEN** per plan (default).
   * Violations block the DRAFT → LOCKED transition.
4. **Locked plans are immutable** except `progressNotes` on WeeklyCommit (the single mutable field for mid-week updates). All other fields (title, description, chessPriority, outcomeId, nonStrategicReason, category, expectedResult, confidence, tags) are frozen after lock.
5. **Reconciliation completeness:** RECONCILING → RECONCILED requires every commit to have a `completionStatus` and, if status ≠ `DONE`, a non-empty `deltaReason`.
6. **Carry-forward lineage:** carried commits store `carriedFromCommitId` (non-null). This creates a DAG, not a chain — a commit can be carried multiple weeks. The system does not enforce a max carry count (but metrics track it for manager visibility).
7. **Optimistic locking:** all writes to plans and commits require a valid `version`. Stale writes return `409`.
8. **Audit completeness:** every state transition and every write to a locked plan produces an `audit_events` row with `actorUserId`, `action`, `timestamp`, `previousState`, `newState`, and `reason` (if applicable). Additionally, **deletes of commits in DRAFT** are audited (commit ID + title captured) to prevent "disappearing work" and support investigation if needed.

---

## 6) Weekly lifecycle and state machine

### States and edit permissions

| State | Add commits | Edit planning fields | Delete commits | Edit `progressNotes` | Edit actuals | Manager actions |
|---|---|---|---|---|---|---|
| **DRAFT** | ✅ | ✅ all fields | ✅ (audited) | ✅ | ❌ n/a | View (draft badge) |
| **LOCKED** | ❌ | ❌ frozen | ❌ | ✅ | ❌ n/a | View |
| **RECONCILING** | ❌ | ❌ frozen | ❌ | ✅ | ✅ (completionStatus, deltaReason, actualResult) | View |
| **RECONCILED** | ❌ | ❌ | ❌ | ❌ | ❌ | Review (approve / request changes / comment) |
| **CARRY_FORWARD** | ❌ | ❌ | ❌ | ❌ | ❌ | Approve or comment only (request changes blocked) |

1. **DRAFT**

   * User can add/edit/delete commits. All fields are mutable. Commits may be incomplete (missing RCDO link, chess priority).
   * Manager can view (optional) but sees "draft" badge.
2. **LOCKED**

   * User cannot add, delete, or edit commit planning fields. Only `progressNotes` is writable (mid-week status updates).
   * Lock can be manual or auto at a configured time (e.g., Monday 10:00 local).
3. **RECONCILING**

   * Triggered at end-of-week (e.g., Friday 16:00), via late lock, or user-initiated.
   * User writes `WeeklyCommitActual` fields (completion status, actual result, delta reason) and can update `progressNotes`. Cannot add/delete/reprioritize commits.
4. **RECONCILED**

   * User submits reconciliation. `reviewStatus` flips to `REVIEW_PENDING`. Plan is read-only for the IC. Manager can review, but carry-forward is not blocked on approval.
5. **CARRY_FORWARD**

   * Incomplete items have been copied into next week's DRAFT. This plan is fully read-only. Manager review is limited to approve/comment (request changes blocked per §6 carry-forward policy).

### State transition rules (MVP)

* DRAFT → LOCKED: user action; requires full validation (see invariants §5)
* LOCKED → RECONCILING: time-based prompt or user action (configurable); at transition the system can trigger AI reconciliation draft (async, beta)
* RECONCILING → RECONCILED: user submit; requires all commits to have a completion status + delta notes for non-completion. Sets `reviewStatus = REVIEW_PENDING`.
* RECONCILED → CARRY_FORWARD: user selects items to carry forward; system creates next week's commits with references. Does **not** require manager approval (see §5, review status model).

### Late lock path (DRAFT → RECONCILING)

If a user never locked their plan and the week has passed, they still need to reconcile. Blocking reconciliation behind a missed lock creates orphaned weeks that never close, which undercuts the "complete weekly lifecycle" goal.

**Behavior:** If `plan.state == DRAFT` and the user starts reconciliation (or the week has ended):

1. System performs an **implicit baseline snapshot** — same as a normal lock (RCDO snapshots populated, validation gating applied). If validation fails, the user must fix commits before reconciling.
2. `plan.lockedAt = now`, `plan.lockType = LATE_LOCK` (vs `ON_TIME` for normal locks).
3. Plan transitions directly to `RECONCILING`, skipping the `LOCKED` state.
4. Manager dashboard shows a **"Late lock"** badge on these plans to surface cadence misses.

This preserves data integrity (every reconciled plan has a baseline) while keeping the "don't auto-lock incomplete plans" philosophy. The `lockType` field enables reporting on cadence adherence without adding a lifecycle state.

### Review status transitions (orthogonal to plan state)

* REVIEW_PENDING → APPROVED: manager approves
* REVIEW_PENDING → CHANGES_REQUESTED: manager requests changes → plan state reverts to RECONCILING with comment; review status stays CHANGES_REQUESTED until user re-submits (**only if carry-forward has not yet been executed** — see below)
* CHANGES_REQUESTED → REVIEW_PENDING: user re-submits reconciliation

**Changes requested after carry-forward:** Once carry-forward has been executed (`plan.carryForwardExecutedAt != null`), the plan's reconciliation data has already influenced next week's draft. Allowing full amendment creates an inconsistency: the prior week is "revised" after it has already seeded the next.

Policy (MVP): If carry-forward has already happened, the manager's review actions are restricted to:
* **Approve** — sign off as-is
* **Comment only** — add feedback without reverting state

The manager **cannot** request changes that revert the plan to RECONCILING. The UI disables "Request Changes" and shows: _"Carry-forward already executed. Add comments for next week's planning."_ This is logged in the audit trail.

Rationale: for a small async team, retroactive amendments to closed weeks create confusion. The manager's feedback is captured via comments and applied forward. If post-MVP experience shows managers need amendment power, we add an explicit "amend reconciliation" flow that does **not** auto-update carried-forward commits but creates a visible revision history.

Manager review is tracked and auditable but does not block the IC's weekly cadence. This is a deliberate design choice for async/remote-first teams.

### Week boundary edge cases

* **User never locks by Friday:** System does **not** auto-lock. An unlocked plan shows a `STALE` visual indicator in the manager dashboard (not a lifecycle state — just a UI flag based on `plan.state == DRAFT && weekStartDate < currentWeek`). Manager can ping the user. When the user is ready, they reconcile via the **late lock path** (see above): the system performs an implicit baseline snapshot and transitions DRAFT → RECONCILING directly, recording `lockType = LATE_LOCK`. This preserves data integrity while keeping the "don't auto-lock incomplete plans" philosophy.
* **Past/future week plans:** Users can create plans for the **current week** and **next week** only. Creating plans for past weeks is blocked (returns `422`). This prevents retroactive plan creation that undermines the cadence discipline. Viewing/reconciling past weeks is always allowed.
* **Holidays / time off:** Out of scope for MVP. Default behavior: the week exists like any other. Users with no commits simply have an empty plan. Post-MVP: integrate with PTO calendar to auto-mark weeks as "OOO" and suppress reminders.
* **Timezone handling:** All `weekStartDate` values are ISO date strings (`2026-03-09`), always a Monday. Time-based triggers (auto-reconciliation prompt) fire based on the user's IANA timezone from their PA profile. The server stores UTC; the client converts for display.

### Validation gating

A plan cannot be LOCKED unless:

* All commits have:

  * title
  * chessPriority
  * RCDO link **OR** nonStrategicReason
* "Exactly 1 KING" rule satisfied (if enabled)
* RCDO service was reachable within the last hour (link validation must be fresh; configurable)

---

## 7) UX / flows (high level)

### IC flow: plan creation and locking

1. Select week (default: current week).
2. Add commitments (inline editor).
3. For each commit:

   * pick chess piece (priority)
   * pick category
   * link to Rally Cry → Objective → Outcome (typeahead + picker)
4. System shows validation panel:

   * missing RCDO links
   * too many high-priority items
5. User clicks **Lock Week**.

### IC flow: reconciliation

1. Open "Reconcile Week".
2. For each commit:

   * completion status: Done / Partially / Not Done / Dropped
   * actual result text
   * delta reason (required if not Done)
3. Summary section:

   * top learnings
   * what to stop/start/continue (optional)
4. Submit reconciliation.

### Manager flow: dashboard and review

1. Dashboard default view: direct reports, current week status.
2. Roll-up panel:

   * commits grouped by Rally Cry / Objective / Outcome
   * counts by chess piece
   * highlight incomplete commits (missing RCDO or priority) and non-strategic work
3. Drill down into a person:

   * see their weekly plan, priorities, and reconciliation notes
4. Manager review actions:

   * Approve
   * Request changes + comment (disabled if `carryForwardExecutedAt != null` — see §6)

---

## 8) Functional requirements (detailed)

### 8.1 Weekly plan CRUD

* Create plan for a given week (one per user per week)
* View plan by week
* Clone from previous week (optional for MVP)
* Delete plan only in DRAFT (optional)

### 8.2 Weekly commit CRUD

* Add/edit/delete commits in DRAFT
* Editing constraints in LOCKED (configurable; default: allow progress notes but not priority/RCDO)
* Bulk edit for category and RCDO link (optional)

### 8.3 RCDO hierarchy linking

* Picker supports:

  * browse tree (Rally Cry → Objective → Outcome)
  * typeahead search by name/ID
* Must store stable identifiers (IDs), not just names
* If RCDO service unavailable:

  * read-only cached view
  * prevent locking if link validation cannot be performed (configurable; default: block lock)

### 8.4 Chess-layer prioritization

* Required field per commit
* Enforced rules (configurable per org/team)
* Roll-up analytics by chess piece

### 8.5 Lifecycle state machine

* Enforced server-side (backend is source of truth)
* All transitions recorded in audit log with actor + timestamp + reason (if applicable)

### 8.6 Reconciliation

* Compare planned vs actual at commit-level
* Required fields on reconciliation submit:

  * completion status for each commit
  * delta reason for incomplete/dropped
* Carry-forward creates next week's commits with:

  * `carriedFromCommitId`
  * annotation "carried forward"

### 8.7 Manager dashboard

* Team status grid:

  * user, week, state, reviewStatus, last updated, #commits, #incomplete (commits with validation errors), #nonStrategic, #KING/#QUEEN
* Roll-up by RCDO:

  * counts of commits, distribution of chess priorities
  * "non-strategic" count (commits with `nonStrategicReason` instead of RCDO link)
  * "incomplete" count (DRAFT commits still missing required fields — actionable signal)
* Drill-down view for each direct report
* Filters: week, state, reviewStatus, rally cry, objective, outcome, chess priority, category, incomplete (DRAFT only), nonStrategic

### 8.8 Comments and review

* Manager comments on plan and/or per commit (MVP can be plan-level only)
* Review decision captured and auditable

---

## 9) Target architecture

This section defines the system's structural design: what containers exist, how they communicate, what external systems they depend on, and how data flows through the system. Non-functional requirements (§10) constrain the architecture; the API surface (§11) specifies its external contract.

### 9.0 Architecture philosophy: modular monolith with event-driven boundaries

A small team (1–10 engineers) building a module embedded in an existing host app does not benefit from a distributed microservice topology. Premature decomposition creates operational overhead (independent deployments, service meshes, distributed tracing across N services, partial failure modes) that dwarfs the complexity of the domain.

**Design stance:** Weekly Commitments ships as a **modular monolith** — a single deployable unit (`weekly-service`) with clear internal module boundaries, backed by a single Postgres database with schema-level separation of concerns. Event-driven boundaries (transactional outbox → message bus → consumers) decouple side effects (notifications, analytics) from the write path, but these consumers are lightweight workers, not separate domain services.

Why this works for our context:

* **Single primary backend artifact.** The core domain ships as one `weekly-service` image and one rolling deploy path. Lightweight workers can reuse the same codebase/image via a different profile, so we avoid a cross-service version matrix in the core workflow.
* **Transactional consistency by default.** State transitions, outbox writes, and audit entries happen in one DB transaction. No sagas, no eventual consistency in the core workflow.
* **Clear seams for future extraction.** Internal modules (plan lifecycle, RCDO integration, AI suggestion, notification) communicate through defined interfaces and domain events. If scale or team growth demands extraction, the seams are already drawn — but we don't pay the distributed-systems tax until we need it.
* **Event-driven where it matters.** The outbox pattern gives us reliable async processing (notifications, analytics) without coupling those concerns into the request path. The message bus is a decoupling mechanism, not a service discovery mechanism.

The sections below describe the system as if each concern were a distinct "container" for clarity, but the deployment reality is: one service, one database, one or more lightweight workers consuming from a message bus.

### 9.1 System context and boundaries

The Weekly Commitments module does not own user identity, org structure, or strategic goals. These are upstream dependencies with explicit contracts. The system context diagram below shows WC's position relative to actors and external systems.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              PA Host Application                                │
│                                                                                 │
│  ┌──────────────────┐    PM Remote     ┌──────────────────────────────────────┐  │
│  │   PA Shell /     │◄───────────────►│  Weekly Commitments Micro-Frontend   │  │
│  │   Host App UI    │   (Module Fed.)  │  (React + TypeScript strict)         │  │
│  └──────────────────┘                  └───────────────┬──────────────────────┘  │
│                                                        │ HTTPS (JWT in header)   │
│  ┌──────────────────┐                  ┌───────────────▼──────────────────────┐  │
│  │  PA Identity      │  JWKS / OAuth   │                                      │  │
│  │  Service (AuthN)  │◄──────────────►│  API Gateway / Edge (PA-managed)     │  │
│  └──────────────────┘                  └───────────────┬──────────────────────┘  │
│                                                        │                         │
└────────────────────────────────────────────────────────┼─────────────────────────┘
                                                         │
                          ┌──────── TRUST BOUNDARY ──────┼────────────────────┐
                          │                              │                    │
                          │          ┌───────────────────▼────────────────┐   │
                          │          │     weekly-service (Java 21)       │   │
                          │          │     ┌─────────────────────────┐    │   │
                          │          │     │  Plan Lifecycle Module  │    │   │
                          │          │     │  RCDO Integration       │    │   │
                          │          │     │  AI Suggestion Layer    │    │   │
                          │          │     │  Outbox Publisher       │    │   │
                          │          │     │  Notification Triggers   │    │   │
                          │          │     └─────────────────────────┘    │   │
                          │          └──┬────────┬────────┬──────────────┘   │
                          │             │        │        │                   │
                          │     ┌───────▼──┐  ┌──▼─────┐ │                   │
                          │     │ Postgres │  │ Redis  │ │                   │
                          │     │ (primary │  │ (cache)│ │                   │
                          │     │  store)  │  └────────┘ │                   │
                          │     └──────────┘             │                   │
                          │                    ┌─────────▼──────────┐        │
                          │                    │  Message Bus       │        │
                          │                    │  (SQS or Kafka)    │        │
                          │                    └─────────┬──────────┘        │
                          │                              │                   │
                          │                    ┌─────────▼──────────┐        │
                          │                    │  Notification      │        │
                          │                    │  Worker             │        │
                          │                    │  (lightweight)      │        │
                          │                    └────────────────────┘        │
                          │                                                  │
                          └──────────────────────────────────────────────────┘

          ┌─────────────────────────────────────────────────────────────────┐
          │                    External Upstream Systems                    │
          │                                                                 │
          │  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
          │  │  Org Graph /   │  │  RCDO Service  │  │  LLM API         │  │
          │  │  HRIS API      │  │  (PA-managed)  │  │  (Claude / etc.) │  │
          │  └────────────────┘  └────────────────┘  └──────────────────┘  │
          └─────────────────────────────────────────────────────────────────┘
```

#### Upstream dependency contracts

| Dependency | What we need | Expected source | Integration pattern | Failure mode |
|---|---|---|---|---|
| **AuthN/AuthZ** | JWT with `userId`, `orgId`, `roles[]` claims. Role values: `IC`, `MANAGER`, `ADMIN`. | PA host identity service (OAuth 2.0 + JWKS endpoint) | Synchronous. JWKS keys cached locally (TTL: 1h, refresh on 401). | Reject request (401/403). No fallback. |
| **Org graph (reporting chain)** | Given a `userId`, resolve direct reports. Used by manager dashboard and authorization ("is X a report of Y?"). | PA directory service or HRIS API. Expected: `GET /api/v1/org/users/{userId}/direct-reports`. | Synchronous with aggressive caching (Redis, TTL: 15 min). Background refresh every 10 min for active managers. | If unavailable: manager dashboard shows stale cached data with "data may be stale" banner. Authorization checks use cached graph; if cache is cold + service is down, deny manager access (fail closed). |
| **RCDO hierarchy** | Full tree of Rally Cries → Defining Objectives → Outcomes with stable IDs. Used for linking picker and validation. | Existing PA API or dedicated RCDO service. Expected: paginated `GET /api/v1/rcdo/tree`, rate limit ≥ 100 req/min. | Synchronous with local cache (Redis, TTL: 5 min). Full tree refresh triggered on cache miss or explicit invalidation. | Cached read-only view (cache TTL: 5 min). Block plan locking if cache is stale > 1 hour and service is unreachable (configurable). |
| **LLM API** | Text-in, structured-JSON-out. Used for RCDO auto-suggest and reconciliation drafts. | Claude API (or equivalent) behind internal abstraction. | Synchronous with hard 5s timeout. Response cached by `orgId + hash(input + rcdoTreeVersion)` so suggestions never bleed across tenants. | Non-blocking. UI falls back to manual entry. Hard timeout: 5s. See §9.5 AI abstraction. |

#### Trust boundaries

Three trust boundaries govern how data enters and exits the system:

1. **PA host ↔ WC micro-frontend.** The micro-frontend runs inside the PA host shell. It receives a valid JWT from the host's auth flow. The micro-frontend never stores credentials — it forwards the host-provided token on every API call. The host controls the authentication ceremony; WC trusts the host's token if it validates against the JWKS endpoint.

2. **API gateway ↔ weekly-service.** The API gateway (PA-managed) terminates TLS and forwards requests with the JWT intact. `weekly-service` validates the JWT signature against the JWKS endpoint on every request (cached keys, refresh on failure). The service trusts **nothing** from the request except the validated JWT claims and the request body (which is schema-validated). The `org_id` used for all data scoping comes exclusively from the JWT `orgId` claim — never from a request parameter or path segment. This eliminates a class of IDOR vulnerabilities.

3. **weekly-service ↔ external systems (RCDO, org graph, LLM).** These are service-to-service calls authenticated via mutual TLS or API keys (depending on host infra). The LLM API is treated as an **untrusted output source**: all LLM responses are schema-validated before use, and no LLM output is persisted without user confirmation. User-authored text sent to the LLM is treated as untrusted input with structured prompt separation (see §4, prompt security).

### 9.2 Container topology and technology choices

The system is composed of five logical containers. In deployment terms, container 2 (`weekly-service`) is the modular-monolith application artifact, containers 3-4 are managed backing services (Postgres, Redis), and container 5 is a separate lightweight worker process.

#### Container 1: WC micro-frontend

| Attribute | Detail |
|---|---|
| **Runtime** | Browser (loaded by PA host via Module Federation / PM remote pattern) |
| **Stack** | React 18+, TypeScript strict, PA design system components |
| **Build output** | Versioned remote entry (`remoteEntry.js`) served from CDN or PA asset host |
| **Shared deps** | React, React DOM, PA design tokens, routing adapter — aligned with host versions to avoid duplication |
| **Routes** | `/weekly` (IC plan view), `/weekly/team` (manager dashboard), `/weekly/plan/:planId` (deep link) |
| **State management** | React Query (TanStack Query) for server state; local component state for ephemeral UI. No global store — the server is the source of truth. |
| **Auth integration** | Receives JWT from PA host auth context. Attaches as `Authorization: Bearer <token>` on every API call. Never persists tokens. |
| **Error handling** | API errors render inline (toast for transient, inline for validation). If weekly-service is unreachable, the module shows a "service unavailable" placeholder — the PA host app remains functional. The micro-frontend is a guest; it must never crash the host. |
| **Observability** | Client-side error reporting (Sentry or PA's existing tool). Performance marks for LCP, TTI. Custom spans for AI suggestion latency (user-perceived). |

#### Container 2: weekly-service (API + domain logic)

| Attribute | Detail |
|---|---|
| **Runtime** | Java 21 (LTS), Spring Boot 3.x |
| **Build** | Gradle (Kotlin DSL) or Maven. Single fat JAR. Container image: `eclipse-temurin:21-jre-alpine`. |
| **API style** | REST/JSON, OpenAPI 3.1 contract-first (code generated from spec). Versioned under `/api/v1/`. |
| **Internal modules** | `plan-lifecycle` (state machine, validation, commands), `rcdo-integration` (cache, search, snapshot), `ai-suggestion` (prompt construction, schema validation, caching), `notification` (notification trigger policy, outbox event production, shared event contracts), `auth` (JWT validation, role+relationship checks), `audit` (append-only event writer) |
| **Persistence** | Spring Data JPA / Hibernate with Flyway migrations. Connection pool: HikariCP (max 20 connections, tuned for small-to-mid team load). |
| **Caching** | Redis (or Valkey) for: `rcdo:tree:{orgId}` (TTL: 5 min), `org:reports:{orgId}:{userId}` (TTL: 15 min), `ai:suggest:{orgId}:{contentHash}:{rcdoVersion}` (TTL: 1 hour), and global JWKS keys (TTL: 1 hour). Cache-aside pattern: read from cache, fetch on miss, write-through. |
| **Concurrency** | Virtual threads (Java 21 Project Loom) for I/O-bound operations (RCDO fetch, LLM calls, org graph lookups). This avoids thread pool exhaustion under load without reactive complexity. |
| **Health checks** | `/actuator/health` (Spring Boot Actuator): liveness (process up), readiness (DB reachable + required config loaded). Redis and message bus connectivity are reported as degraded health contributors, but they do **not** fail readiness because the service can continue in degraded mode (direct upstream calls, queued outbox backlog) without correctness loss. |

**Module boundary discipline:** Internal modules communicate through Java interfaces and domain events (in-process). There are no HTTP calls between modules. Each module owns its repository interfaces and does not directly query another module's tables. Cross-module reads go through service interfaces. This is enforced by ArchUnit tests in CI:

```java
// ArchUnit rule (illustrative)
noClasses().that().resideInAPackage("..plan..")
    .should().dependOnClassesThat().resideInAPackage("..notification..")
    .because("Plan module must not depend on notification internals");
```

#### Container 3: Postgres (primary data store)

| Attribute | Detail |
|---|---|
| **Version** | PostgreSQL 15+ (or 16) |
| **Schema** | Single database, single schema (`weekly`). All tables prefixed by concern (e.g., `weekly_plans`, `audit_events`, `outbox_events`). |
| **Multi-tenancy** | Row-level isolation via `org_id` on every table. No shared rows, no cross-org joins. Additionally, a Postgres Row-Level Security (RLS) policy is applied as a safety net: `CREATE POLICY org_isolation ON weekly_plans USING (org_id = current_setting('app.current_org_id')::uuid)`. The application sets `SET LOCAL app.current_org_id = :orgId` at the start of each transaction (or request-scoped connection use) so pooled connections cannot leak tenant context across requests. This provides database-level enforcement even if application code has a bug in query construction. |
| **Migrations** | Flyway. Forward-only (no down migrations in production). Each migration is idempotent where possible. Migration failures block deployment (see §13). |
| **Backups** | Managed by host infra (e.g., RDS automated backups, point-in-time recovery). WC does not manage its own backup strategy. |
| **Connection security** | TLS in transit. Credentials via environment variable or secrets manager (never in code or config files). |

**Core tables** (illustrative — see §5 for entity definitions):

* `weekly_plans` — `org_id`, `state`, `review_status`, `lock_type`, `carry_forward_executed_at`, `version`, `created_at`, `updated_at`
* `weekly_commits` — `org_id`, `version`, `progress_notes`, RCDO snapshot fields, `created_at`, `updated_at`
* `weekly_commit_actuals` — `org_id`
* `manager_reviews` — `org_id`
* `audit_events` — `org_id`; append-only; never updated or deleted
* `outbox_events` — `org_id`; transactional outbox for reliable event publishing (see §9.3)
* `notifications` — `org_id`, `user_id`, `type`, `payload`, `read_at`, `created_at`; in-app notification store
* `idempotency_keys` — see §11

**Indexes:**

* `(org_id, owner_user_id, week_start_date)` **unique** on `weekly_plans`
* `(org_id, weekly_plan_id)` on `weekly_commits`
* `(org_id, outcome_id)` on `weekly_commits` for roll-up queries
* `(org_id, week_start_date, state)` on `weekly_plans` for manager dashboard filters
* `(published_at)` on `outbox_events` for the publisher poller (null = unpublished)
* `(org_id, user_id, read_at)` on `notifications` for unread notification queries

#### Container 4: Redis (cache + ephemeral state)

| Attribute | Detail |
|---|---|
| **Version** | Redis 7+ or Valkey 7+ |
| **Purpose** | Caching layer only. No durable state. Full data loss is a **warm restart** scenario, not a data loss scenario — the system re-fetches from upstream sources. |
| **Cache keys** | `rcdo:tree:{orgId}` (TTL: 5 min), `org:reports:{orgId}:{userId}` (TTL: 15 min), `ai:suggest:{orgId}:{contentHash}:{rcdoVersion}` (TTL: 1 hour), `jwks:keys` (TTL: 1 hour, global shared cache) |
| **Eviction** | `allkeys-lru`. Max memory sized for expected working set (~50MB for a single-org MVP). |
| **Failure mode** | If Redis is down, weekly-service falls back to direct upstream calls (RCDO, org graph) with in-process LRU cache (Caffeine, small TTL: 60s) as a stopgap. AI suggestion caching is skipped — requests go directly to LLM. Latency increases but correctness is unaffected. |

#### Container 5: Notification worker

| Attribute | Detail |
|---|---|
| **Runtime** | Prefer Java 21 from the same codebase as `weekly-service`, launched with a different main class / Spring profile. This keeps event contracts, observability, and deployment tooling aligned for a small team. |
| **Deployment** | Separate process (e.g., ECS task, k8s Deployment with 1 replica). Reads from message bus, writes to `notifications` table and (post-MVP) sends emails/Slack messages. |
| **Scaling** | Single instance is sufficient for MVP volumes. SQS-based: visibility timeout handles at-least-once. Kafka-based: single consumer group, single partition is fine for MVP. |
| **Idempotency** | Keyed on `eventId`. Duplicate events (at-least-once delivery) are detected and skipped via a `processed_events` table or idempotency check. |
| **Failure mode** | If the worker is down, events accumulate in the message bus. No data loss — events are replayed when the worker recovers. In-app notifications are delayed but not lost. SQS dead-letter queue captures poison messages after 3 retries. |

### 9.3 Event-driven architecture and the outbox pattern

State transitions and audit events are published to a message bus (SQS or Kafka, depending on host infra) for decoupled consumption.

**Consumers:** notification worker (in-app banners, post-MVP email/Slack), analytics aggregation (post-MVP), manager alert triggers.

This keeps the write path fast and lets downstream concerns scale independently — important for a small team that can't afford to maintain tightly coupled notification logic inside the core service.

#### Synchronous vs. asynchronous paths

The system distinguishes clearly between what must happen in the request path (synchronous) and what can happen after the response is sent (asynchronous):

| Path | Pattern | Examples | Latency target |
|---|---|---|---|
| **Synchronous (in-request)** | API call → domain logic → DB write → response | Plan CRUD, lock (with validation + RCDO snapshot), reconciliation submit, commit edits, manager review | p95 < 250ms (CRUD), p95 < 500ms (lock with RCDO validation) |
| **Synchronous (with external call)** | API call → cache check → upstream fetch (on miss) → domain logic → response | RCDO search/tree, org graph lookup for authz, AI suggestion | p95 < 500ms (RCDO), p95 < 5s (AI, hard timeout) |
| **Asynchronous (fire-and-forget from request perspective)** | DB write includes outbox row → poller publishes → consumer processes | Notifications, audit event fan-out, analytics, email/Slack (post-MVP) | Best-effort, < 30s typical, < 5 min SLA |

**Key rule:** The user never waits for an async operation. Lock returns `200` as soon as the DB transaction commits (which includes the outbox row). The notification that tells the manager "plan was locked" is delivered asynchronously. If the notification worker is lagging, the manager sees it late — but the IC's workflow is unblocked.

#### Transactional outbox: reliability guarantee

Publishing directly to Kafka/SQS from the request path risks lost events if the DB commit succeeds but the publish fails (or vice versa). We use a transactional outbox:

1. Every state transition writes both the domain change **and** an `outbox_events` row in the **same DB transaction**. This is an atomic operation — either both succeed or neither does.
2. A background poller (scheduled task within weekly-service, running every 1s) reads unpublished outbox rows (`WHERE published_at IS NULL ORDER BY occurred_at LIMIT 100`) and publishes to the message bus, then marks them `published_at = now()`.
3. Events are **at-least-once**. Consumers must be idempotent, keyed on `eventId` (UUID).
4. If the poller crashes mid-batch, unpublished rows remain in the table and are picked up on the next poll. No events are lost.

**Outbox event schema:**

```
eventId:        UUID (PK)
eventType:      STRING  (e.g., "plan.locked", "plan.reconciled", "review.approved")
aggregateType:  STRING  (e.g., "WeeklyPlan")
aggregateId:    UUID
orgId:          UUID    (for consumer-side tenant routing)
payload:        JSONB   (event-specific data)
schemaVersion:  INT     (for forward-compatible consumers; MVP = 1)
occurredAt:     TIMESTAMP WITH TIME ZONE
publishedAt:    TIMESTAMP WITH TIME ZONE (null until published)
```

**Scaling the outbox:** For MVP volumes (hundreds of events/day), the simple poller is sufficient. If volumes grow to thousands/second, the path is: replace the poller with Debezium CDC (Change Data Capture) reading the Postgres WAL. This is a deployment change, not a code change — the outbox table schema stays the same. See §17 for the evolution roadmap.

### 9.4 Key architectural flows

This section traces end-to-end data flow for the three most architecturally significant operations. Each flow shows the synchronous request path, async side effects, failure handling, and observability integration.

#### Flow 1: Plan locking (DRAFT → LOCKED)

This is the most complex synchronous flow — it combines validation, external data fetch, snapshot creation, state transition, and event publishing in a single request.

```
IC Browser                WC Micro-frontend        API Gateway          weekly-service                      Postgres           Redis            Message Bus
    │                           │                       │                     │                                 │                  │                  │
    │  click "Lock Week"        │                       │                     │                                 │                  │                  │
    ├──────────────────────────►│                       │                     │                                 │                  │                  │
    │                           │  POST /plans/{id}/lock│                     │                                 │                  │                  │
    │                           │  + Idempotency-Key    │                     │                                 │                  │                  │
    │                           │  + If-Match: {ver}    │                     │                                 │                  │                  │
    │                           ├──────────────────────►│                     │                                 │                  │                  │
    │                           │                       │  forward + JWT      │                                 │                  │                  │
    │                           │                       ├────────────────────►│                                 │                  │                  │
    │                           │                       │                     │                                 │                  │                  │
    │                           │                       │                     │ ── 1. Validate JWT (cached JWKS)                    │                  │
    │                           │                       │                     │ ── 2. Extract orgId, userId from claims             │                  │
    │                           │                       │                     │ ── 3. Check idempotency key ───────────────────────►│                  │
    │                           │                       │                     │      (if exists, return stored response)            │                  │
    │                           │                       │                     │ ── 4. Load plan (WHERE org_id=? AND id=?) ─────────►│                  │
    │                           │                       │                     │      Check plan.owner_user_id == JWT userId         │                  │
    │                           │                       │                     │      Check plan.version == If-Match value           │                  │
    │                           │                       │                     │      Check plan.state == DRAFT                      │                  │
    │                           │                       │                     │ ── 5. Load all commits for plan ────────────────────►│                  │
    │                           │                       │                     │ ── 6. Validate commits (chess rules, RCDO/reason)   │                  │
    │                           │                       │                     │      If validation fails → 422 + error codes        │                  │
    │                           │                       │                     │ ── 7. Fetch RCDO names for snapshot ────────────────────────────────►│  │
    │                           │                       │                     │      (cache hit: use cached; miss: call RCDO svc)   │                  │
    │                           │                       │                     │      Check staleness < threshold                    │                  │
    │                           │                       │                     │                                 │                  │                  │
    │                           │                       │                     │ ── 8. BEGIN TRANSACTION ─────────────────────────────►│                  │
    │                           │                       │                     │      a. UPDATE plan: state=LOCKED, lockedAt=now,    │                  │
    │                           │                       │                     │         lockType=ON_TIME, version++                 │                  │
    │                           │                       │                     │      b. UPDATE commits: populate snapshot fields    │                  │
    │                           │                       │                     │      c. INSERT outbox_events: "plan.locked"         │                  │
    │                           │                       │                     │      d. INSERT audit_events: state transition       │                  │
    │                           │                       │                     │      e. INSERT idempotency_keys: store response     │                  │
    │                           │                       │                     │ ── 8. COMMIT TRANSACTION ────────────────────────────►│                  │
    │                           │                       │                     │                                 │                  │                  │
    │                           │                       │  200 OK + plan JSON │                                 │                  │                  │
    │                           │                       │◄────────────────────┤                                 │                  │                  │
    │                           │  200 OK               │                     │                                 │                  │                  │
    │                           │◄──────────────────────┤                     │                                 │                  │                  │
    │  "Week locked" toast      │                       │                     │                                 │                  │                  │
    │◄──────────────────────────┤                       │                     │                                 │                  │                  │
    │                           │                       │                     │                                 │                  │                  │
    │                           │                       │                     │ ── ASYNC (outbox poller, ~1s later) ──────────────────────────────────►│
    │                           │                       │                     │      Publish "plan.locked" event                    │                  │
    │                           │                       │                     │                                 │                  │       ┌──────────┤
    │                           │                       │                     │                                 │                  │       │Notif.    │
    │                           │                       │                     │                                 │                  │       │Worker    │
    │                           │                       │                     │                                 │                  │       │consumes  │
    │                           │                       │                     │                                 │                  │       │→ writes  │
    │                           │                       │                     │                                 │                  │       │  notif.  │
    │                           │                       │                     │                                 │                  │       │  row     │
    │                           │                       │                     │                                 │                  │       └──────────┘
```

**Failure scenarios for lock flow:**

| Failure | Detection | Behavior | User experience |
|---|---|---|---|
| JWT invalid/expired | Step 1 | `401 Unauthorized` | Host app redirects to login |
| Plan not found or wrong org | Step 4 | `404 Not Found` | "Plan not found" error |
| User doesn't own the plan | Step 4 | `403 Forbidden` | "Access denied" error |
| Version mismatch (concurrent edit) | Step 4 | `409 Conflict` with `currentVersion` | "Plan was modified. Refresh and try again." |
| Plan not in DRAFT | Step 4 | `409` with `PLAN_NOT_IN_DRAFT` | "Plan is already locked." |
| Commit validation failure | Step 6 | `422` with specific error codes | Inline errors on each failing commit |
| RCDO cache stale + service down | Step 7 | `422 RCDO_VALIDATION_STALE` | "Cannot verify RCDO links. Try again later." |
| DB transaction failure | Step 8 | `500 Internal Server Error` | "Something went wrong. Try again." Idempotency key not stored → safe to retry. |
| Outbox publish fails (poller) | Async | Events stay in outbox table | No user impact. Notifications delayed. Self-heals on next poll. |

#### Flow 2: AI-assisted RCDO suggestion

This flow is synchronous from the user's perspective but is designed to degrade gracefully. The AI suggestion is a **convenience, not a gate** — the workflow completes with or without it.

```
IC Browser               WC Micro-frontend       weekly-service              Redis              LLM API (Claude)
    │                          │                       │                        │                      │
    │  types commit title      │                       │                        │                      │
    ├─────────────────────────►│                       │                        │                      │
    │                          │  (debounce 500ms)     │                        │                      │
    │                          │  POST /ai/suggest-rcdo│                        │                      │
    │                          │  { title, description }                        │                      │
    │                          ├──────────────────────►│                        │                      │
    │                          │                       │                        │                      │
    │                          │                       │ ── 1. Validate JWT, extract orgId             │
    │                          │                       │ ── 2. Rate limit check (20 req/user/min)      │
    │                          │                       │      If exceeded → 429 Too Many Requests      │
    │                          │                       │ ── 3. Compute cache key:                      │
    │                          │                       │      orgId + hash(title+desc+rcdoTreeVersion) │
    │                          │                       │ ── 4. Check cache ────────────────────────────►│
    │                          │                       │      Cache HIT → return cached suggestions    │
    │                          │                       │      Cache MISS → continue                    │
    │                          │                       │ ── 5. Load RCDO tree from cache ──────────────►│
    │                          │                       │      (If tree is large, pre-filter to ~50     │
    │                          │                       │       candidates via lexical/embedding search) │
    │                          │                       │ ── 6. Construct prompt:                       │
    │                          │                       │      SYSTEM: role + schema contract           │
    │                          │                       │      CONTEXT: RCDO candidate list (IDs+names) │
    │                          │                       │      USER: commit title + description         │
    │                          │                       │      (structured message roles, not concat)   │
    │                          │                       │ ── 7. Call LLM API (5s hard timeout) ─────────────────────────────►│
    │                          │                       │                        │                      │
    │                          │                       │      ◄── LLM response (structured JSON) ──────────────────────────┤
    │                          │                       │                        │                      │
    │                          │                       │ ── 8. Schema-validate response:               │
    │                          │                       │      - Reject any outcomeId not in candidate  │
    │                          │                       │        list (anti-hallucination)              │
    │                          │                       │      - Reject if JSON doesn't match schema    │
    │                          │                       │      - On failure → return empty suggestions  │
    │                          │                       │ ── 9. Cache valid response ───────────────────►│
    │                          │                       │                        │                      │
    │                          │  200 { suggestions: [...] }                    │                      │
    │                          │◄──────────────────────┤                        │                      │
    │  show suggestion chips   │                       │                        │                      │
    │◄─────────────────────────┤                       │                        │                      │
```

**Failure scenarios for AI suggestion flow:**

| Failure | Detection | Behavior | User experience |
|---|---|---|---|
| Rate limit exceeded | Step 2 | `429 Too Many Requests` | UI suppresses further requests for 30s. Manual picker available. |
| LLM timeout (> 5s) | Step 7 | Hard timeout | `200 { "status": "unavailable", "suggestions": [] }`. UI shows manual picker seamlessly. |
| LLM returns malformed JSON | Step 8 | Schema validation failure | Same as timeout: empty suggestions, manual picker. Error logged for investigation. |
| LLM hallucinates an outcomeId | Step 8 | ID not in candidate list | Hallucinated suggestion silently filtered. Remaining valid suggestions returned. |
| Redis down (cache miss) | Step 4/5 | Fallback to direct RCDO fetch and uncached LLM request | Slightly higher latency. Transparent to user. |
| RCDO tree unavailable + cache cold | Step 5 | Cannot build candidate list | `200 { "status": "unavailable", "suggestions": [] }`. AI cannot help without RCDO context. |

#### Flow 3: Manager dashboard aggregation

The manager dashboard is the most read-heavy operation. It fans out to multiple data sources and must return within p95 < 500ms for teams up to 50 direct reports.

```
Manager Browser           WC Micro-frontend       weekly-service              Redis              Postgres
    │                          │                       │                        │                    │
    │  open /weekly/team       │                       │                        │                    │
    ├─────────────────────────►│                       │                        │                    │
    │                          │  GET /weeks/{w}/team/ │                        │                    │
    │                          │  summary?page=1&...   │                        │                    │
    │                          ├──────────────────────►│                        │                    │
    │                          │                       │                        │                    │
    │                          │                       │ ── 1. Validate JWT, extract orgId, userId   │
    │                          │                       │ ── 2. Resolve direct reports ───────────────►│
    │                          │                       │      (cache hit: org:reports:{orgId}:{userId})│
    │                          │                       │      Returns [reportId1, reportId2, ...]     │
    │                          │                       │ ── 3. Query plans for reports ───────────────────────────────────►│
    │                          │                       │      SELECT p.*, count(c.*), ...             │
    │                          │                       │      FROM weekly_plans p                     │
    │                          │                       │      LEFT JOIN weekly_commits c ON ...       │
    │                          │                       │      WHERE p.org_id = ?                      │
    │                          │                       │        AND p.owner_user_id IN (?)            │
    │                          │                       │        AND p.week_start_date = ?             │
    │                          │                       │      GROUP BY p.id                           │
    │                          │                       │      (+ filters: state, outcomeId, etc.)     │
    │                          │                       │      (uses indexes on org_id + week + state) │
    │                          │                       │                        │                    │
    │                          │                       │ ── 4. Compute aggregates:                   │
    │                          │                       │      - Per-user: state, reviewStatus,        │
    │                          │                       │        commitCount, incompleteCount,         │
    │                          │                       │        nonStrategicCount, chessCounts        │
    │                          │                       │      - Summary: reviewStatusCounts           │
    │                          │                       │ ── 5. If RCDO grouping requested:            │
    │                          │                       │      Fetch RCDO names from cache ───────────►│
    │                          │                       │      (for display labels)                    │
    │                          │                       │                        │                    │
    │                          │  200 { users: [...], summary: {...} }          │                    │
    │                          │◄──────────────────────┤                        │                    │
    │  render dashboard        │                       │                        │                    │
    │◄─────────────────────────┤                       │                        │                    │
```

**Performance strategy for dashboard:**

* **Single query, not N+1.** The dashboard query joins `weekly_plans` and `weekly_commits` in a single SQL statement with `GROUP BY`, avoiding per-user round trips.
* **Index-driven.** The composite index `(org_id, week_start_date, state)` on `weekly_plans` makes the base query an index scan, not a table scan.
* **Pagination.** Default page size: 20 users. For teams > 50, pagination prevents response bloat.
* **Org graph caching.** Direct reports are resolved from Redis (15 min TTL), not fetched per request. Background refresh keeps the cache warm for active managers.
* **No pre-aggregation in MVP.** For teams up to 50 users with ~5–10 commits each, the join query is fast enough (< 100ms on indexed Postgres). Pre-aggregated materialized views are a post-MVP optimization if dashboard latency exceeds targets. See §17.

### 9.5 AI abstraction layer

The AI integration is designed as a **thin, replaceable abstraction** — not a deep dependency on any specific LLM provider. This is critical for a small team: provider APIs change, pricing shifts, and new models emerge frequently.

```
weekly-service
  └── ai-suggestion module
        ├── AiSuggestionService (interface)
        │     ├── suggestRcdo(title, description, rcdoCandidates) → SuggestionResult
        │     └── draftReconciliation(planId, commits) → ReconciliationDraft
        │
        ├── LlmClient (interface — the provider abstraction)
        │     └── complete(messages[], responseSchema) → String
        │
        ├── ClaudeLlmClient (implements LlmClient)
        │     └── calls Claude API with API key, model version, timeout
        │
        ├── PromptBuilder (constructs structured prompts)
        │     └── system role + context + user input (never concatenated)
        │
        └── ResponseValidator (schema + ID validation)
              └── rejects hallucinated IDs, malformed JSON
```

**Swap path:** To switch from Claude to another provider (OpenAI, Gemini, local model), implement a new `LlmClient`. No changes to `AiSuggestionService`, `PromptBuilder`, or `ResponseValidator`. The active client is selected by configuration (`ai.provider=claude`), not by code changes.

**Testing:** `LlmClient` is mocked in all unit and integration tests. A `StubLlmClient` returns canned responses for deterministic testing. E2E tests against the real LLM are run nightly, not on every PR (cost + flakiness).

### 9.6 Multi-tenant isolation summary

Multi-tenancy is enforced at multiple layers as defense-in-depth. No single layer's failure can expose cross-org data.

| Layer | Mechanism | What it prevents |
|---|---|---|
| **JWT claims** | `orgId` extracted from validated JWT on every request. Never from request params. | Attacker cannot specify a different org in the URL or body. |
| **Application queries** | Every SQL query includes `WHERE org_id = :orgId` (from JWT). Repository methods enforce this via a base class or aspect. | Application bug that forgets a filter is caught by RLS (next layer). |
| **Postgres RLS** | Row-Level Security policy on all tables: `USING (org_id = current_setting('app.current_org_id')::uuid)`. Set via `SET LOCAL app.current_org_id = :orgId` at the start of each transaction. | Even raw SQL or a repository bug cannot return rows from another org. |
| **Cache key namespacing** | Every tenant-scoped Redis key includes `orgId`: `rcdo:tree:{orgId}`, `org:reports:{orgId}:{userId}`, `ai:suggest:{orgId}:{contentHash}:{rcdoVersion}`. Shared infra caches such as JWKS remain global by design. | Cache poisoning from one org cannot affect another. |
| **Event routing** | Outbox events include `orgId`. Consumers filter by org if needed. | Cross-org event leakage is prevented at the message level. |

### 9.7 Observability hooks (architectural integration)

Observability is not an afterthought — it is wired into the architecture at each container boundary. See §14 for the full observability strategy; this section documents where instrumentation is placed.

| Touchpoint | Instrument | What it captures |
|---|---|---|
| **API gateway → weekly-service** | Correlation ID (`X-Request-Id` header, propagated to all downstream calls and log entries) | End-to-end request tracing |
| **weekly-service inbound** | Spring Boot Actuator + Micrometer metrics | `http_server_requests_seconds` (histogram by endpoint, status, method), `jvm_*` metrics |
| **DB queries** | Hibernate statistics + slow query log (threshold: 100ms) | Query count per request, slow query detection |
| **Redis operations** | Micrometer Redis metrics | Cache hit/miss ratio per key prefix, latency |
| **LLM calls** | Custom timer metric (`ai_llm_request_seconds`) + structured log with token count, model, cache hit/miss | Cost tracking, latency monitoring, cache effectiveness |
| **Outbox poller** | Gauge (`outbox_unpublished_count`), counter (`outbox_published_total`) | Outbox lag detection. Alert if unpublished count > 100 for > 5 min. |
| **Notification worker** | Counter (`notifications_processed_total`), error counter, DLQ depth gauge | Worker health, poison message detection |
| **Micro-frontend** | Web Vitals (LCP, FID, CLS), custom performance marks for AI suggestion round-trip | User-perceived performance |

**Structured log format (all containers):**

```json
{
  "timestamp": "2026-03-11T23:40:00Z",
  "level": "INFO",
  "logger": "com.st6.weekly.plan.PlanLifecycleService",
  "message": "Plan locked",
  "correlationId": "req-abc-123",
  "orgId": "org-uuid",
  "userId": "user-uuid",
  "planId": "plan-uuid",
  "lockType": "ON_TIME",
  "commitCount": 5,
  "durationMs": 142
}
```

Every log entry includes `correlationId` and `orgId` for cross-service tracing and tenant-scoped log queries.

---

## 10) Non-functional requirements

### Security & access control

* Must use existing PA auth (OAuth/JWT); no new login.
* Authorization is **role + relationship**, both required:

  * **IC** (role): read/write own plans only. Any request targeting another user's plan → `403`.
  * **Manager** (role + relationship): can read plans and write reviews **only for users in their direct report chain** per org graph. A user with `MANAGER` role but no reporting relationship to the target user → `403`. Skip-level access (manager's manager) is **not** granted in MVP; post-MVP, configurable per org.
  * **Admin** (role): read all plans across org (optional, MVP can omit).
* If the org graph is unavailable and cache is cold, manager access is **denied** (fail closed). This is preferable to accidentally granting access to the wrong reports.
* Audit log for:

  * state transitions
  * edits after lock (if permitted)
  * manager review decisions
  * authorization failures (403s) — logged with actor, target, and reason for security review

### Performance

* p95 API latency < 250ms for standard CRUD endpoints (internal network)
* Manager roll-up must return within p95 < 500ms for teams up to 50 users
* UI should avoid loading entire org graph; fetch by team/week with pagination

### Reliability

* Graceful degradation if RCDO service is partially down (cached read + banner)
* Idempotent state transition endpoints (avoid double-lock, double-reconcile)
* AI suggestion endpoints are non-blocking - if the LLM is slow or down, the UI falls back to manual entry with no workflow disruption

---

## 11) API surface (illustrative)

All endpoints are versioned under `/api/v1/`. Error responses follow a consistent envelope: `{ "error": { "code": "...", "message": "...", "details": [...] } }`.

### Plans

* `POST /api/v1/weeks/{weekStart}/plans` — create plan for week (idempotent; 201 on create, 200 if exists)
* `GET /api/v1/weeks/{weekStart}/plans/me` — get my plan for a given week
* `GET /api/v1/plans/{planId}` — get plan by ID (authz: owner or their manager)
* `GET /api/v1/weeks/{weekStart}/plans/{userId}` — get a user's plan (manager-only)
* `POST /api/v1/plans/{planId}/lock` — requires `Idempotency-Key` header
* `POST /api/v1/plans/{planId}/start-reconciliation` — requires `Idempotency-Key` header
* `POST /api/v1/plans/{planId}/submit-reconciliation` — requires `Idempotency-Key` header
* `POST /api/v1/plans/{planId}/carry-forward` — requires `Idempotency-Key` header

State transition endpoints accept an `Idempotency-Key` header (client-generated UUID). The server stores it and returns the same response on replay. This prevents double-lock / double-submit from flaky networks or retry logic.

**Idempotency key storage:**

```
idempotency_keys table:
  org_id          UUID NOT NULL
  user_id         UUID NOT NULL
  endpoint        VARCHAR NOT NULL   (e.g., "plans/{id}/lock")
  idempotency_key UUID NOT NULL
  request_hash    VARCHAR NOT NULL   (SHA-256 of request body, to detect misuse)
  response_status INT NOT NULL
  response_body   JSONB NOT NULL
  created_at      TIMESTAMP WITH TIME ZONE NOT NULL

  PK: (org_id, idempotency_key)
```

* On first request: execute action, store response, return it.
* On replay (same key): return stored response without re-executing. If `request_hash` differs (same key, different body), return `422 IDEMPOTENCY_KEY_REUSE`.
* TTL cleanup: background job deletes rows older than 72 hours. State transitions are permanent — the key just prevents duplicate execution within a window.

### Commits

* `POST /api/v1/plans/{planId}/commits` — create a new commit
* `PATCH /api/v1/commits/{commitId}` — partial update; requires `If-Match: {version}` header
* `DELETE /api/v1/commits/{commitId}` — delete (DRAFT only; returns 409 if plan is locked)
* `PATCH /api/v1/commits/{commitId}/actual` — update reconciliation data; requires `If-Match: {version}` header

### Manager

* `GET /api/v1/weeks/{weekStart}/team/summary?page={page}&size={size}&state={state}&outcomeId={id}&incomplete={bool}&nonStrategic={bool}&priority={KING|QUEEN|...}&category={cat}` — paginated, filtered team roll-up. Response includes per-user rows **and** a `reviewStatusCounts` summary (`{ pending: N, approved: N, changesRequested: N }`) for dashboard-level progress at a glance.
* `POST /api/v1/plans/{planId}/review` — approve / request changes (request changes blocked if `carryForwardExecutedAt != null`; returns `409 CARRY_FORWARD_ALREADY_EXECUTED`)

### RCDO (read-through, cached)

* `GET /api/v1/rcdo/search?q=...` - typeahead search
* `GET /api/v1/rcdo/tree` - scoped hierarchy for picker

### AI-assisted (MVP)

* `POST /api/v1/ai/suggest-rcdo` - given commit title/description, returns ranked RCDO suggestions
* `POST /api/v1/ai/draft-reconciliation` - given a plan ID, returns suggested completion statuses and delta summaries

---

## 12) Deployment topology and environments

This section translates the target architecture (§9) into an environment-by-environment deployment view: what runs where, what shared infrastructure each environment requires, and how promotion boundaries are enforced before production. It is intentionally separate from release engineering (§13) so environment design and release process can be reasoned about independently.

### 12.1 Deployable unit inventory

Before mapping units to environments, the table below lists every artifact that must be deployed, who owns it, and what constitutes a "version" of that artifact.

| # | Deployable unit | Artifact type | Versioned how | Owner | Notes |
|---|---|---|---|---|---|
| D1 | **WC micro-frontend** | Versioned `remoteEntry.js` bundle served from CDN / PA asset host | Semantic version embedded in path (`/wc/v1.2.3/remoteEntry.js`) or content-hash filename | Frontend eng | PA host references a pinned remote URL; promotion = updating the pointer |
| D2 | **weekly-service** | Container image (`eclipse-temurin:21-jre-alpine` + fat JAR) | Image tag: `weekly-service:<semver>-<short-sha>` (e.g., `1.4.0-a1b2c3d`) | Backend eng | Single image used for both API mode and worker mode via Spring profile |
| D3 | **DB migrations** | Flyway migration SQL files baked into the weekly-service image | Ordered by Flyway version prefix (`V001__`, `V002__`, …). Migration history table is the source of truth. | Backend eng | Forward-only in non-local environments. Executed on startup in local/dev and as a dedicated pre-deploy task in staging/prod (see §12.5) |
| D4 | **Outbox poller** | Scheduled task inside weekly-service (not a separate artifact) | Same image as D2; enabled via config flag `outbox.poller.enabled=true` | Backend eng | Runs in the API process in local/dev; runs as a dedicated replica or profile in staging/prod (see §12.3) |
| D5 | **Notification worker** | Same weekly-service image, launched with `--spring.profiles.active=worker` | Same image as D2 | Backend eng | Separate ECS task / k8s Deployment. Consumes from message bus, writes notification rows and (post-MVP) sends email/Slack. |
| D6 | **Message infrastructure** | SQS queues + DLQ (or Kafka topics) | IaC-managed (Terraform / CloudFormation). Versioned in the infra repo. | Platform / Backend eng | Queue names are environment-namespaced: `wc-{env}-plan-events`, `wc-{env}-plan-events-dlq` |
| D7 | **Feature flag config** | Flag definitions in LaunchDarkly, AWS AppConfig, or equivalent | Versioned by the flag service's own audit trail | Product / Backend eng | Flags control: module visibility per org/team, AI features, strict chess rules, lock-time automation |
| D8 | **Secrets** | DB credentials, LLM API keys, Redis auth token, and any outbound service credentials | Stored in AWS Secrets Manager / HashiCorp Vault. Rotated on schedule. | Platform eng | Never in code, config files, or long-lived plaintext env files outside local development. Injected at container start via secrets manager integration or init/sidecar pattern. |
| D9 | **Dashboards & alerts** | Grafana dashboards (JSON models), alerting rules (Prometheus/CloudWatch) | IaC-managed or dashboard-as-code in the infra repo | Backend eng / SRE | Deployed alongside service updates. See §14 for metric definitions. |
| D10 | **AI / LLM provider config** | Model version, prompt templates, rate-limit settings, provider selection | Application config (`ai.provider`, `ai.model`, `ai.timeout`, `ai.rateLimit.*`) in environment-specific config files or AppConfig | Backend eng | Prompt templates are versioned in the application repo, not in external systems. Model version pinned per environment to prevent surprise behavior changes. |
| D11 | **Org policy defaults** | Chess rules, cadence config, validation thresholds | `org_policies` table rows or AppConfig JSON (see §5) | Product / Backend eng | Seeded by migration (D3) for new orgs. Updated via admin API (post-MVP) or direct config change. |
| D12 | **TLS certificates** | Certs for service-to-service mTLS and public endpoints | Managed by host platform (ACM, cert-manager). Auto-renewed. | Platform eng | WC does not manage its own certs. Relies on PA host infra. |

### 12.2 Environment inventory

Five environments form the promotion pipeline. Each serves a distinct purpose and has different fidelity, data, and access characteristics.

| Environment | Purpose | Data | Access | Infra fidelity | Promotion gate to next |
|---|---|---|---|---|---|
| **Local** | Developer inner loop. Run everything on one machine. | Seed data (fixtures). Ephemeral. | Developer only | Docker Compose. Minimal. | Commit to feature branch (CI runs) |
| **Dev** | Continuous integration target. Every merged PR deploys here automatically. | Synthetic test data. Reset weekly. | Engineering team | Shared cloud account, single-AZ, smallest instance sizes | All CI checks pass (§13.2) |
| **Staging** | Pre-production validation. Mirrors production topology at reduced scale. | Anonymized production snapshot (refreshed monthly) or realistic synthetic data. Never real PII. | Engineering + QA + Product | Same cloud account as prod (separate VPC), multi-AZ, production-like instance types (smaller count) | QA sign-off + E2E suite green + migration dry-run passes |
| **Preview** | Per-PR ephemeral environment for frontend visual review and integration smoke tests. | Minimal seed data. Torn down on PR close. | PR author + reviewers | Lightweight: static frontend bundle + shared staging backend (read-only mode or dedicated short-lived DB) | Manual approval on PR |
| **Production** | Live traffic. Real users, real data. | Production data. | All authorized users via PA host | Multi-AZ, auto-scaling, full observability, backup/restore | Release approval (manual gate) + canary verification |

### 12.3 Environment-specific deployment matrix

The matrix below shows which deployable units (§12.1) are present in each environment and how they are provisioned. "—" means the unit is not deployed in that environment.

| Deployable unit | Local | Dev | Staging | Preview | Production |
|---|---|---|---|---|---|
| **D1: WC micro-frontend** | Webpack dev server (`localhost:3001`) | S3 + CloudFront (dev subdomain) | S3 + CloudFront (staging subdomain) | S3 + CloudFront (per-PR path prefix: `/preview/{pr-id}/`) | S3 + CloudFront (production CDN, cache TTL: 1 year, immutable content-hash filenames) |
| **D2: weekly-service (API)** | `docker compose up` (single container, API + outbox poller co-located) | ECS Fargate, 1 task, 0.5 vCPU / 1 GB | ECS Fargate, 2 tasks (multi-AZ), 1 vCPU / 2 GB | Shared staging instance (or dedicated ECS task if isolation needed) | ECS Fargate, 2–4 tasks (auto-scaling on CPU/request count), 2 vCPU / 4 GB, multi-AZ |
| **D3: DB migrations** | Run on container startup (`flyway migrate`) | Run on deploy (ECS task init container or pre-deploy step) | Run as pre-deploy step with dry-run validation first | — (uses staging DB or ephemeral DB with migrations applied) | Run as a dedicated ECS task before rolling deploy begins (§12.5) |
| **D4: Outbox poller** | Co-located in the weekly-service container | Co-located in the weekly-service task | Dedicated ECS task (1 replica, `outbox.poller.enabled=true`, API serving disabled) | — (uses staging poller) | Dedicated ECS task (1 replica, single-leader; see §12.6) |
| **D5: Notification worker** | Co-located (in-process, polls from local SQS emulator or direct DB reads) | Dedicated ECS task, 1 replica | Dedicated ECS task, 1 replica | — (uses staging worker) | Dedicated ECS task, 1–2 replicas (SQS-based: visibility timeout handles concurrency) |
| **D6: Message infrastructure** | LocalStack SQS or in-memory queue (Testcontainers in tests) | SQS queues: `wc-dev-plan-events`, `wc-dev-plan-events-dlq` | SQS queues: `wc-staging-plan-events`, `wc-staging-plan-events-dlq` | — (uses staging queues) | SQS queues: `wc-prod-plan-events`, `wc-prod-plan-events-dlq` (encryption at rest: AWS-managed KMS key) |
| **D7: Feature flags** | Hardcoded defaults in `application-local.yml` (no external flag service) | LaunchDarkly / AppConfig, `dev` environment context | LaunchDarkly / AppConfig, `staging` environment context | Inherits staging flag context | LaunchDarkly / AppConfig, `production` environment context. Progressive rollout rules (% of orgs). |
| **D8: Secrets** | `.env.local` file (gitignored). LLM key: test/sandbox key. DB: local superuser. | AWS Secrets Manager (`wc/dev/*`). Rotated quarterly. | AWS Secrets Manager (`wc/staging/*`). Rotated quarterly. | Inherits staging secrets | AWS Secrets Manager (`wc/prod/*`). Rotated monthly. Accessed via ECS task role (no static credentials). |
| **D9: Dashboards & alerts** | — (developer uses local logs + Actuator endpoints) | Grafana dashboards deployed from IaC. Alerts: Slack channel `#wc-dev-alerts` (low severity only). | Grafana dashboards mirroring production layout. Alerts: Slack channel `#wc-staging-alerts`. | — | Grafana dashboards (full suite). PagerDuty integration for P1/P2. Slack `#wc-prod-alerts` for P3+. |
| **D10: AI / LLM config** | `ai.provider=stub` (canned responses, no real LLM calls). Fast, deterministic, free. | `ai.provider=claude`, `ai.model=claude-3-haiku` (cheapest model). Sandbox API key. Rate limit: 5 req/user/min. | `ai.provider=claude`, `ai.model=claude-sonnet` (production model). Staging API key. Rate limit: 20 req/user/min. | Inherits staging AI config | `ai.provider=claude`, `ai.model=claude-sonnet` (pinned version). Production API key. Rate limit: 20 req/user/min. Model version changes require explicit promotion (see §12.5). |
| **D11: Org policy defaults** | Seeded via Flyway migration with sensible defaults | Same as local; reset on weekly DB wipe | Mirrors production policy values | — | Managed via admin API or direct DB update (audited). Changes are tested in staging first. |
| **D12: TLS certificates** | Not used (plain HTTP on localhost) | ACM-managed certs for `dev.wc.internal` | ACM-managed certs for `staging.wc.internal` | Shared staging cert (wildcard `*.staging.wc.internal`) | ACM-managed certs for `wc.pahost.com` (or PA host's domain). Auto-renewed. |

### 12.4 Network topology and region/availability assumptions

#### Region strategy

| Attribute | Decision | Rationale |
|---|---|---|
| **Primary region** | `us-east-1` (or host platform's primary region) | Co-locate with PA host infrastructure to minimize cross-region latency for service-to-service calls (RCDO, org graph, identity). |
| **Multi-AZ** | Yes, in staging and production | ECS tasks spread across ≥ 2 AZs. RDS Multi-AZ (synchronous standby). ElastiCache (Redis) with replica in second AZ. SQS is inherently multi-AZ. |
| **Multi-region** | No (MVP). Single-region with cross-region backup for DR. | A small team cannot justify the operational cost of active-active multi-region. If PA host goes multi-region, WC follows — the seams are clean (stateless API, single-writer DB). |
| **CDN (frontend)** | CloudFront with edge locations | `remoteEntry.js` and static assets are served from edge. Cache TTL: 1 year for content-hashed files, 5 min for `remoteEntry.js` (to allow version pointer updates). |

#### Network segmentation

```
┌─────────────────────────────────────────────────────────────────┐
│                     VPC: wc-{env}                               │
│                                                                 │
│  ┌────────────── Public Subnets (multi-AZ) ──────────────────┐  │
│  │  ALB / API Gateway (PA-managed)                           │  │
│  │  CloudFront origin-facing endpoints                       │  │
│  └──────────────────────┬────────────────────────────────────┘  │
│                         │ (HTTPS only)                          │
│  ┌────────────── Private Subnets (multi-AZ) ─────────────────┐  │
│  │  weekly-service (API tasks)          ┌──────────────────┐  │  │
│  │  weekly-service (outbox poller task) │ ElastiCache Redis│  │  │
│  │  notification-worker (task)          └──────────────────┘  │  │
│  │                                                            │  │
│  │  ┌──────────────────┐  ┌────────────────────────────────┐  │  │
│  │  │ RDS Postgres     │  │ SQS Endpoints (VPC endpoint)  │  │  │
│  │  │ (primary + standby)│ │ Secrets Manager (VPC endpoint)│  │  │
│  │  └──────────────────┘  └────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Security Groups:                                               │
│  • ALB → weekly-service: TCP 8080                               │
│  • weekly-service → RDS: TCP 5432                               │
│  • weekly-service → Redis: TCP 6379                             │
│  • weekly-service → SQS: HTTPS (VPC endpoint)                   │
│  • notification-worker → RDS: TCP 5432                          │
│  • notification-worker → SQS: HTTPS (VPC endpoint)              │
│  • No public IPs on private workloads                           │
│    (external egress, including LLM calls, goes via NAT Gateway  │
│     or a private/VPC endpoint where supported)                  │
└─────────────────────────────────────────────────────────────────┘
```

All service-to-service traffic stays within the VPC. External calls (LLM API, RCDO service if hosted externally) exit through a NAT Gateway or dedicated VPC endpoint. No private subnet resource has a public IP.

### 12.5 Migration and artifact promotion strategy

Database migrations and application artifacts follow separate promotion paths because they have different risk profiles and rollback characteristics.

#### Database migration promotion

Migrations are **forward-only** (no `DOWN` scripts in non-local environments). Every migration must be backward-compatible with the **currently running** application version. This enables zero-downtime deployments: the new schema is applied first, then the new application code rolls out.

| Phase | Action | Gating check |
|---|---|---|
| 1. Author | Developer writes `V{N}__description.sql` in the migrations directory. Migration must be additive (add column, add table, add index) or safely transformative (rename with alias, backfill with default). Destructive changes (drop column, drop table) require a two-phase migration: phase 1 stops writing, phase 2 drops (deployed in a subsequent release). | Code review: migration reviewed by ≥ 1 backend engineer. |
| 2. CI validation | Flyway `migrate` runs against a fresh Testcontainers Postgres in CI. Tests verify the migration is idempotent (re-runnable) where feasible. | CI green. |
| 3. Dev deploy | Migration runs automatically on deploy (init container or pre-task hook). Dev DB is ephemeral — failures are caught here with no blast radius. | Migration succeeds. App starts. Smoke tests pass. |
| 4. Staging dry-run | Before staging deploy, run `flyway info` and `flyway validate` against the staging DB to detect drift. Then execute `flyway migrate`. If migration fails, the deploy is aborted. | Dry-run succeeds. QA validates data integrity on staging. |
| 5. Production deploy | Migration runs as a **dedicated ECS task** (separate from the application rolling update). The task executes `flyway migrate`, exits 0 on success, and the deployment pipeline proceeds to roll out the new application image. If the migration task exits non-zero, the pipeline halts — no new application code is deployed. | Migration task exits 0. Application health checks pass after rollout. |

**Migration rollback:** Since migrations are forward-only, a failed production migration is addressed by authoring a new corrective migration (`V{N+1}__fix_description.sql`), not by running a `DOWN` script. The corrective migration is fast-tracked through the pipeline (skip staging if the fix is trivial and time-sensitive, with post-incident review). RDS point-in-time recovery is the last resort — see §12.7.

#### Frontend remote module promotion

The WC micro-frontend (`remoteEntry.js`) is a versioned bundle deployed to CDN storage. The PA host references a **pinned URL** that determines which version is loaded.

| Phase | Action | What changes |
|---|---|---|
| 1. Build | CI builds the bundle, uploads to S3 at `/wc/{version}/remoteEntry.js` (content-hashed chunks alongside). | New version available on CDN, but no host references it yet. |
| 2. Dev pointer update | Update the PA host's dev config to point to the new remote URL. | Dev host loads new micro-frontend. |
| 3. Staging pointer update | Update staging host config. QA exercises the integrated experience. | Staging host loads new micro-frontend. |
| 4. Production pointer update | Update production host config (often via feature flag or host-managed config). Can be canary: 10% of sessions → 50% → 100%. | Production host progressively loads new micro-frontend. |
| 5. Rollback | Revert the pointer to the previous version URL. Old bundle is still on CDN. Instant. | Immediate rollback, no rebuild required. |

**Version compatibility contract:** The micro-frontend and weekly-service must maintain a **one-version-back compatibility window**. At any given moment, the frontend version in production may be one release behind the backend (during a rolling deploy) or vice versa. The API contract (OpenAPI spec) enforces this: breaking changes require a new API version (`/api/v2/`), and the frontend must handle graceful degradation for endpoints it doesn't yet know about (unknown fields are ignored, missing optional fields use defaults).

#### AI model version promotion

LLM model versions are pinned per environment (§12.3, D10). Changing the model version follows the same environment promotion path as application config:

1. Update `ai.model` in dev config → run AI-specific integration tests (canned prompt → expected schema output).
2. Promote to staging → run the full AI suggestion test suite against the real model. Compare suggestion quality metrics (acceptance rate on test corpus) against baseline.
3. Promote to production → monitor AI suggestion acceptance rate (§2 success metrics) for 48 hours. If acceptance rate drops > 10 percentage points, roll back the model version via config change (no redeploy needed).

### 12.6 Singleton and scaling constraints

Some deployable units must run as singletons (exactly one active instance) to avoid correctness issues. Others can scale horizontally.

| Unit | Scaling model | Why | Failure handling |
|---|---|---|---|
| **weekly-service (API)** | Horizontal (2–4 tasks in production, auto-scale on CPU > 60% or request count > 200 req/s per task) | Stateless request handling. All state is in Postgres/Redis. | ECS replaces failed tasks. ALB health check: `/actuator/health/readiness`. Unhealthy tasks are drained (30s) then terminated. |
| **Outbox poller** | Singleton (exactly 1 active instance) | Multiple pollers can be made safe with `SELECT ... FOR UPDATE SKIP LOCKED`, but a singleton keeps operations simpler, avoids unnecessary DB contention, and is sufficient for MVP throughput. Duplicate delivery is still possible if a crash happens after publish but before `published_at` is recorded, so consumers remain idempotent regardless. | If the poller task crashes, ECS restarts it. Unpublished events accumulate in the outbox table and are picked up on restart — no data loss. Alert if `outbox_unpublished_count > 100` for > 5 min (poller may be stuck). |
| **Notification worker** | Horizontal (1–2 tasks, scale on SQS queue depth) | SQS visibility timeout ensures each message is processed by exactly one consumer. Idempotency key prevents duplicate notifications even with at-least-once delivery. | ECS replaces failed tasks. DLQ captures poison messages after 3 retries. Alert on DLQ depth > 0. |
| **DB migrations** | Singleton (dedicated task, run once per deploy) | Flyway's lock mechanism prevents concurrent migration execution, but running migrations from a dedicated task is cleaner and avoids race conditions during rolling deploys. | If migration task fails, pipeline halts. No new application code is deployed. See §12.5. |

### 12.7 Backup, restore, and disaster recovery

#### Backup strategy

| Resource | Backup method | Frequency | Retention | Storage |
|---|---|---|---|---|
| **Postgres (RDS)** | Automated RDS snapshots + continuous WAL archiving (point-in-time recovery) | Automated snapshots: daily. WAL: continuous. | 30 days (automated snapshots). WAL: 7 days (point-in-time recovery window). | Same-region RDS storage + daily cross-region snapshot copy for DR. |
| **Redis (ElastiCache)** | No backup. Redis is a cache — full data loss is a warm restart. | — | — | — |
| **S3 (frontend bundles)** | S3 versioning enabled. Lifecycle policy retains old versions for 90 days. | Continuous (S3 versioning). | 90 days. | Same-region S3 with cross-region replication for the production bucket. |
| **SQS (messages)** | No backup. Messages are transient. Outbox table in Postgres is the durable source of truth. | — | — | — |
| **Secrets Manager** | Automatic versioning by AWS Secrets Manager. Previous versions retained for 30 days. | Continuous. | 30 days. | AWS-managed. |
| **Feature flag state** | Flag service's own audit trail and version history (LaunchDarkly / AppConfig). | Continuous. | Per vendor retention (typically unlimited audit trail). | Vendor-managed. |

**Restore verification:** Backups are not considered real until they are restored successfully. Once per quarter, the team performs a restore drill: recover the production snapshot into a staging/DR account, run Flyway `validate`, execute smoke tests, and record actual restore time against the RTO/RPO targets below.

#### Recovery objectives

| Scenario | RTO (Recovery Time Objective) | RPO (Recovery Point Objective) | Recovery procedure |
|---|---|---|---|
| **Single task failure** (API, worker, poller) | < 2 min | 0 (no data loss — state is in DB) | ECS auto-replaces the task. ALB routes to healthy tasks during replacement. |
| **AZ failure** | < 5 min | 0 (Multi-AZ RDS failover is synchronous) | ECS places new tasks in surviving AZ. RDS fails over to standby (automatic, < 60s). Redis replica promoted (automatic). |
| **Postgres corruption or accidental data deletion** | < 1 hour | < 5 min (point-in-time recovery from WAL) | Restore RDS to point-in-time. Update application config to point to restored instance. Verify data integrity. Flyway validates migration state. |
| **Full region failure** | < 4 hours (manual DR process for MVP) | < 24 hours (last successful daily cross-region snapshot copy) | Restore from cross-region RDS snapshot in DR region. Deploy application stack via IaC in DR region. Update DNS. Accept data loss from the snapshot gap. Post-MVP: reduce RPO further with more frequent cross-region replication. |
| **Accidental deployment of bad migration** | < 30 min | 0 if corrective migration; < 5 min if point-in-time restore | Preferred: author and deploy a corrective migration (`V{N+1}__fix.sql`). Last resort: RDS point-in-time restore to pre-migration timestamp, then redeploy previous application version. |
| **Bad frontend deploy** | < 2 min | 0 (no data at risk — frontend is stateless) | Revert CDN pointer to previous `remoteEntry.js` version. Instant. See §12.5. |
| **Bad backend deploy** | < 10 min | 0 (rolling deploy; old tasks still serving during rollout) | ECS rolling deploy: if new tasks fail health checks, deployment is automatically rolled back (ECS circuit breaker). Manual: force redeploy previous image tag. |
| **LLM provider outage** | 0 (no recovery needed) | N/A | AI features degrade gracefully (§9.5). Users see manual picker. No action required unless outage exceeds 24 hours, in which case evaluate switching `ai.provider` config to a backup provider. |

#### Rollback mechanics (summary)

| Artifact | Rollback mechanism | Speed | Data impact |
|---|---|---|---|
| **Frontend bundle** | Revert CDN pointer URL to previous version | Seconds (config change propagation) | None — frontend is stateless |
| **Backend image** | ECS: force new deployment with previous image tag. Or: ECS deployment circuit breaker auto-reverts if health checks fail during rollout. | 2–10 min (task drain + new task startup) | None — DB schema is backward-compatible by design (§12.5) |
| **Database migration** | Corrective forward migration (preferred) or RDS point-in-time restore (last resort) | 5–60 min depending on approach | Corrective: 0 loss. PITR: loss of writes between restore point and now. |
| **Feature flag** | Toggle flag off in flag service dashboard | Seconds | None — feature is disabled, data is retained |
| **AI model version** | Update `ai.model` config and restart tasks (or use dynamic config refresh) | 1–5 min | None — AI suggestions are stateless |
| **Org policy change** | Revert config value in AppConfig or `org_policies` table | Seconds to minutes | None — policies are evaluated at request time |

### 12.8 Infrastructure as code

All environment infrastructure is defined in code, versioned alongside the application, and applied through the CI/CD pipeline. No manual cloud console changes in staging or production.

| Concern | Tool | Repository location |
|---|---|---|
| **Cloud resources** (VPC, ECS, RDS, ElastiCache, SQS, S3, CloudFront, IAM roles) | Terraform (HCL) with per-environment `.tfvars` files | `infra/terraform/` in the project repo (or dedicated infra repo if PA host mandates separation) |
| **Container definitions** (ECS task definitions, health checks, env vars, secrets references) | Terraform + templated ECS task definition JSON | `infra/terraform/ecs/` |
| **Database migrations** | Flyway SQL files baked into the weekly-service image | `src/main/resources/db/migration/` in the application repo |
| **Dashboards and alerts** | Grafana dashboard JSON + alert rule definitions | `infra/observability/` |
| **Feature flag definitions** | Flag schema + default values defined in code; synced to flag service via CI | `infra/flags/` |

**Environment creation:** A new environment (e.g., a second staging for load testing) is created by running `terraform apply -var-file=envs/load-test.tfvars`. All resources are namespaced by environment name. Tear-down: `terraform destroy`.

### 12.9 Local development environment

The local environment deserves explicit documentation because developer productivity depends on it.

```bash
# Start all dependencies
docker compose up -d

# Services started:
#   postgres:15       → localhost:5432  (DB: weekly, user: weekly, pw: local)
#   redis:7           → localhost:6379
#   localstack        → localhost:4566  (SQS emulation)
#   weekly-service    → localhost:8080  (API + outbox poller, profile=local)
#   wc-frontend       → localhost:3001  (Webpack dev server, hot reload)

# Seed data (RCDO tree, test users, sample plans)
./scripts/seed-local.sh

# Run backend tests (Testcontainers — no docker compose needed)
./gradlew test

# Run frontend tests
cd frontend && npm test
```

**Local environment characteristics:**

* **No external dependencies.** LLM calls use `StubLlmClient` (canned responses). RCDO and org graph are seeded locally.
* **Same migration path.** Flyway runs on startup, identical to production. Developers see migration issues immediately.
* **Feature flags are hardcoded.** All flags default to "enabled" in `application-local.yml` so developers can exercise all features.
* **Hot reload.** Frontend: Webpack HMR. Backend: Spring DevTools (class reload on save) or JVM hot-swap.
* **Observability.** Logs go to stdout (structured JSON). Actuator endpoints at `localhost:8080/actuator/`. No Grafana/alerting locally — developers use log output and Actuator for debugging.

---

## 13) Release engineering, CI/CD, and QA

This section covers the full delivery pipeline: how code is tested, built, promoted, and released across the deployment topology defined in §12. Because this repository is currently documentation-only, everything below describes the **target delivery pipeline** that will be built alongside the implementation effort — not jobs that already exist. The pipeline design is tightly coupled to the strict-typing, test-first philosophy expressed throughout this PRD: if it isn't enforced by automation, it doesn't count.

Structure: test strategy (§13.1) defines what we test and why; CI/CD quality gates (§13.2) define automated enforcement on every PR; artifact publishing and signing (§13.3) define supply-chain integrity; preview environments (§13.4) enable collaborative review; staged promotion (§13.5) defines how artifacts move from dev to production; the release model (§13.6) covers branching, versioning, approvals, and cadence; the rollout plan (§13.7) defines the phased user exposure strategy; database migration safety (§13.8) codifies the one-deployment-window backward-compatibility rule; and rollback and incident hooks (§13.9) close the loop.

### 13.1 Test strategy

The test strategy follows a strict pyramid: fast unit tests form the base, integration and contract tests verify boundaries, and a small set of E2E tests validate golden paths. Every layer is run in CI; no test category is "optional" for merge.

#### Frontend tests (TypeScript strict)

| Layer | Scope | Tool | Runs in CI | Coverage target |
|---|---|---|---|---|
| **Unit** | Pure functions: state machine transitions, chess-rule validation, date/week utilities, form validation logic | Vitest (or Jest) | Every PR | ≥ 90% line coverage on `src/lib/` and `src/utils/` |
| **Component** | React components in isolation: commit editor (RCDO picker, chess selector, validation inline errors), reconciliation form (required fields, delta reason gating), manager dashboard filters and roll-up display | React Testing Library + Vitest | Every PR | ≥ 80% line coverage on `src/components/` |
| **Contract** | Generated TypeScript client against OpenAPI 3.1 spec. Validates that the frontend's API calls match the spec and that the spec matches the backend's actual responses. Uses `openapi-typescript` for type generation and `msw` (Mock Service Worker) for spec-driven request mocking. | openapi-typescript + msw + Vitest | Every PR (spec validation). Full client-server contract: nightly. | 100% of API endpoints exercised |
| **E2E** | Golden-path user journeys through the integrated micro-frontend + backend: IC create → lock → reconcile → carry-forward; manager dashboard → drill-down → review; permission denial (IC accessing another user's plan). | Playwright | PR (smoke subset: 3 critical paths, < 5 min). Nightly (full suite: all §18 acceptance criteria). | All §18 acceptance criteria covered |
| **Visual regression** | Screenshot comparison for key views (plan editor, manager dashboard, reconciliation view) to catch unintended layout changes. | Playwright + Percy or Chromatic | PR (if component files changed) | Key views covered; threshold: 0.1% pixel diff |
| **Accessibility** | Automated a11y audits on all pages. Manual audit quarterly. | axe-core (via Playwright or jest-axe) | Every PR | 0 critical/serious violations |

#### Backend tests (Java 21)

| Layer | Scope | Tool | Runs in CI | Coverage target |
|---|---|---|---|---|
| **Unit** | Domain logic in isolation: plan lifecycle state machine (every valid and invalid transition), chess-rule evaluator, RCDO snapshot logic, authorization predicates (`isOwner`, `isManagerOf`), validation gating (permissive draft vs. strict lock), AI response schema validation, prompt builder output structure | JUnit 5 + Mockito | Every PR | ≥ 90% line coverage on `domain/` and `service/` packages |
| **Integration** | Full request path against real infrastructure (Testcontainers Postgres + Redis): CRUD correctness, transactional outbox atomicity (state change + outbox row in one tx), unique constraint enforcement (`org_id + user_id + week_start_date`), optimistic locking (concurrent writes → 409), carry-forward lineage, RLS policy verification (query with wrong `app.current_org_id` returns 0 rows), Flyway migration correctness (fresh DB → latest schema) | JUnit 5 + Spring Boot Test + Testcontainers | Every PR | ≥ 80% line coverage on `repository/` and `controller/` packages. 100% of state transitions exercised. |
| **API contract** | OpenAPI spec compliance: every endpoint's request/response matches the spec. Backward compatibility: `openapi-diff` compares the PR's spec against `main` and rejects breaking changes (removed fields, narrowed types, changed required status) unless the PR targets a new API version (`/api/v2/`). | springdoc-openapi (spec generation) + openapi-diff (breaking change detection) + Schemathesis (fuzz testing against spec) | Every PR | 0 breaking changes on same API version. 100% endpoint coverage in contract tests. |
| **ArchUnit** | Module boundary enforcement: plan module does not import notification internals; repository implementations don't cross module boundaries; no direct SQL queries outside repository layer; controllers don't contain business logic. | ArchUnit | Every PR | 0 violations |
| **Property-based** | Random sequences of state transitions applied to a plan to prove: (a) every invalid transition is rejected, (b) every valid sequence reaches a terminal state, (c) audit events are emitted for every transition. Random concurrent writes to prove optimistic locking never silently drops an update. | jqwik (JUnit 5 property-based testing) | Every PR (100 iterations). Nightly (10,000 iterations). | All state machine invariants hold under random input. |
| **Migration** | Every Flyway migration is tested: (a) apply to fresh DB — succeeds, (b) apply to previous-version DB — succeeds (forward migration), (c) schema after migration matches expected (via schema snapshot comparison or pg_dump diff). | Testcontainers + Flyway + custom JUnit extension | Every PR (if migration files changed). Nightly (full migration chain from V001). | 100% of migrations pass idempotency check. |

#### AI-specific tests

AI features present a testing challenge because LLM output is non-deterministic. The strategy separates **contract correctness** (deterministic, always tested) from **suggestion quality** (non-deterministic, tested on schedule).

| Aspect | How it's tested | When |
|---|---|---|
| **Schema validation** | Unit tests with canned LLM responses (valid, malformed, hallucinated IDs, empty). Verify that `ResponseValidator` accepts/rejects correctly. | Every PR |
| **Prompt construction** | Unit tests that assert prompt structure: system message present, RCDO candidates included, user input in correct message role (not concatenated into system prompt). | Every PR |
| **Provider abstraction** | Integration test with `StubLlmClient` returns canned response → full pipeline (cache check → prompt build → call → validate → cache write) works end-to-end. | Every PR |
| **Timeout and fallback** | Integration test with `SlowLlmClient` (sleeps 6s) → verify 5s timeout fires → verify `{ "status": "unavailable", "suggestions": [] }` response. | Every PR |
| **Rate limiting** | Integration test: fire 21 requests in 1 minute from same user → 21st returns `429`. | Every PR |
| **Suggestion quality (real LLM)** | Run a corpus of 50 commit titles against the real LLM API. Measure: % of suggestions where `outcomeId` is in the top-3 human-labeled matches. Baseline: ≥ 60%. | Nightly (cost-controlled; ~$2/run with Haiku). On model version promotion (§12.5). |

### 13.2 CI/CD quality gates (PR validation pipeline)

Every pull request triggers an automated pipeline that must pass before merge is permitted. No exceptions, no "skip CI" comments. The pipeline is the team's immune system — it enforces the invariants that code review alone cannot reliably catch.

**Pipeline trigger:** Any push to a branch with an open PR against `main`. Also triggered on PR creation and re-triggered on base branch update (rebase).

#### Gate 1: Static analysis and formatting (< 2 min)

These checks run first because they're fast and catch trivial issues before burning compute on tests.

| Check | Frontend (TypeScript) | Backend (Java 21) |
|---|---|---|
| **Type checking** | `tsc --noEmit --strict` — zero errors. No `@ts-ignore` without an adjacent `// REASON:` comment explaining why. | Java compiler with `-Werror` (warnings are errors). No `@SuppressWarnings` without a `// REASON:` comment. |
| **Linting** | ESLint with the project's strict ruleset (no-any, no-explicit-any, consistent-type-imports, etc.). Zero warnings — warnings are errors in CI. | Checkstyle or SpotBugs with the project's ruleset. Zero warnings. |
| **Formatting** | Prettier — check mode (`--check`). If formatting differs, fail. | Spotless — check mode (`spotlessCheck`). If formatting differs, fail. |
| **Import hygiene** | ESLint `no-restricted-imports` rules enforce that components don't import from other modules' internals (mirrors ArchUnit for the frontend). | ArchUnit rules (see §13.1). |

#### Gate 2: Unit and component tests (< 5 min)

| Check | Frontend | Backend |
|---|---|---|
| **Unit tests** | `vitest run` — all pass. | `./gradlew test --tests '*Unit*'` — all pass. |
| **Component tests** | Included in the Vitest run (React Testing Library tests). | N/A (backend has no component test concept). |
| **Coverage** | Enforced thresholds (see §13.1 table). Coverage report uploaded as PR comment. Drop below threshold = fail. | Enforced thresholds (see §13.1 table). JaCoCo report. Drop below threshold = fail. |
| **ArchUnit** | N/A | `./gradlew test --tests '*ArchUnit*'` — all pass. |

#### Gate 3: Integration tests (< 10 min)

| Check | Frontend | Backend |
|---|---|---|
| **Integration tests** | N/A (frontend integration is covered by contract and E2E tests). | `./gradlew test --tests '*Integration*'` — Testcontainers Postgres + Redis. All pass. |
| **Migration validation** | N/A | If `db/migration/` files changed: Testcontainers Flyway migration test (fresh DB + incremental apply). |
| **Property-based tests** | N/A | `./gradlew test --tests '*Property*'` — 100 iterations per property. |

#### Gate 4: Contract and API checks (< 3 min)

| Check | Description | Failure means |
|---|---|---|
| **OpenAPI spec generation** | Backend: `./gradlew generateOpenApiSpec` produces `openapi.yaml`. This is committed to the repo (not generated at runtime). If the generated spec differs from the committed spec, the build fails — developers must regenerate and commit. | Spec is out of sync with code. |
| **OpenAPI breaking change detection** | `openapi-diff` compares PR spec against `main` branch spec. Breaking changes (removed endpoints, removed required fields, narrowed types, changed status codes) fail the build unless the PR explicitly targets a new API version. Additive changes (new endpoints, new optional fields) are allowed. | PR introduces a backward-incompatible API change. |
| **Frontend contract validation** | Generated TypeScript types from the OpenAPI spec are regenerated during CI. If the generated types differ from the committed types, the build fails. This ensures the frontend is always in sync with the API contract. | Frontend types are stale relative to the API spec. |
| **Schema migration backward compatibility** | If a migration removes or renames a column/table, CI checks that the **previous** application version's queries still work against the **new** schema (via a dedicated test that loads the old schema expectations and runs them against the new migration). This enforces the one-deployment-window backward-compatibility rule (§13.8). | Migration would break the currently deployed application version. |

#### Gate 5: Security and supply-chain checks (< 5 min)

| Check | Tool | What it catches | Failure threshold |
|---|---|---|---|
| **Dependency vulnerability scan (frontend)** | `npm audit` or Snyk | Known CVEs in npm dependencies | Critical or high severity = fail. Medium = warn (PR comment). |
| **Dependency vulnerability scan (backend)** | OWASP Dependency-Check (Gradle plugin) or Snyk | Known CVEs in Maven/Gradle dependencies | CVSS ≥ 7.0 = fail. CVSS 4.0–6.9 = warn. |
| **Static application security testing (SAST)** | Semgrep with custom rules for: SQL injection patterns, JWT handling errors, cross-tenant data access patterns, hardcoded secrets | Code-level security vulnerabilities | Any finding with severity ≥ high = fail. |
| **Secret detection** | Gitleaks or TruffleHog (pre-commit hook + CI check) | Accidentally committed API keys, tokens, passwords | Any detected secret = fail. |
| **License compliance** | `license-checker` (npm) + Gradle license plugin | Copyleft or restricted licenses in dependencies | GPL/AGPL in transitive deps = fail (unless explicitly approved). |
| **Container image scan** | Trivy (on the built Docker image) | OS-level and language-level CVEs in the container image | Critical = fail. High = fail if fix is available. |

#### Gate 6: Build and artifact generation (< 5 min)

| Check | Frontend | Backend |
|---|---|---|
| **Build** | `npm run build` — produces `remoteEntry.js` + content-hashed chunks. Zero warnings. | `./gradlew bootJar` — produces fat JAR. `docker build` — produces container image. |
| **Artifact tagging** | Bundle uploaded to S3 at `/wc/preview/{pr-id}/{sha}/remoteEntry.js` for preview (§13.4). | Image tagged: `weekly-service:pr-{pr-number}-{short-sha}`. Pushed to ECR. |
| **SBOM generation** | CycloneDX (npm plugin) generates `sbom.json` attached to the build. | CycloneDX (Gradle plugin) generates `sbom.json`. Attached to the container image as a label. |
| **Image signing** | N/A (frontend bundles are integrity-checked via content-hash filenames + S3 checksums). | Container image signed with Cosign (Sigstore). Signature stored in ECR alongside the image. Production deployments verify the signature before pulling. |

#### Gate 7: E2E smoke tests (< 5 min, PR subset only)

| Check | Description |
|---|---|
| **Smoke E2E** | Playwright runs 3 critical-path tests against the preview environment (§13.4): (1) IC create → lock, (2) IC reconcile → carry-forward, (3) manager dashboard loads with data. If any fail, the PR is blocked. |
| **Full E2E** | Nightly run of the complete Playwright suite (all §18 acceptance criteria) against the dev environment. Failures create a GitHub issue assigned to the on-call engineer. |

#### Pipeline summary (visual)

```
PR push
  │
  ├─► Gate 1: Static analysis + formatting        [< 2 min]  ──► FAIL = block merge
  │
  ├─► Gate 2: Unit + component tests + coverage   [< 5 min]  ──► FAIL = block merge
  │
  ├─► Gate 3: Integration + migration + property   [< 10 min] ──► FAIL = block merge
  │
  ├─► Gate 4: Contract + API compat checks         [< 3 min]  ──► FAIL = block merge
  │
  ├─► Gate 5: Security + supply-chain scans        [< 5 min]  ──► FAIL (critical/high) = block merge
  │
  ├─► Gate 6: Build + artifact + SBOM + signing    [< 5 min]  ──► FAIL = block merge
  │
  └─► Gate 7: E2E smoke (against preview env)      [< 5 min]  ──► FAIL = block merge

  Total wall time (parallelized): < 15 min target
  Merge requirement: all 7 gates green + ≥ 1 code review approval
```

**Parallelization:** Gates 1–5 run in parallel (no dependencies between them). Gate 6 depends on Gate 1 (type check must pass before build). Gate 7 depends on Gate 6 (needs built artifacts for preview). Target total wall time: **< 15 minutes** from push to merge-ready.

### 13.3 Artifact publishing and supply-chain integrity

Artifacts are the deployable outputs of the CI pipeline. Every artifact is versioned, integrity-checked, and traceable to a specific commit.

#### Artifact inventory

| Artifact | Format | Registry | Versioning | Integrity |
|---|---|---|---|---|
| **Frontend bundle** | `remoteEntry.js` + content-hashed chunks | S3 (CDN-backed) | Path-based: `/wc/{semver}/remoteEntry.js`. Content-hashed chunk filenames ensure cache correctness. | S3 object checksums (SHA-256). Immutable: once uploaded, never overwritten (new version = new path). |
| **Backend container image** | OCI image (`eclipse-temurin:21-jre-alpine` + fat JAR) | Amazon ECR (private) | Image tag: `weekly-service:{semver}-{short-sha}` (e.g., `1.4.0-a1b2c3d`). Also tagged `latest-dev`, `latest-staging`, `latest-prod` as promotion aliases. | Cosign signature (Sigstore keyless signing via CI identity). SBOM attached as image label (CycloneDX). ECR image scanning enabled (on push). |
| **OpenAPI spec** | `openapi.yaml` (committed to repo) | Git repository | Versioned with the code. Tagged releases include the spec as a release asset. | Git commit hash. Spec is generated from code — the generation check (§13.2, Gate 4) ensures it matches the implementation. |
| **SBOM (frontend)** | CycloneDX JSON | Attached to GitHub release + uploaded to S3 alongside the bundle | Same version as the frontend bundle | SHA-256 checksum |
| **SBOM (backend)** | CycloneDX JSON | Attached to GitHub release + embedded as OCI image label | Same version as the container image | Part of the signed image |
| **Flyway migrations** | SQL files baked into the container image (`src/main/resources/db/migration/`) | Part of the backend container image | Flyway version prefix (`V001__`, `V002__`, …). Migration history table tracks applied versions. | Immutable once released — migrations are never modified after merge to `main`. CI enforces this: a PR that modifies an existing migration file (not the latest unreleased one) fails. |

#### Artifact lifecycle

1. **PR build:** Artifacts are created for preview/testing but tagged as non-promotable (`pr-{number}-{sha}`).
2. **Merge to main:** Artifacts are rebuilt from the merge commit (not reused from PR — ensures the merged state is what we ship). Tagged with the next semantic version (see §13.6).
3. **Promotion:** The same artifact (same image digest, same bundle hash) is promoted through environments. We never rebuild for staging or production — the artifact that passed CI is the artifact that ships. Environment-specific configuration is injected at runtime (env vars, secrets, feature flags), not baked into the artifact.
4. **Retention:** PR artifacts are deleted after 7 days. Release artifacts are retained for 1 year. SBOM and signing metadata are retained for the life of the artifact.

### 13.4 Preview environments

Preview environments (§12.2) enable collaborative review of frontend changes in an integrated context before merge. They are the bridge between "it works on my machine" and "it works in the real environment."

#### How preview environments work

| Aspect | Detail |
|---|---|
| **Trigger** | Automatically created when a PR is opened or updated. Torn down when the PR is closed or merged. |
| **Frontend** | PR-specific bundle uploaded to S3 at `/wc/preview/{pr-id}/{sha}/remoteEntry.js`. A lightweight preview host page (or the staging PA host configured via URL parameter) loads this remote. |
| **Backend** | By default, the preview frontend connects to the **staging** backend (shared). If the PR includes backend changes, a dedicated short-lived ECS task is spun up from the PR image, connected to an ephemeral Postgres (Flyway-migrated from scratch with seed data). The preview URL switches to this dedicated backend. |
| **Data** | Minimal seed data (3 users, 2 teams, 1 RCDO tree, sample plans in various states). Sufficient for visual review and smoke tests. |
| **URL** | `https://preview-{pr-id}.wc.staging.internal` (or equivalent PA host preview route). Posted as a comment on the PR by the CI bot. |
| **Lifetime** | Max 72 hours after last PR update. Auto-torn-down on PR close/merge. |
| **Cost control** | Ephemeral backend uses the smallest Fargate task size (0.25 vCPU / 0.5 GB). Database is a Testcontainers-style ephemeral Postgres (or RDS with `db.t3.micro`, auto-deleted). Budget cap: preview infra cost < 5% of staging cost. |

#### What reviewers do with preview environments

* **Visual review:** See the actual micro-frontend rendered in the host shell. No screenshots, no "trust me, it looks right."
* **Smoke test:** Gate 7 E2E tests run against the preview. Reviewers can also manually exercise the flow.
* **Stakeholder review:** Product and design can see changes before they reach dev/staging. PR comments reference specific preview URLs.

### 13.5 Staged promotion: dev → staging → production

Artifacts follow a strict promotion path through environments (§12.2). Each promotion boundary has explicit gates. An artifact that fails a gate does not advance — there are no "emergency skip" levers in the promotion pipeline (hotfixes have their own fast-track path; see §13.6).

#### Promotion flow

```
   merge to main
        │
        ▼
  ┌──────────┐     auto-deploy      ┌──────────┐    manual gate     ┌────────────┐
  │  CI/CD   │ ──────────────────►  │   Dev    │ ─────────────────► │  Staging   │
  │  Build   │                      │          │                    │            │
  └──────────┘                      └──────────┘                    └─────┬──────┘
                                                                         │
                                                                   manual gate +
                                                                   release approval
                                                                         │
                                                                         ▼
                                                                  ┌────────────┐
                                                                  │ Production │
                                                                  │ (canary →  │
                                                                  │  full)     │
                                                                  └────────────┘
```

#### Gate details per promotion boundary

| Boundary | Gate | Who | Automated? | Blocks on failure? |
|---|---|---|---|---|
| **CI → Dev** | All 7 CI gates pass (§13.2) | CI system | Yes | Yes — broken `main` is never deployed |
| **CI → Dev** | Container image signature verified | CI deploy step | Yes | Yes |
| **Dev → Staging** | Dev smoke tests pass (automated, post-deploy) | CI system | Yes | Yes |
| **Dev → Staging** | No critical/high vulnerabilities in latest scan | Security tooling | Yes | Yes |
| **Dev → Staging** | Migration dry-run against staging DB succeeds (`flyway validate` + `flyway info`) | CI deploy step | Yes | Yes |
| **Dev → Staging** | Engineering approval (any backend or frontend engineer on the team) | Engineer | Manual (GitHub Environment protection rule) | Yes |
| **Staging → Prod** | Full E2E suite passes on staging | CI system | Yes | Yes |
| **Staging → Prod** | QA sign-off (manual exploratory testing, especially for UI/UX changes and edge cases) | QA engineer | Manual (checkbox in release PR or deployment tool) | Yes |
| **Staging → Prod** | Migration dry-run against production DB succeeds | CI deploy step | Yes | Yes |
| **Staging → Prod** | SBOM reviewed for new dependencies (automated diff + manual review for flagged items) | Engineer + Security | Semi-automated | Yes (for flagged items) |
| **Staging → Prod** | Release approval from tech lead or engineering manager | Tech lead / EM | Manual (GitHub Environment protection rule) | Yes |
| **Staging → Prod** | No P1/P2 incidents currently open against the service | Incident management system | Automated check | Yes (soft gate — can be overridden by tech lead with documented reason) |

#### Canary deployment to production

Production deploys use a **canary strategy** to limit blast radius. This is not optional — every production deploy follows this pattern.

| Phase | Traffic split | Duration | Success criteria | Failure action |
|---|---|---|---|---|
| **1. Migration** | 0% (no new code yet) | 1–5 min | Migration ECS task exits 0. `flyway validate` passes. | Halt deploy. Author corrective migration. |
| **2. Canary** | 10% of requests routed to new task(s) via ALB weighted target group | 15 min minimum (configurable, longer for risky changes) | Error rate on canary tasks ≤ baseline + 0.5%. p95 latency on canary ≤ baseline + 50ms. No new error codes in logs. | Auto-rollback: ECS deployment circuit breaker reverts to previous task definition. Alert on-call. |
| **3. Rolling** | Incremental: 10% → 25% → 50% → 100% (ECS rolling update, one task at a time) | 5–15 min total | Same criteria as canary, evaluated continuously. | Pause rollout. If metrics recover, continue. If not, full rollback. |
| **4. Bake** | 100% on new version | 30 min post-deploy observation | All SLOs (§14) within bounds. No anomalous alert firing. | Roll back to previous version. Post-incident review. |

**Frontend canary:** The micro-frontend canary is controlled differently — via the CDN pointer and feature flags. The PA host can be configured to load the new `remoteEntry.js` version for a percentage of sessions (e.g., via a feature flag that selects the remote URL). The same bake-time observation applies: monitor client-side error rates and Web Vitals for 30 minutes before promoting to 100%.

**Blue/green as an alternative:** If the PA host infrastructure supports blue/green deployments (two full environments, traffic switch), WC can adopt that model instead. The key requirement is the same: progressive traffic shift with automated rollback on metric degradation. The canary model described above works with standard ECS rolling deployments and requires no additional infrastructure.

### 13.6 Release model: branching, versioning, approvals, and cadence

#### Branching strategy: trunk-based development

The team uses **trunk-based development** with short-lived feature branches. This is the simplest model that supports continuous delivery for a small team.

| Branch | Purpose | Lifetime | Who merges |
|---|---|---|---|
| `main` | The single source of truth. Always deployable. Every commit on `main` has passed all CI gates. | Permanent | PR merge (squash-and-merge enforced) |
| `feature/{ticket-id}-{description}` | Feature work. Branched from `main`, merged back via PR. | Hours to days (< 5 days target). Longer branches must rebase frequently. | PR author after approval |
| `hotfix/{ticket-id}-{description}` | Urgent production fixes. Branched from the production release tag. | Hours (< 4 hours target). | PR author after expedited approval (see hotfix path below) |
| `release/v{major}.{minor}` | **Not used in MVP.** Trunk-based development ships from `main`. Release branches are introduced only if the team grows beyond 5 engineers and needs to stabilize a release while `main` continues. | N/A (MVP) | N/A |

**No long-lived branches.** Feature flags (§13.7) replace feature branches for work-in-progress that spans multiple PRs. Code lands on `main` behind a flag, not on a long-lived branch.

#### Version numbering: semantic versioning

All artifacts follow [Semantic Versioning 2.0.0](https://semver.org/):

* **MAJOR** (`X.0.0`): Breaking API change (new `/api/v{N}/` version, removal of deprecated endpoint). Expected: rare (< 1/year).
* **MINOR** (`X.Y.0`): New feature, new endpoint, additive schema change. Expected: every 1–2 weeks during active development.
* **PATCH** (`X.Y.Z`): Bug fix, dependency update, config change. Expected: as needed.

**Version source of truth:** A `version.txt` file (or `gradle.properties` / `package.json` version field) in the repo. The CI pipeline reads this to tag artifacts. Version bumps are part of the PR (developer bumps the version as part of their change). CI fails if the version has not been bumped and the diff includes non-docs changes (enforced by a simple script comparing against the latest release tag).

**Git tags:** Every merge to `main` that produces a release artifact is tagged `v{semver}` (e.g., `v1.4.0`). Tags are immutable — once created, never moved or deleted. The tag triggers the promotion pipeline.

#### Release approvals

| Release type | Approval required | Approver | SLA |
|---|---|---|---|
| **Standard release** (MINOR or PATCH) | 1 code review + staging QA sign-off + tech lead approval for production promotion | Tech lead or engineering manager | Code review: < 4 hours. QA: < 1 business day. Prod approval: < 2 hours during business hours. |
| **Hotfix** (PATCH, emergency) | 1 code review (expedited: any senior engineer, review within 30 min) + abbreviated QA (smoke test only, < 30 min) + tech lead approval (or delegate if unavailable) | Any senior engineer + tech lead | Total: < 2 hours from incident to production deploy |
| **Breaking change** (MAJOR) | 2 code reviews + full QA regression + tech lead + engineering manager approval + 1 week advance notice to consumers | Tech lead + EM | Code review: < 1 business day. QA: < 2 business days. Advance notice: 1 week. |

#### Hotfix path

Hotfixes bypass the normal dev → staging → production flow because speed matters more than ceremony when production is broken. The safety net is the same CI gates — they just run faster because the diff is small.

```
Production incident detected
  │
  ├─► 1. Branch from latest production tag (e.g., `hotfix/WC-123-fix-lock-race`)
  │
  ├─► 2. Fix + write regression test (the test must fail without the fix)
  │
  ├─► 3. Open PR against `main`. All CI gates run (they're fast for small diffs).
  │      Expedited code review (any senior engineer, < 30 min SLA).
  │
  ├─► 4. Merge to `main`. CI builds release artifact (PATCH version bump).
  │
  ├─► 5. Deploy to staging. Run smoke E2E suite (< 5 min).
  │
  ├─► 6. Deploy to production with canary (15 min bake, same as standard).
  │      Tech lead approves production promotion.
  │
  └─► 7. Post-incident: backfill full E2E run. Write incident report. Identify
         how to prevent recurrence (test gap, monitoring gap, etc.).
```

**Cherry-pick policy:** Hotfixes always merge to `main` first, then deploy forward. We do not cherry-pick from `main` to a release branch (there are no release branches in MVP). If the hotfix cannot be cleanly applied to `main` (e.g., `main` has diverged significantly), the engineer creates a forward-fix PR targeting `main` and a separate minimal hotfix PR for the production tag. Both are reviewed.

#### Release cadence

The team targets a **weekly release cadence** during active development, but releases are not calendar-bound. Any merge to `main` can be promoted to production if it passes all gates. The weekly cadence is a social convention — it sets stakeholder expectations for when new features become available — not a technical constraint.

| Phase | Expected cadence | Rationale |
|---|---|---|
| **Pre-MVP (build phase)** | Continuous deploy to dev. Staging deploys 2–3x/week. No production (no users yet). | Fast iteration. Catch integration issues early. |
| **MVP launch** | Controlled: 1 production deploy/week on Tuesdays (avoid Friday deploys). | Minimize risk during initial user exposure. Tuesday gives 3 business days to respond to issues. |
| **Post-MVP (steady state)** | On-demand: any passing `main` commit can go to production. Target: 2–3 production deploys/week. | Team has confidence in the pipeline. Feature flags control exposure. |
| **Hotfixes** | Immediate (any time, any day). | Production issues don't wait for the next release window. |

### 13.7 Rollout plan and feature-flag strategy

#### Phased user rollout

Feature flags control which users see the Weekly Commitments module. This decouples **deployment** (artifact is in production) from **release** (users can access the feature). Every deploy is a deployment; not every deployment is a release.

| Phase | Audience | Duration | Flag configuration | Success criteria to advance |
|---|---|---|---|---|
| **Phase 0: Internal dogfood** | 1 team (2 managers, 10 ICs). The team building the product. | 2 weeks | `wc.module.enabled`: `true` for `orgId=ST6` only. All sub-flags enabled. | No P1/P2 bugs. Core workflow (create → lock → reconcile → review) completes without manual intervention. Team feedback incorporated. |
| **Phase 1: Early adopters** | 3–5 volunteer teams across the org. Mix of team sizes (5–15 ICs per team). | 3–4 weeks | `wc.module.enabled`: `true` for specific `orgId` + `teamId` combinations. | ≥ 90% RCDO link rate. ≥ 80% reconciliation completion rate. Manager review latency < 2 business days (median). No critical UX friction points. |
| **Phase 2: Broad rollout** | All teams in the org. Progressive: 25% → 50% → 100% of teams over 2 weeks. | 2–3 weeks | `wc.module.enabled`: percentage rollout by `orgId`. | Success metrics (§2) met at scale. System reliability ≥ 99.9%. Dashboard latency < 500ms p95 for largest teams. |
| **Phase 3: 15Five transition** | All teams. 15Five weekly planning disabled. WC is the system of record. | 1–2 weeks (parallel run), then 15Five sunset. | `wc.module.enabled`: `true` globally. `15five.weekly.enabled`: `false`. | Data migration verified (if applicable). No user-reported data gaps. |

#### Capability rollout beyond MVP

The phased rollout above is about replacing 15Five safely. The roadmap in §17 is about **expanding capability safely after adoption is proven**. Those later capabilities should not be bundled into a single "v2" launch. Each ships as its own rollout wave with separate flags, success criteria, and rollback rules.

| Capability wave | Roadmap horizon | Initial audience | Rollout rule | Exit criteria before broader enablement |
|---|---|---|---|---|
| **Operational maturity features** (email/Slack notifications, skip-level read-only views, analytics reports) | H1 (§17.3) | 1–2 pilot teams + a small executive/manager cohort | Opt-in only. Enable per org/team after support docs and alerting are in place. | No privacy/access incidents. Notification delivery success and dashboard freshness meet targets for 2 consecutive weeks. |
| **Scale extensions** (cross-team rollups, Jira/Linear integrations, capacity planning) | H2 (§17.4) | Selected business units with clear need | Enable only after the triggering threshold is met and the ADR/epic is complete. | Proven business value, acceptable support load, and no regression to core workflow SLOs. |
| **Agentic AI workflows** (planning assistant, misalignment detector, richer reconciliation) | H2 (§17.4.3) | Explicitly opted-in teams with budget ownership | Launch as beta behind org-level flags and per-agent budget caps. Human approval remains mandatory. | AI acceptance/usage metrics are positive, costs stay within budget, and governance review approves broader rollout. |
| **Platform capabilities** (workflow engine, federation, public API) | H3 (§17.5) | Specific orgs/integration partners, never blanket-enabled first | Contract-first rollout: ADR approved, security/privacy review passed, sandbox or pilot environment validated. | Operating model, ownership, and support boundaries are agreed before GA. |

**Rollout governance rule:** every post-MVP capability needs (1) a named owner, (2) an enable/disable flag, (3) a documented success metric, and (4) a rollback path before it is exposed to real users. This keeps the near-term rollout practical while allowing the platform to evolve without a risky big-bang release.

#### Feature flag inventory

| Flag | Type | Controls | Default (off) | Rollout plan |
|---|---|---|---|---|
| `wc.module.enabled` | Boolean (per org/team) | Whether the WC module is visible in the PA host navigation | `false` | Phased rollout per above |
| `wc.chess.strict` | Boolean (per org) | Whether chess rules (1 KING, 2 QUEEN max) are enforced at lock time | `true` (strict by default) | Enabled from Phase 0. Can be loosened per-team if feedback warrants. |
| `wc.lock.auto` | Boolean (per org) | Whether plans auto-transition to RECONCILING at the configured time | `false` (manual lock in MVP) | Enabled in Phase 1 for teams that request it |
| `wc.ai.suggest` | Boolean (per org) | Whether the AI RCDO suggestion feature is available | `true` | Enabled from Phase 0. Can be disabled if LLM costs need management. |
| `wc.ai.reconciliation` | Boolean (per org) | Whether the AI reconciliation draft feature is available (beta) | `false` | Enabled in Phase 1 behind explicit opt-in. |
| `wc.ai.manager-insights` | Boolean (per org) | Whether the AI manager insight summaries are shown on the dashboard | `false` | Enabled in Phase 1 behind explicit opt-in. |
| `wc.notifications.email` | Boolean (per org) | Whether email notifications are sent | `false` | H1 pilot only, then broader enablement if delivery metrics are healthy. |
| `wc.notifications.slack` | Boolean (per org) | Whether Slack notifications are sent | `false` | H1 pilot only, then broader enablement if delivery metrics are healthy. |
| `wc.dashboard.skipLevel` | Boolean (per org) | Whether skip-level users can see aggregate read-only dashboards | `false` | H1 only, after ADR-005 and privacy review. |
| `wc.capacity.enabled` | Boolean (per org/team) | Whether capacity fields and capacity roll-ups are shown | `false` | H2 opt-in only for teams that request time-allocation visibility. |
| `wc.integrations.jira` | Boolean (per org) | Whether Jira linking and sync features are available | `false` | H2 opt-in after integration and security review. |
| `wc.api.public` | Boolean (global/per consumer) | Whether the public API / webhook surface is enabled for a consumer | `false` | H3 only, after ADR-016, sandbox validation, and support readiness. |

**Flag hygiene:** Feature flags have a **lifecycle**. Temporary flags (rollout gates) are removed from code within 2 sprints of reaching 100% rollout. Permanent flags (configuration toggles like `wc.chess.strict`) remain. The team reviews the flag inventory monthly and removes stale flags. Stale flag detection: if a flag has been `true` for all contexts for > 60 days, CI emits a warning suggesting removal.

### 13.8 Database migration safety: the one-deployment-window rule

Database schema changes are the riskiest part of any deployment because they cannot be easily rolled back. This section codifies the safety rules that protect production data. These rules complement the migration promotion strategy in §12.5.

#### Core rule: backward-compatible through one deployment window

Every database migration must be **backward-compatible with the currently deployed application version**. This means:

* The **old application code** (version N) must continue to work correctly against the **new schema** (version N+1).
* The **new application code** (version N+1) must work correctly against both the **old schema** (version N, during rollback) and the **new schema** (version N+1).

This guarantees zero-downtime deployments: the migration runs first, then the application rolls out. At any point during the rolling deploy, some tasks run version N and some run version N+1, all against the same (migrated) database. Both must function correctly.

#### Safe migration patterns

| Change type | Safe pattern | Unsafe pattern (never do this) |
|---|---|---|
| **Add column** | `ALTER TABLE ADD COLUMN ... DEFAULT ... NOT NULL` (Postgres fills existing rows instantly with the default). Or: add as nullable, backfill, then add NOT NULL constraint in a follow-up migration. | Add NOT NULL column without default (locks table, fails if rows exist) |
| **Remove column** | Phase 1: stop reading/writing the column in application code (deploy). Phase 2: `ALTER TABLE DROP COLUMN` in the next release's migration. Never in the same release. | Drop column while the old application version still references it |
| **Rename column** | Phase 1: add new column + trigger to sync old → new. Phase 2: migrate application to use new column. Phase 3: drop old column + trigger. | `ALTER TABLE RENAME COLUMN` (breaks old application code immediately) |
| **Add table** | `CREATE TABLE IF NOT EXISTS` — always safe. | N/A (always safe) |
| **Drop table** | Phase 1: stop all reads/writes in application. Phase 2: drop in next release. | Drop table while any application version still references it |
| **Add index** | `CREATE INDEX CONCURRENTLY` (non-blocking in Postgres). | `CREATE INDEX` without `CONCURRENTLY` (locks table for writes during build) |
| **Change column type** | Add new column with new type, backfill, migrate reads, drop old column. Multi-phase. | `ALTER TABLE ALTER COLUMN TYPE` (may lock table, may fail for incompatible types) |
| **Add constraint** | `ALTER TABLE ADD CONSTRAINT ... NOT VALID` → `ALTER TABLE VALIDATE CONSTRAINT` (two steps, non-blocking). | `ADD CONSTRAINT` without `NOT VALID` (scans entire table, blocks writes) |

#### CI enforcement

The CI pipeline (§13.2, Gate 4) enforces migration safety:

1. **No modified migrations.** If a PR modifies an existing migration file (any file with a Flyway version prefix that is already present on `main`), the build fails. Migrations are append-only after merge.
2. **Schema compatibility check.** For PRs that include both a migration and application code changes, CI runs the **previous** application version's integration tests against the **new** schema. If any test fails, the migration is not backward-compatible and the PR is blocked.
3. **Destructive change detection.** A custom linter scans migration SQL for `DROP COLUMN`, `DROP TABLE`, `ALTER TABLE RENAME`, and `ALTER COLUMN TYPE` without the approved multi-phase pattern (e.g., presence of `-- phase: 2` annotation). Flagged migrations require explicit tech lead approval on the PR.

### 13.9 Rollback and incident hooks

Rollback mechanics are documented in §12.7. This section covers the **process hooks** that connect the deployment pipeline to incident management.

#### Automated rollback triggers

| Signal | Source | Action |
|---|---|---|
| **ECS health check failure** | ALB target group health check (`/actuator/health/readiness`) fails for new tasks during rolling deploy | ECS deployment circuit breaker automatically reverts to the previous task definition. No human action needed. |
| **Error rate spike** | CloudWatch alarm: 5xx error rate on canary tasks > baseline + 0.5% for 5 consecutive minutes | Alarm triggers SNS → Lambda that calls ECS `UpdateService` to force previous task definition. Pages on-call via PagerDuty. |
| **Latency spike** | CloudWatch alarm: p95 latency on canary tasks > baseline + 100ms for 10 consecutive minutes | Same as error rate: auto-rollback + page. |
| **Migration failure** | Migration ECS task exits non-zero | Deployment pipeline halts. No application code is deployed. Alert on-call. See §12.5 for corrective migration process. |
| **Manual rollback** | Engineer determines the deploy is bad (user reports, log anomalies, metric drift below alarm threshold) | Engineer runs `./scripts/rollback.sh {service} {previous-version}` or clicks "Rollback" in the deployment dashboard. Script updates ECS service to the previous task definition and posts to `#wc-prod-alerts`. |

#### Incident hooks

The deployment pipeline integrates with the team's incident management process:

| Hook | Trigger | Action |
|---|---|---|
| **Deploy freeze during incidents** | P1 or P2 incident is open against the service (status tracked in incident management tool) | CI → production promotion gate checks for open incidents (§13.5). Deploy is blocked until the incident is resolved or the tech lead explicitly overrides with a documented reason. |
| **Post-deploy incident creation** | Automated rollback fires (any trigger above) | Create an incident ticket automatically: title = "Auto-rollback: {service} {version}", severity = P2, assigned to on-call, linked to the deploy metadata (commit SHA, image tag, deploy timestamp). |
| **Deploy audit trail** | Every production deployment | Record in a `deploy_log` (database table or external tool): timestamp, image tag, commit SHA, deployer, migration version applied, canary duration, rollback (yes/no), rollback reason. Retained for 1 year. |
| **Stakeholder notification** | Production deploy completes (success or rollback) | Slack message to `#wc-releases`: "✅ `weekly-service:1.4.0-a1b2c3d` deployed to production" or "🔴 `weekly-service:1.4.0-a1b2c3d` rolled back — incident WC-456 created". |
| **Post-incident test gap analysis** | Incident post-mortem completed | Mandatory action item: identify what test (unit, integration, E2E) would have caught the issue. Add the test. If no test can catch it, add a monitoring rule. Document in the post-mortem. |

---

## 14) Operations, observability, and governance

This section defines how the system is monitored, operated, and governed after it reaches production. §9.7 documents _where_ observability instrumentation is placed architecturally; this section covers the _operational_ dimension: what we measure, what we promise, how we alert, who responds, and what long-term governance disciplines keep the system trustworthy. A system is not production-ready when the code works — it is production-ready when the team can operate, diagnose, and govern it under real-world conditions without heroics.

Structure: SLOs and SLIs (§14.1) define what "healthy" means in measurable terms; alerting strategy (§14.2) translates those signals into human actions; dashboards (§14.3) provide situational awareness; on-call and incident response (§14.4) define who acts and how; runbooks (§14.5) codify response procedures; cost monitoring (§14.6) prevents surprise bills from AI and messaging; audit, retention, and compliance (§14.7) cover data governance; security operations (§14.8) cover runtime security posture; dependency ownership (§14.9) assigns accountability for upstream integrations; and the deployment readiness checklist (§14.10) gates the MVP launch.

### 14.1 Service level objectives (SLOs) and service level indicators (SLIs)

SLOs are the quantitative definitions of "good enough." They set expectations with stakeholders (product, engineering leadership, end users) and drive alerting thresholds. Every SLO has a corresponding SLI (what we measure), a target (what we promise), and a measurement window.

**Philosophy:** We set SLOs tight enough to catch real degradation but loose enough that the team is not burned out by alert noise. SLO budgets (the allowed error margin) are explicitly tracked — when the budget is nearly exhausted, the team shifts focus from features to reliability work. This is not negotiable.

#### Availability SLOs

| SLO | SLI (what we measure) | Target | Measurement window | Budget (allowed failures) |
|---|---|---|---|---|
| **API availability** | Percentage of non-5xx responses for all `/api/v1/*` endpoints (excluding health checks). Measured at the ALB. | ≥ 99.9% | Rolling 30 days | ~43 minutes of downtime or ~4,300 failed requests per 4.3M total (scaled to actual traffic) |
| **Micro-frontend load success** | Percentage of successful `remoteEntry.js` loads (HTTP 200, no JS parse error). Measured via client-side error reporting. | ≥ 99.95% | Rolling 30 days | ~22 minutes of frontend unavailability |
| **Notification delivery** | Percentage of outbox events that result in a delivered in-app notification within 5 minutes of the triggering state transition. Measured by comparing `outbox_events.occurred_at` to `notifications.created_at`. | ≥ 99.5% | Rolling 30 days | ~216 minutes of cumulative notification delay |

#### Latency SLOs

| SLO | SLI (what we measure) | Target | Measurement window |
|---|---|---|---|
| **CRUD API latency** | p95 response time for `GET`/`POST`/`PATCH`/`DELETE` on plan and commit endpoints | < 250ms | Rolling 7 days |
| **Lock endpoint latency** | p95 response time for `POST /plans/{id}/lock` (includes RCDO snapshot fetch) | < 500ms | Rolling 7 days |
| **Manager dashboard latency** | p95 response time for `GET /weeks/{w}/team/summary` (teams up to 50 reports) | < 500ms | Rolling 7 days |
| **AI suggestion latency** | p95 response time for `POST /ai/suggest-rcdo` (end-to-end including LLM call) | < 5,000ms | Rolling 7 days |
| **AI suggestion latency (cache hit)** | p95 response time for `POST /ai/suggest-rcdo` when cache hits | < 100ms | Rolling 7 days |
| **Micro-frontend LCP** | Largest Contentful Paint on `/weekly` (plan view) and `/weekly/team` (dashboard) | < 2,000ms | Rolling 7 days |

#### Correctness SLOs

| SLO | SLI (what we measure) | Target | Measurement window |
|---|---|---|---|
| **State machine integrity** | Percentage of state transitions that produce a corresponding `audit_events` row (verified by reconciliation job that compares plan state history against audit log). | 100% | Continuous (checked daily by a background job) |
| **Outbox delivery** | Percentage of `outbox_events` rows where `published_at` is set within 60 seconds of `occurred_at`. | ≥ 99.9% | Rolling 7 days |
| **Cross-tenant isolation** | Number of API responses that include data from an `org_id` different from the JWT's `orgId` claim (detected by audit sampling and RLS violation logs). | 0 (absolute) | Continuous |

#### SLO budget tracking and burn-rate alerts

SLO compliance is not checked once a month in a report — it is tracked in real time. The primary alerting mechanism is **burn-rate alerts**: if the error budget is being consumed faster than expected, the team is notified before the SLO is breached.

| Alert | Condition | Severity | Action |
|---|---|---|---|
| **Fast burn (API availability)** | Error budget consumption rate > 14.4x normal (i.e., the 30-day budget would be exhausted in < 2 days at current rate) over a 1-hour window | P1 (page) | Immediate investigation. Likely active incident. |
| **Slow burn (API availability)** | Error budget consumption rate > 3x normal over a 6-hour window | P2 (Slack + ticket) | Investigation within 4 business hours. Likely a degradation, not an outage. |
| **Budget warning (API availability)** | > 50% of the 30-day error budget consumed with > 15 days remaining | P3 (Slack) | Team reviews. Shift sprint allocation toward reliability if trend continues. |
| **Budget exhausted (API availability)** | 100% of the 30-day error budget consumed | P2 (Slack + ticket) | Feature freeze until budget recovers. All engineering effort on reliability. Tech lead + EM informed. |

The same burn-rate model applies to latency and notification delivery SLOs at proportional thresholds.

### 14.2 Alerting strategy

Alerts are the bridge between metrics and human action. A bad alerting strategy (too noisy, too vague, poorly routed) is worse than no alerting at all because it trains the team to ignore pages.

#### Alert design principles

1. **Every alert must be actionable.** If the on-call engineer cannot take a specific action when the alert fires, the alert should not exist. "CPU is high" is not actionable. "Request latency exceeds SLO and the outbox poller is stuck" is.
2. **Alerts fire on symptoms, not causes.** Users experience latency and errors, not CPU spikes. Alert on the SLI (latency, error rate), not the underlying resource metric. Resource metrics are on dashboards for diagnosis, not in alert rules.
3. **Two interrupting severities in practice.** P1 = page (wake someone up). P2 = Slack + ticket (investigate within business hours). Lower-priority P3/P4 signals may still be emitted to Slack or tracked as tickets, but they are informational only — they never page and they should not interrupt the team outside the normal review loop.
4. **Alert fatigue is a bug.** If an alert fires > 3 times in a week without resulting in a fix, the alert is broken — either the threshold is wrong or the underlying issue needs permanent resolution. Noisy alerts are reviewed and fixed in the weekly operational review.

#### Alert inventory

| Alert name | SLI / metric | Condition | Severity | Route | Runbook reference |
|---|---|---|---|---|---|
| **API error rate — fast burn** | `http_server_requests_seconds_count{status=~"5.."}` / total | > 14.4x budget burn over 1h | P1 | PagerDuty → on-call engineer | §14.5 Runbook R1 |
| **API error rate — slow burn** | Same metric | > 3x budget burn over 6h | P2 | Slack `#wc-prod-alerts` + Jira ticket | §14.5 Runbook R1 |
| **API latency — CRUD p95** | `http_server_requests_seconds{quantile="0.95", uri=~"/api/v1/(plans\|commits).*"}` | > 250ms for 10 consecutive minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R2 |
| **API latency — dashboard p95** | `http_server_requests_seconds{quantile="0.95", uri="/api/v1/weeks/{weekStart}/team/summary"}` | > 500ms for 10 consecutive minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R2 |
| **Outbox lag** | `outbox_unpublished_count` (gauge) | > 100 for 5 consecutive minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R3 |
| **Outbox stall** | `outbox_published_total` (counter rate) | Rate = 0 for 10 consecutive minutes AND `outbox_unpublished_count` > 0 | P1 | PagerDuty → on-call engineer | §14.5 Runbook R3 |
| **Notification worker DLQ depth** | `sqs_dlq_messages_visible` for `wc-prod-plan-events-dlq` | > 0 for 5 minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R4 |
| **Notification delivery delay** | `notification_delivery_delay_seconds` (histogram, p95) | > 300s (5 min) for 15 consecutive minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R4 |
| **DB connection pool exhaustion** | `hikaricp_connections_active` / `hikaricp_connections_max` | > 80% for 5 consecutive minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R5 |
| **DB replication lag** (if read replicas added post-MVP) | `rds_replica_lag_seconds` | > 5s for 5 consecutive minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R5 |
| **Redis unavailable** | `redis_connection_errors_total` (counter rate) | > 0 for 3 consecutive minutes | P2 | Slack `#wc-prod-alerts` | §14.5 Runbook R6 |
| **LLM timeout rate** | `ai_llm_request_seconds_count{status="timeout"}` / total AI requests | > 20% of AI requests timing out over 15 minutes | P3 (Slack only) | Slack `#wc-prod-alerts` | §14.5 Runbook R7 |
| **LLM cost anomaly** | `ai_llm_tokens_total` (daily aggregate) | > 2x the trailing 7-day daily average | P3 (Slack only) | Slack `#wc-prod-alerts` | §14.5 Runbook R8 |
| **AI suggestion acceptance rate drop** | `ai_suggestion_accepted_total` / (`ai_suggestion_accepted_total` + `ai_suggestion_rejected_total`) | < 50% over a rolling 7-day window (below the 60% target) | P3 (Slack only, weekly review item) | Slack `#wc-ai-quality` | §14.5 Runbook R9 |
| **RLS violation detected** | Custom log pattern: `rls_violation` or Postgres `insufficient_privilege` on data tables | Any occurrence | P1 | PagerDuty → on-call engineer + security channel | §14.5 Runbook R10 |
| **Auth failure spike** | `http_server_requests_seconds_count{status="403"}` | > 50 403s from the same `userId` or IP in 5 minutes | P2 | Slack `#wc-security-alerts` | §14.5 Runbook R10 |
| **Canary deployment health** | Error rate on canary task group vs baseline | Canary error rate > baseline + 0.5% for 5 minutes | P1 (auto-rollback + page) | PagerDuty → on-call engineer | §13.9 |

#### Alert suppression and maintenance windows

During planned maintenance (e.g., RDS maintenance window, infrastructure migrations), alerts are suppressed for the affected components via the alerting tool's maintenance window feature (PagerDuty or equivalent). Suppression requires:
* A maintenance ticket created in advance
* Start and end time defined
* Alert suppression scoped to specific alerts, not globally
* Post-maintenance: engineer verifies all services healthy before closing the maintenance window

### 14.3 Dashboards

Dashboards provide the team with at-a-glance situational awareness. They are not alert replacements — they are diagnostic and trend-analysis tools. All dashboards are defined as code (Grafana JSON models in `infra/observability/`) and deployed through the CI pipeline.

#### Dashboard inventory

| Dashboard | Audience | Key panels | Refresh interval |
|---|---|---|---|
| **WC Service Health** | On-call engineer, team standup | SLO burn-rate gauges (availability, latency, notification delivery). Error rate time series. Latency percentiles (p50, p95, p99) by endpoint group. Active request count. JVM heap/GC metrics. HikariCP pool utilization. | 30s |
| **WC Business Metrics** | Product, engineering leadership | Weekly active users. Plans created / locked / reconciled (funnel). RCDO link rate (% of commits with outcomeId). Reconciliation completion rate. Carry-forward rate. Manager review latency (median, p95). AI suggestion acceptance rate. Non-strategic commit percentage. | 5 min |
| **WC AI Operations** | Backend engineer, AI feature owner | LLM request rate. LLM latency (p50, p95). Cache hit ratio for AI suggestions. Token usage (input/output) per request. Timeout rate. Error rate by error type (schema validation failure, hallucinated ID, provider error). Daily cost estimate (tokens × price). Suggestion acceptance rate over time. | 1 min |
| **WC Outbox & Events** | Backend engineer | Outbox unpublished count (gauge). Publish rate (events/sec). Consumer lag (SQS: approximate message age; Kafka: consumer offset lag). DLQ depth. Notification delivery delay histogram. Events by type (plan.locked, plan.reconciled, etc.). | 30s |
| **WC Infrastructure** | On-call engineer, platform eng | ECS task count and health. CPU/memory utilization per task. RDS connections, IOPS, replication lag. Redis memory usage, hit/miss ratio, connection count. SQS queue depth and age. ALB request count, target response time, healthy/unhealthy target count. | 30s |
| **WC Deployment Tracker** | Engineering team | Recent deploys (time, version, status). Canary metrics during active deploy. Rollback history. Migration history. Feature flag change log. | 1 min |

#### Dashboard access control

| Role | Dashboards accessible | Edit access |
|---|---|---|
| On-call engineer | All | Read-only (dashboards are code — changes go through PR) |
| Backend / frontend engineer | All | Read-only |
| Engineering manager / tech lead | All | Read-only |
| Product | WC Business Metrics, WC AI Operations | Read-only |
| SRE / platform | WC Infrastructure, WC Service Health | Read-only |

### 14.4 On-call ownership and incident response

#### On-call structure

The Weekly Commitments service is owned by the team that builds it. There is no separate SRE team for MVP. On-call responsibility rotates among backend engineers on the team.

| Attribute | Detail |
|---|---|
| **On-call rotation** | Weekly rotation among backend engineers (minimum 2 engineers in rotation to avoid single-person burnout). Rotation managed in PagerDuty. |
| **On-call hours** | Business hours + 1 hour buffer (7:00 AM – 7:00 PM local, Monday–Friday) for MVP. P1 pages outside business hours are handled by the on-call engineer but the team commits to resolving staffing if after-hours pages become frequent (> 1/month). |
| **Escalation path** | Level 1: on-call engineer (5 min response SLA for P1). Level 2: tech lead (15 min, if L1 cannot resolve or needs authority for risky actions like manual DB changes). Level 3: engineering manager (30 min, for organizational escalation or cross-team dependency issues). |
| **On-call handoff** | Monday morning. Outgoing on-call posts a summary in `#wc-oncall`: open incidents, recurring alerts, things to watch. Incoming on-call acknowledges. |
| **On-call expectations** | Acknowledge P1 pages within 5 minutes. Start active investigation within 15 minutes. Post initial status update in `#wc-incidents` within 30 minutes. No expectation to fix everything alone — escalate early if the issue is outside your domain. |
| **Compensation** | Per company policy. On-call is acknowledged work, not a side effect of employment. |

#### Incident severity definitions

| Severity | Definition | Examples | Response SLA | Communication |
|---|---|---|---|---|
| **P1 — Critical** | Service is down or data integrity is at risk. Users cannot complete the core workflow (create → lock → reconcile). | API returning 5xx on all requests. Cross-tenant data leak detected. DB corruption. | Acknowledge: 5 min. Active investigation: 15 min. Status update: every 30 min. | `#wc-incidents` (Slack). PagerDuty. Stakeholder update within 1 hour. |
| **P2 — High** | Significant degradation but core workflow is functional. A major feature is impaired. | Manager dashboard returning stale data. Notifications delayed > 30 min. AI suggestions fully unavailable. Lock endpoint timing out intermittently. | Acknowledge: 15 min. Investigation: within 4 business hours. | `#wc-incidents` (Slack). Jira ticket. |
| **P3 — Medium** | Minor degradation. Workaround exists. Cosmetic or non-critical feature issue. | AI suggestion quality degraded. Single notification type not firing. Slow query on infrequently used filter. | Ticket created. Addressed within current sprint. | `#wc-prod-alerts` (Slack). Jira ticket. |
| **P4 — Low** | Improvement opportunity surfaced by monitoring. No user impact. | Cache hit ratio below optimal. Approaching connection pool threshold under peak load. Stale feature flag detected. | Backlog grooming. | Jira ticket only. |

#### Incident lifecycle

```
Detection (alert / user report / monitoring)
  │
  ├─► 1. Triage: On-call confirms severity (P1/P2/P3/P4)
  │      Creates incident channel: #wc-incident-{date}-{short-desc}
  │      Posts initial assessment
  │
  ├─► 2. Investigate: Follow runbook (§14.5). Check dashboards.
  │      Communicate findings every 30 min (P1) or 2h (P2).
  │
  ├─► 3. Mitigate: Apply immediate fix (rollback, config change, scale up,
  │      feature flag toggle). Goal: restore service, not find root cause.
  │
  ├─► 4. Resolve: Confirm SLIs are back within SLO bounds.
  │      Close the incident channel with summary.
  │
  └─► 5. Post-mortem (P1 and P2 only, within 3 business days):
         Blameless post-mortem document. Required sections:
         - Timeline (with UTC timestamps)
         - Impact (users affected, duration, SLO budget consumed)
         - Root cause (5-whys or equivalent)
         - Contributing factors
         - Action items (each with owner and due date):
           • What test would have caught this? Add it.
           • What monitoring would have detected this sooner? Add it.
           • What runbook step was missing? Update it.
         Post-mortem shared with team + engineering leadership.
         Action items tracked in Jira, due dates enforced.
```

### 14.5 Runbooks

Runbooks codify the response to known failure modes. They are living documents stored in `docs/runbooks/` in the project repo (versioned alongside the code). Each runbook maps to one or more alerts from §14.2.

#### Runbook index

| ID | Title | Triggered by | Summary of steps |
|---|---|---|---|
| **R1** | API error rate elevated | API error rate alerts (fast/slow burn) | 1. Check which endpoints are erroring (dashboard: WC Service Health → error rate by endpoint). 2. Check recent deploys (dashboard: Deployment Tracker). If deploy occurred in last 2h, consider rollback. 3. Check Postgres connectivity (`/actuator/health`). 4. Check for query timeouts in slow query log. 5. Check upstream dependency health (RCDO, org graph). 6. If isolated to one endpoint, check for bad input patterns in logs (filter by `correlationId`). 7. If widespread, check ECS task health and resource metrics (OOM? CPU throttle?). 8. Escalate to L2 if not resolved within 30 min. |
| **R2** | API latency elevated | API latency CRUD/dashboard alerts | 1. Check DB query latency (dashboard: Infrastructure → RDS IOPS, connections). 2. Check HikariCP pool utilization (> 80% = bottleneck). 3. Check Redis latency and connection errors (cache miss storm?). 4. Check for slow queries (Postgres `pg_stat_activity`). 5. Check if a migration recently ran (index rebuild in progress?). 6. If dashboard-specific: check query plan for team summary endpoint; consider adding missing index. 7. If AI endpoint: check LLM latency separately (R7). |
| **R3** | Outbox lag / stall | Outbox lag, outbox stall alerts | 1. Check if outbox poller ECS task is running (`ecs describe-tasks`). Restart if crashed. 2. Check `outbox_events` table: `SELECT count(*) FROM outbox_events WHERE published_at IS NULL`. 3. Check message bus health (SQS: is the queue accessible? Permissions?). 4. If poller is running but not publishing: check logs for errors (serialization failure? network issue to SQS?). 5. If queue is full / throttled: check SQS metrics. 6. Manual drain (emergency): `UPDATE outbox_events SET published_at = now() WHERE published_at IS NULL AND occurred_at < now() - interval '1 hour'` — only if events are confirmed already processed or are safe to skip (notifications only, not data events). |
| **R4** | Notification delivery delayed / DLQ growth | Notification delivery delay, DLQ depth alerts | 1. Check notification worker ECS task health. Restart if crashed. 2. Check DLQ messages: read a sample (`aws sqs receive-message --queue-url {dlq}`). Identify the failure pattern (deserialization error? DB write failure? unknown event type?). 3. If DB write failure: check `notifications` table health and Postgres connectivity. 4. If unknown event type: likely a schema version mismatch — check if the worker is running an older image than the API. Redeploy worker. 5. Reprocess DLQ messages after fixing the root cause: `aws sqs start-message-move-task --source-arn {dlq} --destination-arn {main-queue}`. |
| **R5** | Database health issues | DB connection pool exhaustion, replication lag alerts | 1. Check `pg_stat_activity` for long-running queries or idle-in-transaction connections. Kill if appropriate (`SELECT pg_terminate_backend(pid)`). 2. Check HikariCP metrics: are connections being leaked (active count grows, never returns)? Look for missing `@Transactional` or unclosed connections in code. 3. Check RDS metrics: CPU, IOPS, freeable memory. If resource-constrained, consider scaling the instance class (requires maintenance window for non-Multi-AZ). 4. Replication lag: check if a long-running query on the replica is blocking replay. Consider canceling the query. |
| **R6** | Redis unavailable | Redis connection error alert | 1. Check ElastiCache console: is the node healthy? Rebooting? Maintenance? 2. Check security group rules: can weekly-service reach Redis on port 6379? 3. Check Redis memory (`INFO memory`): is eviction happening aggressively? 4. The system degrades gracefully (§9.2, Container 4): direct upstream calls + Caffeine in-process cache. **No immediate data loss.** 5. If Redis is unrecoverable: failover to replica (if configured) or provision a new node. Cache will warm organically. |
| **R7** | LLM timeout rate elevated | LLM timeout rate alert | 1. Check LLM provider status page (e.g., status.anthropic.com). 2. Check `ai_llm_request_seconds` metrics: is latency increasing or are requests timing out immediately (network issue vs. provider slowness)? 3. If provider is degraded: no action needed — the system degrades gracefully (users see manual picker). Post status update in `#wc-prod-alerts`. 4. If network issue (requests timeout immediately): check NAT Gateway health, VPC endpoint (if configured), DNS resolution. 5. If persistent (> 4h): consider temporarily disabling AI features via feature flag (`wc.ai.suggest=false`) to reduce noise in logs and metrics. |
| **R8** | LLM cost anomaly | LLM cost anomaly alert | 1. Check `ai_llm_tokens_total` metric: which endpoint is driving the spike (RCDO suggest vs. reconciliation draft)? 2. Check cache hit ratio: did the cache get cleared or is `rcdoTreeVersion` changing frequently (causing cache busts)? 3. Check per-user request volume: is a single user or org generating disproportionate traffic (abuse or automation)? 4. If abuse: temporarily reduce rate limit for the specific user/org. 5. If legitimate spike (many new users onboarding): confirm cost is within acceptable range. Adjust rate limits or cache TTLs if needed. 6. Monthly: review AI cost as % of total infra cost (target: < 15% of total service cost). |
| **R9** | AI suggestion quality degraded | AI suggestion acceptance rate drop alert | 1. Check if the LLM model version changed recently (§12.5, AI model promotion). If yes, consider rolling back model version. 2. Check if the RCDO hierarchy changed significantly (large reorg, many new outcomes). Prompt context may need tuning. 3. Check sample rejected suggestions in logs: are suggestions nonsensical or just slightly off? 4. This is a P3 — not an emergency. Schedule a review of prompt templates and candidate set retrieval logic. 5. If acceptance rate stays < 50% for 2 consecutive weeks, escalate to product for a decision: tune prompts, switch model, or accept lower quality. |
| **R10** | Security event detected | RLS violation, auth failure spike alerts | 1. **RLS violation (P1):** Immediately check the violating query in Postgres logs. Identify the code path. This may indicate a bug that bypasses application-level `org_id` filtering. If confirmed: assess blast radius (was cross-tenant data actually returned?). If yes, invoke the data breach response process (see §14.7). If RLS prevented the leak (violation was caught, no data returned): fix the application bug urgently but this is not a breach. 2. **Auth failure spike:** Check if it's a single user (password issue, account misconfigured) or a pattern (brute force, enumeration). If brute force: coordinate with PA identity team for IP-level blocking. Check if failures correspond to a real attack or a misconfigured integration. |

**Runbook maintenance:** Runbooks are reviewed quarterly and after every P1/P2 incident (as a post-mortem action item). Stale runbooks are worse than no runbooks because they create false confidence. Each runbook has a `last_reviewed` date in its header.

### 14.6 Cost monitoring and controls

AI tokens and messaging infrastructure are consumption-based costs that can spike unexpectedly. This section defines cost visibility, budgets, and guardrails.

#### AI / LLM cost model

| Cost driver | Metric | Estimation basis (MVP) | Monthly budget (MVP) |
|---|---|---|---|
| **RCDO suggestion (input tokens)** | `ai_llm_input_tokens_total` | ~500 tokens/request (system prompt + RCDO candidate list + commit title/desc) × 20 requests/user/week × 50 users | ~2M input tokens/month |
| **RCDO suggestion (output tokens)** | `ai_llm_output_tokens_total` | ~200 tokens/response (5 suggestions with rationale) × same volume | ~800K output tokens/month |
| **Reconciliation draft (input)** | `ai_llm_input_tokens_total{endpoint="reconciliation"}` | ~1,000 tokens/request × 50 users/week × 4 weeks | ~200K input tokens/month |
| **Reconciliation draft (output)** | `ai_llm_output_tokens_total{endpoint="reconciliation"}` | ~500 tokens/response × same volume | ~100K output tokens/month |
| **Total estimated LLM cost** | Aggregate | At Claude Sonnet pricing (~$3/M input, ~$15/M output) | ~$25–50/month at MVP scale |

**Cost guardrails:**

| Guardrail | Mechanism | Threshold |
|---|---|---|
| **Per-user rate limit** | Application-enforced (§4): 20 AI requests/user/min | Hard limit. Returns 429. |
| **Per-org daily token cap** | Application-enforced: sum tokens per `orgId` per day. If exceeded, AI features return `{ "status": "unavailable" }` for the remainder of the day. | 100K tokens/org/day (configurable). |
| **Cache-first strategy** | Suggestions cached by `orgId + hash(input + rcdoVersion)` (§9.2). Cache hit avoids LLM call entirely. | Target cache hit rate: ≥ 40% |
| **Model selection per environment** | Dev uses cheapest model (Haiku); staging and prod use Sonnet (§12.3). | Config-enforced. |
| **Monthly cost alarm** | CloudWatch billing alarm on the LLM API key's usage (or provider's billing API). | Alert at 80% of monthly budget. Hard alert at 100%. |
| **Quarterly cost review** | AI cost reviewed as a line item in the quarterly infrastructure cost review. | AI cost should be < 15% of total service infrastructure cost. |

#### Messaging (SQS) cost model

SQS costs are negligible at MVP scale (standard queue: ~$0.40/M requests). Monitoring is included for completeness and to catch misconfiguration.

| Guardrail | Mechanism | Threshold |
|---|---|---|
| **Queue depth alarm** | CloudWatch alarm on `ApproximateNumberOfMessagesVisible` | > 10,000 messages (indicates consumer is down or overwhelmed). |
| **Message age alarm** | CloudWatch alarm on `ApproximateAgeOfOldestMessage` | > 1 hour (messages are not being consumed). |

#### Infrastructure cost visibility

| Resource | Cost driver | Monitoring |
|---|---|---|
| **ECS Fargate** | vCPU-hours + memory-hours | AWS Cost Explorer, tagged by service (`wc-*`). Monthly review. |
| **RDS Postgres** | Instance hours + storage + IOPS | AWS Cost Explorer. Alert if storage grows > 50% quarter-over-quarter (unexpected data accumulation). |
| **ElastiCache Redis** | Node hours | AWS Cost Explorer. |
| **S3 + CloudFront** | Storage + requests + data transfer | AWS Cost Explorer. CDN data transfer is the main variable cost; cache hit ratio directly impacts it. |
| **Secrets Manager** | Per-secret per-month + API calls | Negligible but tracked. |

**Total infrastructure budget target (MVP):** < $500/month for all environments combined. Production accounts for ~60% of cost. This is reviewed monthly.

### 14.7 Audit, data retention, and compliance

#### Audit trail

The `audit_events` table (§5) is the system's permanent record of every significant action. It is append-only — rows are never updated or deleted by the application.

| Attribute | Policy |
|---|---|
| **What is audited** | Every state transition (plan lifecycle, review status). Every write to a locked plan (`progressNotes` updates). Every commit deletion (DRAFT state, title captured). Every authorization failure (403). Every manager review decision. Every admin config change. |
| **Audit record contents** | `orgId`, `actorUserId`, `action`, `aggregateType`, `aggregateId`, `previousState`, `newState`, `reason` (if applicable), `ipAddress` (from request header), `correlationId`, `timestamp` (UTC). |
| **Immutability** | Application code has no `UPDATE` or `DELETE` operations on `audit_events`. Database user permissions for the application role exclude `UPDATE`/`DELETE` on this table (enforced via Postgres `GRANT`). Only a DBA role (used for retention cleanup) has delete permissions. |
| **Tamper detection** | Each audit row includes a `hash` field: `SHA-256(previous_row_hash + current_row_payload)`. This creates a hash chain. A background job verifies chain integrity daily. Chain breaks indicate tampering or data corruption. |

#### Data retention

| Data category | Retention period | Deletion method | Rationale |
|---|---|---|---|
| **Weekly plans, commits, actuals** | 3 years from `created_at` | Background job: soft-delete (set `deleted_at`), then hard-delete after 90-day grace period. | Sufficient for year-over-year trend analysis and any employment-related inquiries. |
| **Audit events** | 5 years from `created_at` | Background job: archive to S3 (Parquet format, partitioned by `org_id` and year), then delete from Postgres. | Regulatory and compliance buffer. Archived data is queryable via Athena if needed. |
| **Outbox events** | 30 days from `published_at` | Background job: delete rows where `published_at < now() - interval '30 days'`. | Outbox is operational, not archival. Events are consumed and processed; the outbox table is a delivery mechanism, not a log. |
| **Idempotency keys** | 72 hours from `created_at` | Background job (already defined in §11). | Short-lived operational data. |
| **Notifications** | 90 days from `created_at` | Background job: delete. | In-app notifications have a short relevance window. |
| **AI suggestion cache (Redis)** | 1 hour TTL (self-evicting) | Redis TTL. | Cache only; no retention requirement. |
| **Structured logs** | 90 days in log aggregator (CloudWatch Logs / ELK). 1 year in cold storage (S3 Glacier). | CloudWatch Logs retention policy + S3 lifecycle policy. | 90 days covers most incident investigations. 1 year in cold storage for compliance. |
| **Metrics** | 15 months at full resolution. 5 years at 1-hour aggregation. | CloudWatch / Prometheus retention policy. | Full resolution for recent analysis; aggregated for long-term trends. |

**Data deletion and privacy:** If a user requests data deletion (right to be forgotten or equivalent), the process is:
1. Soft-delete all plans, commits, and actuals for the user across all orgs.
2. Anonymize audit events: replace `actorUserId` with a hash. Do not delete audit events (they serve compliance purposes).
3. Delete notifications for the user.
4. Confirm deletion via a checklist (manual process for MVP; automated post-MVP).

#### Compliance considerations

| Concern | Posture |
|---|---|
| **PII stored** | `userId` (UUID, not PII itself — the mapping to a name lives in the PA identity service). Plan content (commit titles, descriptions) may contain PII if users type it. Treat all user-authored text as potentially containing PII. |
| **Data residency** | Data resides in the primary AWS region (§12.4). Cross-region backups are to a region within the same geographic jurisdiction. If the org operates under data residency requirements (e.g., EU data in EU region), the deployment must be configured accordingly — this is a host infrastructure concern, not a WC application concern. |
| **Encryption at rest** | Postgres: RDS encryption enabled (AES-256, AWS-managed KMS key). S3: SSE-S3 or SSE-KMS. SQS: encryption enabled. Redis: encryption at rest enabled. |
| **Encryption in transit** | TLS 1.2+ for all connections (API, database, Redis, SQS, LLM API). No plaintext traffic in staging or production. |
| **Access logging** | All API access is logged (structured logs with `userId`, `orgId`, `endpoint`, `method`, `status`). S3 access logging enabled for the frontend bundle bucket. RDS audit logging enabled for DDL and failed authentication. |
| **SOC 2 readiness** | The audit trail, access controls, encryption, and retention policies described in this section are designed to support a SOC 2 Type II audit. The team maintains a controls mapping document (post-MVP) that maps each SOC 2 trust service criterion to the corresponding WC control. |

### 14.8 Security operations

Runtime security posture goes beyond the access control model defined in §10 and the supply-chain checks in §13.2. This section covers operational security monitoring and response.

| Control | Implementation | Monitoring |
|---|---|---|
| **JWT validation** | Every request validated against JWKS endpoint (§9.1). Keys cached with 1h TTL, refreshed on signature failure. | Alert on sustained JWT validation failures (> 10/min) — may indicate key rotation issue or attack. |
| **Rate limiting (API-wide)** | ALB or API gateway rate limit: 1,000 requests/min per source IP for authenticated endpoints. | Alert on rate-limit trigger events. Log source IP and `userId`. |
| **Rate limiting (AI-specific)** | Application-enforced: 20 AI requests/user/min (§4). | Alert on sustained rate-limit hits from a single user (> 5 triggers in 10 min). |
| **SQL injection prevention** | Parameterized queries only (JPA/Hibernate). No raw SQL concatenation. ArchUnit rule: no usage of `createNativeQuery` without `@ApprovedRawQuery` annotation. | SAST (Semgrep) in CI. No runtime monitoring needed if code is clean. |
| **Dependency patching** | Automated dependency updates (Dependabot or Renovate). Critical/high CVEs patched within 7 days. | Weekly Dependabot PR review. Snyk continuous monitoring with Slack alerts for new critical CVEs. |
| **Secrets rotation** | DB credentials: rotated monthly (production), quarterly (non-prod). LLM API keys: rotated quarterly. Redis auth token: rotated quarterly. | Alert if a secret has not been rotated within its scheduled window (Secrets Manager rotation status). |
| **Container image patching** | Base image (`eclipse-temurin:21-jre-alpine`) rebuilt weekly to pick up OS-level patches. Trivy scan on every build (§13.2). | Alert on critical CVEs in the running image (Trivy continuous scanning or ECR enhanced scanning). |
| **Network segmentation** | No public IPs on workloads. Egress via NAT Gateway. Security groups restrict port access (§12.4). | VPC Flow Logs enabled. Alert on unexpected egress patterns (traffic to unusual IPs/ports). |
| **Admin access to production** | No standing SSH/exec access to production containers. Break-glass procedure: request temporary access via ticketing system, approved by tech lead, automatically revoked after 2 hours. All commands logged (ECS Exec with CloudTrail logging). | Alert on any `ecs:ExecuteCommand` API call in production. |

### 14.9 Dependency ownership and integration health

The Weekly Commitments service depends on several upstream systems it does not own (§9.1). This section assigns ownership for each integration and defines health monitoring expectations.

| Upstream dependency | WC integration owner | Upstream owner (PA team) | Health signal | Escalation path |
|---|---|---|---|---|
| **PA Identity / AuthN (JWKS, OAuth)** | WC auth module owner (backend eng) | PA platform team | JWKS endpoint reachability. JWT validation success rate. | If JWKS is unreachable for > 5 min: P1 escalation to PA platform team via `#pa-platform-support`. WC is fully blocked (all requests return 401). |
| **Org graph / HRIS API** | WC dashboard module owner (backend eng) | PA people systems team | API response time. Cache hit rate for org graph. | If org graph is unavailable for > 15 min and cache is cold: P2. Manager dashboard is degraded. Escalate via `#pa-people-systems-support`. |
| **RCDO hierarchy service** | WC RCDO integration module owner (backend eng) | PA strategy / product team | API response time. RCDO tree freshness (last successful cache refresh). | If RCDO is unavailable for > 1 hour and cache is stale beyond threshold: plan locking is blocked (by design, §5). P2 escalation. |
| **LLM API (Claude / provider)** | WC AI module owner (backend eng) | External vendor (Anthropic / provider) | LLM latency, timeout rate, error rate. Provider status page monitoring. | If LLM is unavailable: no escalation needed (graceful degradation). If unavailable > 24h: evaluate switching provider or model via the abstraction described in §9.5 and the promotion path in §12.5. |
| **PA host shell (Module Federation)** | WC frontend owner (frontend eng) | PA frontend platform team | `remoteEntry.js` load success rate. Module initialization errors in client-side error reporting. | If host shell changes break WC loading: P2 escalation to PA frontend platform team. WC frontend owner investigates compatibility. |
| **AWS managed services (RDS, ElastiCache, SQS, S3)** | WC backend engineers (operational responsibility) | AWS (vendor) + PA platform team (account-level) | AWS Health Dashboard. Service-specific CloudWatch metrics. | Standard AWS support channels. PA platform team for account-level issues (limits, permissions). |

**Integration health dashboard:** The WC Service Health dashboard (§14.3) includes a panel showing the health status of each upstream dependency: last successful call, error rate in the last 5 minutes, and cache freshness. This provides at-a-glance dependency health without requiring the on-call engineer to check multiple systems.

**Dependency contract changes:** If an upstream dependency changes its API contract (new version, breaking change, deprecation), the WC integration owner is responsible for:
1. Assessing impact on WC within 5 business days of notification.
2. Creating a Jira epic for the migration work.
3. Completing the migration before the upstream deprecation deadline (or negotiating an extension).
4. Updating WC contract tests (§13.1) to reflect the new contract.

### 14.10 Deployment readiness checklist

This checklist gates the MVP production launch. Every item must be explicitly verified and signed off. It is not a one-time exercise — it is re-verified for major releases (any MINOR version that introduces a new module or external integration).

#### Pre-launch readiness (MVP gate)

| Category | Checklist item | Verified by | Status |
|---|---|---|---|
| **Infrastructure** | All production infrastructure provisioned via IaC (§12.8). No manual console resources. | Platform eng | ☐ |
| **Infrastructure** | Multi-AZ deployment confirmed (ECS tasks in ≥ 2 AZs, RDS Multi-AZ, Redis replica). | Platform eng | ☐ |
| **Infrastructure** | TLS enabled on all connections (API, DB, Redis, SQS, LLM). No plaintext in staging or prod. | Platform eng | ☐ |
| **Infrastructure** | Secrets stored in Secrets Manager. No secrets in code, config files, or environment variable definitions. | Backend eng + security review | ☐ |
| **Infrastructure** | Network segmentation verified: no public IPs on workloads, egress via NAT Gateway only (§12.4). | Platform eng | ☐ |
| **Database** | All Flyway migrations applied cleanly to a fresh database and incrementally from V001. | Backend eng (CI verification) | ☐ |
| **Database** | RLS policies active on all tenant-scoped tables. Verified by integration test (§13.1). | Backend eng | ☐ |
| **Database** | Automated backups configured (daily snapshots, continuous WAL). Cross-region snapshot copy enabled. | Platform eng | ☐ |
| **Database** | Backup restore drill completed. Actual restore time recorded and within RTO (§12.7). | Backend eng + platform eng | ☐ |
| **Observability** | All dashboards deployed (§14.3). Verified: panels load real data, no "no data" states. | Backend eng | ☐ |
| **Observability** | All alerts configured and routed correctly (§14.2). Verified: test alert fires and reaches PagerDuty/Slack. | Backend eng + on-call eng | ☐ |
| **Observability** | Structured logging confirmed: `correlationId` and `orgId` present in all log entries. Logs appear in CloudWatch. | Backend eng | ☐ |
| **Observability** | SLO burn-rate alerts configured and tested with synthetic error injection. | Backend eng | ☐ |
| **Security** | JWT validation tested: expired token → 401, wrong org → 403, missing role → 403. | Backend eng (integration tests) | ☐ |
| **Security** | Cross-tenant isolation verified: RLS test, application-level `org_id` enforcement, cache key namespacing. | Backend eng (integration tests) + security review | ☐ |
| **Security** | Container image scanned. Zero critical CVEs. High CVEs documented with mitigation timeline. | CI (Trivy) + security review | ☐ |
| **Security** | SAST scan clean. No high/critical findings. | CI (Semgrep) | ☐ |
| **Security** | Dependency vulnerability scan clean. No critical CVEs. | CI (OWASP / Snyk) | ☐ |
| **CI/CD** | All 7 CI gates passing on `main` (§13.2). No skipped or flaky tests. | Backend eng + frontend eng | ☐ |
| **CI/CD** | Canary deployment tested in staging: deploy new version, verify traffic split, verify auto-rollback on injected failure. | Backend eng | ☐ |
| **CI/CD** | Rollback procedure tested: frontend pointer revert, backend image rollback, feature flag toggle. | Backend eng | ☐ |
| **On-call** | On-call rotation configured in PagerDuty with ≥ 2 engineers. | Tech lead | ☐ |
| **On-call** | Escalation path documented and tested (test page reaches L1, L2, L3). | Tech lead | ☐ |
| **Runbooks** | All runbooks (§14.5) written and reviewed by a second engineer. | Backend eng (peer review) | ☐ |
| **Feature flags** | All flags (§13.7) configured in the flag service with correct default values. | Backend eng + product | ☐ |
| **Feature flags** | Phase 0 rollout targeting configured (ST6 org only). | Product + backend eng | ☐ |
| **Performance** | Load test completed: simulated 50 concurrent users, 200 req/s sustained for 10 min. All SLOs met. | Backend eng | ☐ |
| **Performance** | Manager dashboard tested with 50-user team. p95 latency < 500ms confirmed. | Backend eng | ☐ |
| **AI** | LLM provider key provisioned for production. Rate limits and cost caps configured. | Backend eng | ☐ |
| **AI** | AI fallback tested in production environment: disable LLM endpoint, verify graceful degradation. | Backend eng | ☐ |
| **Documentation** | API documentation (OpenAPI spec) published and accessible to consumers. | Backend eng | ☐ |
| **Documentation** | Architecture diagrams (§9) reviewed and accurate. | Tech lead | ☐ |
| **Stakeholder** | Product owner has reviewed and approved §18 acceptance criteria as complete. | Product | ☐ |
| **Stakeholder** | QA has completed manual exploratory testing on staging. No P1/P2 bugs open. | QA | ☐ |

#### Post-launch operational verification (within 48 hours of Phase 0 go-live)

| Check | What to verify | Owner |
|---|---|---|
| **SLO baseline** | All SLOs are within target bounds with real traffic. Record initial baselines for burn-rate alerting calibration. | On-call eng |
| **Alert calibration** | Review all alerts that fired in the first 48 hours. Adjust thresholds for any that are too noisy or too quiet. | On-call eng + tech lead |
| **Dashboard accuracy** | Business metrics dashboard shows accurate data: plan counts, RCDO link rates, reconciliation rates match manual spot-checks. | Product + backend eng |
| **Notification delivery** | Verify in-app notifications are delivered for all trigger types (§4 notification table). Test each trigger manually. | QA + backend eng |
| **AI quality** | Review AI suggestion acceptance rate for Phase 0 users. If < 50%, investigate prompt quality and RCDO candidate retrieval. | AI module owner |
| **Cost actuals** | Check actual AI token usage and infrastructure cost against budget estimates (§14.6). Adjust rate limits or cache strategy if costs are significantly above forecast. | Backend eng |
| **User feedback** | Collect qualitative feedback from Phase 0 users (survey or 1:1). Identify top friction points for immediate iteration. | Product |
| **Incident readiness** | Confirm that if a P1 occurred right now, the team could detect it (alerts), diagnose it (dashboards + runbooks), and mitigate it (rollback). Dry-run if any doubt. | Tech lead |

---

## 15) Risks and mitigations

The table below mixes **delivery risks** (what could derail MVP launch) and **trajectory risks** (what could make the current architecture fail as adoption grows). The roadmap in §17 exists to turn trajectory risks into explicit, trigger-based investments instead of vague future debt.

| Risk | Why it matters | Mitigation now | Trigger that escalates it | Planned response |
|---|---|---|---|---|
| **RCDO data quality / changing taxonomy** | Strategy linkage is the product thesis; broken or unstable upstream data undermines trust immediately. | Store stable IDs + snapshots at lock, tolerate archived nodes, cache aggressively, surface stale links with an "archived" badge. | Lock failures caused by stale RCDO data exceed acceptable levels, or upstream API reliability falls below agreed SLO. | Formalize upstream contract ownership; if needed, build a dedicated read model / sync path. |
| **External dependency fragility** (identity, org graph, RCDO, LLM) | WC depends on multiple upstream systems it does not own. A weak contract can make the whole module appear unreliable. | Fail closed on authz, degrade gracefully for AI, cache org graph/RCDO, maintain the dependency owner matrix and alerting in §14.9. | Repeated incidents or dependency drift across releases. | Promote the dependency assumptions into ADR-backed integration contracts and, where necessary, move from request-time lookups to replicated read models. |
| **Overly rigid workflow creates user friction** | Hard-lock rules and strict validation could reduce adoption if they feel punitive. | Strict-at-lock but permissive-in-draft model, feature flags, manager request-changes path, phased rollout with feedback loops. | Phase 0/1 feedback shows users are bypassing the workflow, or reconciliation completion falls below the success target. | Adjust org policies, loosen constraints selectively, or prioritize configurable workflow/cadence follow-ons instead of forcing one-size-fits-all behavior. |
| **Manager dashboard performance degrades with scale** | Rollups are the main managerial value surface; if they become slow, the product loses executive credibility. | Indexed queries, pagination, single-query aggregation, team-size assumptions documented in §9.4. | Dashboard p95 exceeds 400ms or read pressure on Postgres climbs toward saturation. | Execute ADR-001 (read replica) and ADR-004 (analytics read model) per §17.3. |
| **Event backlog delays notifications and downstream automation** | Async side effects are intentionally decoupled, but laggy events erode user confidence and can block future analytics/agent workflows. | Transactional outbox, idempotent consumers, DLQ, outbox lag alerts, singleton poller with recovery plan. | Outbox publish latency > 5s p95, backlog spikes, or DLQ depth becomes non-zero repeatedly. | Execute ADR-002 (CDC) and, later if justified, ADR-007 (notification service extraction). |
| **AI quality, cost, or governance risk** | AI is part of the product thesis, but low-quality suggestions or uncontrolled spend can quickly erode trust. | Human-in-the-loop, schema validation, anti-hallucination rules, org-scoped caching, rate limiting, cost dashboards. | Suggestion acceptance remains low, token spend exceeds budget, or governance requires stricter approval/audit. | Tune prompts/models first; only expand into agentic workflows after the trigger conditions in §17.4.3 are met and governance is explicit. |
| **Tenant isolation and executive visibility conflict** | Cross-team and skip-level views increase data reach; a mistake here is both a security issue and a trust issue. | JWT-derived `orgId`, application query scoping, Postgres RLS, cache namespacing, aggregate-only skip-level semantics by default. | Requests emerge for skip-level, cross-team, or cross-org rollups. | Treat each new visibility tier as an ADR-governed access change (ADR-005, ADR-008, ADR-012), never as an ad hoc permission tweak. |
| **Premature service sprawl** | A small team can drown in operational overhead if every new capability becomes a new service too early. | Default to the modular monolith, shared image, shared observability, and in-process module boundaries. | Engineering team size, deploy frequency, or domain complexity reaches the thresholds in §17.6. | Extract workers/services only where a clear boundary and operational benefit exist; otherwise extend the monolith. |
| **Small team, large roadmap** | The document is intentionally ambitious. Without sequencing discipline, the team could overbuild and miss the MVP. | Ruthless MVP scope, trigger-based roadmap, phased rollout, leverage PA platform primitives, keep future items as ADRs/epics until justified. | More than one major roadmap migration is proposed at the same time, or delivery predictability drops. | Enforce the "one migration at a time" rule in §17.1 and re-prioritize against measurable pain, not enthusiasm. |

---

## 16) Decisions (resolved) and open questions

### Resolved (opinionated defaults - configurable later)

* **LOCKED is a hard lock.** Users cannot delete or reprioritize commits after lock. They can add progress notes. Rationale: soft locks defeat the purpose of forced prioritization. If this creates friction, we loosen it per-team via config - but start strict.
* **Week starts Monday.** All `weekStart` dates are ISO Monday dates. Timezone: user's configured timezone in PA profile (stored as IANA tz string). Lock and reconciliation triggers use the user's local time.
* **Managers can comment and review, not edit team plans.** Editing someone else's plan breaks ownership. Managers use "request changes" to push a plan back to RECONCILING with a reason.
* **The MVP remains a modular monolith until measurable thresholds say otherwise.** Future extractions are roadmap items, not implied commitments.

### Open questions and decision deadlines

These questions do not block MVP delivery, but they **do** block specific roadmap steps. Each should be converted into an ADR or explicit follow-on epic before the corresponding horizon is entered.

| Question | Current working assumption | Latest decision point | If unresolved, what slips |
|---|---|---|---|
| **Are RCDO entities sourced from a stable PA service, or do we need our own replicated read model?** | MVP consumes an existing API and caches locally. If no viable API exists, a lightweight read model becomes the fast-follow. | Before broad rollout completes, because dashboard scale and analytics depend on reliable RCDO access. | Analytics/reporting work and any executive rollups tied to strategy hierarchy quality. |
| **What org-graph contract is available for skip-level and cross-team visibility?** | MVP only needs direct reports. Deeper hierarchy resolution is deferred. | Before ADR-005 / skip-level rollout in H1. | Skip-level dashboards and any transitive access model. |
| **Which message bus posture is strategic for PA long term: SQS as default, or Kafka as a shared platform standard?** | MVP uses the simplest host-supported option (SQS if available). Event contracts stay bus-agnostic. | Before ADR-002 if outbox/CDC scale triggers are reached. | CDC implementation details, replay strategy, and multi-consumer expansion. |
| **What AI governance envelope is acceptable for richer agents?** (allowed data sources, approval logging, budget ownership, model/vendor policy) | MVP AI is suggestion-only and limited to commit text + RCDO context. | Before enabling agentic workflows in H2. | Planning assistant, misalignment detector, and any integrations that enrich agent context. |
| **What access semantics are acceptable for executive and cross-org rollups?** | MVP and early H1 visibility remain manager/direct-manager scoped, with skip-level views aggregate-only by default. | Before ADR-008 / ADR-012. | Cross-team heatmaps, federation, and executive dashboards. |
| **Should notifications become a shared enterprise capability or remain WC-owned?** | MVP keeps notifications WC-local because the team is small and the channel set is narrow. | Before ADR-007 if other PA modules want to reuse the same worker/service. | Notification service extraction and shared preference management. |
| **What external API posture is actually desired?** Internal-only, partner-only, or public marketplace? | MVP exposes only internal APIs used by the WC micro-frontend. | Before ADR-016 / H3 platform work. | Public API, webhooks, and partner onboarding investments. |

---

## 17) Architectural evolution roadmap

This section maps the 12–24 month evolution of the Weekly Commitments platform from MVP to enterprise scale. The roadmap is structured around **triggering thresholds**: each evolution step is tied to a measurable signal (user count, data volume, team size, latency degradation) that justifies the investment. We do not evolve the architecture on a calendar — we evolve it when the current design demonstrably cannot meet the demands placed on it. Every milestone below includes the trigger, what changes, what stays the same, and the expected ADR or follow-on epic.

### 17.1 Guiding principles for evolution

1. **Defer complexity until pain is measurable.** The modular monolith (§9.0) is the correct starting point. Extraction, decomposition, and new infrastructure are responses to observed bottlenecks, not anticipated ones.
2. **Preserve the seams.** The module boundaries, event contracts, and interface abstractions built in the MVP exist specifically to make future extraction cheap. Protect them — every shortcut that crosses a module boundary is tech debt against future evolution.
3. **One migration at a time.** Never run two infrastructure migrations concurrently (e.g., CDC adoption + service extraction). Each migration has its own rollback plan and stabilization window.
4. **ADRs as first-class artifacts.** Every architectural decision in this roadmap that changes a structural assumption from the MVP must produce an Architecture Decision Record (ADR) before implementation begins. ADRs are stored in `docs/adrs/`, numbered sequentially, and follow the [Michael Nygard template](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions): Title, Status, Context, Decision, Consequences.

### 17.2 Evolution timeline overview

The table below provides a high-level map. Detailed sections follow.

| Horizon | Timeframe | Theme | Key milestones |
|---|---|---|---|
| **H0: MVP** | Months 0–3 | Ship and stabilize | Modular monolith, single-writer Postgres, transactional outbox with poller, in-app notifications, AI RCDO suggest. Feature-flagged rollout (§13.7). |
| **H1: Operational maturity** | Months 3–6 | Harden, observe, automate | Read replica for dashboards, CDC replaces outbox poller, email/Slack notifications, analytics read model (materialized views), deployment automation hardening, skip-level read-only visibility. |
| **H2: Scale and extend** | Months 6–12 | Horizontal scale, richer AI, cross-team | Extract notification worker into an independent service, cross-team/cross-org rollups, agentic AI workflows (weekly reminders, proactive misalignment detection), integration with Jira/Linear, capacity planning module. |
| **H3: Platform** | Months 12–24 | Federation, multi-region, ecosystem | Multi-org federation, configurable workflow engine (custom lifecycles), event-driven analytics pipeline (data lake), multi-region active-passive, API marketplace for integrations, governance automation. |

### 17.3 H1: Operational maturity (months 3–6)

#### 17.3.1 Read replica for manager dashboard

**Trigger:** Manager dashboard p95 latency exceeds 400ms with teams of 30+ users, or the primary DB's read IOPS consistently exceeds 70% of provisioned capacity.

**What changes:**
- Add an RDS read replica in the same region.
- Route all `GET` endpoints on the manager dashboard (`/weeks/{w}/team/summary`, RCDO tree reads, and reporting queries) to the read replica via a Spring `@Transactional(readOnly = true)` routing datasource.
- Write operations (plan CRUD, state transitions, reviews) continue to hit the primary.

**What stays the same:** Application code, API contracts, and the single-writer model. The read replica is a transparent infrastructure addition — consumers of the API see no difference.

**Risks:** Replication lag can cause a manager to see a stale plan state immediately after an IC locks. Mitigation: display a "data may be up to a few seconds behind" banner on the dashboard; for drill-down views where a manager just received a notification, route the individual plan read to the primary (read-your-writes semantics for notification-driven flows).

**ADR:** `ADR-001: Add read replica for manager dashboard queries`
**Epic:** `WC-SCALE-001: Read replica routing`

#### 17.3.2 CDC replaces outbox poller

**Trigger:** Outbox poller latency exceeds 5s p95 (from write to publish), or event volume exceeds 1,000 events/hour sustained, or the outbox table grows beyond 100K unpublished rows during peak.

**What changes:**
- Deploy Debezium CDC connector reading the Postgres WAL.
- Debezium captures `INSERT` operations on the `outbox_events` table and publishes them to the message bus (SQS or Kafka).
- Disable the application-level outbox poller (`outbox.poller.enabled=false`).
- The `outbox_events` table schema remains identical (§9.3) — Debezium reads the same rows the poller would have.

**What stays the same:** Outbox event schema, consumer logic, at-least-once delivery guarantees, idempotency requirements. This is a deployment change, not a code change.

**Operational note:** Debezium requires access to the Postgres replication slot. Coordinate with the PA platform team for RDS logical replication configuration. Monitor Debezium connector health as a new dependency.

**ADR:** `ADR-002: Replace outbox poller with Debezium CDC`
**Epic:** `WC-SCALE-002: CDC adoption`

#### 17.3.3 Email and Slack notifications

**Trigger:** Phase 1 rollout feedback confirms demand for push notifications outside the app (expected during months 2–3).

**What changes:**
- The notification worker (Container 5, §9.2) gains two new output adapters: email (via SES or PA's existing email service) and Slack (via Slack webhook or bot API).
- Notification routing is controlled by user preferences (new `notification_preferences` table: `user_id`, `org_id`, `channel` [in_app | email | slack], `enabled`).
- Feature flags `wc.notifications.email` and `wc.notifications.slack` (§13.7) control availability.
- Outbox event contracts remain unchanged — the worker consumes the same events and decides the delivery channel based on user preferences.

**What stays the same:** Outbox event schema, notification trigger logic (§4), the in-app notification path.

**ADR:** `ADR-003: Multi-channel notification delivery`
**Epic:** `WC-NOTIFY-001: Email and Slack notification channels`

#### 17.3.4 Analytics read model (materialized views)

**Trigger:** Product requests trend analysis (week-over-week RCDO link rates, reconciliation completion trends, carry-forward frequency by team) that cannot be served efficiently from the OLTP tables without expensive aggregation queries.

**What changes:**
- Introduce Postgres materialized views for key analytics aggregates, refreshed on a schedule (every 15 minutes or triggered by significant event batches).
- Views: `mv_weekly_plan_summary` (per-org, per-week aggregate: plan counts by state, RCDO link rate, reconciliation rate), `mv_team_alignment` (per-team, per-rally-cry commit distribution), `mv_carry_forward_trends` (carry-forward frequency by user/team, rolling 12 weeks).
- Materialized views live in the same Postgres instance (or on the read replica, §17.3.1) and are queried by new reporting endpoints (`/api/v1/reports/*`).

**What stays the same:** Core OLTP tables, API contracts for existing endpoints. Reporting endpoints are additive.

**ADR:** `ADR-004: Materialized views for analytics read model`
**Epic:** `WC-ANALYTICS-001: Weekly alignment trend reports`

#### 17.3.5 Skip-level read-only visibility

**Trigger:** Organizational demand for directors/VPs to see alignment data across multiple teams without accessing individual plans. Expected during Phase 2 rollout (months 3–4).

**What changes:**
- Extend the authorization model (§10) to support **transitive reporting chains**: a user with `MANAGER` role can view dashboard data for their direct reports _and_ their direct reports' reports, recursively up to a configurable depth (default: 2 levels).
- The org graph integration (§9.1) already resolves direct reports. Extend it to resolve the transitive closure (cached, with depth limit).
- Skip-level users see the same dashboard views (§7) but with a team selector that shows their org subtree. Drill-down into individual plans is restricted to the direct manager only — skip-level users see aggregate views.
- No edit or review permissions for skip-level users. Read-only aggregates.

**What stays the same:** Data model, plan lifecycle, manager review model. The change is purely in authorization and dashboard query scope.

**ADR:** `ADR-005: Skip-level read-only dashboard access`
**Epic:** `WC-ACCESS-001: Skip-level visibility`

#### 17.3.6 Deployment automation hardening

**Trigger:** Team grows beyond 3 engineers, or deploy frequency exceeds 3x/week, or a manual step in the promotion pipeline causes a production incident.

**What changes:**
- Fully automate the dev → staging promotion (remove the manual approval gate; replace with automated E2E suite pass + auto-promote if green).
- Implement automated canary analysis: replace manual bake-time observation (§13.5) with a canary analysis tool (e.g., Kayenta / Argo Rollouts analysis) that compares canary metrics against baseline and auto-promotes or auto-rolls-back.
- Add deploy frequency and lead-time-to-production metrics to the Deployment Tracker dashboard (§14.3). Track DORA metrics: deployment frequency, lead time for changes, change failure rate, time to restore service.

**What stays the same:** Artifact promotion model (same artifact through all environments), canary deployment pattern, IaC-based infrastructure.

**ADR:** `ADR-006: Automated canary analysis and promotion`
**Epic:** `WC-CICD-001: Deployment automation hardening`

### 17.4 H2: Scale and extend (months 6–12)

#### 17.4.1 Extract notification service

**Trigger:** Notification logic grows beyond simple event-to-message translation (e.g., digest emails, scheduled reminders, user preference routing, multi-channel delivery with retry), and the notification worker's codebase starts to diverge from the core weekly-service domain, creating merge conflicts and slowing both teams.

**What changes:**
- Extract the notification worker into an independent deployable service with its own repository (or at minimum, a separate build pipeline and deployment lifecycle).
- The notification service owns its own database (notification preferences, delivery state, template management).
- Communication: consumes from the same message bus topics/queues. No synchronous dependency on weekly-service.
- weekly-service no longer writes to the `notifications` table directly for in-app notifications. Instead, the notification service manages all channels (in-app, email, Slack) and exposes a `GET /api/v1/notifications` endpoint that the micro-frontend calls.

**What stays the same:** Outbox event contracts (the notification service consumes the same events). The micro-frontend's notification display (it now calls the notification service API instead of weekly-service, which is a URL change behind a config flag).

**Scaling rationale:** This extraction is justified only when notification logic becomes complex enough to warrant independent development and deployment. For a team of 1–3 engineers, the co-located worker (§9.2, Container 5) is simpler and sufficient.

**ADR:** `ADR-007: Extract notification service`
**Epic:** `WC-ARCH-001: Notification service extraction`

#### 17.4.2 Cross-team and cross-org rollups

**Trigger:** Organization scales beyond a single business unit using WC, and leadership requests a unified view of alignment across multiple teams/orgs. Expected when WC reaches > 200 active users across > 10 teams.

**What changes:**
- Introduce a **rollup aggregation service** (or a dedicated reporting module within the monolith, if extraction is premature) that computes cross-team alignment metrics.
- Cross-team rollups read from the analytics materialized views (§17.3.4) and the org graph (expanded to resolve team hierarchies, not just reporting chains).
- New dashboard views: "Org alignment heatmap" (Rally Cries × teams, showing commitment density and completion rates), "Strategic coverage" (which Outcomes have zero commitments this week), "Capacity distribution" (% of commits per team that are strategic vs. non-strategic).
- Data isolation: cross-org rollups are only available if the orgs are part of the same parent entity and the requesting user has an `ADMIN` or `EXECUTIVE` role with explicit cross-org permissions. This is a new role level, not an extension of the existing `MANAGER` role.

**What stays the same:** Per-team data model, plan lifecycle, individual user experience. Cross-team rollups are purely additive read-only views.

**ADR:** `ADR-008: Cross-team and cross-org rollup architecture`
**Epic:** `WC-SCALE-003: Cross-team alignment dashboard`

#### 17.4.3 Agentic AI workflows

**Trigger:** AI suggestion acceptance rate stabilizes above 70%, and product identifies high-leverage automation opportunities that go beyond single-request suggestion (e.g., proactive weekly reminders, multi-step reconciliation assistance, misalignment pattern detection).

**What changes:**
- Evolve from synchronous single-shot LLM calls (§9.5) to **multi-step agent workflows** orchestrated by a lightweight agent framework (e.g., LangGraph, custom state machine, or a simple DAG executor).
- **Agent 1: Weekly planning assistant.** On Sunday evening (or Monday morning, timezone-aware), an agent reviews the user's carried-forward items, the current RCDO tree, and recent team activity. It drafts a proposed weekly plan (set of commits with RCDO links and chess priorities) and sends a notification: "Your AI-drafted plan for this week is ready for review." The user reviews, modifies, and locks — the agent never auto-locks.
- **Agent 2: Misalignment detector.** A scheduled agent (daily) scans all active plans for a team and identifies patterns: Outcomes with no coverage, Rally Cries with disproportionate effort, sustained non-strategic work exceeding a threshold. Generates a natural-language briefing for the manager, surfaced on the dashboard.
- **Agent 3: Reconciliation assistant.** Enhanced version of the MVP reconciliation draft: the agent pulls context from linked systems (Jira ticket status, Git commit activity, Slack channel activity — post-integration) to pre-populate completion status and delta reasons with evidence.
- All agent outputs remain **suggestions** — no agent has write authority. The human confirms every action.

**What stays the same:** The `AiSuggestionService` interface (§9.5). Agents are consumers of the same interface, extended with multi-turn capabilities. The `LlmClient` abstraction remains the provider boundary. Schema validation and anti-hallucination guarantees apply to every agent output.

**Infrastructure additions:** Agent workflows are stateful (multi-step, with intermediate results). They require a lightweight execution engine (state stored in Postgres, scheduled by a cron-like trigger or SQS delayed messages). Agent execution logs are stored for debugging and audit. Cost controls extend to per-agent-run budgets (max tokens per agent execution).

**ADR:** `ADR-009: Agentic AI workflow architecture`
**Epic:** `WC-AI-001: Weekly planning assistant agent`, `WC-AI-002: Misalignment detector agent`, `WC-AI-003: Enhanced reconciliation agent`

#### 17.4.4 Integration with Jira / Linear and collaboration tools

**Trigger:** User feedback consistently cites double-entry (commit in WC + ticket in Jira/Linear) as a top friction point. Expected during Phase 2–3 rollout.

**What changes:**
- Bidirectional integration with Jira and/or Linear:
  - **Inbound:** When creating a commit, the user can link an existing Jira/Linear ticket. The commit title, description, and status can be pre-populated from the ticket. The `outcomeId` can be inferred from the ticket's epic/project mapping (if the org maintains RCDO → epic mappings).
  - **Outbound:** When a commit's reconciliation status changes, the linked Jira/Linear ticket can be updated (e.g., add a comment: "Weekly reconciliation: marked as Partially Done — {deltaReason}").
- Integration is via a generic **integration adapter** pattern: `IntegrationAdapter` interface with implementations for Jira (REST API), Linear (GraphQL), and future tools. Adapters are registered per org in configuration.
- OAuth 2.0 for user-level authorization to the external tool. Token management: encrypted at rest in a `user_integrations` table, refreshed on use.

**What stays the same:** Core data model (commits are still WC entities; the Jira/Linear link is a reference, not a dependency). Plan lifecycle does not depend on external tool availability — integrations are best-effort enrichments.

**ADR:** `ADR-010: External tool integration adapter architecture`
**Epic:** `WC-INTEGRATE-001: Jira integration`, `WC-INTEGRATE-002: Linear integration`

#### 17.4.5 Capacity planning module

**Trigger:** Organizations request the ability to estimate and track time allocation against commitments. Expected from Phase 1 feedback.

**What changes:**
- Add optional fields to `WeeklyCommit`: `estimatedHours` (nullable), `actualHours` (nullable, filled during reconciliation).
- Dashboard extensions: team capacity utilization view (total estimated hours vs. available hours per user per week), capacity-weighted RCDO alignment (hours allocated to strategic vs. non-strategic work).
- Available hours per user: configurable in user profile or org defaults (e.g., 40 hours/week, minus meetings estimate).
- Fields are optional and hidden behind a feature flag (`wc.capacity.enabled`). Teams that don't want time tracking don't see it.

**What stays the same:** Plan lifecycle, chess prioritization, RCDO linking. Capacity is an additive dimension, not a replacement for any existing concept.

**ADR:** `ADR-011: Capacity planning module`
**Epic:** `WC-CAPACITY-001: Capacity estimation and tracking`

### 17.5 H3: Platform (months 12–24)

#### 17.5.1 Multi-org federation

**Trigger:** WC is adopted by multiple independent organizations (business units, subsidiaries, or separate companies within a holding structure), and there is a need to federate alignment data across org boundaries for executive visibility.

**What changes:**
- Introduce a **federation layer** that aggregates anonymized or permissioned alignment metrics across participating orgs. Each org retains full data sovereignty — federation reads are pull-based (each org's WC instance exposes a standardized reporting API), not push-based.
- Federation metadata: which Rally Cries / Defining Objectives are shared across orgs (federated RCDO tree), what metrics each org exposes (configured per org, default: aggregate counts only, no individual plan data).
- Executive dashboards show cross-org strategic coverage without exposing individual team or user data.

**What stays the same:** Per-org data isolation (RLS, §9.6). Individual user experience. The federation layer is a new read-only service that consumes reporting APIs — it has no write path into any org's WC instance.

**ADR:** `ADR-012: Multi-org federation architecture`
**Epic:** `WC-PLATFORM-001: Federation layer`

#### 17.5.2 Configurable workflow engine

**Trigger:** Different orgs or teams request custom lifecycle variations — e.g., a "review before lock" stage, a "team standup sync" state, or different reconciliation cadences (bi-weekly instead of weekly). The hardcoded state machine (§6) cannot accommodate these without code changes.

**What changes:**
- Replace the hardcoded state machine with a **configurable workflow engine** that defines plan lifecycle states, transitions, and validation rules as data (stored in a `workflow_definitions` table or a DSL configuration).
- The engine supports: custom states (with configurable edit permissions), custom transition rules (conditions, validation hooks), custom notification triggers per transition, custom cadence (weekly, bi-weekly, monthly).
- A workflow editor UI (admin-only) allows orgs to customize their lifecycle without engineering involvement.
- The default workflow matches the MVP lifecycle exactly — existing orgs see no change.

**What stays the same:** Core data model (plans, commits, actuals, reviews). The configurable engine is a generalization of the existing state machine, not a replacement of the domain model.

**Complexity warning:** This is a significant investment. It is justified only if 3+ orgs request materially different lifecycles. If variations are minor (e.g., just cadence differences), simpler configuration (cadence config in `org_policies`, §5) is preferable.

**ADR:** `ADR-013: Configurable workflow engine`
**Epic:** `WC-PLATFORM-002: Workflow engine`

#### 17.5.3 Event-driven analytics pipeline (data lake)

**Trigger:** Analytics query volume or complexity exceeds what materialized views (§17.3.4) can support. Signs: materialized view refresh takes > 5 minutes, product requests ad-hoc queries that require full table scans, or data science team needs raw event data for modeling.

**What changes:**
- Stand up an event-driven analytics pipeline: CDC events (§17.3.2) are routed to a data lake (S3 + Parquet format, partitioned by `org_id` and date).
- Schema registry (e.g., AWS Glue Schema Registry or Confluent Schema Registry) ensures event schema evolution is managed.
- Analytics consumers (dbt transformations, Spark jobs, or Athena queries) operate on the data lake, not on the production database.
- Materialized views in Postgres are retired once the data lake pipeline is proven.

**What stays the same:** Production database schema, API contracts. The analytics pipeline is a read-side fork — it consumes the same CDC events that the notification system uses.

**ADR:** `ADR-014: Event-driven analytics pipeline`
**Epic:** `WC-DATA-001: Data lake and analytics pipeline`

#### 17.5.4 Multi-region active-passive

**Trigger:** The organization's RTO/RPO requirements tighten beyond what single-region + cross-region snapshots (§12.7) can deliver, or regulatory requirements mandate data residency in a specific region for a new customer segment.

**What changes:**
- Deploy a passive WC stack in a secondary AWS region (same IaC, different region variables).
- Postgres: RDS cross-region read replica with promotion capability. RPO improves from < 24 hours (daily snapshot) to < 1 minute (streaming replication).
- S3: cross-region replication (already in place for frontend bundles; extend to data lake).
- DNS failover: Route 53 health-check-based failover from primary to secondary region.
- Promotion from passive to active is a manual decision (not automatic) — split-brain prevention requires human judgment in the MVP of multi-region.

**What stays the same:** Application code (the single-writer model means no multi-master complexity). The primary region handles all writes; the secondary region is a warm standby.

**Post-H3 evolution:** If business requirements demand active-active multi-region (writes in both regions), the architecture would need conflict resolution (CRDTs for plan state, or a global write coordinator). This is a fundamentally different architecture and should be evaluated as a new product decision, not an incremental evolution.

**ADR:** `ADR-015: Multi-region active-passive deployment`
**Epic:** `WC-INFRA-001: Multi-region deployment`

#### 17.5.5 API marketplace and integration ecosystem

**Trigger:** Third-party tools or internal teams want to build on WC data (e.g., a custom reporting tool, an HR integration, a Slack bot that surfaces alignment data). The current API (§11) is designed for the WC micro-frontend, not for external consumers.

**What changes:**
- Publish a **public API** (separate from the internal API) with:
  - API key management (per-consumer keys with rate limits and scope restrictions).
  - Webhook subscriptions (consumers register a URL to receive specific event types, e.g., `plan.locked`, `review.approved`).
  - An API developer portal with documentation, sandbox environment, and usage analytics.
- The public API is a thin layer over the internal API — it adds authentication (API keys), authorization (scoped access), rate limiting (per-consumer), and webhook delivery. It does not expose all internal endpoints; only a curated, stable subset.

**What stays the same:** Internal API contracts. The public API is additive.

**ADR:** `ADR-016: Public API and webhook platform`
**Epic:** `WC-PLATFORM-003: API marketplace`

### 17.6 Scaling thresholds reference

This table consolidates the quantitative triggers referenced throughout the roadmap. Each threshold maps to a specific evolution step.

| Metric | Current MVP capacity | Watch threshold | Action threshold | Evolution step |
|---|---|---|---|---|
| **Active users** | < 100 | 200 | 500 | Cross-team rollups (§17.4.2), capacity planning (§17.4.5) |
| **Concurrent API requests** | 50 req/s sustained | 150 req/s | 300 req/s | Read replica (§17.3.1), auto-scaling tuning |
| **Dashboard query latency (p95)** | < 200ms | 400ms | 600ms | Read replica (§17.3.1), materialized views (§17.3.4) |
| **Outbox events/hour** | < 500 | 1,000 | 5,000 | CDC adoption (§17.3.2) |
| **Outbox publish latency (p95)** | < 2s | 5s | 10s | CDC adoption (§17.3.2) |
| **Database storage (GB)** | < 5 GB | 20 GB | 50 GB | Data retention automation, analytics pipeline (§17.5.3) |
| **Team count using WC** | 1–5 | 10 | 25 | Skip-level visibility (§17.3.5), cross-team rollups (§17.4.2) |
| **Engineering team size** | 1–3 | 5 | 8 | Service extraction (§17.4.1), deployment automation (§17.3.6) |
| **AI token spend (monthly)** | < $50 | $200 | $500 | Model optimization, caching tuning, per-org budgets |
| **Distinct org count** | 1 | 3 | 10 | Multi-org federation (§17.5.1) |
| **Lifecycle customization requests** | 0 | 2 distinct requests | 3+ orgs with materially different needs | Configurable workflow engine (§17.5.2) |
| **RTO requirement** | < 4 hours | < 1 hour | < 15 minutes | Multi-region active-passive (§17.5.4) |
| **External API consumer requests** | 0 | 2 internal teams | 3+ external consumers | API marketplace (§17.5.5) |

### 17.7 Technology bets and sunset plan

#### Active technology bets

| Technology | Bet | Confidence | Hedge |
|---|---|---|---|
| **Java 21 + Virtual Threads** | Virtual threads replace reactive programming for I/O-bound workloads without the complexity of reactive frameworks. | High (Project Loom is GA, Spring Boot 3.2+ supports it natively). | If virtual thread bugs surface under production load, fall back to a traditional thread pool with bounded concurrency. No code change needed — configuration only. |
| **Postgres as the single data store** | A well-tuned Postgres instance (with read replica) handles both OLTP and analytics workloads for the first 12 months. | High for MVP scale. Medium for H2+ analytics. | Materialized views (§17.3.4) extend Postgres's analytics capability. If analytics outgrows Postgres, the data lake pipeline (§17.5.3) offloads those queries. |
| **SQS over Kafka for messaging** | SQS is operationally simpler (no cluster management, no partition rebalancing) and sufficient for MVP event volumes. | High for MVP. Medium for H2+ if event replay or multi-consumer fan-out becomes critical. | The outbox event schema is bus-agnostic. Migrating from SQS to Kafka requires changing the publish/consume infrastructure (a deployment change), not the event contracts or application logic. The CDC path (§17.3.2) further decouples this — Debezium can publish to either. |
| **Claude as primary LLM** | Claude's structured output capability and prompt-following quality are best-in-class for the RCDO suggestion use case. | Medium (LLM landscape evolves rapidly). | The `LlmClient` abstraction (§9.5) makes provider switching a configuration change. Nightly quality tests (§13.1) detect degradation on any provider. Model version is pinned per environment (§12.3) — upgrades are explicit. |
| **Module Federation (PM remote pattern)** | Module Federation is the most mature micro-frontend integration pattern for React applications. | High (widely adopted, strong webpack/rspack support). | If the PA host migrates to a different micro-frontend pattern (e.g., import maps, native federation), the WC build pipeline changes but the application code is largely unaffected (React components are framework-agnostic). |

#### Planned sunsets

| Component | Sunset trigger | Replacement | Timeline |
|---|---|---|---|
| **Outbox poller (application-level)** | CDC adoption (§17.3.2) | Debezium CDC connector | H1 (months 3–6) |
| **In-process notification delivery** | Notification service extraction (§17.4.1) | Independent notification service | H2 (months 6–12) |
| **Materialized views in Postgres** | Data lake pipeline proven (§17.5.3) | S3 + dbt + Athena | H3 (months 12–24) |
| **Hardcoded state machine** | 3+ orgs request different lifecycles | Configurable workflow engine (§17.5.2) | H3 (months 12–24), only if triggered |
| **Single-region deployment** | RTO requirement tightens below 1 hour | Multi-region active-passive (§17.5.4) | H3 (months 12–24), only if triggered |

### 17.8 ADR and follow-on epic registry

The following ADRs and epics are identified by this roadmap. They are not prioritized against feature work until their triggering threshold is reached. Each ADR should be drafted 2–4 weeks before the expected trigger to allow review time.

| ADR # | Title | Trigger reference | Follow-on epic(s) |
|---|---|---|---|
| ADR-001 | Add read replica for manager dashboard queries | §17.3.1 | WC-SCALE-001 |
| ADR-002 | Replace outbox poller with Debezium CDC | §17.3.2 | WC-SCALE-002 |
| ADR-003 | Multi-channel notification delivery | §17.3.3 | WC-NOTIFY-001 |
| ADR-004 | Materialized views for analytics read model | §17.3.4 | WC-ANALYTICS-001 |
| ADR-005 | Skip-level read-only dashboard access | §17.3.5 | WC-ACCESS-001 |
| ADR-006 | Automated canary analysis and promotion | §17.3.6 | WC-CICD-001 |
| ADR-007 | Extract notification service | §17.4.1 | WC-ARCH-001 |
| ADR-008 | Cross-team and cross-org rollup architecture | §17.4.2 | WC-SCALE-003 |
| ADR-009 | Agentic AI workflow architecture | §17.4.3 | WC-AI-001, WC-AI-002, WC-AI-003 |
| ADR-010 | External tool integration adapter architecture | §17.4.4 | WC-INTEGRATE-001, WC-INTEGRATE-002 |
| ADR-011 | Capacity planning module | §17.4.5 | WC-CAPACITY-001 |
| ADR-012 | Multi-org federation architecture | §17.5.1 | WC-PLATFORM-001 |
| ADR-013 | Configurable workflow engine | §17.5.2 | WC-PLATFORM-002 |
| ADR-014 | Event-driven analytics pipeline | §17.5.3 | WC-DATA-001 |
| ADR-015 | Multi-region active-passive deployment | §17.5.4 | WC-INFRA-001 |
| ADR-016 | Public API and webhook platform | §17.5.5 | WC-PLATFORM-003 |

### 17.9 What the MVP intentionally defers (and why that's fine)

The MVP makes several simplifying assumptions that are explicitly _not_ technical debt — they are appropriate constraints for a small team shipping to < 100 users. Each assumption becomes tech debt only if the corresponding trigger threshold is crossed without action.

| MVP assumption | Why it's fine now | When it becomes tech debt | Resolution |
|---|---|---|---|
| Single Postgres instance (no read replica) | Dashboard queries are fast on indexed tables for teams < 50 | Dashboard p95 > 400ms | ADR-001 |
| Application-level outbox poller | < 500 events/hour; poller latency < 2s | Event volume > 1,000/hour or latency > 5s | ADR-002 |
| In-app notifications only | Sufficient for Phase 0/1 dogfood | User feedback demands push notifications | ADR-003 |
| No skip-level visibility | Only 1–5 teams using WC | Leadership requests cross-team views | ADR-005 |
| Synchronous single-shot AI | RCDO suggest is the only AI feature at launch | Product identifies multi-step automation opportunities | ADR-009 |
| No external tool integrations | Users manage commits manually | Double-entry friction becomes top feedback item | ADR-010 |
| Hardcoded weekly lifecycle | All orgs use the same cadence | 3+ orgs need different cadences | ADR-013 |
| Single-region deployment | RTO < 4 hours is acceptable | Business requires RTO < 1 hour | ADR-015 |
| Internal API only | No external consumers | 3+ teams want to build on WC data | ADR-016 |

---

## 18) MVP acceptance criteria (demo-ready)

Each criterion is written as a testable assertion.

### Core workflow

1. **Plan creation:** IC creates a plan for the current week. System returns 201. Creating a second plan for the same week returns 200 (idempotent, same plan).
2. **Lock validation — happy path:** IC adds 3 commits (1 KING, 1 QUEEN, 1 ROOK), all with RCDO links. Lock succeeds. Plan state = LOCKED. RCDO snapshot fields are populated on all commits.
3. **Lock validation — rejection cases:**
   * Lock with a commit missing `chessPriority` → `422` with error code `MISSING_CHESS_PRIORITY`.
   * Lock with a commit missing both `outcomeId` and `nonStrategicReason` → `422` with `MISSING_RCDO_OR_REASON`.
   * Lock with 2 KING commits → `422` with `CHESS_RULE_VIOLATION`.
   * Lock with 3 QUEEN commits → `422` with `CHESS_RULE_VIOLATION`.
4. **Locked plan immutability:** PATCH to change `chessPriority` on a locked commit → `409` with `FIELD_FROZEN`. PATCH to update `progressNotes` → `200`.
5. **Reconciliation:** IC marks commits as Done/Partially/Dropped with delta reasons. Submit succeeds. Plan state = RECONCILED, review status = REVIEW_PENDING.
6. **Reconciliation — rejection:** Submit with a commit marked `DROPPED` but empty `deltaReason` → `422` with `MISSING_DELTA_REASON`.
7. **Carry-forward:** IC carries forward 1 incomplete commit. Next week's plan is created in DRAFT with a new commit where `carriedFromCommitId` = original commit ID.
8. **Optimistic lock conflict:** Two concurrent PATCHes to the same commit with the same `version` → first succeeds, second returns `409 Conflict`.

### Manager experience

9. **Dashboard:** Manager sees all direct reports' plans for the current week with state, review status, commit counts, incomplete count, and non-strategic count.
10. **Filtered roll-up:** Manager filters by `priority=KING` and `incomplete=true` and gets only matching commits (DRAFT plans with missing RCDO or priority).
11. **Review flow:** Manager approves a plan → review status = APPROVED. Manager requests changes → review status = CHANGES_REQUESTED, plan state reverts to RECONCILING with manager's comment attached.

### Permissions (negative tests)

12. IC requests `GET /api/v1/weeks/{weekStart}/plans/{otherUserId}` → `403 Forbidden`.
13. IC requests `POST /api/v1/plans/{planId}/review` → `403 Forbidden`.
14. Manager requests `PATCH /api/v1/commits/{commitId}` on a report's commit → `403 Forbidden` (managers review, not edit).

### Late lock and edge cases

15. **Late lock:** Plan is in DRAFT, week has passed. User starts reconciliation → system performs implicit lock (`lockType = LATE_LOCK`, `lockedAt = now`), populates RCDO snapshots, transitions to RECONCILING. Manager dashboard shows "Late lock" badge.
16. **Late lock — validation still applies:** Late lock with a commit missing `chessPriority` → `422` with `MISSING_CHESS_PRIORITY`. No implicit lock is created.
17. **Changes requested after carry-forward:** Manager attempts "Request Changes" on a plan where `carryForwardExecutedAt != null` → `409` with `CARRY_FORWARD_ALREADY_EXECUTED`. Manager can still approve or comment.

### AI-assisted

18. **RCDO auto-suggest:** IC types a commit title → `POST /api/v1/ai/suggest-rcdo` returns ≤ 5 suggestions within 5 seconds. All `outcomeId` values exist in the RCDO hierarchy. Response matches JSON schema.
19. **AI fallback:** When LLM is unreachable (simulated timeout), the suggest endpoint returns `200` with `{ "status": "unavailable", "suggestions": [] }`. UI shows manual picker without error state.
20. **Reconciliation draft (beta):** `POST /api/v1/ai/draft-reconciliation` returns suggested statuses for all commits. Suggestions are clearly marked as AI-generated in the UI.

### Tests

21. **Backend:** ≥ 1 integration test per acceptance criterion above, running against Testcontainers Postgres.
22. **Frontend:** Component tests for commit editor validation, chess priority rules, reconciliation required fields.
23. **E2E (Playwright):** Golden path (create → lock → reconcile → manager approve). Failure path (lock rejected due to missing RCDO + too many QUEENs). Late lock path (skip lock → reconcile from draft).

---

## Appendix A: Error code catalog

All error codes are returned in the standard envelope: `{ "error": { "code": "...", "message": "...", "details": [...] } }`.

| HTTP status | Error code | Trigger | Details |
|---|---|---|---|
| `401` | `UNAUTHORIZED` | Missing or invalid JWT | — |
| `403` | `FORBIDDEN` | Valid JWT but no access (wrong role or no reporting relationship) | `{ "targetUserId": "..." }` |
| `409` | `CONFLICT` | Optimistic lock version mismatch | `{ "currentVersion": N }` |
| `409` | `FIELD_FROZEN` | Attempt to edit a frozen field on a locked/reconciling plan | `{ "field": "chessPriority", "planState": "LOCKED" }` |
| `409` | `PLAN_NOT_IN_DRAFT` | Attempt to delete a commit on a non-DRAFT plan | `{ "planState": "LOCKED" }` |
| `409` | `CARRY_FORWARD_ALREADY_EXECUTED` | Manager attempts "request changes" after carry-forward | `{ "carryForwardExecutedAt": "..." }` |
| `422` | `MISSING_CHESS_PRIORITY` | Lock attempted with commit(s) missing chess priority | `{ "commitIds": ["..."] }` |
| `422` | `MISSING_RCDO_OR_REASON` | Lock attempted with commit(s) missing both `outcomeId` and `nonStrategicReason` | `{ "commitIds": ["..."] }` |
| `422` | `CONFLICTING_LINK` | Commit has both `outcomeId` and `nonStrategicReason` set | `{ "commitId": "..." }` |
| `422` | `CHESS_RULE_VIOLATION` | Lock attempted with invalid chess distribution | `{ "rule": "MAX_KING", "expected": 1, "actual": 2 }` |
| `422` | `MISSING_DELTA_REASON` | Reconciliation submitted with incomplete delta reasons | `{ "commitIds": ["..."] }` |
| `422` | `MISSING_COMPLETION_STATUS` | Reconciliation submitted with commits missing completion status | `{ "commitIds": ["..."] }` |
| `422` | `INVALID_WEEK_START` | `weekStart` is not a Monday | `{ "provided": "2026-03-10" }` |
| `422` | `PAST_WEEK_CREATION_BLOCKED` | Attempt to create a plan for a past week | `{ "weekStart": "..." }` |
| `422` | `RCDO_VALIDATION_STALE` | Lock attempted but RCDO cache is stale beyond threshold | `{ "lastRefreshedAt": "..." }` |
| `422` | `IDEMPOTENCY_KEY_REUSE` | Same idempotency key used with a different request body | `{ "originalRequestHash": "..." }` |
| `503` | `SERVICE_UNAVAILABLE` | Backing service (RCDO, org graph) is down | `{ "dependency": "rcdo" }` |

---

## Appendix B: Event catalog

All events follow the outbox schema (§9.3). `schemaVersion: 1` for MVP.

| Event type | Aggregate | Trigger | Payload fields |
|---|---|---|---|
| `plan.created` | WeeklyPlan | Plan created | `planId`, `userId`, `weekStart` |
| `plan.locked` | WeeklyPlan | DRAFT → LOCKED (or late lock) | `planId`, `lockType` (`ON_TIME` / `LATE_LOCK`), `commitCount`, `incompleteCount` (should be 0) |
| `plan.reconciliation_started` | WeeklyPlan | LOCKED → RECONCILING | `planId` |
| `plan.reconciled` | WeeklyPlan | RECONCILING → RECONCILED | `planId`, `doneCount`, `partialCount`, `droppedCount` |
| `plan.carry_forward` | WeeklyPlan | RECONCILED → CARRY_FORWARD | `planId`, `carriedCommitIds[]`, `targetWeekStart` |
| `review.submitted` | ManagerReview | Manager submits review | `planId`, `reviewerUserId`, `decision` (`approved` / `changes_requested` / `comment_only`), `comment` |
| `commit.created` | WeeklyCommit | Commit added to plan | `commitId`, `planId`, `chessPriority` (nullable in DRAFT) |
| `commit.updated` | WeeklyCommit | Commit fields changed | `commitId`, `changedFields[]` |
| `commit.deleted` | WeeklyCommit | Commit deleted (DRAFT only) | `commitId`, `title` (captured for audit) |
| `commit.actual_updated` | WeeklyCommit | Reconciliation data written | `commitId`, `completionStatus` |
