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
