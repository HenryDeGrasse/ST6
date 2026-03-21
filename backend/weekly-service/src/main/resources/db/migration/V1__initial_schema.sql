-- Weekly Commitments – complete initial schema
-- All tables use org_id for row-level tenant isolation.

-- ─── Weekly Plans ───────────────────────────────────────────

CREATE TABLE IF NOT EXISTS weekly_plans (
    id                        UUID PRIMARY KEY,
    org_id                    UUID NOT NULL,
    owner_user_id             UUID NOT NULL,
    week_start_date           DATE NOT NULL,
    state                     VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    review_status             VARCHAR(30) NOT NULL DEFAULT 'REVIEW_NOT_APPLICABLE',
    lock_type                 VARCHAR(15),
    locked_at                 TIMESTAMP WITH TIME ZONE,
    carry_forward_executed_at TIMESTAMP WITH TIME ZONE,
    version                   INTEGER NOT NULL DEFAULT 1,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_plan_per_user_week UNIQUE (org_id, owner_user_id, week_start_date),
    CONSTRAINT chk_plan_state CHECK (state IN ('DRAFT','LOCKED','RECONCILING','RECONCILED','CARRY_FORWARD')),
    CONSTRAINT chk_review_status CHECK (review_status IN ('REVIEW_NOT_APPLICABLE','REVIEW_PENDING','CHANGES_REQUESTED','APPROVED')),
    CONSTRAINT chk_lock_type CHECK (lock_type IS NULL OR lock_type IN ('ON_TIME','LATE_LOCK'))
);

CREATE INDEX idx_plans_org_week_state ON weekly_plans (org_id, week_start_date, state);

-- ─── Weekly Commits ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS weekly_commits (
    id                        UUID PRIMARY KEY,
    org_id                    UUID NOT NULL,
    weekly_plan_id            UUID NOT NULL REFERENCES weekly_plans(id) ON DELETE CASCADE,
    title                     VARCHAR(500) NOT NULL,
    description               TEXT NOT NULL DEFAULT '',
    chess_priority            VARCHAR(10),
    category                  VARCHAR(20),
    outcome_id                UUID,
    non_strategic_reason      TEXT,
    expected_result           TEXT NOT NULL DEFAULT '',
    confidence                NUMERIC(3,2),
    tags                      TEXT[] NOT NULL DEFAULT '{}',
    progress_notes            TEXT NOT NULL DEFAULT '',

    -- RCDO snapshot (populated at lock time)
    snapshot_rally_cry_id     UUID,
    snapshot_rally_cry_name   VARCHAR(500),
    snapshot_objective_id     UUID,
    snapshot_objective_name   VARCHAR(500),
    snapshot_outcome_id       UUID,
    snapshot_outcome_name     VARCHAR(500),

    -- Carry-forward lineage
    carried_from_commit_id    UUID,

    version                   INTEGER NOT NULL DEFAULT 1,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_chess_priority CHECK (chess_priority IS NULL OR chess_priority IN ('KING','QUEEN','ROOK','BISHOP','KNIGHT','PAWN')),
    CONSTRAINT chk_category CHECK (category IS NULL OR category IN ('DELIVERY','OPERATIONS','CUSTOMER','GTM','PEOPLE','LEARNING','TECH_DEBT'))
);

CREATE INDEX idx_commits_plan ON weekly_commits (org_id, weekly_plan_id);
CREATE INDEX idx_commits_outcome ON weekly_commits (org_id, outcome_id);

-- ─── Weekly Commit Actuals ──────────────────────────────────

CREATE TABLE IF NOT EXISTS weekly_commit_actuals (
    commit_id                 UUID PRIMARY KEY REFERENCES weekly_commits(id) ON DELETE CASCADE,
    org_id                    UUID NOT NULL,
    actual_result             TEXT NOT NULL DEFAULT '',
    completion_status         VARCHAR(15) NOT NULL,
    delta_reason              TEXT,
    time_spent                INTEGER,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_completion_status CHECK (completion_status IN ('DONE','PARTIALLY','NOT_DONE','DROPPED'))
);

-- ─── Manager Reviews ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS manager_reviews (
    id                        UUID PRIMARY KEY,
    org_id                    UUID NOT NULL,
    weekly_plan_id            UUID NOT NULL REFERENCES weekly_plans(id) ON DELETE CASCADE,
    reviewer_user_id          UUID NOT NULL,
    decision                  VARCHAR(20) NOT NULL,
    comments                  TEXT NOT NULL DEFAULT '',
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_review_decision CHECK (decision IN ('APPROVED','CHANGES_REQUESTED'))
);

CREATE INDEX idx_reviews_plan ON manager_reviews (org_id, weekly_plan_id);

-- ─── Audit Events (append-only) ─────────────────────────────

CREATE TABLE IF NOT EXISTS audit_events (
    id                        UUID PRIMARY KEY,
    org_id                    UUID NOT NULL,
    actor_user_id             UUID NOT NULL,
    action                    VARCHAR(100) NOT NULL,
    aggregate_type            VARCHAR(50) NOT NULL,
    aggregate_id              UUID NOT NULL,
    previous_state            VARCHAR(50),
    new_state                 VARCHAR(50),
    reason                    TEXT,
    ip_address                VARCHAR(45),
    correlation_id            VARCHAR(100),
    hash                      VARCHAR(128),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_org_aggregate ON audit_events (org_id, aggregate_type, aggregate_id);
CREATE INDEX idx_audit_org_created ON audit_events (org_id, created_at);

-- ─── Outbox Events (transactional outbox) ───────────────────

CREATE TABLE IF NOT EXISTS outbox_events (
    event_id                  UUID PRIMARY KEY,
    event_type                VARCHAR(100) NOT NULL,
    aggregate_type            VARCHAR(50) NOT NULL,
    aggregate_id              UUID NOT NULL,
    org_id                    UUID NOT NULL,
    payload                   JSONB NOT NULL,
    schema_version            INTEGER NOT NULL DEFAULT 1,
    occurred_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at              TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published_at) WHERE published_at IS NULL;

-- ─── Idempotency Keys ───────────────────────────────────────

CREATE TABLE IF NOT EXISTS idempotency_keys (
    org_id                    UUID NOT NULL,
    idempotency_key           UUID NOT NULL,
    user_id                   UUID NOT NULL,
    endpoint                  VARCHAR(200) NOT NULL,
    request_hash              VARCHAR(64) NOT NULL,
    response_status           INTEGER NOT NULL,
    response_body             JSONB NOT NULL,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (org_id, idempotency_key)
);

-- ─── Notifications (in-app MVP) ─────────────────────────────

CREATE TABLE IF NOT EXISTS notifications (
    id                        UUID PRIMARY KEY,
    org_id                    UUID NOT NULL,
    user_id                   UUID NOT NULL,
    type                      VARCHAR(50) NOT NULL,
    payload                   JSONB NOT NULL DEFAULT '{}',
    read_at                   TIMESTAMP WITH TIME ZONE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_unread ON notifications (org_id, user_id, read_at);

-- ─── Org Policies (configurable defaults) ───────────────────

CREATE TABLE IF NOT EXISTS org_policies (
    org_id                    UUID PRIMARY KEY,
    chess_king_required       BOOLEAN NOT NULL DEFAULT TRUE,
    chess_max_king            INTEGER NOT NULL DEFAULT 1,
    chess_max_queen           INTEGER NOT NULL DEFAULT 2,
    lock_day                  VARCHAR(10) NOT NULL DEFAULT 'MONDAY',
    lock_time                 VARCHAR(5) NOT NULL DEFAULT '10:00',
    reconcile_day             VARCHAR(10) NOT NULL DEFAULT 'FRIDAY',
    reconcile_time            VARCHAR(5) NOT NULL DEFAULT '16:00',
    block_lock_on_stale_rcdo  BOOLEAN NOT NULL DEFAULT TRUE,
    rcdo_staleness_threshold_minutes INTEGER NOT NULL DEFAULT 60,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ─── Row-Level Security ─────────────────────────────────────
-- RLS policies enforce org-scoped tenant isolation at the DB level.
-- The application sets:  SET LOCAL app.current_org_id = :orgId;
-- at the start of each transaction.

ALTER TABLE weekly_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE weekly_commits ENABLE ROW LEVEL SECURITY;
ALTER TABLE weekly_commit_actuals ENABLE ROW LEVEL SECURITY;
ALTER TABLE manager_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_policies ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_plans ON weekly_plans
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_commits ON weekly_commits
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_actuals ON weekly_commit_actuals
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_reviews ON manager_reviews
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_audit ON audit_events
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_outbox ON outbox_events
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_idempotency ON idempotency_keys
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_notifications ON notifications
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);

CREATE POLICY org_isolation_policies ON org_policies
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
