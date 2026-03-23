-- V19: Daily planning-copilot snapshot persistence
-- One snapshot per org + manager + week + calendar day.
-- Avoids redundant AI generation; managers can force-regenerate on demand.

CREATE TABLE IF NOT EXISTS planning_copilot_snapshots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            UUID        NOT NULL,
    manager_user_id   UUID        NOT NULL,
    week_start        DATE        NOT NULL,
    snapshot_date     DATE        NOT NULL DEFAULT CURRENT_DATE,
    payload_json      JSONB       NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique constraint: one snapshot per manager per week per day
CREATE UNIQUE INDEX IF NOT EXISTS idx_copilot_snapshot_unique
    ON planning_copilot_snapshots (org_id, manager_user_id, week_start, snapshot_date);

-- Fast lookup by org + manager + week (most recent day first)
CREATE INDEX IF NOT EXISTS idx_copilot_snapshot_lookup
    ON planning_copilot_snapshots (org_id, manager_user_id, week_start, snapshot_date DESC);
