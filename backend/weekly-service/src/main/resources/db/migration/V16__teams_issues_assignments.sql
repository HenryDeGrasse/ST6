-- Phase 6: Issue Backlog, Teams & AI-Powered Work Intelligence
--
-- ADDITIVE ONLY — no existing tables, columns, or indexes are dropped.
-- The weekly_commits table is preserved intact (Phase A of two-phase migration).
-- Phase B removal migrations are deferred to a future release.
--
-- Creation order:
--   1. teams, team_members, team_access_requests
--   2. issues (FK → teams)
--   3. issue_activities (FK → issues)
--   4. weekly_assignments (FK → weekly_plans, issues)
--   5. weekly_assignment_actuals (FK → weekly_assignments)
--   6. Additive ALTER on weekly_commits (crosswalk: source_issue_id → issues)

-- ─── Teams ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS teams (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    name                    VARCHAR(100) NOT NULL,
    key_prefix              VARCHAR(10) NOT NULL,
    description             TEXT NOT NULL DEFAULT '',
    owner_user_id           UUID NOT NULL,
    -- Monotonically-increasing counter: incremented atomically by the
    -- issue creation service via UPDATE … RETURNING to avoid races.
    issue_sequence          INTEGER NOT NULL DEFAULT 0,
    version                 INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_team_name   UNIQUE (org_id, name),
    CONSTRAINT uq_team_prefix UNIQUE (org_id, key_prefix)
);

CREATE INDEX IF NOT EXISTS idx_teams_org ON teams (org_id);

-- ─── Team Members ────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS team_members (
    team_id                 UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL,
    org_id                  UUID NOT NULL,
    role                    VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (team_id, user_id),
    CONSTRAINT chk_team_role CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX IF NOT EXISTS idx_team_members_user ON team_members (org_id, user_id);

-- ─── Team Access Requests ────────────────────────────────────

CREATE TABLE IF NOT EXISTS team_access_requests (
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

CREATE INDEX IF NOT EXISTS idx_team_access_requests_team
    ON team_access_requests (team_id, status);
CREATE INDEX IF NOT EXISTS idx_team_access_requests_requester
    ON team_access_requests (org_id, requester_user_id);

-- ─── Issues ─────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS issues (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    team_id                 UUID NOT NULL REFERENCES teams(id),

    -- Human-readable key, e.g. "PLAT-42"
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

    -- Simple dependency graph
    blocked_by_issue_id     UUID REFERENCES issues(id),

    -- Lifecycle
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    -- AI metadata
    ai_recommended_rank     INTEGER,
    ai_rank_rationale       TEXT,
    ai_suggested_effort_type VARCHAR(15),

    -- RAG sync tracking (incremented each time the embedding must be refreshed)
    embedding_version       INTEGER NOT NULL DEFAULT 0,

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
    )),
    CONSTRAINT chk_ai_effort_type CHECK (ai_suggested_effort_type IS NULL
        OR ai_suggested_effort_type IN ('BUILD', 'MAINTAIN', 'COLLABORATE', 'LEARN'))
);

CREATE INDEX IF NOT EXISTS idx_issues_team_status
    ON issues (team_id, status);
CREATE INDEX IF NOT EXISTS idx_issues_assignee
    ON issues (org_id, assignee_user_id, status);
CREATE INDEX IF NOT EXISTS idx_issues_outcome
    ON issues (org_id, outcome_id);
CREATE INDEX IF NOT EXISTS idx_issues_org_key
    ON issues (org_id, issue_key);
CREATE INDEX IF NOT EXISTS idx_issues_embedding_version
    ON issues (embedding_version) WHERE archived_at IS NULL;

-- ─── Issue Activities ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS issue_activities (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    issue_id                UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    actor_user_id           UUID NOT NULL,
    activity_type           VARCHAR(30) NOT NULL,

    -- Polymorphic payload — only the relevant columns are populated per type
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

CREATE INDEX IF NOT EXISTS idx_activities_issue
    ON issue_activities (issue_id, created_at);
CREATE INDEX IF NOT EXISTS idx_activities_user
    ON issue_activities (org_id, actor_user_id, created_at);

-- ─── Weekly Assignments ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS weekly_assignments (
    id                      UUID PRIMARY KEY,
    org_id                  UUID NOT NULL,
    weekly_plan_id          UUID NOT NULL REFERENCES weekly_plans(id) ON DELETE CASCADE,
    issue_id                UUID NOT NULL REFERENCES issues(id),

    -- Per-week overrides
    chess_priority_override VARCHAR(10),
    expected_result         TEXT NOT NULL DEFAULT '',
    confidence              NUMERIC(3,2),

    -- RCDO snapshot (populated at plan lock time, same pattern as weekly_commits)
    snapshot_rally_cry_id   UUID,
    snapshot_rally_cry_name VARCHAR(500),
    snapshot_objective_id   UUID,
    snapshot_objective_name VARCHAR(500),
    snapshot_outcome_id     UUID,
    snapshot_outcome_name   VARCHAR(500),

    -- Tags for draft-source tracking (same as weekly_commits.tags)
    tags                    TEXT[] NOT NULL DEFAULT '{}',

    -- Crosswalk: back-reference to the legacy commit this assignment mirrors.
    -- Unique: each commit maps to at most one assignment.
    -- Null when the assignment was created directly (not migrated from a commit).
    legacy_commit_id        UUID REFERENCES weekly_commits(id),

    version                 INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_assignment_per_plan UNIQUE (weekly_plan_id, issue_id),
    CONSTRAINT uq_legacy_commit       UNIQUE (legacy_commit_id),
    CONSTRAINT chk_assignment_chess   CHECK (chess_priority_override IS NULL OR
        chess_priority_override IN ('KING','QUEEN','ROOK','BISHOP','KNIGHT','PAWN'))
);

CREATE INDEX IF NOT EXISTS idx_assignments_plan
    ON weekly_assignments (org_id, weekly_plan_id);
CREATE INDEX IF NOT EXISTS idx_assignments_issue
    ON weekly_assignments (issue_id);
CREATE INDEX IF NOT EXISTS idx_assignments_legacy_commit
    ON weekly_assignments (legacy_commit_id) WHERE legacy_commit_id IS NOT NULL;

-- ─── Weekly Assignment Actuals ───────────────────────────────

CREATE TABLE IF NOT EXISTS weekly_assignment_actuals (
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

-- ─── Additive ALTER on weekly_commits ───────────────────────
--
-- Reverse crosswalk: when a commit is created via dual-write alongside an
-- issue, source_issue_id stores the issue's ID so the new model can locate
-- the canonical Issue record for any commit.  Old code ignores this column
-- (PRD §13.8 backward-compatible rule).

ALTER TABLE weekly_commits
    ADD COLUMN IF NOT EXISTS source_issue_id UUID REFERENCES issues(id);

CREATE INDEX IF NOT EXISTS idx_commits_source_issue
    ON weekly_commits (source_issue_id) WHERE source_issue_id IS NOT NULL;

-- ─── Row-Level Security ──────────────────────────────────────
--
-- Every new table gets RLS enabled and an org_isolation policy using the
-- same pattern as V1 (NULLIF current_setting).

ALTER TABLE teams ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_access_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE issues ENABLE ROW LEVEL SECURITY;
ALTER TABLE issue_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE weekly_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE weekly_assignment_actuals ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_teams ON teams
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_team_members ON team_members
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_team_access_requests ON team_access_requests
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_issues ON issues
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_issue_activities ON issue_activities
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_weekly_assignments ON weekly_assignments
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_weekly_assignment_actuals ON weekly_assignment_actuals
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
