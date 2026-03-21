-- Phase 3: Outcome Urgency — outcome_metadata table
-- Stores target-date, progress tracking, and computed urgency band for each
-- RCDO outcome. One row per (org_id, outcome_id); recomputed on a schedule
-- by UrgencyComputeJob.
-- RLS follows the same org_isolation pattern established in V1__initial_schema.sql.

-- ─── outcome_metadata ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS outcome_metadata (
    org_id                    UUID NOT NULL,
    outcome_id                UUID NOT NULL,

    -- Target-date tracking
    target_date               DATE,

    -- Progress configuration
    progress_type             VARCHAR(20)              NOT NULL DEFAULT 'ACTIVITY',
    metric_name               VARCHAR(200),
    target_value              NUMERIC,
    current_value             NUMERIC,
    unit                      VARCHAR(50),
    milestones                JSONB,

    -- Computed progress and urgency (updated by UrgencyComputeJob)
    progress_pct              NUMERIC(5,2),
    urgency_band              VARCHAR(20),
    last_computed_at          TIMESTAMP WITH TIME ZONE,

    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (org_id, outcome_id),

    CONSTRAINT chk_outcome_metadata_progress_type
        CHECK (progress_type IN ('ACTIVITY', 'METRIC', 'MILESTONE')),
    CONSTRAINT chk_outcome_metadata_urgency_band
        CHECK (urgency_band IS NULL OR urgency_band IN ('ON_TRACK', 'NEEDS_ATTENTION', 'AT_RISK', 'CRITICAL', 'NO_TARGET'))
);

-- Dashboard query: fetch outcomes by urgency band for a given org
CREATE INDEX idx_outcome_metadata_org_urgency ON outcome_metadata (org_id, urgency_band);

-- ─── Row-Level Security ──────────────────────────────────────────────────────

ALTER TABLE outcome_metadata ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_outcome_metadata ON outcome_metadata
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
