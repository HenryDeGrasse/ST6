-- ---------------------------------------------------------------------------
-- Seed data for local development (PRD §12.9)
--
-- Three personas for demo:
--   Alice Chen  (IC)      — current week DRAFT with validation issues
--   Bob Martinez (IC)     — current week RECONCILED, review pending
--   Carol Park  (Manager) — current week LOCKED, manages Alice & Bob
--
-- Shared by: seed-local.sh, dev.sh (Docker fallback)
-- ---------------------------------------------------------------------------

-- ═══════════════════════════════════════════════════════════════════════════
-- Org policies
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO org_policies (org_id, chess_king_required, chess_max_king, chess_max_queen)
VALUES ('a0000000-0000-0000-0000-000000000001'::uuid, true, 1, 2)
ON CONFLICT (org_id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- ALICE CHEN (IC) — Current week: DRAFT with 4 commits, 1 missing RCDO
-- User ID: c0000000-0000-0000-0000-000000000010
-- ═══════════════════════════════════════════════════════════════════════════

-- Current week DRAFT plan
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, version)
VALUES (
    'b0000000-0000-0000-0000-000000000010'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000010'::uuid,
    date_trunc('week', CURRENT_DATE)::date,
    'DRAFT',
    'REVIEW_NOT_APPLICABLE',
    1
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Alice's commits (current week)
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id, expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000010'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000010'::uuid,
    'Build onboarding flow for enterprise customers',
    'Design and implement the multi-step onboarding wizard with SSO integration.',
    'KING',
    'DELIVERY',
    'e0000000-0000-0000-0000-000000000001'::uuid,
    'Onboarding wizard deployed to staging with SSO working end-to-end.',
    1
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id, expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000011'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000010'::uuid,
    'Write integration tests for auth module',
    'Cover the JWT validation, role extraction, and org-scoping paths.',
    'ROOK',
    'DELIVERY',
    '30000000-0000-0000-0000-000000000008'::uuid,
    'Auth module at 90%+ branch coverage.',
    1
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id, expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000012'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000010'::uuid,
    'Prep tech talk on event sourcing patterns',
    'Research and draft slides for the Friday engineering tech talk.',
    'KNIGHT',
    'LEARNING',
    '30000000-0000-0000-0000-000000000009'::uuid,
    'Slide deck ready and dry-run completed.',
    1
) ON CONFLICT DO NOTHING;

-- This commit intentionally has NO outcome and NO non_strategic_reason → validation error
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, version)
VALUES (
    'd0000000-0000-0000-0000-000000000013'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000010'::uuid,
    'Update team wiki with new runbook',
    'Document the incident response process and add links to dashboards.',
    'PAWN',
    'OPERATIONS',
    1
) ON CONFLICT DO NOTHING;

-- Alice's previous week: RECONCILED + APPROVED
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version)
VALUES (
    'b0000000-0000-0000-0000-000000000011'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000010'::uuid,
    date_trunc('week', CURRENT_DATE)::date - 7,
    'RECONCILED',
    'APPROVED',
    'ON_TIME',
    NOW() - interval '7 days',
    3
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name, version)
VALUES (
    'd0000000-0000-0000-0000-000000000014'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000011'::uuid,
    'Design SSO integration spec',
    'KING', 'DELIVERY',
    'e0000000-0000-0000-0000-000000000001'::uuid,
    '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR',
    '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
    'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, created_at, updated_at)
VALUES (
    'd0000000-0000-0000-0000-000000000014'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'SSO spec completed and reviewed by security team. Ready for implementation.',
    'DONE', NOW() - interval '3 days', NOW() - interval '3 days'
) ON CONFLICT (commit_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- BOB MARTINEZ (IC) — Current week: RECONCILED, awaiting manager review
-- User ID: c0000000-0000-0000-0000-000000000020
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version)
VALUES (
    'b0000000-0000-0000-0000-000000000020'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000020'::uuid,
    date_trunc('week', CURRENT_DATE)::date,
    'RECONCILED',
    'REVIEW_PENDING',
    'ON_TIME',
    NOW() - interval '4 days',
    4
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Bob's commits (current week, with snapshots since plan was locked)
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name,
    expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000020'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000020'::uuid,
    'Launch enterprise demo environment',
    'Deploy the multi-tenant demo instance with sample data and SSO bypass for sales.',
    'KING', 'DELIVERY',
    '30000000-0000-0000-0000-000000000002'::uuid,
    '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR',
    '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
    '30000000-0000-0000-0000-000000000002'::uuid, 'Launch enterprise demo environment',
    'Demo env live with 3 sample orgs and working SSO bypass.',
    3
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, created_at, updated_at)
VALUES (
    'd0000000-0000-0000-0000-000000000020'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Demo environment deployed to demo.internal with 3 sample orgs. SSO bypass works. Handed off to sales team.',
    'DONE', NOW() - interval '1 day', NOW() - interval '1 day'
) ON CONFLICT (commit_id) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name,
    expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000021'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000020'::uuid,
    'Reduce API p95 latency below 200ms',
    'Profile slow endpoints, optimize N+1 queries, add Redis caching for hot paths.',
    'QUEEN', 'DELIVERY',
    '30000000-0000-0000-0000-000000000007'::uuid,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
    '30000000-0000-0000-0000-000000000007'::uuid, 'Reduce deploy-to-production time to < 15 min',
    'p95 latency < 200ms on all critical endpoints.',
    3
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, delta_reason, created_at, updated_at)
VALUES (
    'd0000000-0000-0000-0000-000000000021'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Optimized 3 of 5 slow endpoints. Dashboard endpoint still at 280ms due to complex aggregation query.',
    'PARTIALLY',
    'Dashboard aggregation query requires a materialized view — scheduled for next week.',
    NOW() - interval '1 day', NOW() - interval '1 day'
) ON CONFLICT (commit_id) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name,
    expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000022'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000020'::uuid,
    'Customer health-score alerting MVP',
    'Build the initial alerting pipeline that emails CSMs when health score drops below threshold.',
    'QUEEN', 'CUSTOMER',
    '30000000-0000-0000-0000-000000000011'::uuid,
    '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession',
    '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
    '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting',
    'Alerting pipeline deployed and sending test emails.',
    3
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, delta_reason, created_at, updated_at)
VALUES (
    'd0000000-0000-0000-0000-000000000022'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Blocked on access to the health-score API. Waiting on platform team for credentials.',
    'NOT_DONE',
    'External dependency — platform team credential provisioning took longer than expected. Carrying forward.',
    NOW() - interval '1 day', NOW() - interval '1 day'
) ON CONFLICT (commit_id) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category,
    non_strategic_reason, expected_result, version,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name)
VALUES (
    'd0000000-0000-0000-0000-000000000023'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000020'::uuid,
    'Upgrade CI runner fleet to ARM',
    'Migrate GitHub Actions runners from x86 to ARM for cost savings.',
    'PAWN', 'OPERATIONS',
    'Infrastructure cost optimization — not tied to a strategic outcome',
    'CI runners migrated and build times validated.',
    3,
    NULL, NULL, NULL, NULL, NULL, NULL
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, created_at, updated_at)
VALUES (
    'd0000000-0000-0000-0000-000000000023'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'All runners migrated. Build times 15% faster. Monthly savings ~$200.',
    'DONE', NOW() - interval '1 day', NOW() - interval '1 day'
) ON CONFLICT (commit_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- CAROL PARK (Manager + IC) — Current week: LOCKED (her own plan)
-- User ID: c0000000-0000-0000-0000-000000000001  (the original dev user)
-- She manages Alice and Bob
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version)
VALUES (
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000001'::uuid,
    date_trunc('week', CURRENT_DATE)::date,
    'LOCKED',
    'REVIEW_NOT_APPLICABLE',
    'ON_TIME',
    NOW() - interval '3 days',
    2
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name,
    expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'Finalize Q2 OKR alignment with leadership',
    'Work with VP Eng to ensure Rally Cries map to board-level priorities.',
    'KING', 'PEOPLE',
    'e0000000-0000-0000-0000-000000000002'::uuid,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
    'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime',
    'Q2 OKRs finalized and communicated to team.',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name,
    expected_result, version)
VALUES (
    'd0000000-0000-0000-0000-000000000002'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'Review and approve team reconciliations',
    'Review Bob and Alice weekly reconciliations, provide actionable feedback.',
    'QUEEN', 'PEOPLE',
    '30000000-0000-0000-0000-000000000009'::uuid,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
    '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk',
    'All pending reviews completed with written feedback.',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category,
    non_strategic_reason, expected_result, version,
    snapshot_rally_cry_id, snapshot_rally_cry_name,
    snapshot_objective_id, snapshot_objective_name,
    snapshot_outcome_id, snapshot_outcome_name)
VALUES (
    'd0000000-0000-0000-0000-000000000003'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000001'::uuid,
    'Expense reports and vendor contract renewals',
    'Monthly administrative overhead — process Q1 expense reports and review 2 vendor contracts.',
    'PAWN', 'OPERATIONS',
    'Routine administrative task — not tied to strategic outcomes',
    'Expense reports submitted, contracts reviewed.',
    2,
    NULL, NULL, NULL, NULL, NULL, NULL
) ON CONFLICT DO NOTHING;
