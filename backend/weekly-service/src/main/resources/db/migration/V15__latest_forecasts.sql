-- Phase 5 foundation: persisted latest target-date forecasts.
-- Shared by the forecasting job, outcome APIs, and executive rollups.

CREATE TABLE IF NOT EXISTS latest_forecasts (
    org_id                 UUID                     NOT NULL,
    outcome_id             UUID                     NOT NULL,
    projected_target_date  DATE,
    projected_progress_pct NUMERIC(5,2),
    projected_velocity     NUMERIC(8,4),
    confidence_score       NUMERIC(5,4)            NOT NULL DEFAULT 0,
    forecast_status        VARCHAR(32)              NOT NULL DEFAULT 'NO_DATA',
    model_version          VARCHAR(64)              NOT NULL DEFAULT 'phase5-foundation',
    forecast_inputs_json   JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    forecast_details_json  JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    computed_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (org_id, outcome_id)
);

CREATE INDEX IF NOT EXISTS idx_latest_forecasts_org_status
    ON latest_forecasts (org_id, forecast_status);

ALTER TABLE latest_forecasts ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_latest_forecasts ON latest_forecasts
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
