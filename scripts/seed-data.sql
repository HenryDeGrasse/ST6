-- ---------------------------------------------------------------------------
-- Seed data for local development (PRD §12.9)
-- Creates test org policies, sample plans, commits, and actuals.
--
-- Shared by: seed-local.sh, dev.sh (Docker fallback)
-- ---------------------------------------------------------------------------

-- Org policies (default settings)
INSERT INTO org_policies (org_id, chess_king_required, chess_max_king, chess_max_queen)
VALUES ('a0000000-0000-0000-0000-000000000001'::uuid, true, 1, 2)
ON CONFLICT (org_id) DO NOTHING;

-- Sample plan in DRAFT state
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, version)
VALUES (
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000001'::uuid,
    date_trunc('week', CURRENT_DATE)::date,
    'DRAFT',
    'REVIEW_NOT_APPLICABLE',
    1
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Sample commits for the draft plan
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, version)
VALUES (
    'd0000000-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'Ship auth integration',
    'KING',
    'DELIVERY',
    'e0000000-0000-0000-0000-000000000001'::uuid,
    1
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, version)
VALUES (
    'd0000000-0000-0000-0000-000000000002'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'Review team OKRs',
    'QUEEN',
    'PEOPLE',
    'e0000000-0000-0000-0000-000000000002'::uuid,
    1
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, non_strategic_reason, version)
VALUES (
    'd0000000-0000-0000-0000-000000000003'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'Office supplies order',
    'PAWN',
    'OPERATIONS',
    'Routine administrative task',
    1
) ON CONFLICT DO NOTHING;

-- Previous week's reconciled plan for easier local browsing/demo
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version)
VALUES (
    'b0000000-0000-0000-0000-000000000002'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000001'::uuid,
    date_trunc('week', CURRENT_DATE)::date - 7,
    'RECONCILED',
    'APPROVED',
    'ON_TIME',
    NOW() - interval '7 days',
    3
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name,
    version
)
VALUES (
    'd0000000-0000-0000-0000-000000000004'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000002'::uuid,
    'Retrospective and carry-forward planning',
    'KING',
    'PEOPLE',
    'e0000000-0000-0000-0000-000000000002'::uuid,
    '10000000-0000-0000-0000-000000000002'::uuid,
    'World-class engineering culture',
    '20000000-0000-0000-0000-000000000003'::uuid,
    'Ship reliable software faster',
    'e0000000-0000-0000-0000-000000000002'::uuid,
    'Achieve 99.9% API uptime',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, created_at, updated_at)
VALUES (
    'd0000000-0000-0000-0000-000000000004'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Completed retrospective and documented next steps',
    'DONE',
    NOW() - interval '7 days',
    NOW() - interval '7 days'
) ON CONFLICT (commit_id) DO NOTHING;
