-- Wave 3: Foundational user model — user model snapshots table
-- Stores computed user model snapshots for personalized AI behaviour.

CREATE TABLE IF NOT EXISTS user_model_snapshots (
    org_id          UUID NOT NULL,
    user_id         UUID NOT NULL,
    computed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    weeks_analyzed  INTEGER NOT NULL,
    model_json      JSONB NOT NULL,
    PRIMARY KEY (org_id, user_id)
);

ALTER TABLE user_model_snapshots ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_user_model ON user_model_snapshots
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
