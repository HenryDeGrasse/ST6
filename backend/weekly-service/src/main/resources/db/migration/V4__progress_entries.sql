-- Wave 2: Quick Daily Check-In — progress entries table
-- Stores structured micro-updates (check-ins) for a weekly commit.
-- Append-only: no UPDATE or DELETE allowed (enforced by application logic).

CREATE TABLE IF NOT EXISTS progress_entries (
    id          UUID PRIMARY KEY,
    org_id      UUID NOT NULL,
    commit_id   UUID NOT NULL REFERENCES weekly_commits(id) ON DELETE CASCADE,
    status      VARCHAR(15) NOT NULL,
    note        TEXT NOT NULL DEFAULT '',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_progress_status CHECK (status IN ('ON_TRACK', 'AT_RISK', 'BLOCKED', 'DONE_EARLY'))
);

CREATE INDEX idx_progress_entries_lookup
    ON progress_entries (org_id, commit_id, created_at);

ALTER TABLE progress_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_progress ON progress_entries
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
