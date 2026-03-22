-- V18: Assignment-model materialized views (Phase 6 – additive only)
--
-- These views read EXCLUSIVELY from weekly_assignments / issues /
-- weekly_assignment_actuals.  They are the _v2 counterparts to the V9
-- commit-based views (mv_outcome_coverage_weekly, mv_user_weekly_summary).
--
-- Double-counting prevention strategy (per PRD §13.8):
--   During the dual-write Phase A, every commit has a mirrored assignment
--   (linked via weekly_commits.source_issue_id / weekly_assignments.legacy_commit_id).
--   The old commit-based views continue to serve existing analytics surfaces;
--   these _v2 views serve the new assignment-based surfaces.
--   Neither set UNIONs across both tables, eliminating double-counting by design.
--
-- Phase B (future release) will drop the old views and rename _v2 → canonical.

-- ─── mv_outcome_coverage_weekly_v2 ─────────────────────────────────────────
--
-- Per-outcome, per-week assignment activity — structural counterpart to
-- mv_outcome_coverage_weekly, sourced from weekly_assignments JOIN issues.

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_outcome_coverage_weekly_v2 AS
SELECT
    wa.org_id,
    i.outcome_id,
    wp.week_start_date,
    COUNT(wa.id)                                                        AS commit_count,
    COUNT(DISTINCT wp.owner_user_id)                                    AS contributor_count,
    COUNT(wa.id) FILTER (
        WHERE COALESCE(wa.chess_priority_override, i.chess_priority) IN ('KING', 'QUEEN')
    )                                                                   AS high_priority_count
FROM weekly_assignments wa
JOIN issues i
    ON i.id      = wa.issue_id
    AND i.org_id = wa.org_id
JOIN weekly_plans wp
    ON wp.id      = wa.weekly_plan_id
    AND wp.org_id = wa.org_id
WHERE
    wp.state IN ('LOCKED', 'RECONCILING', 'RECONCILED', 'CARRY_FORWARD')
    AND i.outcome_id IS NOT NULL
GROUP BY
    wa.org_id,
    i.outcome_id,
    wp.week_start_date;

-- Required for REFRESH CONCURRENTLY.
CREATE UNIQUE INDEX IF NOT EXISTS uix_mv_outcome_coverage_weekly_v2
    ON mv_outcome_coverage_weekly_v2 (org_id, outcome_id, week_start_date);

CREATE INDEX IF NOT EXISTS idx_mv_outcome_coverage_weekly_v2_week
    ON mv_outcome_coverage_weekly_v2 (org_id, week_start_date);

-- ─── mv_user_weekly_summary_v2 ──────────────────────────────────────────────
--
-- Per-user, per-week assignment roll-up — structural counterpart to
-- mv_user_weekly_summary, sourced from weekly_assignments / weekly_assignment_actuals.
--
-- "carried_commits" semantics for the assignment model: an assignment is
-- considered "carried" when the same issue appeared in the immediately preceding
-- week's plan for the same owner AND was not completed (no DONE actual).

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_user_weekly_summary_v2 AS
SELECT
    wp.org_id,
    wp.owner_user_id,
    wp.week_start_date,
    wp.state,
    wp.lock_type,
    COUNT(wa.id)                                                              AS total_commits,
    COUNT(wa.id) FILTER (WHERE i.outcome_id IS NOT NULL)                     AS strategic_commits,
    COUNT(wa.id) FILTER (
        WHERE EXISTS (
            SELECT 1
            FROM weekly_assignments prev_wa
            JOIN weekly_plans prev_wp ON prev_wp.id = prev_wa.weekly_plan_id
            LEFT JOIN weekly_assignment_actuals prev_waa
                ON prev_waa.assignment_id = prev_wa.id
            WHERE prev_wa.issue_id        = wa.issue_id
              AND prev_wa.org_id          = wa.org_id
              AND prev_wp.owner_user_id   = wp.owner_user_id
              AND prev_wp.week_start_date = wp.week_start_date - INTERVAL '7 days'
              AND (prev_waa.completion_status IS NULL
                   OR prev_waa.completion_status != 'DONE')
        )
    )                                                                         AS carried_commits,
    AVG(wa.confidence)                                                        AS avg_confidence,
    COUNT(waa.assignment_id) FILTER (WHERE waa.completion_status = 'DONE')   AS done_count,
    CASE WHEN wp.state = 'RECONCILED' THEN 1 ELSE 0 END                      AS reconciled_count
FROM weekly_plans wp
LEFT JOIN weekly_assignments wa
    ON wa.weekly_plan_id = wp.id
    AND wa.org_id        = wp.org_id
LEFT JOIN issues i
    ON i.id      = wa.issue_id
    AND i.org_id = wa.org_id
LEFT JOIN weekly_assignment_actuals waa
    ON waa.assignment_id = wa.id
GROUP BY
    wp.org_id,
    wp.owner_user_id,
    wp.week_start_date,
    wp.state,
    wp.lock_type;

-- Required for REFRESH CONCURRENTLY.
CREATE UNIQUE INDEX IF NOT EXISTS uix_mv_user_weekly_summary_v2
    ON mv_user_weekly_summary_v2 (org_id, owner_user_id, week_start_date);

CREATE INDEX IF NOT EXISTS idx_mv_user_summary_v2_week
    ON mv_user_weekly_summary_v2 (org_id, week_start_date);

CREATE INDEX IF NOT EXISTS idx_mv_user_summary_v2_user
    ON mv_user_weekly_summary_v2 (org_id, owner_user_id);

-- ─── mv_team_backlog_health ──────────────────────────────────────────────────
--
-- Per-team backlog health snapshot — open issue count, average age, blocked
-- count, effort-type distribution, and average cycle time for completed issues.
-- Used by the Team Dashboard "Backlog Health" section and the
-- TeamBacklogHealthProvider service.
--
-- NOTE: avg_issue_age_days is computed relative to NOW() at refresh time.
-- The value will be correct to within one refresh interval (15 min).

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_team_backlog_health AS
WITH open_issues AS (
    SELECT
        i.org_id,
        i.team_id,
        i.id,
        i.created_at,
        i.blocked_by_issue_id,
        i.effort_type
    FROM issues i
    WHERE i.status IN ('OPEN', 'IN_PROGRESS')
      AND i.archived_at IS NULL
),
cycle_times AS (
    -- Average days from issue creation to DONE completion for closed issues.
    SELECT
        i.org_id,
        i.team_id,
        AVG(EXTRACT(EPOCH FROM (waa.updated_at - i.created_at)) / 86400.0) AS avg_cycle_time_days
    FROM issues i
    JOIN weekly_assignments wa ON wa.issue_id = i.id
    JOIN weekly_assignment_actuals waa
        ON waa.assignment_id       = wa.id
        AND waa.completion_status  = 'DONE'
    WHERE i.status       = 'DONE'
      AND i.archived_at IS NULL
    GROUP BY i.org_id, i.team_id
)
SELECT
    oi.org_id,
    oi.team_id,
    COUNT(oi.id)                                                              AS open_issue_count,
    COALESCE(
        AVG(EXTRACT(EPOCH FROM (NOW() - oi.created_at)) / 86400.0),
        0
    )                                                                         AS avg_issue_age_days,
    COUNT(oi.id) FILTER (WHERE oi.blocked_by_issue_id IS NOT NULL)           AS blocked_count,
    COUNT(oi.id) FILTER (WHERE oi.effort_type = 'BUILD')                     AS build_count,
    COUNT(oi.id) FILTER (WHERE oi.effort_type = 'MAINTAIN')                  AS maintain_count,
    COUNT(oi.id) FILTER (WHERE oi.effort_type = 'COLLABORATE')               AS collaborate_count,
    COUNT(oi.id) FILTER (WHERE oi.effort_type = 'LEARN')                     AS learn_count,
    COALESCE(ct.avg_cycle_time_days, 0)                                       AS avg_cycle_time_days
FROM open_issues oi
LEFT JOIN cycle_times ct
    ON ct.org_id   = oi.org_id
    AND ct.team_id = oi.team_id
GROUP BY
    oi.org_id,
    oi.team_id,
    ct.avg_cycle_time_days;

-- Required for REFRESH CONCURRENTLY.
CREATE UNIQUE INDEX IF NOT EXISTS uix_mv_team_backlog_health
    ON mv_team_backlog_health (org_id, team_id);
