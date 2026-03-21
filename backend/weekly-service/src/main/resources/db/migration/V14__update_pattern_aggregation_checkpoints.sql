-- Persistent checkpoints for nightly update-pattern aggregation.
-- Prevents overlapping lookback windows from being re-counted after worker restarts.

CREATE TABLE IF NOT EXISTS update_pattern_aggregation_checkpoints (
    org_id              UUID PRIMARY KEY,
    last_aggregated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE update_pattern_aggregation_checkpoints ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_update_pattern_checkpoints ON update_pattern_aggregation_checkpoints
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
