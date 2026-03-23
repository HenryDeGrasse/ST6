-- ---------------------------------------------------------------------------
-- Seed data for local development (PRD §12.9)
--
-- Four personas for demo:
--   Alice Chen   (IC)                  — current week DRAFT with validation issues
--   Bob Martinez (IC)                  — current week RECONCILED, review pending
--   Carol Park   (Manager)             — current week LOCKED, manages Alice & Bob
--   Dana Torres  (Admin + Manager + IC) — 4 weeks of reconciled history for insights/capacity
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


-- ═══════════════════════════════════════════════════════════════════════════
-- DANA TORRES (Admin + Manager + IC) — 4 weeks history (W-4..W-1), RECONCILED/APPROVED
-- User ID: c0000000-0000-0000-0000-000000000030
-- PEOPLE & OPERATIONS focus, very high completion rate, IMPROVING strategic alignment
-- ═══════════════════════════════════════════════════════════════════════════

-- Dana W-4 through W-1 (all RECONCILED/APPROVED)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at) VALUES
  ('b4000000-0000-0000-0000-d00000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 28, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '31 days', 3, NOW() - interval '32 days', NOW() - interval '25 days'),
  ('b4000000-0000-0000-0000-d00000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 21, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '24 days', 3, NOW() - interval '25 days', NOW() - interval '18 days'),
  ('b4000000-0000-0000-0000-d00000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 14, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '17 days', 3, NOW() - interval '18 days', NOW() - interval '11 days'),
  ('b4000000-0000-0000-0000-d00000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 7,  'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '10 days', 3, NOW() - interval '11 days', NOW() - interval '4 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Dana W-4 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-d040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000004'::uuid,
   'SOC2 audit kick-off: scope org-wide evidence plan', 'KING', 'OPERATIONS', '30000000-0000-0000-0000-000000000005'::uuid, 10.0, 0.80,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000005'::uuid, 'Complete SOC2 Type II certification', 2),
  ('d1000000-0000-0000-d040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000004'::uuid,
   'Launch engineer tech-talk program', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000009'::uuid, 8.0, 0.85,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk', 2),
  ('d1000000-0000-0000-d040-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000004'::uuid,
   'Cross-team OKR alignment workshop', 'ROOK', 'PEOPLE', 'e0000000-0000-0000-0000-000000000002'::uuid, 6.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime', 2),
  ('d1000000-0000-0000-d040-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000004'::uuid,
   'Budget forecasting and vendor contract reviews', 'PAWN', 'OPERATIONS', NULL, 4.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-d040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Evidence scope defined across 5 teams. Audit timeline confirmed with external auditor.', 'DONE', 11.0, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d1000000-0000-0000-d040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Program launched: 8 engineers signed up, first 3 talks scheduled.', 'DONE', 7.0, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d1000000-0000-0000-d040-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Workshop completed. All teams aligned on Q2 OKRs.', 'DONE', 6.5, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d1000000-0000-0000-d040-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Q2 budget finalized. 3 vendor contracts renewed.', 'DONE', 3.5, NOW() - interval '25 days', NOW() - interval '25 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Dana W-3 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-d030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000003'::uuid,
   'SOC2 evidence collection: access controls & logging', 'KING', 'OPERATIONS', '30000000-0000-0000-0000-000000000005'::uuid, 12.0, 0.75,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000005'::uuid, 'Complete SOC2 Type II certification', 2),
  ('d1000000-0000-0000-d030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000003'::uuid,
   'Executive sponsor for health-score alerting rollout', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000011'::uuid, 6.0, 0.85,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting', 2),
  ('d1000000-0000-0000-d030-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000003'::uuid,
   'Senior engineer hiring: final round interviews', 'ROOK', 'PEOPLE', '30000000-0000-0000-0000-000000000009'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk', 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-d030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Evidence collected for access controls. Logging gaps identified in 2 services.', 'DONE', 13.0, NOW() - interval '18 days', NOW() - interval '18 days'),
  ('d1000000-0000-0000-d030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Unblocked credential provisioning. Rollout expanded to all pilot accounts.', 'DONE', 5.0, NOW() - interval '18 days', NOW() - interval '18 days'),
  ('d1000000-0000-0000-d030-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Final round done. Offer extended to top candidate.', 'DONE', 9.0, NOW() - interval '18 days', NOW() - interval '18 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Dana W-2 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-d020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000002'::uuid,
   'SOC2 gap remediation: logging service patches', 'KING', 'OPERATIONS', '30000000-0000-0000-0000-000000000005'::uuid, 10.0, 0.80,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000005'::uuid, 'Complete SOC2 Type II certification', 2),
  ('d1000000-0000-0000-d020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000002'::uuid,
   'Engineer tech talks: first 3 sessions delivered', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000009'::uuid, 6.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk', 2),
  ('d1000000-0000-0000-d020-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000002'::uuid,
   'Q3 headcount and capacity planning review', 'ROOK', 'PEOPLE', '30000000-0000-0000-0000-000000000010'::uuid, 5.0, 0.85,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000010'::uuid, 'Implement weekly commitments module', 2),
  ('d1000000-0000-0000-d020-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000002'::uuid,
   'Security policy documentation update', 'PAWN', 'OPERATIONS', NULL, 3.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-d020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Logging patches deployed to all 2 affected services. Auditor confirmed gaps resolved.', 'DONE', 11.0, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d1000000-0000-0000-d020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, '3 talks delivered with avg 94% satisfaction rating from attendees.', 'DONE', 5.5, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d1000000-0000-0000-d020-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Q3 capacity plan submitted to finance. 2 new headcount requests approved.', 'DONE', 4.5, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d1000000-0000-0000-d020-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Security policies updated and published on internal wiki.', 'DONE', 2.5, NOW() - interval '11 days', NOW() - interval '11 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Dana W-1 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-d010-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000001'::uuid,
   'SOC2 pre-audit readiness review with external auditor', 'KING', 'OPERATIONS', '30000000-0000-0000-0000-000000000005'::uuid, 10.0, 0.85,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000005'::uuid, 'Complete SOC2 Type II certification', 2),
  ('d1000000-0000-0000-d010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000001'::uuid,
   'NPS program exec briefing and board update', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000012'::uuid, 6.0, 0.90,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000012'::uuid, 'Achieve NPS > 60', 2),
  ('d1000000-0000-0000-d010-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b4000000-0000-0000-0000-d00000000001'::uuid,
   'Mid-year performance reviews: team leads', 'ROOK', 'PEOPLE', '30000000-0000-0000-0000-000000000009'::uuid, 8.0, 0.85,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk', 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-d010-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Pre-audit review passed. Zero critical findings. Audit scheduled for next month.', 'DONE', 11.0, NOW() - interval '4 days', NOW() - interval '4 days'),
  ('d1000000-0000-0000-d010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Board briefed on NPS trajectory. Current NPS = 54, target 60 by EOQ.', 'DONE', 5.0, NOW() - interval '4 days', NOW() - interval '4 days'),
  ('d1000000-0000-0000-d010-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'All 5 team-lead reviews completed. 2 promotion recommendations submitted.', 'DONE', 9.0, NOW() - interval '4 days', NOW() - interval '4 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Dana Torres — seed a snapshot so /api/v1/users/me/profile is populated after local reset
INSERT INTO user_model_snapshots (org_id, user_id, computed_at, weeks_analyzed, model_json) VALUES
  ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, NOW(), 4, '{
    "performanceProfile": {
      "estimationAccuracy": 0.88,
      "completionReliability": 0.97,
      "avgCommitsPerWeek": 3.5,
      "avgCarryForwardPerWeek": 0.0,
      "topCategories": ["OPERATIONS", "PEOPLE"],
      "categoryCompletionRates": {"OPERATIONS": 1.0, "PEOPLE": 0.95},
      "priorityCompletionRates": {"KING": 1.0, "QUEEN": 0.95, "ROOK": 1.0, "PAWN": 1.0}
    },
    "preferences": {
      "typicalPriorityPattern": "1K-1Q-1R-1P",
      "recurringCommitTitles": ["SOC2 evidence collection", "Engineer tech talks", "Budget forecasting"],
      "avgCheckInsPerWeek": 1.5,
      "preferredUpdateDays": ["MONDAY", "THURSDAY"]
    },
    "trends": {
      "strategicAlignmentTrend": "IMPROVING",
      "completionTrend": "IMPROVING",
      "carryForwardTrend": "IMPROVING"
    }
  }')
ON CONFLICT (org_id, user_id) DO NOTHING;




-- ═══════════════════════════════════════════════════════════════════════════
-- PHASE 6+: Teams · Issues · Assignments · History · User Models
-- ═══════════════════════════════════════════════════════════════════════════
-- Two teams:
--   Platform Engineering (ENG) — Carol owns, Alice+Bob+Carol+Dana, 25 issues
--   Growth & Product     (GRW) — Dana owns, Carol+Dana, 10 lighter issues
-- All 4 personas have 5-6 weeks of RECONCILED history with hours data.
-- User model snapshots seeded for all 4.
-- ═══════════════════════════════════════════════════════════════════════════


-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │  HISTORICAL PLANS — Alice, Bob, Carol (W-6 through W-2)                 │
-- └──────────────────────────────────────────────────────────────────────────┘

-- ── ALICE W-6 ──────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('ba060000-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,date_trunc('week',CURRENT_DATE)::date-42,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'45 days',3,NOW()-interval'46 days',NOW()-interval'39 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('ca060001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba060000-0000-0000-0000-a00000000001'::uuid,'Scaffold enterprise onboarding service','KING','DELIVERY','e0000000-0000-0000-0000-000000000001'::uuid,12.0,0.70,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','e0000000-0000-0000-0000-000000000001'::uuid,'Close 10 enterprise deals in Q1',2),
('ca060002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba060000-0000-0000-0000-a00000000001'::uuid,'Fix flaky CI pipeline for auth module','ROOK','DELIVERY','30000000-0000-0000-0000-000000000008'::uuid,6.0,0.80,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000008'::uuid,'Increase unit test coverage to 85%',2),
('ca060003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba060000-0000-0000-0000-a00000000001'::uuid,'Watch AWS re:Invent cloud-native workshop','KNIGHT','LEARNING','30000000-0000-0000-0000-000000000009'::uuid,4.0,0.90,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('ca060001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Service skeleton with Gradle, Spring Boot shell, and health endpoint deployed to CI.','DONE',14.0,NOW()-interval'39 days',NOW()-interval'39 days'),
('ca060002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Root cause was Docker layer caching. Fixed with explicit cache keys. All tests green.','DONE',8.0,NOW()-interval'39 days',NOW()-interval'39 days'),
('ca060003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Watched 3 of 5 sessions. Notes in #engineering-learning channel.','PARTIALLY',3.0,NOW()-interval'39 days',NOW()-interval'39 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── ALICE W-5 ──────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('ba050000-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,date_trunc('week',CURRENT_DATE)::date-35,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'38 days',3,NOW()-interval'39 days',NOW()-interval'32 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('ca050001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba050000-0000-0000-0000-a00000000001'::uuid,'Implement SAML SSO adapter for onboarding','KING','DELIVERY','e0000000-0000-0000-0000-000000000001'::uuid,14.0,0.75,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','e0000000-0000-0000-0000-000000000001'::uuid,'Close 10 enterprise deals in Q1',2),
('ca050002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba050000-0000-0000-0000-a00000000001'::uuid,'Write JWT validation unit tests','ROOK','DELIVERY','30000000-0000-0000-0000-000000000008'::uuid,8.0,0.85,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000008'::uuid,'Increase unit test coverage to 85%',2),
('ca050003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba050000-0000-0000-0000-a00000000001'::uuid,'Update Swagger docs for auth endpoints','PAWN','OPERATIONS',NULL,3.0,0.95,NULL,NULL,NULL,NULL,NULL,NULL,2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('ca050001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'SAML adapter implemented and passing integration tests. IdP metadata parsing complete.','DONE',16.0,NOW()-interval'32 days',NOW()-interval'32 days'),
('ca050002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'JWT tests written. Auth module coverage up to 78% from 62%.','DONE',7.0,NOW()-interval'32 days',NOW()-interval'32 days'),
('ca050003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'API docs updated in Swagger and published to internal portal.','DONE',2.5,NOW()-interval'32 days',NOW()-interval'32 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── ALICE W-4 (carry-forward from onboarding step 4) ─────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('ba040000-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,date_trunc('week',CURRENT_DATE)::date-28,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'31 days',3,NOW()-interval'32 days',NOW()-interval'25 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('ca040001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba040000-0000-0000-0000-a00000000001'::uuid,'Build onboarding wizard UI steps 1–4','KING','DELIVERY','e0000000-0000-0000-0000-000000000001'::uuid,16.0,0.65,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','e0000000-0000-0000-0000-000000000001'::uuid,'Close 10 enterprise deals in Q1',2),
('ca040002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba040000-0000-0000-0000-a00000000001'::uuid,'Peer-review Bob API latency optimization PR','ROOK','PEOPLE','30000000-0000-0000-0000-000000000007'::uuid,4.0,0.90,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000007'::uuid,'Reduce deploy-to-production time to < 15 min',2),
('ca040003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba040000-0000-0000-0000-a00000000001'::uuid,'Update onboarding E2E test suite','ROOK','DELIVERY','30000000-0000-0000-0000-000000000008'::uuid,6.0,0.80,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000008'::uuid,'Increase unit test coverage to 85%',2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,delta_reason,created_at,updated_at) VALUES
('ca040001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Steps 1-3 complete. Step 4 org-provisioning blocked on backend API.','PARTIALLY',14.0,'Backend org-provisioning API not ready. Step 4 carries forward.',NOW()-interval'25 days',NOW()-interval'25 days'),
('ca040002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'PR reviewed with 12 comments. Approved after 2nd round.','DONE',3.5,NULL,NOW()-interval'25 days',NOW()-interval'25 days'),
('ca040003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'E2E tests updated for steps 1-3. Step 4 tests deferred.','DONE',5.0,NULL,NOW()-interval'25 days',NOW()-interval'25 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── ALICE W-3 (step 4 carry-forward completes) ───────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('ba030000-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,date_trunc('week',CURRENT_DATE)::date-21,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'24 days',3,NOW()-interval'25 days',NOW()-interval'18 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,carried_from_commit_id,version) VALUES
('ca030001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba030000-0000-0000-0000-a00000000001'::uuid,'Complete wizard step 4: org provisioning flow','KING','DELIVERY','e0000000-0000-0000-0000-000000000001'::uuid,10.0,0.80,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','e0000000-0000-0000-0000-000000000001'::uuid,'Close 10 enterprise deals in Q1','ca040001-0000-0000-0000-a00000000001'::uuid,2),
('ca030002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba030000-0000-0000-0000-a00000000001'::uuid,'Add token-bucket rate limiting to API gateway','QUEEN','DELIVERY','30000000-0000-0000-0000-000000000007'::uuid,8.0,0.75,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000007'::uuid,'Reduce deploy-to-production time to < 15 min',NULL,2),
('ca030003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba030000-0000-0000-0000-a00000000001'::uuid,'Facilitate sprint retrospective','PAWN','PEOPLE',NULL,2.0,0.95,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('ca030001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Step 4 complete. Full wizard flow working end-to-end in staging with 3 test orgs.','DONE',9.0,NOW()-interval'18 days',NOW()-interval'18 days'),
('ca030002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Rate limiter implemented at gateway. Load test passed at 500 RPS. p95 < 180ms.','DONE',10.0,NOW()-interval'18 days',NOW()-interval'18 days'),
('ca030003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Retro facilitated. 4 action items captured and assigned.','DONE',1.5,NOW()-interval'18 days',NOW()-interval'18 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── ALICE W-2 ──────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('ba020000-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,date_trunc('week',CURRENT_DATE)::date-14,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'17 days',3,NOW()-interval'18 days',NOW()-interval'11 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('ca020001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba020000-0000-0000-0000-a00000000001'::uuid,'Deploy onboarding wizard to production','KING','DELIVERY','e0000000-0000-0000-0000-000000000001'::uuid,8.0,0.85,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','e0000000-0000-0000-0000-000000000001'::uuid,'Close 10 enterprise deals in Q1',2),
('ca020002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba020000-0000-0000-0000-a00000000001'::uuid,'Write integration tests for rate limiter','ROOK','DELIVERY','30000000-0000-0000-0000-000000000008'::uuid,6.0,0.80,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000008'::uuid,'Increase unit test coverage to 85%',2),
('ca020003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'ba020000-0000-0000-0000-a00000000001'::uuid,'Prep event sourcing tech talk slides','KNIGHT','LEARNING','30000000-0000-0000-0000-000000000009'::uuid,4.0,0.85,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('ca020001-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Deployed to prod. 3 enterprise customers self-onboarded in first 48h. Zero support tickets.','DONE',7.0,NOW()-interval'11 days',NOW()-interval'11 days'),
('ca020002-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Integration tests cover 5 rate-limit scenarios. Auth module coverage now at 84%.','DONE',5.5,NOW()-interval'11 days',NOW()-interval'11 days'),
('ca020003-0000-0000-0000-a00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Slides done. Dry-run with Carol. Talk scheduled for next Thursday.','DONE',3.5,NOW()-interval'11 days',NOW()-interval'11 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Add hours to Alice's existing current-week DRAFT commits
UPDATE weekly_commits SET estimated_hours=16.0,confidence=0.70 WHERE id='d0000000-0000-0000-0000-000000000010'::uuid;
UPDATE weekly_commits SET estimated_hours=8.0, confidence=0.85 WHERE id='d0000000-0000-0000-0000-000000000011'::uuid;
UPDATE weekly_commits SET estimated_hours=4.0, confidence=0.90 WHERE id='d0000000-0000-0000-0000-000000000012'::uuid;
UPDATE weekly_commits SET estimated_hours=3.0, confidence=0.80 WHERE id='d0000000-0000-0000-0000-000000000013'::uuid;

-- ── BOB W-5 ────────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('bb050000-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,date_trunc('week',CURRENT_DATE)::date-35,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'38 days',3,NOW()-interval'39 days',NOW()-interval'32 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('cb050001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb050000-0000-0000-0000-b00000000001'::uuid,'Prototype multi-tenant demo environment','KING','DELIVERY','30000000-0000-0000-0000-000000000002'::uuid,14.0,0.75,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','30000000-0000-0000-0000-000000000002'::uuid,'Launch enterprise demo environment',2),
('cb050002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb050000-0000-0000-0000-b00000000001'::uuid,'Profile and fix N+1 queries in list endpoints','QUEEN','DELIVERY','30000000-0000-0000-0000-000000000007'::uuid,10.0,0.80,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000007'::uuid,'Reduce deploy-to-production time to < 15 min',2),
('cb050003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb050000-0000-0000-0000-b00000000001'::uuid,'Update on-call handoff runbook','PAWN','OPERATIONS',NULL,2.0,0.95,NULL,NULL,NULL,NULL,NULL,NULL,2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('cb050001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Demo env scaffolded with Terraform. 2 sample orgs provisioned with realistic data.','DONE',15.0,NOW()-interval'32 days',NOW()-interval'32 days'),
('cb050002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Fixed 8 N+1 queries across 3 endpoints. p95 latency dropped from 450ms to 220ms.','DONE',11.0,NOW()-interval'32 days',NOW()-interval'32 days'),
('cb050003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Runbook updated and verified by 2 team members.','DONE',1.5,NOW()-interval'32 days',NOW()-interval'32 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── BOB W-4 ────────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('bb040000-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,date_trunc('week',CURRENT_DATE)::date-28,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'31 days',3,NOW()-interval'32 days',NOW()-interval'25 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('cb040001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb040000-0000-0000-0000-b00000000001'::uuid,'Add SSO bypass and sample data to demo env','KING','DELIVERY','30000000-0000-0000-0000-000000000002'::uuid,12.0,0.80,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','30000000-0000-0000-0000-000000000002'::uuid,'Launch enterprise demo environment',2),
('cb040002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb040000-0000-0000-0000-b00000000001'::uuid,'Add Redis caching to top 4 API endpoints','QUEEN','DELIVERY','30000000-0000-0000-0000-000000000007'::uuid,10.0,0.75,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000007'::uuid,'Reduce deploy-to-production time to < 15 min',2),
('cb040003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb040000-0000-0000-0000-b00000000001'::uuid,'Design health-score data model','ROOK','CUSTOMER','30000000-0000-0000-0000-000000000011'::uuid,6.0,0.85,'10000000-0000-0000-0000-000000000003'::uuid,'Customer obsession','20000000-0000-0000-0000-000000000005'::uuid,'Reduce churn to < 5%','30000000-0000-0000-0000-000000000011'::uuid,'Launch proactive health-score alerting',2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('cb040001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'SSO bypass working. 3 sample orgs with realistic data. Sales gave thumbs up.','DONE',11.0,NOW()-interval'25 days',NOW()-interval'25 days'),
('cb040002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Redis caching on 4 endpoints. p95 now at 195ms - target met.','DONE',12.0,NOW()-interval'25 days',NOW()-interval'25 days'),
('cb040003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Data model reviewed and approved. Schema migration written and reviewed.','DONE',5.0,NOW()-interval'25 days',NOW()-interval'25 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── BOB W-3 ────────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('bb030000-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,date_trunc('week',CURRENT_DATE)::date-21,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'24 days',3,NOW()-interval'25 days',NOW()-interval'18 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('cb030001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb030000-0000-0000-0000-b00000000001'::uuid,'Load test and harden demo environment','KING','DELIVERY','30000000-0000-0000-0000-000000000002'::uuid,10.0,0.85,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','30000000-0000-0000-0000-000000000002'::uuid,'Launch enterprise demo environment',2),
('cb030002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb030000-0000-0000-0000-b00000000001'::uuid,'Build health-score ingestion pipeline MVP','QUEEN','DELIVERY','30000000-0000-0000-0000-000000000011'::uuid,12.0,0.70,'10000000-0000-0000-0000-000000000003'::uuid,'Customer obsession','20000000-0000-0000-0000-000000000005'::uuid,'Reduce churn to < 5%','30000000-0000-0000-0000-000000000011'::uuid,'Launch proactive health-score alerting',2),
('cb030003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb030000-0000-0000-0000-b00000000001'::uuid,'Pair with junior dev on testing practices','BISHOP','PEOPLE','30000000-0000-0000-0000-000000000009'::uuid,3.0,0.90,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,delta_reason,created_at,updated_at) VALUES
('cb030001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Tested to 200 concurrent users. Fixed 2 connection pool leaks.','DONE',9.0,NULL,NOW()-interval'18 days',NOW()-interval'18 days'),
('cb030002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Pipeline scaffolded but blocked on platform team credentials. Functional with mock data.','PARTIALLY',10.0,'Platform team credential provisioning delayed 1 week.',NOW()-interval'18 days',NOW()-interval'18 days'),
('cb030003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'3 pairing sessions. Junior dev submitted first independent PR.','DONE',3.5,NULL,NOW()-interval'18 days',NOW()-interval'18 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── BOB W-2 ────────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('bb020000-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,date_trunc('week',CURRENT_DATE)::date-14,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'17 days',3,NOW()-interval'18 days',NOW()-interval'11 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('cb020001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb020000-0000-0000-0000-b00000000001'::uuid,'Demo environment production readiness review','KING','DELIVERY','30000000-0000-0000-0000-000000000002'::uuid,8.0,0.90,'10000000-0000-0000-0000-000000000001'::uuid,'Scale to $500M ARR','20000000-0000-0000-0000-000000000001'::uuid,'Accelerate enterprise pipeline','30000000-0000-0000-0000-000000000002'::uuid,'Launch enterprise demo environment',2),
('cb020002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb020000-0000-0000-0000-b00000000001'::uuid,'Optimize dashboard aggregate query with materialized view','QUEEN','DELIVERY','30000000-0000-0000-0000-000000000007'::uuid,10.0,0.75,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','30000000-0000-0000-0000-000000000007'::uuid,'Reduce deploy-to-production time to < 15 min',2),
('cb020003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bb020000-0000-0000-0000-b00000000001'::uuid,'Write API timeout incident post-mortem','PAWN','OPERATIONS',NULL,3.0,0.95,NULL,NULL,NULL,NULL,NULL,NULL,2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('cb020001-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Readiness review passed. Demo env cleared for sales use by VP of Sales.','DONE',7.0,NOW()-interval'11 days',NOW()-interval'11 days'),
('cb020002-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Materialized view designed. Implementation started. p95 still 280ms — next week.','PARTIALLY',8.0,NOW()-interval'11 days',NOW()-interval'11 days'),
('cb020003-0000-0000-0000-b00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Post-mortem written and reviewed. 3 action items: circuit breaker, timeout tuning, alerting.','DONE',2.5,NOW()-interval'11 days',NOW()-interval'11 days')
ON CONFLICT (commit_id) DO NOTHING;

UPDATE weekly_commits SET estimated_hours=14.0,confidence=0.85 WHERE id='d0000000-0000-0000-0000-000000000020'::uuid;
UPDATE weekly_commits SET estimated_hours=10.0,confidence=0.70 WHERE id='d0000000-0000-0000-0000-000000000021'::uuid;
UPDATE weekly_commits SET estimated_hours=8.0, confidence=0.60 WHERE id='d0000000-0000-0000-0000-000000000022'::uuid;
UPDATE weekly_commits SET estimated_hours=3.0, confidence=0.95 WHERE id='d0000000-0000-0000-0000-000000000023'::uuid;

-- ── CAROL W-5 ──────────────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('bc050000-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,date_trunc('week',CURRENT_DATE)::date-35,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'38 days',3,NOW()-interval'39 days',NOW()-interval'32 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('cc050001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc050000-0000-0000-0000-c00000000001'::uuid,'Q2 strategic planning session with VPE','KING','PEOPLE','e0000000-0000-0000-0000-000000000002'::uuid,8.0,0.85,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','e0000000-0000-0000-0000-000000000002'::uuid,'Achieve 99.9% API uptime',2),
('cc050002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc050000-0000-0000-0000-c00000000001'::uuid,'Review Alice W-5 reconciliation','QUEEN','PEOPLE','30000000-0000-0000-0000-000000000009'::uuid,3.0,0.90,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2),
('cc050003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc050000-0000-0000-0000-c00000000001'::uuid,'Vendor license renewal negotiations','PAWN','OPERATIONS',NULL,4.0,0.90,NULL,NULL,NULL,NULL,NULL,NULL,2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('cc050001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Planning session complete. Q2 Rally Cries drafted and shared org-wide.','DONE',7.0,NOW()-interval'32 days',NOW()-interval'32 days'),
('cc050002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Reviewed and approved with feedback on estimation accuracy.','DONE',2.5,NOW()-interval'32 days',NOW()-interval'32 days'),
('cc050003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'2 of 3 renewals processed. Third deferred — legal review needed.','PARTIALLY',3.5,NOW()-interval'32 days',NOW()-interval'32 days')
ON CONFLICT (commit_id) DO NOTHING;

-- ── CAROL W-4, W-3, W-2 ────────────────────────────────────────────────────
INSERT INTO weekly_plans (id,org_id,owner_user_id,week_start_date,state,review_status,lock_type,locked_at,version,created_at,updated_at) VALUES
('bc040000-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,date_trunc('week',CURRENT_DATE)::date-28,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'31 days',3,NOW()-interval'32 days',NOW()-interval'25 days'),
('bc030000-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,date_trunc('week',CURRENT_DATE)::date-21,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'24 days',3,NOW()-interval'25 days',NOW()-interval'18 days'),
('bc020000-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,date_trunc('week',CURRENT_DATE)::date-14,'RECONCILED','APPROVED','ON_TIME',NOW()-interval'17 days',3,NOW()-interval'18 days',NOW()-interval'11 days')
ON CONFLICT (org_id,owner_user_id,week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id,org_id,weekly_plan_id,title,chess_priority,category,outcome_id,estimated_hours,confidence,snapshot_rally_cry_id,snapshot_rally_cry_name,snapshot_objective_id,snapshot_objective_name,snapshot_outcome_id,snapshot_outcome_name,version) VALUES
('cc040001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc040000-0000-0000-0000-c00000000001'::uuid,'Engineering hiring pipeline review','KING','PEOPLE','30000000-0000-0000-0000-000000000009'::uuid,6.0,0.85,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2),
('cc040002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc040000-0000-0000-0000-c00000000001'::uuid,'Review Bob W-4 reconciliation','QUEEN','PEOPLE','30000000-0000-0000-0000-000000000009'::uuid,2.0,0.95,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2),
('cc040003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc040000-0000-0000-0000-c00000000001'::uuid,'Complete remaining vendor renewal','PAWN','OPERATIONS',NULL,2.0,0.95,NULL,NULL,NULL,NULL,NULL,NULL,2),
('cc030001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc030000-0000-0000-0000-c00000000001'::uuid,'1:1s with Alice and Bob — mid-quarter check-in','KING','PEOPLE','30000000-0000-0000-0000-000000000009'::uuid,4.0,0.90,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2),
('cc030002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc030000-0000-0000-0000-c00000000001'::uuid,'Cross-team API standards doc v1','QUEEN','DELIVERY','e0000000-0000-0000-0000-000000000002'::uuid,6.0,0.80,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','e0000000-0000-0000-0000-000000000002'::uuid,'Achieve 99.9% API uptime',2),
('cc030003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc030000-0000-0000-0000-c00000000001'::uuid,'Monthly budget reconciliation','PAWN','OPERATIONS',NULL,3.0,0.95,NULL,NULL,NULL,NULL,NULL,NULL,2),
('cc020001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc020000-0000-0000-0000-c00000000001'::uuid,'Finalize Q2 OKR deck for all-hands','KING','PEOPLE','e0000000-0000-0000-0000-000000000002'::uuid,6.0,0.85,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000003'::uuid,'Ship reliable software faster','e0000000-0000-0000-0000-000000000002'::uuid,'Achieve 99.9% API uptime',2),
('cc020002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc020000-0000-0000-0000-c00000000001'::uuid,'Review Alice and Bob W-2 reconciliations','QUEEN','PEOPLE','30000000-0000-0000-0000-000000000009'::uuid,3.0,0.90,'10000000-0000-0000-0000-000000000002'::uuid,'World-class engineering culture','20000000-0000-0000-0000-000000000004'::uuid,'Invest in team growth','30000000-0000-0000-0000-000000000009'::uuid,'Every engineer presents at a tech talk',2),
('cc020003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'bc020000-0000-0000-0000-c00000000001'::uuid,'Prepare team Q3 capacity plan','ROOK','OPERATIONS',NULL,4.0,0.80,NULL,NULL,NULL,NULL,NULL,NULL,2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id,org_id,actual_result,completion_status,actual_hours,created_at,updated_at) VALUES
('cc040001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Pipeline reviewed. 5 candidates in final round. 2 strong fits identified.','DONE',5.5,NOW()-interval'25 days',NOW()-interval'25 days'),
('cc040002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Reviewed and approved. Strong strategic alignment noted.','DONE',1.5,NOW()-interval'25 days',NOW()-interval'25 days'),
('cc040003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Renewal completed. 8% cost reduction negotiated.','DONE',1.5,NOW()-interval'25 days',NOW()-interval'25 days'),
('cc030001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'1:1s done. Both on track. Alice needs estimation coaching.','DONE',3.5,NOW()-interval'18 days',NOW()-interval'18 days'),
('cc030002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'API standards doc v1 published. 3 teams adopted.','DONE',7.0,NOW()-interval'18 days',NOW()-interval'18 days'),
('cc030003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Budget reconciled. Under budget by 4%.','DONE',2.0,NOW()-interval'18 days',NOW()-interval'18 days'),
('cc020001-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Deck delivered at all-hands. Positive leadership feedback.','DONE',5.0,NOW()-interval'11 days',NOW()-interval'11 days'),
('cc020002-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Both reviews completed. Bob approved; Alice approved with coaching notes.','DONE',2.5,NOW()-interval'11 days',NOW()-interval'11 days'),
('cc020003-0000-0000-0000-c00000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Q3 capacity draft submitted. 1 headcount approved.','DONE',3.5,NOW()-interval'11 days',NOW()-interval'11 days')
ON CONFLICT (commit_id) DO NOTHING;

UPDATE weekly_commits SET estimated_hours=8.0, confidence=0.85 WHERE id='d0000000-0000-0000-0000-000000000001'::uuid;
UPDATE weekly_commits SET estimated_hours=4.0, confidence=0.90 WHERE id='d0000000-0000-0000-0000-000000000002'::uuid;
UPDATE weekly_commits SET estimated_hours=3.0, confidence=0.95 WHERE id='d0000000-0000-0000-0000-000000000003'::uuid;


-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │  TEAM 1: Platform Engineering (ENG)                                     │
-- │  Owner: Carol  │  Members: Alice, Bob, Carol, Dana  │  25 issues        │
-- └──────────────────────────────────────────────────────────────────────────┘

INSERT INTO teams (id,org_id,name,key_prefix,description,owner_user_id,issue_sequence,version,created_at,updated_at)
VALUES ('f0000000-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Platform Engineering','ENG','Core platform, APIs, infrastructure, and security team.','c0000000-0000-0000-0000-000000000001'::uuid,25,1,NOW()-interval'90 days',NOW()-interval'90 days')
ON CONFLICT (org_id,name) DO NOTHING;

UPDATE teams SET issue_sequence=25
WHERE id='f0000000-0000-0000-0000-000000000001'::uuid;

INSERT INTO team_members (team_id,user_id,org_id,role,joined_at) VALUES
('f0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'OWNER', NOW()-interval'90 days'),
('f0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'MEMBER',NOW()-interval'90 days'),
('f0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'MEMBER',NOW()-interval'90 days'),
('f0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'MEMBER',NOW()-interval'90 days')
ON CONFLICT (team_id,user_id) DO NOTHING;

-- 25 ENG issues — rich, realistic, varied statuses and assignees
INSERT INTO issues (id,org_id,team_id,issue_key,sequence_number,title,description,effort_type,estimated_hours,chess_priority,outcome_id,creator_user_id,assignee_user_id,status,embedding_version,version,created_at,updated_at) VALUES

-- IN_PROGRESS / active work
('e1000001-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-1',1,
'Build enterprise onboarding wizard with SSO','Multi-step wizard: org setup → team invite → SSO config → sample data. Must support SAML and OIDC.','BUILD',24.0,'KING','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'IN_PROGRESS',0,2,NOW()-interval'30 days',NOW()-interval'1 day'),

('e1000002-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-2',2,
'Implement materialized view for dashboard aggregate query','Dashboard p95 is 280ms due to multi-join aggregation. Replace with incremental materialized view refreshed on write.','BUILD',12.0,'QUEEN','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'IN_PROGRESS',0,1,NOW()-interval'10 days',NOW()-interval'2 days'),

('e1000003-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-3',3,
'Implement customer health-score alerting pipeline','CSM email alerts when health score drops below configurable threshold. Plugs into existing notification service.','BUILD',14.0,'QUEEN','30000000-0000-0000-0000-000000000011'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'IN_PROGRESS',0,1,NOW()-interval'14 days',NOW()-interval'3 days'),

-- OPEN — ready for next sprint
('e1000004-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-4',4,
'Add token-bucket rate limiting to API gateway','Protect public API from abuse. Implement at the Kong gateway layer with per-org limits configurable via admin API.','BUILD',10.0,'ROOK','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,NULL,'OPEN',0,1,NOW()-interval'7 days',NOW()-interval'7 days'),

('e1000005-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-5',5,
'Rotate service account credentials post-SOC2 audit','Auditor flagged stale credentials in 3 services (auth, billing, integrations). Rotate and update Vault secrets manager.','MAINTAIN',5.0,'ROOK','30000000-0000-0000-0000-000000000005'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,NULL,'OPEN',0,1,NOW()-interval'5 days',NOW()-interval'5 days'),

('e1000006-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-6',6,
'Implement webhook v2 retry with exponential backoff','Replace fire-and-forget webhook delivery with SQS-backed retry queue. Dead-letter after 5 attempts with alerting.','BUILD',10.0,'QUEEN','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,NULL,'OPEN',0,1,NOW()-interval'6 days',NOW()-interval'6 days'),

('e1000007-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-7',7,
'Migrate CI runners from x86 to ARM (cost reduction)','All 12 GitHub Actions runner pools need ARM equivalents. Validate build times and docker cross-compilation.','MAINTAIN',6.0,'PAWN',NULL,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'OPEN',0,1,NOW()-interval'4 days',NOW()-interval'4 days'),

('e1000008-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-8',8,
'Set up structured logging with correlation IDs','Migrate from unstructured console.log to JSON structured logs. Add request correlation IDs traceable across services.','MAINTAIN',7.0,'ROOK','e0000000-0000-0000-0000-000000000002'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'OPEN',0,1,NOW()-interval'3 days',NOW()-interval'3 days'),

('e1000009-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-9',9,
'Automate DB backup verification (weekly restore test)','Weekly automated restore-from-backup with data integrity checks. Alert to PagerDuty on failure.','MAINTAIN',8.0,'ROOK','e0000000-0000-0000-0000-000000000002'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,NULL,'OPEN',0,1,NOW()-interval'2 days',NOW()-interval'2 days'),

('e1000010-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-10',10,
'Spike: Postgres logical replication for multi-region reads','Evaluate PG logical replication vs Citus for US-East/US-West read path. Deliverable: ADR with recommendation.','LEARN',8.0,'KNIGHT','e0000000-0000-0000-0000-000000000002'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,NULL,'OPEN',0,1,NOW()-interval'2 days',NOW()-interval'2 days'),

('e1000011-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-11',11,
'Implement feature flag service (LaunchDarkly SDK)','Replace 12 hardcoded feature checks with LaunchDarkly SDK. Admin UI for flag management. Zero-downtime migration.','BUILD',10.0,'ROOK','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,NULL,'OPEN',0,1,NOW()-interval'1 day',NOW()-interval'1 day'),

('e1000012-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-12',12,
'Performance benchmark suite for critical API paths','k6 load tests in CI. Fail build if p95 > 300ms on /api/plans, /api/commits, /api/teams endpoints.','MAINTAIN',6.0,'ROOK','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'IN_PROGRESS',0,1,NOW()-interval'5 days',NOW()-interval'1 day'),

('e1000013-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-13',13,
'Conduct Q3 engineering retrospective','Quarterly team retrospective on process, tooling, and culture. Facilitate, capture action items, assign owners.','COLLABORATE',3.0,'BISHOP','e0000000-0000-0000-0000-000000000002'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,'OPEN',0,1,NOW()-interval'1 day',NOW()-interval'1 day'),

('e1000014-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-14',14,
'Customer onboarding funnel analytics dashboard','Internal BI dashboard: signup → SSO → first project → 7-day active. Drive self-serve conversion insights.','BUILD',12.0,'QUEEN','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,NULL,'OPEN',0,1,NOW()-interval'1 day',NOW()-interval'1 day'),

('e1000015-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-15',15,
'Upgrade auth service to support OIDC in addition to SAML','Enterprise customers requesting OIDC (Okta, Azure AD). Add OIDC discovery + PKCE flow alongside existing SAML.','BUILD',16.0,'KING','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,NULL,'OPEN',0,1,NOW(),NOW()),

('e1000016-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-16',16,
'Quarterly security dependency audit and upgrades','Run Snyk+Trivy scan across all services. Triage and upgrade all critical/high CVEs. Document any accepted risks.','MAINTAIN',5.0,'PAWN',NULL,'c0000000-0000-0000-0000-000000000030'::uuid,NULL,'OPEN',0,1,NOW(),NOW()),

('e1000017-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-17',17,
'Implement org-level audit log export (SOC2 requirement)','Customers need downloadable audit logs for compliance. CSV + JSON export with date range filter and signed S3 URLs.','BUILD',10.0,'ROOK','30000000-0000-0000-0000-000000000005'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,NULL,'OPEN',0,1,NOW(),NOW()),

('e1000018-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-18',18,
'Add circuit breakers to all external service calls','Wrap Stripe, SendGrid, PagerDuty, and Pinecone clients with Resilience4j circuit breakers. Fallback strategies per service.','BUILD',8.0,'ROOK','e0000000-0000-0000-0000-000000000002'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,NULL,'OPEN',0,1,NOW(),NOW()),

-- DONE — shipped recently
('e1000019-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-19',19,
'Launch enterprise demo environment with sample data','Multi-tenant demo with 3 sample orgs, SSO bypass, and realistic data for sales calls.','BUILD',14.0,'KING','30000000-0000-0000-0000-000000000002'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'DONE',0,2,NOW()-interval'20 days',NOW()-interval'7 days'),

('e1000020-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-20',20,
'Migrate all CI runners from x86 to ARM (done)','All 12 runner pools migrated. Build times 22% faster. Monthly saving ~$200.','MAINTAIN',5.0,'PAWN',NULL,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'DONE',0,2,NOW()-interval'15 days',NOW()-interval'10 days'),

('e1000021-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-21',21,
'N+1 query audit and optimization for list endpoints','Identified and fixed 8 N+1 patterns. p95 latency dropped from 450ms to 220ms on list APIs.','BUILD',10.0,'QUEEN','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'DONE',0,2,NOW()-interval'25 days',NOW()-interval'18 days'),

('e1000022-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-22',22,
'Add Redis caching to hot API paths','Token-bucket caching for /api/rcdo/tree, /api/teams, /api/users/me. p95 < 200ms.','BUILD',10.0,'QUEEN','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,'DONE',0,2,NOW()-interval'22 days',NOW()-interval'15 days'),

-- ARCHIVED
('e1000023-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-23',23,
'Retire legacy webhook v1 endpoints','Remove deprecated v1 webhook path. All customer tenants migrated to v2.','MAINTAIN',6.0,'PAWN',NULL,'c0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'ARCHIVED',0,2,NOW()-interval'45 days',NOW()-interval'20 days'),

('e1000024-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-24',24,
'Evaluate Honeycomb vs Datadog for distributed tracing','Spike: compare cost, DX, and query capability. Deliverable: recommendation doc.','LEARN',6.0,'KNIGHT','30000000-0000-0000-0000-000000000007'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,'DONE',0,2,NOW()-interval'30 days',NOW()-interval'20 days'),

('e1000025-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f0000000-0000-0000-0000-000000000001'::uuid,'ENG-25',25,
'SOC2 pre-audit readiness review with external auditor','Pre-audit review passed. Zero critical findings. Formal audit scheduled for next month.','COLLABORATE',10.0,'KING','30000000-0000-0000-0000-000000000005'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'DONE',0,2,NOW()-interval'10 days',NOW()-interval'4 days')

ON CONFLICT DO NOTHING;

-- Activities for ENG issues (CREATED events)
INSERT INTO issue_activities (id,org_id,issue_id,actor_user_id,activity_type,metadata,created_at)
SELECT gen_random_uuid(),'a0000000-0000-0000-0000-000000000001'::uuid,i.id,i.creator_user_id,'CREATED','{}',i.created_at
FROM issues i WHERE i.team_id='f0000000-0000-0000-0000-000000000001'::uuid
ON CONFLICT DO NOTHING;

-- Status change activities for IN_PROGRESS issues
INSERT INTO issue_activities (id,org_id,issue_id,actor_user_id,activity_type,old_value,new_value,metadata,created_at)
SELECT gen_random_uuid(),'a0000000-0000-0000-0000-000000000001'::uuid,i.id,i.assignee_user_id,'STATUS_CHANGE','OPEN','IN_PROGRESS','{}',i.updated_at-interval'1 day'
FROM issues i WHERE i.status='IN_PROGRESS' AND i.team_id='f0000000-0000-0000-0000-000000000001'::uuid AND i.assignee_user_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Status changes for DONE issues
INSERT INTO issue_activities (id,org_id,issue_id,actor_user_id,activity_type,old_value,new_value,metadata,created_at)
SELECT gen_random_uuid(),'a0000000-0000-0000-0000-000000000001'::uuid,i.id,i.assignee_user_id,'STATUS_CHANGE','IN_PROGRESS','DONE','{}',i.updated_at
FROM issues i WHERE i.status='DONE' AND i.team_id='f0000000-0000-0000-0000-000000000001'::uuid AND i.assignee_user_id IS NOT NULL
ON CONFLICT DO NOTHING;


-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │  TEAM 2: Growth & Product (GRW)                                         │
-- │  Owner: Dana  │  Members: Dana + Carol (cross-team)  │  10 issues       │
-- │  Less fleshed out — shows inter-team visibility feature                 │
-- └──────────────────────────────────────────────────────────────────────────┘

INSERT INTO teams (id,org_id,name,key_prefix,description,owner_user_id,issue_sequence,version,created_at,updated_at)
VALUES ('f2000000-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Growth & Product','GRW','Product analytics, growth experiments, and customer success tooling.','c0000000-0000-0000-0000-000000000030'::uuid,10,1,NOW()-interval'60 days',NOW()-interval'60 days')
ON CONFLICT (org_id,name) DO NOTHING;

INSERT INTO team_members (team_id,user_id,org_id,role,joined_at) VALUES
('f2000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'OWNER', NOW()-interval'60 days'),
('f2000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'MEMBER',NOW()-interval'60 days')
ON CONFLICT (team_id,user_id) DO NOTHING;

INSERT INTO issues (id,org_id,team_id,issue_key,sequence_number,title,description,effort_type,estimated_hours,chess_priority,outcome_id,creator_user_id,assignee_user_id,status,embedding_version,version,created_at,updated_at) VALUES

('e2000001-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-1',1,
'Launch in-app NPS survey at 30/60/90 day marks','Automated NPS collection. Results feed health-score model. Integrate with Intercom for follow-up.','BUILD',12.0,'QUEEN','30000000-0000-0000-0000-000000000012'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'IN_PROGRESS',0,1,NOW()-interval'14 days',NOW()-interval'3 days'),

('e2000002-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-2',2,
'A/B test: onboarding checklist vs wizard flow','Measure which onboarding UX drives higher 7-day activation. LaunchDarkly flag, Mixpanel tracking.','COLLABORATE',8.0,'ROOK','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,NULL,'OPEN',0,1,NOW()-interval'10 days',NOW()-interval'10 days'),

('e2000003-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-3',3,
'Customer health score v1 model','Weighted composite: login frequency, feature usage, support tickets, NPS. Threshold: <40 = at-risk.','BUILD',16.0,'KING','30000000-0000-0000-0000-000000000011'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'IN_PROGRESS',0,1,NOW()-interval'20 days',NOW()-interval'2 days'),

('e2000004-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-4',4,
'Churn prediction model — pilot with 20 accounts','ML model using 90-day usage signals. Deliverable: ranked list of churn risk with recommended intervention.','LEARN',10.0,'QUEEN','30000000-0000-0000-0000-000000000012'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,NULL,'OPEN',0,1,NOW()-interval'5 days',NOW()-interval'5 days'),

('e2000005-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-5',5,
'Exec dashboard: MRR, churn, NPS, activation cohorts','C-suite BI dashboard with weekly snapshots. Connects to Stripe, Intercom, and internal DB.','BUILD',14.0,'KING','30000000-0000-0000-0000-000000000012'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,'OPEN',0,1,NOW()-interval'3 days',NOW()-interval'3 days'),

('e2000006-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-6',6,
'Referral program v1','Post-onboarding referral prompt. $200 credit per converted referral. Stripe coupon integration.','BUILD',10.0,'ROOK','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,NULL,'OPEN',0,1,NOW()-interval'2 days',NOW()-interval'2 days'),

('e2000007-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-7',7,
'Activation email sequence (days 1, 3, 7)','Triggered emails based on onboarding progress. Personalized by role (IC vs Manager). SendGrid templates.','BUILD',8.0,'ROOK','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,NULL,'OPEN',0,1,NOW()-interval'1 day',NOW()-interval'1 day'),

('e2000008-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-8',8,
'Healthcare vertical GTM analysis','Research compliance needs (HIPAA), competitor landscape, and buyer persona for healthcare pilot. 3 customer interviews.','LEARN',8.0,'BISHOP','30000000-0000-0000-0000-000000000003'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,NULL,'OPEN',0,1,NOW()-interval'1 day',NOW()-interval'1 day'),

('e2000009-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-9',9,
'Segment.io integration for product analytics','Replace custom event tracking with Segment. Route to Mixpanel + Amplitude. 30 key events instrumented.','BUILD',10.0,'ROOK','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,'DONE',0,2,NOW()-interval'20 days',NOW()-interval'10 days'),

('e2000010-0000-0000-0000-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'f2000000-0000-0000-0000-000000000001'::uuid,'GRW-10',10,
'Win/loss analysis framework — first 10 deals','Interview 5 won and 5 lost deals. Synthesize into product feedback themes. Share with eng and exec.','COLLABORATE',8.0,'BISHOP','e0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,NULL,'OPEN',0,1,NOW(),NOW())

ON CONFLICT DO NOTHING;

-- GRW issue activities
INSERT INTO issue_activities (id,org_id,issue_id,actor_user_id,activity_type,metadata,created_at)
SELECT gen_random_uuid(),'a0000000-0000-0000-0000-000000000001'::uuid,i.id,i.creator_user_id,'CREATED','{}',i.created_at
FROM issues i WHERE i.team_id='f2000000-0000-0000-0000-000000000001'::uuid
ON CONFLICT DO NOTHING;

INSERT INTO issue_activities (id,org_id,issue_id,actor_user_id,activity_type,old_value,new_value,metadata,created_at)
SELECT gen_random_uuid(),'a0000000-0000-0000-0000-000000000001'::uuid,i.id,i.assignee_user_id,'STATUS_CHANGE','OPEN','IN_PROGRESS','{}',i.updated_at-interval'1 day'
FROM issues i WHERE i.status='IN_PROGRESS' AND i.team_id='f2000000-0000-0000-0000-000000000001'::uuid AND i.assignee_user_id IS NOT NULL
ON CONFLICT DO NOTHING;


-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │  WEEKLY ASSIGNMENTS — link issues to existing plans                     │
-- └──────────────────────────────────────────────────────────────────────────┘

-- ENG-1 (IN_PROGRESS) → Alice's current DRAFT plan
INSERT INTO weekly_assignments (id,org_id,weekly_plan_id,issue_id,chess_priority_override,expected_result,confidence,tags,version,created_at,updated_at)
VALUES ('fa010000-0000-0000-0001-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'b0000000-0000-0000-0000-000000000010'::uuid,'e1000001-0000-0000-0000-000000000001'::uuid,'KING','Onboarding wizard deployed to staging with SAML and OIDC working end-to-end.',0.70,'{}',1,NOW()-interval'13 days',NOW()-interval'1 day')
ON CONFLICT (weekly_plan_id,issue_id) DO NOTHING;

-- ENG-19 (DONE) → Bob's RECONCILED current plan
INSERT INTO weekly_assignments (id,org_id,weekly_plan_id,issue_id,chess_priority_override,expected_result,confidence,tags,version,created_at,updated_at)
VALUES ('fa010000-0000-0000-0019-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'b0000000-0000-0000-0000-000000000020'::uuid,'e1000019-0000-0000-0000-000000000001'::uuid,'KING','Demo env live, 3 sample orgs with SSO bypass, cleared for sales use.',0.90,'{}',2,NOW()-interval'9 days',NOW()-interval'7 days')
ON CONFLICT (weekly_plan_id,issue_id) DO NOTHING;

INSERT INTO weekly_assignment_actuals (assignment_id,org_id,actual_result,completion_status,delta_reason,hours_spent,created_at,updated_at)
VALUES ('fa010000-0000-0000-0019-000000000001'::uuid,'a0000000-0000-0000-0000-000000000001'::uuid,'Demo env live with 3 sample orgs. SSO bypass works. VP Sales approved for use.','DONE',NULL,14.0,NOW()-interval'7 days',NOW()-interval'7 days')
ON CONFLICT (assignment_id) DO NOTHING;


-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │  USER MODEL SNAPSHOTS — all 4 personas                                  │
-- └──────────────────────────────────────────────────────────────────────────┘

INSERT INTO user_model_snapshots (org_id,user_id,computed_at,weeks_analyzed,model_json) VALUES

('a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000010'::uuid,NOW(),6,'{
  "performanceProfile":{
    "estimationAccuracy":0.82,"completionReliability":0.89,"avgCommitsPerWeek":3.0,
    "avgCarryForwardPerWeek":0.17,"topCategories":["DELIVERY","LEARNING"],
    "categoryCompletionRates":{"DELIVERY":0.92,"LEARNING":0.75,"PEOPLE":1.0,"OPERATIONS":1.0},
    "priorityCompletionRates":{"KING":0.83,"QUEEN":1.0,"ROOK":1.0,"KNIGHT":0.75,"PAWN":1.0}
  },
  "preferences":{
    "typicalPriorityPattern":"1K-1R-1K/P",
    "recurringCommitTitles":["onboarding wizard","auth module tests","tech talk prep"],
    "avgCheckInsPerWeek":2.0,"preferredUpdateDays":["TUESDAY","THURSDAY"]
  },
  "trends":{"strategicAlignmentTrend":"IMPROVING","completionTrend":"IMPROVING","carryForwardTrend":"STABLE"}
}'),

('a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000020'::uuid,NOW(),5,'{
  "performanceProfile":{
    "estimationAccuracy":0.91,"completionReliability":0.93,"avgCommitsPerWeek":3.2,
    "avgCarryForwardPerWeek":0.0,"topCategories":["DELIVERY","CUSTOMER"],
    "categoryCompletionRates":{"DELIVERY":0.93,"CUSTOMER":0.67,"PEOPLE":1.0,"OPERATIONS":1.0},
    "priorityCompletionRates":{"KING":1.0,"QUEEN":0.80,"ROOK":1.0,"BISHOP":1.0,"PAWN":1.0}
  },
  "preferences":{
    "typicalPriorityPattern":"1K-1Q-1R/P",
    "recurringCommitTitles":["demo environment","API optimization","health-score"],
    "avgCheckInsPerWeek":1.8,"preferredUpdateDays":["MONDAY","FRIDAY"]
  },
  "trends":{"strategicAlignmentTrend":"STABLE","completionTrend":"STABLE","carryForwardTrend":"IMPROVING"}
}'),

('a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000001'::uuid,NOW(),5,'{
  "performanceProfile":{
    "estimationAccuracy":0.90,"completionReliability":0.95,"avgCommitsPerWeek":3.0,
    "avgCarryForwardPerWeek":0.0,"topCategories":["PEOPLE","OPERATIONS"],
    "categoryCompletionRates":{"PEOPLE":0.95,"OPERATIONS":0.90,"DELIVERY":1.0},
    "priorityCompletionRates":{"KING":1.0,"QUEEN":1.0,"ROOK":1.0,"PAWN":0.83}
  },
  "preferences":{
    "typicalPriorityPattern":"1K-1Q-1P",
    "recurringCommitTitles":["review reconciliations","1:1s","budget","vendor"],
    "avgCheckInsPerWeek":1.5,"preferredUpdateDays":["MONDAY","WEDNESDAY"]
  },
  "trends":{"strategicAlignmentTrend":"STABLE","completionTrend":"IMPROVING","carryForwardTrend":"STABLE"}
}'),

('a0000000-0000-0000-0000-000000000001'::uuid,'c0000000-0000-0000-0000-000000000030'::uuid,NOW(),4,'{
  "performanceProfile":{
    "estimationAccuracy":0.88,"completionReliability":0.97,"avgCommitsPerWeek":3.5,
    "avgCarryForwardPerWeek":0.0,"topCategories":["OPERATIONS","PEOPLE"],
    "categoryCompletionRates":{"OPERATIONS":1.0,"PEOPLE":0.95},
    "priorityCompletionRates":{"KING":1.0,"QUEEN":0.95,"ROOK":1.0,"PAWN":1.0}
  },
  "preferences":{
    "typicalPriorityPattern":"1K-1Q-1R-1P",
    "recurringCommitTitles":["SOC2 evidence","engineer tech talks","budget forecasting"],
    "avgCheckInsPerWeek":1.5,"preferredUpdateDays":["MONDAY","THURSDAY"]
  },
  "trends":{"strategicAlignmentTrend":"IMPROVING","completionTrend":"IMPROVING","carryForwardTrend":"IMPROVING"}
}')

ON CONFLICT (org_id,user_id) DO NOTHING;


-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │  GENERAL TEAM — add all personas as members                             │
-- └──────────────────────────────────────────────────────────────────────────┘
INSERT INTO team_members (team_id,user_id,org_id,role,joined_at)
SELECT t.id,u.user_id,t.org_id,u.role,NOW()
FROM teams t
CROSS JOIN (VALUES
  ('c0000000-0000-0000-0000-000000000001'::uuid,'MEMBER'),
  ('c0000000-0000-0000-0000-000000000010'::uuid,'MEMBER'),
  ('c0000000-0000-0000-0000-000000000020'::uuid,'MEMBER'),
  ('c0000000-0000-0000-0000-000000000030'::uuid,'MEMBER')
) AS u(user_id,role)
WHERE t.name='General' AND t.org_id='a0000000-0000-0000-0000-000000000001'::uuid
ON CONFLICT (team_id,user_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- W-1 FILL (week of Mar 16–22) — Alice, Bob, Carol
--
-- Before this patch Alice had 1 commit for W-1, Bob had 0, Carol had 0.
-- All three plans are RECONCILED/APPROVED so the prior week looks complete.
-- Narrative continuity:
--   Alice  — delivered tech talk (prepped in W-2), pushed auth coverage to 90%,
--             wrote structured logging ADR, facilitated sprint retro
--   Bob    — completed dashboard materialized view (partial in W-2), unblocked
--             health-score pipeline after credentials arrived, circuit breaker spike
--   Carol  — reviewed Alice & Bob W-1 reconciliations, closed senior engineer
--             offer, submitted Q3 headcount plan to leadership
-- ═══════════════════════════════════════════════════════════════════════════

-- ── ALICE W-1 — patch existing plan + add 3 more commits ─────────────────

-- Backfill hours / confidence on the existing single commit
UPDATE weekly_commits
   SET estimated_hours = 10.0, confidence = 0.85
 WHERE id = 'd0000000-0000-0000-0000-000000000014'::uuid;

-- Commit 2: deliver the tech talk that was prepped during W-2
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    version)
VALUES (
    'da010002-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000011'::uuid,
    'Deliver event sourcing tech talk at eng all-hands',
    'KNIGHT', 'LEARNING',
    '30000000-0000-0000-0000-000000000009'::uuid,
    4.0, 0.90,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
    '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'da010002-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Talk delivered to 22 engineers. Received top NPS score of the quarter (4.8/5). Recording shared in #engineering-learning.',
    'DONE', 3.5, NOW() - interval '5 days', NOW() - interval '5 days'
) ON CONFLICT (commit_id) DO NOTHING;

-- Commit 3: structured logging ADR + implementation kick-off (links to ENG-8)
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    version)
VALUES (
    'da010003-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000011'::uuid,
    'Write ADR and scaffold structured logging with correlation IDs',
    'ROOK', 'DELIVERY',
    'e0000000-0000-0000-0000-000000000002'::uuid,
    8.0, 0.80,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
    'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'da010003-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'ADR approved. JSON structured logging scaffolded across auth and onboarding services. Correlation IDs propagate end-to-end through 3 hops.',
    'DONE', 9.0, NOW() - interval '4 days', NOW() - interval '4 days'
) ON CONFLICT (commit_id) DO NOTHING;

-- Commit 4: sprint retro facilitation (non-strategic)
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category,
    non_strategic_reason,
    estimated_hours, confidence,
    version)
VALUES (
    'da010004-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'b0000000-0000-0000-0000-000000000011'::uuid,
    'Facilitate sprint retrospective and planning',
    'PAWN', 'OPERATIONS',
    'Routine ceremony facilitation — not tied to a strategic outcome',
    2.0, 0.95,
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'da010004-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Retro done. 5 action items captured. Velocity discussion revealed estimation gap — will use story points next sprint.',
    'DONE', 2.0, NOW() - interval '4 days', NOW() - interval '4 days'
) ON CONFLICT (commit_id) DO NOTHING;


-- ── BOB W-1 — new RECONCILED plan ─────────────────────────────────────────

INSERT INTO weekly_plans (
    id, org_id, owner_user_id, week_start_date,
    state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES (
    'bb010000-0000-0000-0000-b00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000020'::uuid,
    date_trunc('week', CURRENT_DATE)::date - 7,
    'RECONCILED', 'APPROVED', 'ON_TIME',
    NOW() - interval '10 days', 3,
    NOW() - interval '11 days', NOW() - interval '4 days'
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Bob W-1 commit 1: dashboard materialized view (completing the W-2 partial)
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    carried_from_commit_id,  version)
VALUES (
    'cb010001-0000-0000-0000-b00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'bb010000-0000-0000-0000-b00000000001'::uuid,
    'Ship dashboard materialized view — reduce p95 to < 200ms',
    'KING', 'DELIVERY',
    '30000000-0000-0000-0000-000000000007'::uuid,
    10.0, 0.85,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
    '30000000-0000-0000-0000-000000000007'::uuid, 'Reduce deploy-to-production time to < 15 min',
    'cb020002-0000-0000-0000-b00000000001'::uuid,
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'cb010001-0000-0000-0000-b00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Materialized view live in production. Dashboard p95 now 145ms — 48% improvement. Auto-refresh every 30s via pg_cron.',
    'DONE', 11.0, NOW() - interval '5 days', NOW() - interval '5 days'
) ON CONFLICT (commit_id) DO NOTHING;

-- Bob W-1 commit 2: health-score alerting pipeline (unblocked from W-3 partial)
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    carried_from_commit_id,  version)
VALUES (
    'cb010002-0000-0000-0000-b00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'bb010000-0000-0000-0000-b00000000001'::uuid,
    'Ship health-score alerting pipeline to production',
    'QUEEN', 'CUSTOMER',
    '30000000-0000-0000-0000-000000000011'::uuid,
    12.0, 0.75,
    '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession',
    '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
    '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting',
    'cb030002-0000-0000-0000-b00000000001'::uuid,
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'cb010002-0000-0000-0000-b00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Pipeline deployed to production. Credentials finally provisioned by platform team. 3 CSMs received their first alerts within 2 hours of go-live. Zero false positives in first 48h.',
    'DONE', 13.0, NOW() - interval '5 days', NOW() - interval '5 days'
) ON CONFLICT (commit_id) DO NOTHING;

-- Bob W-1 commit 3: add circuit breakers to external service calls (ENG-18)
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    version)
VALUES (
    'cb010003-0000-0000-0000-b00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'bb010000-0000-0000-0000-b00000000001'::uuid,
    'Add circuit breakers to Stripe, SendGrid, and PagerDuty clients',
    'ROOK', 'DELIVERY',
    'e0000000-0000-0000-0000-000000000002'::uuid,
    8.0, 0.80,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
    'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'cb010003-0000-0000-0000-b00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Resilience4j circuit breakers on all 3 external clients. Fallback strategies: Stripe → retry queue, SendGrid → SES fallback, PagerDuty → log only. Load test passed.',
    'DONE', 9.0, NOW() - interval '4 days', NOW() - interval '4 days'
) ON CONFLICT (commit_id) DO NOTHING;


-- ── CAROL W-1 — new RECONCILED plan ───────────────────────────────────────

INSERT INTO weekly_plans (
    id, org_id, owner_user_id, week_start_date,
    state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES (
    'bc010000-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'c0000000-0000-0000-0000-000000000001'::uuid,
    date_trunc('week', CURRENT_DATE)::date - 7,
    'RECONCILED', 'APPROVED', 'ON_TIME',
    NOW() - interval '10 days', 3,
    NOW() - interval '11 days', NOW() - interval '4 days'
) ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Carol W-1 commit 1: review Alice and Bob W-1 reconciliations
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    version)
VALUES (
    'cc010001-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'bc010000-0000-0000-0000-c00000000001'::uuid,
    'Review and approve Alice and Bob W-1 reconciliations',
    'KING', 'PEOPLE',
    '30000000-0000-0000-0000-000000000009'::uuid,
    4.0, 0.90,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
    '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'cc010001-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Both approved. Alice: excellent tech talk delivery and logging work. Bob: outstanding — shipped two long-running items in one week after credential unblock. Written feedback shared.',
    'DONE', 3.5, NOW() - interval '5 days', NOW() - interval '5 days'
) ON CONFLICT (commit_id) DO NOTHING;

-- Carol W-1 commit 2: close senior engineer hire
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    version)
VALUES (
    'cc010002-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'bc010000-0000-0000-0000-c00000000001'::uuid,
    'Close senior engineer offer and kick off onboarding',
    'QUEEN', 'PEOPLE',
    '30000000-0000-0000-0000-000000000009'::uuid,
    5.0, 0.85,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
    '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'cc010002-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Offer accepted by top candidate (Priya S, Staff Engineer). Start date: Apr 7. Onboarding doc drafted and assigned to Alice as buddy.',
    'DONE', 4.0, NOW() - interval '5 days', NOW() - interval '5 days'
) ON CONFLICT (commit_id) DO NOTHING;

-- Carol W-1 commit 3: Q3 headcount plan
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id,
    estimated_hours, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    version)
VALUES (
    'cc010003-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'bc010000-0000-0000-0000-c00000000001'::uuid,
    'Submit final Q3 headcount plan to leadership',
    'ROOK', 'PEOPLE',
    'e0000000-0000-0000-0000-000000000002'::uuid,
    3.0, 0.90,
    '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture',
    '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
    'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime',
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'cc010003-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    '3 headcount requests submitted: 1 Staff Eng (Priya, offer accepted), 1 Senior SRE (pipeline), 1 mid-level (backfill). VP approved all 3. Finance alignment confirmed.',
    'DONE', 2.5, NOW() - interval '5 days', NOW() - interval '5 days'
) ON CONFLICT (commit_id) DO NOTHING;

-- ── Carol W-1 admin PAWN (non-strategic) ─────────────────────────────────
INSERT INTO weekly_commits (
    id, org_id, weekly_plan_id, title, chess_priority, category,
    non_strategic_reason,
    estimated_hours, confidence,
    version)
VALUES (
    'cc010004-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'bc010000-0000-0000-0000-c00000000001'::uuid,
    'Quarterly compliance training and expense reconciliation',
    'PAWN', 'OPERATIONS',
    'Mandatory compliance training and monthly admin tasks — not tied to strategic outcomes',
    2.0, 0.95,
    2
) ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at)
VALUES (
    'cc010004-0000-0000-0000-c00000000001'::uuid,
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'Compliance training completed (100%). March expense report submitted.',
    'DONE', 1.5, NOW() - interval '4 days', NOW() - interval '4 days'
) ON CONFLICT (commit_id) DO NOTHING;
