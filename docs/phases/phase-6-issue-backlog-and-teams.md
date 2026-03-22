# Phase 6: Issue Backlog, Teams & AI-Powered Work Intelligence

> **Status:** ✅ Implemented (Phase A — additive tables, dual-write, full UI).
> Phase B (drop deprecated commit artifacts) deferred to after runtime surfaces
> are fully ported.
>
> **Implemented:** 2026-03-22. Run ID `2026-03-21T23-50-53-764Z-6a0485ae`.
> 20 steps, 259 changed files.
>
> **Core insight:** Commits were throwaway objects — created fresh each
> week, cloned on carry-forward, no persistent identity. Now issues are
> persistent, team-scoped work items. Weekly assignments bind issues to
> plans. The AI reasons over work history via Pinecone RAG with HyDE.

### What was built

| Layer | Artifacts |
|-------|-----------|
| **Database** | V16 (teams, issues, assignments), V17 (data migration), V18 (materialized views) |
| **Backend** | `team/` package (CRUD + access requests), `issues/` package (CRUD + lifecycle), `assignment/` package (entities + repos), `ai/rag/` package (Pinecone, HyDE, embedding pipeline), dual-write compatibility in `PlanService` |
| **Frontend** | `BacklogPage`, `TeamManagementPage`, `IssueCreateForm`, `IssueDetailPanel`, `BacklogPickerDialog`, 6 new hooks |
| **Tests** | 16 new frontend test files, 17 new backend test files (+177 frontend tests, +45 contract tests) |
| **Contracts** | `EffortType` enum, Team/Issue/Assignment types, new API paths |

---

## 1. The Problem

### Ephemeral commits can't carry context

The current `weekly_commits` table creates a new row each week. Carry-forward
copies the title and links it via `carried_from_commit_id`, but:

- Comments, time entries, and progress notes are lost or scattered
- There's no way to see the full history of a piece of work across weeks
- The AI can't reason about "how long has this been open?" or "who else
  worked on this?"
- There's no backlog — work either exists in this week's plan or it doesn't
  exist at all

### No team concept

The system has `OrgGraphClient` with manager → direct-reports, but:

- ICs can't see what their teammates are working on (only managers can)
- There's no shared pool of work to pull from
- Cross-functional collaboration is invisible
- The AI suggests coverage gaps but has nothing specific to recommend

### AI suggestions are vague

The `DefaultNextWorkSuggestionService` produces:

- **Carry-forward items** — specific but limited to your own history
- **Coverage gaps** — "Consider contributing to: Outcome X" — too vague
- **External tickets** — depends on Jira/Linear integration being wired

With a backlog of concrete, estimated, prioritized work items, the AI can
make *specific, capacity-aware recommendations*.

---

## 2. Core Model: Issues as the Unit of Work

### The shift

```
BEFORE:  WeeklyPlan → WeeklyCommit (ephemeral, per-week)
AFTER:   Issue (persistent) → WeeklyAssignment (issue committed to a week)
```

An **Issue** is a persistent unit of work that lives in a team's backlog.
A **Weekly Assignment** is the act of committing to work on that issue in
a specific week. The weekly plan becomes a *view* over assignments.

### Issue entity

```sql
CREATE TABLE issues (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    team_id                 UUID NOT NULL REFERENCES teams(id),
    
    -- Human-readable ID: "PLAT-42"
    issue_key               VARCHAR(20) NOT NULL,
    sequence_number         INTEGER NOT NULL,
    
    -- Core fields
    title                   VARCHAR(500) NOT NULL,
    description             TEXT NOT NULL DEFAULT '',
    effort_type             VARCHAR(15),
    estimated_hours         NUMERIC(6,2),
    chess_priority          VARCHAR(10),
    
    -- RCDO alignment
    outcome_id              UUID,
    non_strategic_reason    TEXT,
    
    -- Ownership
    creator_user_id         UUID NOT NULL,
    assignee_user_id        UUID,
    
    -- Simple dependency
    blocked_by_issue_id     UUID REFERENCES issues(id),
    
    -- Status
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    
    -- AI metadata
    ai_recommended_rank     INTEGER,
    ai_rank_rationale       TEXT,
    ai_suggested_effort_type VARCHAR(15),
    
    -- Timestamps
    version                 INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    archived_at             TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT uq_issue_key UNIQUE (org_id, issue_key),
    CONSTRAINT uq_issue_seq UNIQUE (team_id, sequence_number),
    CONSTRAINT chk_issue_status CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'DONE', 'ARCHIVED'
    )),
    CONSTRAINT chk_effort_type CHECK (effort_type IS NULL OR effort_type IN (
        'BUILD', 'MAINTAIN', 'COLLABORATE', 'LEARN'
    )),
    CONSTRAINT chk_issue_chess CHECK (chess_priority IS NULL OR chess_priority IN (
        'KING', 'QUEEN', 'ROOK', 'BISHOP', 'KNIGHT', 'PAWN'
    ))
);

CREATE INDEX idx_issues_team_status ON issues (team_id, status);
CREATE INDEX idx_issues_assignee ON issues (org_id, assignee_user_id, status);
CREATE INDEX idx_issues_outcome ON issues (org_id, outcome_id);
CREATE INDEX idx_issues_org_key ON issues (org_id, issue_key);
```

### Weekly assignments (replaces weekly_commits)

```sql
CREATE TABLE weekly_assignments (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    weekly_plan_id          UUID NOT NULL REFERENCES weekly_plans(id) ON DELETE CASCADE,
    issue_id                UUID NOT NULL REFERENCES issues(id),
    
    -- Per-week overrides (chess priority locked at plan lock time)
    chess_priority_override VARCHAR(10),
    expected_result         TEXT NOT NULL DEFAULT '',
    confidence              NUMERIC(3,2),
    
    -- RCDO snapshot (populated at lock time, same as today)
    snapshot_rally_cry_id   UUID,
    snapshot_rally_cry_name VARCHAR(500),
    snapshot_objective_id   UUID,
    snapshot_objective_name VARCHAR(500),
    snapshot_outcome_id     UUID,
    snapshot_outcome_name   VARCHAR(500),
    
    -- Tags for draft source tracking
    tags                    TEXT[] NOT NULL DEFAULT '{}',
    
    version                 INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_assignment_per_plan UNIQUE (weekly_plan_id, issue_id),
    CONSTRAINT chk_assignment_chess CHECK (chess_priority_override IS NULL OR
        chess_priority_override IN ('KING','QUEEN','ROOK','BISHOP','KNIGHT','PAWN'))
);

CREATE INDEX idx_assignments_plan ON weekly_assignments (org_id, weekly_plan_id);
CREATE INDEX idx_assignments_issue ON weekly_assignments (issue_id);
```

### Weekly assignment actuals (replaces weekly_commit_actuals)

```sql
CREATE TABLE weekly_assignment_actuals (
    assignment_id           UUID PRIMARY KEY REFERENCES weekly_assignments(id) ON DELETE CASCADE,
    org_id                  UUID NOT NULL,
    actual_result           TEXT NOT NULL DEFAULT '',
    completion_status       VARCHAR(15) NOT NULL,
    delta_reason            TEXT,
    hours_spent             NUMERIC(6,2),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_actual_status CHECK (completion_status IN (
        'DONE', 'PARTIALLY', 'NOT_DONE', 'DROPPED'
    ))
);
```

### Issue activity log

```sql
CREATE TABLE issue_activities (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    issue_id                UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    actor_user_id           UUID NOT NULL,
    activity_type           VARCHAR(30) NOT NULL,
    
    -- Polymorphic payload
    old_value               TEXT,
    new_value               TEXT,
    comment_text            TEXT,
    hours_logged            NUMERIC(6,2),
    metadata                JSONB NOT NULL DEFAULT '{}',
    
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_activity_type CHECK (activity_type IN (
        'CREATED', 'STATUS_CHANGE', 'ASSIGNMENT_CHANGE',
        'PRIORITY_CHANGE', 'EFFORT_TYPE_CHANGE', 'ESTIMATE_CHANGE',
        'COMMENT', 'TIME_ENTRY', 'OUTCOME_CHANGE',
        'COMMITTED_TO_WEEK', 'RELEASED_TO_BACKLOG',
        'CARRIED_FORWARD', 'BLOCKED', 'UNBLOCKED',
        'DESCRIPTION_CHANGE', 'TITLE_CHANGE'
    ))
);

CREATE INDEX idx_activities_issue ON issue_activities (issue_id, created_at);
CREATE INDEX idx_activities_user ON issue_activities (org_id, actor_user_id, created_at);
```

---

## 3. Teams

### Team entity

```sql
CREATE TABLE teams (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    key_prefix              VARCHAR(10) NOT NULL,
    description             TEXT NOT NULL DEFAULT '',
    owner_user_id           UUID NOT NULL,   -- The manager who created it
    issue_sequence           INTEGER NOT NULL DEFAULT 0,
    
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_team_name UNIQUE (org_id, name),
    CONSTRAINT uq_team_prefix UNIQUE (org_id, key_prefix)
);

CREATE TABLE team_members (
    team_id                 UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL,
    org_id                  UUID NOT NULL,
    role                    VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (team_id, user_id),
    CONSTRAINT chk_team_role CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX idx_team_members_user ON team_members (org_id, user_id);

CREATE TABLE team_access_requests (
    id                      UUID PRIMARY KEY,
    team_id                 UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    requester_user_id       UUID NOT NULL,
    org_id                  UUID NOT NULL,
    status                  VARCHAR(15) NOT NULL DEFAULT 'PENDING',
    decided_by_user_id      UUID,
    decided_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_request_status CHECK (status IN ('PENDING', 'APPROVED', 'DENIED'))
);
```

### Team creation flow

1. Manager clicks "Create Team"
2. Enters name (e.g., "Platform") → prefix auto-derived (`PLAT`, first 4 chars uppercase, deduped)
3. Manager can override prefix
4. Manager adds members from org roster (auto-populated with direct reports)
5. Manager can add non-reports (cross-functional)
6. Non-members see a "Request Access" button

### Visibility rules

| Role | What they see |
|------|---------------|
| **IC** | Own team(s) backlog + issues in Outcomes they've had assignments for in the last 8 weeks |
| **Manager** | Their team(s) + all issues assigned to any direct report (regardless of team) |
| **Admin** | Everything org-wide |

---

## 4. Effort Types (replacing CommitCategory)

The existing 7 categories (DELIVERY, OPERATIONS, CUSTOMER, GTM, PEOPLE,
LEARNING, TECH_DEBT) are replaced with 4 effort types:

| Effort Type | Meaning | Replaces |
|------------|---------|----------|
| **BUILD** | Creating something new — features, tools, content, infrastructure | DELIVERY, GTM |
| **MAINTAIN** | Keeping things running — ops, bugs, incidents, tech debt | OPERATIONS, TECH_DEBT |
| **COLLABORATE** | Working with/for others — reviews, mentoring, meetings, customer work | PEOPLE, CUSTOMER |
| **LEARN** | Investing in growth — spikes, training, research, experiments | LEARNING |

AI suggests effort type on issue creation based on:
- Outcome keywords and parent Rally Cry context
- Description text similarity to past issues
- Historical patterns for the creator

UX: chips displayed with the AI suggestion pre-selected (dimmed highlight),
user clicks to confirm or override.

---

## 5. Lifecycle Flows

### Creating an issue

**From backlog view:**
```
User clicks "New Issue" →
  Title (required)
  Description (optional, rich text)
  Team (auto-selected to user's primary team)
  Outcome link (required by default; toggle "Non-strategic" to skip)
  Estimated hours (optional; AI suggests if confident)
  Effort type (AI pre-selects; user confirms/overrides)
  Chess priority (optional; AI suggests based on urgency context)
  Assignee (optional; defaults to unassigned)
  Blocked by (optional; search existing issues)
→ Issue created with status OPEN
→ Activity log: "CREATED by Alice"
```

**From weekly plan view:**
```
User clicks "Add to Plan" →
  Option A: "From Backlog" → search/browse → pull into this week
  Option B: "New Issue" → same form as above, but auto-assigns to this week
→ Issue gets a weekly_assignment for this week
→ Status flips to IN_PROGRESS if it was OPEN
→ Activity log: "COMMITTED_TO_WEEK 2026-03-16 by Alice"
```

**AI-inspired creation (coverage gaps):**
```
User opens "New Issue" → AI sidebar:
  "Outcomes that need attention:"
  - "Reduce API latency" (AT_RISK, 3 weeks to target, 0 commits in 4 weeks)
  - "Onboard 3 new customers" (NEEDS_ATTENTION, coverage gap 2 weeks)
User selects one → Outcome auto-linked, AI suggests title and estimate
```

### Weekly planning

```
Plan view shows:
  [This Week's Plan]
  - PLAT-42: Add Redis caching layer (4h, BUILD, ROOK)    [Remove]
  - PLAT-38: Fix auth timeout (2h, MAINTAIN, QUEEN)        [Remove]
  + [Add from Backlog]  [New Issue]

  [AI Recommendations]
  "Based on your 18h remaining capacity and 2 AT_RISK Outcomes:"
  1. PLAT-45: Database index optimization (3h) — unblocks PLAT-42
  2. PLAT-51: API rate limiting (5h) — Outcome at risk, target in 2 weeks
  [Accept] [Dismiss]
```

### Reconciliation

```
DONE     → issue.status = DONE, archived_at set to now + 8 weeks
PARTIALLY → issue.status = IN_PROGRESS
            → Prompt: "Carry forward to next week?" or "Release to backlog?"
            → If carry: weekly_assignment created for next week, history preserved
            → Activity: "CARRIED_FORWARD from 2026-03-16 to 2026-03-23"
NOT_DONE → Same prompt as PARTIALLY
            → If release: issue.assignee cleared (anyone can claim)
            → Activity: "RELEASED_TO_BACKLOG by Alice"
DROPPED  → issue.status = OPEN, assignee cleared
            → Activity: "STATUS_CHANGE DROPPED, RELEASED_TO_BACKLOG"
```

### Chess priority behavior

- Chess priority lives on the issue itself (not the weekly assignment)
- Priority is editable while the issue is in a DRAFT plan
- When the plan is LOCKED, the priority is frozen for that week (stored in
  `chess_priority_override` on the weekly assignment for historical record)
- The issue's chess priority can still change in the next week
- AI chess-aware downgrade (Phase 6.1 fix) applies at suggestion time

---

## 6. AI Capabilities

### 6.1 Smart backlog ranking

A scheduled job (or on-demand trigger) computes `ai_recommended_rank` for
each open issue in a team's backlog:

**Inputs:**
- Outcome urgency band (CRITICAL / AT_RISK / NEEDS_ATTENTION / ON_TRACK)
- Outcome target date and forecast confidence
- Issue estimated hours
- Dependency chain (is this blocked? does it unblock something?)
- Team capacity (aggregate remaining hours this week)
- Individual assignee capacity and velocity (from user model)
- Coverage gap data (how long since team committed to this Outcome?)

**Ranking formula (Phase 1 — deterministic):**
```
score = urgency_weight × time_pressure × effort_fit × dependency_bonus
where:
  urgency_weight  = {CRITICAL: 4, AT_RISK: 3, NEEDS_ATTENTION: 2, ON_TRACK: 1}
  time_pressure   = max(1, 5 - weeks_until_target) / 4
  effort_fit      = 1.0 if estimated_hours <= user_remaining_capacity else 0.5
  dependency_bonus = 1.5 if this issue unblocks other issues else 1.0
```

**Phase 2 — LLM re-ranking:**
Similar to the existing next-work LLM ranking, send the top-K candidates
to the LLM with full context for nuanced reordering and rationale generation.

### 6.2 AI-suggested effort type

On issue creation, if the AI has sufficient context (linked Outcome, description
text), it suggests an effort type:

```
POST /ai/suggest-effort-type
{
  "title": "Add Redis caching layer",
  "description": "Implement caching for the hot API paths...",
  "outcomeId": "uuid-of-reduce-latency"
}
→ { "suggestedType": "BUILD", "confidence": 0.85 }
```

Below confidence threshold (e.g., 0.6), no suggestion is shown.

### 6.3 Overcommit detection with backlog release suggestions

When a user's weekly plan exceeds their realistic capacity cap:

```
"You have 32h committed but your realistic cap is 24h.
 Consider deferring back to backlog:
 - PLAT-51: API rate limiting (5h, ROOK) — Outcome is ON_TRACK, no time pressure
 - PLAT-53: Update API docs (3h, PAWN) — low urgency
 [Defer PLAT-51] [Defer PLAT-53] [Keep all]"
```

### 6.4 Coverage gap → issue inspiration

The existing coverage gap data feeds into the issue creation flow:

```
[New Issue]
┌─────────────────────────────────────────────────────────┐
│  AI Suggestions — Outcomes that need attention           │
│                                                          │
│  ⚠ "Reduce API latency" — AT_RISK, 0 commits in 4 wks  │
│     Suggested title: "Profile and optimize slow queries" │
│     Est. effort: ~4h (based on similar past work)        │
│     [Use this →]                                         │
│                                                          │
│  ⚠ "Onboard 3 new customers" — NEEDS_ATTENTION           │
│     Suggested title: "Prepare onboarding checklist v2"   │
│     Est. effort: ~6h                                     │
│     [Use this →]                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 7. RAG: Semantic Search over Work History

### Why RAG?

The AI needs to reason about historical issues to:
- Suggest effort estimates ("similar work took 3–5h")
- Find related issues ("this looks like PLAT-28 from 6 weeks ago")
- Generate coverage gap suggestions with rich context
- Power the smart backlog ranking with historical patterns

Loading all issues into the prompt doesn't scale. RAG with vector search
provides relevant context without blowing up token counts.

### Vector store: Pinecone

**Index:** One Pinecone index per org (or a single index with org_id as
metadata filter for multi-tenant).

**Namespace:** `issues` (future: `comments`, `check-ins` as separate namespaces).

### Document schema

Each issue is embedded as a single document:

```json
{
  "id": "issue-uuid",
  "values": [0.012, -0.034, ...],   // 1536-dim embedding vector
  "metadata": {
    "org_id": "org-uuid",
    "team_id": "team-uuid",
    "issue_key": "PLAT-42",
    "status": "IN_PROGRESS",
    "effort_type": "BUILD",
    "outcome_id": "outcome-uuid",
    "outcome_name": "Reduce API latency",
    "rally_cry_name": "Ship V2",
    "assignee_user_id": "user-uuid",
    "estimated_hours": 4.0,
    "chess_priority": "ROOK",
    "created_at": "2026-03-10T10:00:00Z",
    "week_last_active": "2026-03-16"
  }
}
```

### Parsing and chunking strategy

Issues are relatively short documents (title + description + comments),
so we **don't chunk** — each issue becomes one embedding. The text input
to the embedding model is a structured concatenation:

```
Embedding input template:
───────────────────────────
Issue: {issue_key} — {title}
Team: {team_name}
Outcome: {rally_cry_name} / {objective_name} / {outcome_name}
Effort: {effort_type}, {estimated_hours}h estimated
Status: {status}
Description: {description}

Activity summary:
- {latest 5 activity entries, one line each}
- "{comment_text}" — {actor_name}, {date}

Reconciliation history:
- Week of {date}: {completion_status}, {hours_spent}h spent. "{actual_result}"
───────────────────────────
```

**Why this format:**
- Structured prefix (issue key, team, outcome) ensures metadata is always
  in the embedding, improving retrieval for filtered queries
- Activity summary captures the *narrative* of the issue — what happened,
  who said what — which is critical for similarity matching
- Reconciliation history gives the AI temporal context about velocity
- Keeping it as one document avoids cross-chunk retrieval complexity

**Max length handling:** If the concatenated text exceeds 8,000 tokens
(the `text-embedding-3-small` limit), truncate the activity summary to
the most recent 3 entries and the reconciliation history to the most
recent 4 weeks.

### Embedding model

**OpenAI `text-embedding-3-small`** (1536 dimensions)
- Cost: $0.02 per 1M tokens — negligible for issue-level embeddings
- Latency: ~100ms per request
- Quality: strong for structured text with mixed metadata and natural language

### Embedding pipeline

```
Issue created/updated
       │
       ▼
IssueEmbeddingJob (async, debounced 30s)
       │
       ├── Render embedding input from template
       ├── Call OpenAI embedding API
       ├── Upsert to Pinecone with metadata
       └── Store embedding_version on issue row
```

**Triggers:**
- Issue created → embed immediately
- Issue updated (title, description, outcome, status change) → debounce 30s, re-embed
- Comment added → debounce 30s, re-embed (comment text is part of the input)
- Reconciliation completed → re-embed (adds actual result to history)

**Batch backfill:** On first deploy or index rebuild, a one-time job
iterates all non-archived issues and embeds them.

### HyDE (Hypothetical Document Embeddings)

For queries where the user's intent is abstract ("what should I work on
next?" or "find work related to performance"), standard query embedding
often underperforms because the query text is short and doesn't resemble
the document format.

**HyDE approach:** Generate a hypothetical ideal document, then embed *that*
and use it as the query vector.

```
User query: "What should I work on next?"
     │
     ▼
LLM generates hypothetical document:
  "Issue: PLAT-XX — Optimize database query performance
   Team: Platform
   Outcome: Ship V2 / Core Performance / Reduce API latency
   Effort: BUILD, 4h estimated
   Status: OPEN
   Description: Profile the slow API endpoints identified in last week's
   monitoring alerts and implement query optimizations..."
     │
     ▼
Embed the hypothetical document (not the original query)
     │
     ▼
Pinecone similarity search → returns real issues that match
```

**When to use HyDE:**
- **Backlog ranking:** "Find the most impactful issues for this user this week"
  → Generate a hypothetical ideal work item based on urgency context, capacity,
  and Outcome risk → embed → retrieve similar real issues
- **Coverage gap inspiration:** "What kind of work would help Outcome X?"
  → Generate hypothetical issue description → embed → find similar past work
- **Smart search:** User types a natural-language query in the backlog
  → HyDE transforms it into document-shaped embedding input

**When NOT to use HyDE:**
- **Direct similarity:** "Find issues like PLAT-42" → just use PLAT-42's
  existing embedding as the query vector
- **Metadata filtering:** "Show all BUILD issues for Outcome X" → use
  Pinecone metadata filter, no embedding needed

### HyDE implementation

```java
public class HydeQueryService {
    
    private final LlmClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final PineconeClient pineconeClient;
    
    /**
     * Generates a HyDE query for backlog recommendation.
     *
     * @param userContext  user's capacity, current plan, recent history
     * @param outcomeContext  at-risk outcomes, coverage gaps, urgency data
     * @return ranked list of real issues from vector similarity search
     */
    public List<ScoredIssue> recommendWithHyde(
            UserWorkContext userContext,
            OutcomeRiskContext outcomeContext,
            int topK) {
        
        // 1. Generate hypothetical ideal issue
        String hydePrompt = PromptBuilder.buildHydeRecommendationPrompt(
            userContext, outcomeContext);
        String hypotheticalDoc = llmClient.complete(
            List.of(new LlmClient.Message("system", hydePrompt)),
            null  // no schema — free-form text
        );
        
        // 2. Embed the hypothetical document
        float[] queryVector = embeddingClient.embed(hypotheticalDoc);
        
        // 3. Query Pinecone with metadata filters
        PineconeQuery query = PineconeQuery.builder()
            .vector(queryVector)
            .topK(topK)
            .filter(Map.of(
                "org_id", userContext.orgId().toString(),
                "status", Map.of("$in", List.of("OPEN", "IN_PROGRESS"))
            ))
            .includeMetadata(true)
            .build();
        
        return pineconeClient.query(query).stream()
            .map(match -> new ScoredIssue(
                UUID.fromString(match.getId()),
                match.getScore(),
                match.getMetadata()))
            .toList();
    }
}
```

### Retrieval patterns

| Use case | Method | Top-K | Filters |
|----------|--------|-------|---------|
| **Weekly plan recommendations** | HyDE with user + urgency context | 10 | org_id, status=OPEN, team_id in user's teams |
| **Similar issue search** | Direct embedding of source issue | 5 | org_id, issue_id != source |
| **Coverage gap inspiration** | HyDE with outcome risk context | 5 | org_id, outcome_id = target |
| **Effort estimation** | Direct embedding of new issue title+desc | 8 | org_id, status=DONE, effort_type match |
| **Backlog search** | HyDE for natural language, direct for issue key | 20 | org_id, team_id filter |

### Local development

For local dev without Pinecone:
- `EmbeddingClient` interface with `PineconeEmbeddingClient` and
  `InMemoryEmbeddingClient` implementations
- `InMemoryEmbeddingClient` stores vectors in a `ConcurrentHashMap` and
  does brute-force cosine similarity — slow but functional for <1000 issues
- Toggle via `weekly.rag.provider=pinecone|memory` in application config

---

## 8. API Surface

### Issue CRUD

```
POST   /api/v1/teams/{teamId}/issues              — Create issue
GET    /api/v1/teams/{teamId}/issues               — List team backlog (paginated, filterable)
GET    /api/v1/issues/{issueId}                     — Get issue detail with activity log
PATCH  /api/v1/issues/{issueId}                     — Update issue fields
DELETE /api/v1/issues/{issueId}                     — Soft-delete (archive)
```

### Issue actions

```
POST   /api/v1/issues/{issueId}/assign             — Assign to user
POST   /api/v1/issues/{issueId}/commit              — Add to weekly plan
POST   /api/v1/issues/{issueId}/release             — Release back to backlog
POST   /api/v1/issues/{issueId}/comment             — Add comment
POST   /api/v1/issues/{issueId}/time-entry          — Log time
```

### Team management

```
POST   /api/v1/teams                                — Create team (manager only)
GET    /api/v1/teams                                 — List user's teams
GET    /api/v1/teams/{teamId}                        — Get team details + members
PATCH  /api/v1/teams/{teamId}                        — Update team
POST   /api/v1/teams/{teamId}/members               — Add member (owner only)
DELETE /api/v1/teams/{teamId}/members/{userId}       — Remove member (owner only)
POST   /api/v1/teams/{teamId}/access-requests       — Request access
PATCH  /api/v1/teams/{teamId}/access-requests/{id}  — Approve/deny
```

### AI endpoints

```
POST   /api/v1/ai/suggest-effort-type               — AI-suggested effort type
POST   /api/v1/ai/rank-backlog                       — Trigger AI backlog ranking
POST   /api/v1/ai/recommend-weekly-issues            — HyDE-powered weekly recommendations
POST   /api/v1/ai/suggest-deferrals                  — Overcommit relief suggestions
GET    /api/v1/ai/coverage-gap-inspirations          — Issue creation suggestions from gaps
POST   /api/v1/ai/search-issues                      — Semantic search over issue history
```

### Weekly plan integration

```
POST   /api/v1/weeks/{weekStart}/plan/assignments   — Add issue to this week's plan
DELETE /api/v1/weeks/{weekStart}/plan/assignments/{assignmentId} — Remove from plan
```

---

## 9. Migration Strategy

Since this is pre-production, we do a single big migration:

### Migrations (implemented)

**V16__teams_issues_assignments.sql** (additive — no drops)
1. Create `teams`, `team_members`, `team_access_requests` tables
2. Create `issues`, `issue_activities` tables
3. Create `weekly_assignments`, `weekly_assignment_actuals` tables
4. Add `effort_type` column to issues (replaces `category`)
5. All RLS policies and indexes

**V17__migrate_commits_to_issues.sql** (data migration)
1. Creates a default "General" team per org
2. For each `weekly_commit`, creates a corresponding `issue`
3. Creates `weekly_assignment` rows linking issues to their plans
4. Migrates `weekly_commit_actuals` → `weekly_assignment_actuals`
5. Preserves carry-forward chains via `issue_activities`
6. **Does not drop** `weekly_commits` — dual-write keeps both tables active

**V18__assignment_materialized_views.sql**
1. Materialized views for assignment-based analytics
2. Supports the new backlog health metrics

### Seed data

`scripts/seed-data.sql` has been updated to:
- Create teams for each persona's manager
- Create issues with full activity history
- Create weekly_assignments linking issues to plans
- Issue embeddings handled by `IssueEmbeddingJob` on startup (in-memory mode for local dev)

---

## 10. Frontend Changes

### New pages/components

| Component | Description |
|-----------|-------------|
| `BacklogPage` | Team backlog view with filters (status, effort type, assignee, outcome) and AI ranking toggle |
| `IssueDetailPanel` | Slide-out panel showing full issue detail, activity log, comments, time entries |
| `IssueCreateForm` | Form with AI-suggested effort type, coverage gap inspiration sidebar |
| `TeamManagementPage` | Team settings, member management, access requests |
| `BacklogPickerDialog` | "Add from backlog" dialog in weekly plan view |

### Modified pages

| Page | Changes |
|------|---------|
| `WeeklyPlanPage` | Replace commit CRUD with assignment CRUD; add "Add from Backlog" button; add "Release to Backlog" in reconciliation flow |
| `MyInsightsPage` | Effort type distribution chart (replaces category donut) |
| `TeamDashboardPage` | Add backlog health metrics (open count, avg age, blocked count) |
| `ExecutiveDashboardPage` | Add org-wide backlog metrics |

### Nav updates

Add "Backlog" as a nav item visible to all users, between "My Plan" and
"My Insights."

---

## 11. Implementation order

```
Step 1:  Schema + Team CRUD + Issue CRUD (no AI, no RAG)          ✅ Done
Step 2:  Weekly assignment integration (replace commits)           ✅ Done (dual-write)
Step 3:  Reconciliation flow updates (carry-forward / release)     ✅ Done
Step 4:  Backlog UI + issue detail panel                           ✅ Done
Step 5:  AI effort type suggestion + deterministic backlog ranking ✅ Done
Step 6:  RAG infrastructure (Pinecone + embedding pipeline)        ✅ Done
Step 7:  HyDE-powered recommendations + semantic search            ✅ Done
Step 8:  Coverage gap inspiration in issue creation                ✅ Done
Step 9:  Overcommit deferral suggestions                           ✅ Done
Step 10: Team management UI + access requests                      ✅ Done
```

### Phase B (future — not yet implemented)

```
Step 11: Remove deprecated weekly_commits / weekly_commit_actuals tables
Step 12: Remove CommitCategory enum (fully replaced by EffortType)
Step 13: Remove dual-write paths in PlanService
Step 14: Update all E2E tests to use assignment-based APIs exclusively
```

Phase B is deferred until all runtime surfaces (frontend pages, API consumers,
seed data, E2E tests) are verified to work exclusively through the new
issue/assignment model.

---

## 12. Success metrics

| Metric | Target | How measured |
|--------|--------|-------------|
| **Backlog utilization** | >60% of weekly assignments come from backlog (not new issues) | `weekly_assignments` with pre-existing `issue_id` |
| **Issue cycle time** | Median <2 weeks from OPEN to DONE | `issues.created_at` → `issues.archived_at` |
| **AI recommendation acceptance** | >30% of AI-recommended issues pulled into plans | `ai_suggestion_feedback` with `ACCEPT` action |
| **Effort estimation accuracy** | <25% deviation between estimated and actual hours | Compare `estimated_hours` vs sum of `hours_logged` |
| **Coverage gap reduction** | 50% fewer 4+ week coverage gaps | Same metric as today but with specific issues addressing gaps |
| **Carry-forward reduction** | <15% of assignments carry forward (vs ~30% today) | Better planning = less unfinished work |
