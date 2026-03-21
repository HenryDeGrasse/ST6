-- V9: Materialized views for multi-week descriptive & diagnostic analytics
--
-- These views are pre-aggregated snapshots refreshed on a schedule by
-- MaterializedViewRefreshJob. Analytics queries filter by org_id explicitly
-- (no RLS — materialized views do not support row-level security).
--
-- REFRESH CONCURRENTLY requires a UNIQUE index on each view.

-- ─── mv_outcome_coverage_weekly ─────────────────────────────────────────────
--
-- Per-outcome, per-week commit activity — used by OutcomeCoverageTimeline.
-- Covers only locked/in-progress/completed plans so draft noise is excluded.
-- Rows are limited to commits that carry an outcome_id (strategic commits).

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_outcome_coverage_weekly AS
SELECT
    wc.org_id,
    wc.outcome_id,
    wp.week_start_date,
    COUNT(wc.id)                                                        AS commit_count,
    COUNT(DISTINCT wp.owner_user_id)                                    AS contributor_count,
    COUNT(wc.id) FILTER (WHERE wc.chess_priority IN ('KING', 'QUEEN'))  AS high_priority_count
FROM weekly_commits wc
JOIN weekly_plans wp
    ON wp.id      = wc.weekly_plan_id
    AND wp.org_id = wc.org_id
WHERE
    -- Only non-draft plans so analytics reflect committed work
    wp.state IN ('LOCKED', 'RECONCILING', 'RECONCILED', 'CARRY_FORWARD')
    -- Only strategic commits tied to an outcome
    AND wc.outcome_id IS NOT NULL
GROUP BY
    wc.org_id,
    wc.outcome_id,
    wp.week_start_date;

-- Required for REFRESH CONCURRENTLY; grouping guarantee: one plan per
-- (org_id, owner_user_id, week_start_date), outcome_id is non-null.
CREATE UNIQUE INDEX IF NOT EXISTS uix_mv_outcome_coverage_weekly
    ON mv_outcome_coverage_weekly (org_id, outcome_id, week_start_date);

-- Supporting index for range scans by week
CREATE INDEX IF NOT EXISTS idx_mv_outcome_coverage_week
    ON mv_outcome_coverage_weekly (org_id, week_start_date);

-- ─── mv_user_weekly_summary ──────────────────────────────────────────────────
--
-- Per-user, per-week commit & reconciliation roll-up — used by
-- CarryForwardHeatmap and user-level diagnostic queries.
-- Includes all plan states so managers can see DRAFT activity too.

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_user_weekly_summary AS
SELECT
    wp.org_id,
    wp.owner_user_id,
    wp.week_start_date,
    wp.state,
    wp.lock_type,
    -- Commit volume
    COUNT(wc.id)                                                              AS total_commits,
    COUNT(wc.id) FILTER (WHERE wc.outcome_id IS NOT NULL)                    AS strategic_commits,
    COUNT(wc.id) FILTER (WHERE wc.carried_from_commit_id IS NOT NULL)        AS carried_commits,
    -- Quality signal
    AVG(wc.confidence)                                                        AS avg_confidence,
    -- Reconciliation outcomes (from actuals written at end-of-week)
    COUNT(wca.commit_id) FILTER (WHERE wca.completion_status = 'DONE')       AS done_count,
    -- Plan-level reconciliation flag expressed as 0/1 for sum-aggregation
    CASE WHEN wp.state = 'RECONCILED' THEN 1 ELSE 0 END                      AS reconciled_count
FROM weekly_plans wp
LEFT JOIN weekly_commits wc
    ON wc.weekly_plan_id = wp.id
    AND wc.org_id        = wp.org_id
LEFT JOIN weekly_commit_actuals wca
    ON wca.commit_id = wc.id
    AND wca.org_id   = wp.org_id
GROUP BY
    wp.org_id,
    wp.owner_user_id,
    wp.week_start_date,
    wp.state,
    wp.lock_type;

-- Required for REFRESH CONCURRENTLY.
-- Safe because weekly_plans enforces UNIQUE (org_id, owner_user_id, week_start_date),
-- so the (state, lock_type) grouping columns cannot produce duplicate rows for
-- the same (org_id, owner_user_id, week_start_date) tuple.
CREATE UNIQUE INDEX IF NOT EXISTS uix_mv_user_weekly_summary
    ON mv_user_weekly_summary (org_id, owner_user_id, week_start_date);

-- Supporting indexes for common access patterns
CREATE INDEX IF NOT EXISTS idx_mv_user_summary_week
    ON mv_user_weekly_summary (org_id, week_start_date);
CREATE INDEX IF NOT EXISTS idx_mv_user_summary_user
    ON mv_user_weekly_summary (org_id, owner_user_id);
