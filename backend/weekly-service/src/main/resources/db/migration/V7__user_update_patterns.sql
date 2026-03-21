-- Wave 3: Rapid-fire check-in update flow — user update patterns table
-- Tracks recurring note patterns per user and category for AI suggestion seeding.

CREATE TABLE IF NOT EXISTS user_update_patterns (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL,
    user_id         UUID NOT NULL,
    category        VARCHAR(20),
    note_text       TEXT NOT NULL,
    frequency       INTEGER NOT NULL DEFAULT 1,
    last_used_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_update_patterns_user
    ON user_update_patterns (org_id, user_id, category);

ALTER TABLE user_update_patterns ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_update_patterns ON user_update_patterns
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
