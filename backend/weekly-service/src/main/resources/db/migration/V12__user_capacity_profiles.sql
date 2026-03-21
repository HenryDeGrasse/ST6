-- Phase 4: Capacity Planning — per-user capacity profiles
-- Stores the computed capacity profile for each user within an org,
-- updated weekly by the CapacityComputeJob scheduled task.
-- RLS follows the same org_isolation pattern established in V1__initial_schema.sql.

CREATE TABLE IF NOT EXISTS user_capacity_profiles (
    org_id                      UUID NOT NULL,
    user_id                     UUID NOT NULL,
    weeks_analyzed              INTEGER NOT NULL,
    avg_estimated_hours         NUMERIC(5,1),
    avg_actual_hours            NUMERIC(5,1),
    estimation_bias             NUMERIC(4,2),
    realistic_weekly_cap        NUMERIC(5,1),
    category_bias_json          JSONB,
    priority_completion_json    JSONB,
    confidence_level            VARCHAR(10),
    computed_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (org_id, user_id)
);

ALTER TABLE user_capacity_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_capacity_profiles ON user_capacity_profiles
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
