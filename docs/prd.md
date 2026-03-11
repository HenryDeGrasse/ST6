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

## 1.1) External dependencies and system boundaries

The Weekly Commitments module does not own user identity, org structure, or strategic goals. These are upstream dependencies with explicit contracts.

| Dependency | What we need | Expected source | Failure mode |
|---|---|---|---|
| **AuthN/AuthZ** | JWT with `userId`, `orgId`, `roles[]` claims. Role values: `IC`, `MANAGER`, `ADMIN`. | PA host identity service (OAuth 2.0 + JWKS endpoint) | Reject request (401/403). No fallback. |
| **Org graph (reporting chain)** | Given a `userId`, resolve direct reports. Used by manager dashboard and authorization ("is X a report of Y?"). | PA directory service or HRIS API. Expected: `GET /api/v1/org/users/{userId}/direct-reports`. | If unavailable: manager dashboard shows stale cached data with banner. Cache TTL: 15 min. Authorization checks use cached graph; if cache is cold + service is down, deny manager access (fail closed). |
| **RCDO hierarchy** | Full tree of Rally Cries → Defining Objectives → Outcomes with stable IDs. Used for linking picker and validation. | Existing PA API or dedicated RCDO service. Expected: paginated `GET /api/v1/rcdo/tree`, rate limit ≥ 100 req/min. | Cached read-only view (cache TTL: 5 min). Block plan locking if cache is stale > 1 hour and service is unreachable (configurable). |
| **LLM API** | Text-in, structured-JSON-out. Used for RCDO auto-suggest and reconciliation drafts. | Claude API (or equivalent) behind internal abstraction. | Non-blocking. UI falls back to manual entry. Hard timeout: 5s. |

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
* **Cost controls:** Cache suggestions keyed on `hash(commitTitle + commitDescription + rcdoTreeVersion)`. Rate limit: 20 AI requests per user per minute. Sufficient for active plan editing; prevents runaway costs.

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

## 9) Non-functional requirements

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

### Event-driven patterns

* State transitions and audit events are published to a message bus (SQS or Kafka, depending on host infra) for decoupled consumption.
* Consumers: notification delivery (in-app, email, Slack), analytics aggregation, manager alert triggers.
* This keeps the write path fast and lets downstream concerns scale independently — important for a small team that can't afford to maintain tightly coupled notification logic inside the core service.

**Reliability: transactional outbox pattern**

Publishing directly to Kafka/SQS from the request path risks lost events if the DB commit succeeds but the publish fails (or vice versa). We use a transactional outbox:

1. Every state transition writes both the domain change **and** an `outbox_events` row in the **same DB transaction**.
2. A background poller (or CDC connector) reads unpublished outbox rows and publishes to the message bus, then marks them `published_at`.
3. Events are **at-least-once**. Consumers must be idempotent, keyed on `eventId` (UUID).

Outbox event schema (illustrative):

```
eventId:        UUID (PK)
eventType:      STRING  (e.g., "plan.locked", "plan.reconciled", "review.approved")
aggregateType:  STRING  (e.g., "WeeklyPlan")
aggregateId:    UUID
payload:        JSONB   (event-specific data)
schemaVersion:  INT     (for forward-compatible consumers)
occurredAt:     TIMESTAMP WITH TIME ZONE
publishedAt:    TIMESTAMP WITH TIME ZONE (null until published)
```

Even a partial implementation of this pattern (outbox table + simple poller) signals production-grade thinking. The full CDC path (Debezium) is a post-MVP optimization.

### Observability

* Structured logs with correlation IDs
* Metrics:

  * state transition counts
  * lock/reconcile completion rates
  * incomplete commit counts (DRAFT), non-strategic commit counts (locked+)
  * AI suggestion accept/reject rates
  * API latency/error rates
* Distributed tracing across host → micro-frontend → backend → LLM API
* Deploy behind feature flags (LaunchDarkly or AWS AppConfig) for safe rollout with a small team

---

## 10) Technical approach (implementation-level product constraints)

### Micro-frontend requirements

* Built as a **remote module** consumable by PA host (PM remote pattern)
* React + TypeScript **strict mode**
* Shared dependencies aligned with host (React version, design system, routing)
* Route integration:

  * `/weekly` (IC)
  * `/weekly/team` (manager)

### Backend service

* Java 21 service ("weekly-service") per assignment requirements
  * _Note: ST6's primary stack is TypeScript/Node.js. If the host PA app backend is Node-based, consider aligning to reduce operational overhead for a small team. Java 21 is used here because the assignment specifies it._
* REST endpoints (OpenAPI contract published; GraphQL only if host already standardizes on it)
* SQL persistence (Postgres preferred; SQL Server if host mandates)
* DB migration tooling (Flyway or Liquibase)
* Event publishing to SQS/Kafka for audit trail and notifications

### Data storage

**Multi-tenancy:** Every table includes an `org_id` column (UUID, from JWT `orgId` claim). All queries are scoped by `org_id` — cross-org data access is impossible at the query layer, not just the auth layer. This is defense-in-depth: even a bug in authorization logic cannot leak data across orgs.

Core tables (illustrative):

* `weekly_plans` — includes `org_id`, `state`, `review_status`, `lock_type`, `carry_forward_executed_at`, `version` (optimistic lock), `created_at`, `updated_at`
* `weekly_commits` — includes `org_id`, `version`, `progress_notes`, RCDO snapshot fields (`snapshot_rally_cry_name`, etc.), `created_at`, `updated_at`
* `weekly_commit_actuals` — includes `org_id`
* `manager_reviews` — includes `org_id`
* `audit_events` — includes `org_id`; append-only; never updated or deleted
* `outbox_events` — includes `org_id`; transactional outbox for reliable event publishing (see §9)
* `idempotency_keys` — see §11

Indexes:

* `(org_id, owner_user_id, week_start_date)` **unique** on `weekly_plans`
* `(org_id, weekly_plan_id)` on `weekly_commits`
* `(org_id, outcome_id)` on `weekly_commits` for roll-up queries
* `(org_id, week_start_date, state)` on `weekly_plans` for manager dashboard filters
* `(published_at)` on `outbox_events` for the publisher poller (null = unpublished)

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

## 12) QA and test strategy (emphasis for an impressive demo)

### Frontend tests (TypeScript)

* Unit tests for state machine UI logic (pure functions)
* Component tests (React Testing Library) for:

  * commit editor validation
  * chess priority rules
  * reconciliation required fields
* Contract tests against OpenAPI (generated client + schema validation)
* E2E tests (Playwright):

  * Create draft → lock → reconcile → manager approve
  * Manager dashboard roll-up filtering
  * Permissions: IC cannot see others; manager can

### Backend tests (Java 21)

* Unit tests for:

  * state transition rules
  * authorization checks
  * validation gating (RCDO link rules, chess rules)
* Integration tests using **Testcontainers** (Postgres):

  * CRUD correctness
  * transactional behavior
  * unique constraints and carry-forward lineage
* API contract tests:

  * OpenAPI schema validation
  * backward compatibility checks
* Property-based tests (optional but impressive):

  * random sequences of state transitions to prove invalid transitions are rejected

### CI/CD quality gates

* TypeScript: `tsc --noEmit`, lint, unit tests, coverage threshold
* Java: spotless/format, unit+integration tests, coverage threshold
* E2E: run on PR + nightly
* Build artifacts: versioned, signed, deployed behind feature flag

---

## 13) Rollout plan

* Phase 0: internal dogfood for 1 team (2 managers, 10 ICs)
* Phase 1: expand to 3-5 teams; tune rules (KING/QUEEN caps)
* Phase 2: organization-wide rollout; retire 15Five weekly planning usage

Feature flags:

* Enable module per org/team
* Enable strict chess rules
* Enable lock-time automation

---

## 14) Risks and mitigations

* **RCDO data quality / changing taxonomy**

  * Mitigation: store stable IDs + snapshots at lock; tolerate renamed/archived nodes; cache; surface stale links with "archived" badge
* **Overly rigid locking creates user friction**

  * Mitigation: configurable lock windows + manager unlock path; start strict, loosen per feedback
* **Manager dashboard performance with larger teams**

  * Mitigation: pre-aggregations by week; indexed queries; pagination
* **Adoption risk**

  * Mitigation: AI-assisted RCDO linking and reconciliation drafts reduce manual effort; tight UX, minimal fields, fast entry, "clone last week", reminders
* **AI suggestion quality / hallucination**

  * Mitigation: suggestions are never auto-applied; user always confirms. Prompt includes full RCDO hierarchy as context to ground responses. Log accept/reject rates to measure and improve.
* **Small team, big scope**

  * Mitigation: ruthless MVP scoping; AI features use a thin prompt layer (not a custom model); leverage existing PA infra for auth, hosting, CI/CD. Feature flags let us ship incrementally.

---

## 15) Decisions (resolved) and open questions

### Resolved (opinionated defaults - configurable later)

* **LOCKED is a hard lock.** Users cannot delete or reprioritize commits after lock. They can add progress notes. Rationale: soft locks defeat the purpose of forced prioritization. If this creates friction, we loosen it per-team via config - but start strict.
* **Week starts Monday.** All `weekStart` dates are ISO Monday dates. Timezone: user's configured timezone in PA profile (stored as IANA tz string). Lock and reconciliation triggers use the user's local time.
* **Managers can comment and review, not edit team plans.** Editing someone else's plan breaks ownership. Managers use "request changes" to push a plan back to RECONCILING with a reason.

### Open questions (not blocking MVP)

* Are RCDO entities sourced from an existing service in PA, or do we need to build a read model? (MVP assumption: we consume an existing API and cache locally. If no API exists, we build a lightweight RCDO admin as a fast-follow.)
* What LLM provider does the org prefer? (MVP assumption: call Claude API behind a thin abstraction layer so we can swap providers without code changes.)

---

## 16) MVP acceptance criteria (demo-ready)

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

All events follow the outbox schema (§9). `schemaVersion: 1` for MVP.

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

