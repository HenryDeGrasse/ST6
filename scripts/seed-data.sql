-- ---------------------------------------------------------------------------
-- Seed data for local development (PRD §12.9)
--
-- Four personas with realistic history:
--   Alice Chen   (IC)               — current week DRAFT; strong DELIVERY focus
--   Bob Martinez (IC)               — current week RECONCILED; customer + ops focus
--   Carol Park   (Manager)          — current week LOCKED; manages Alice & Bob
--   Dana Torres  (Admin+Mgr+IC)     — 4 weeks history (W-4..W-1); PEOPLE & OPERATIONS focus
--
-- Includes: plans, commits with hours, actuals, progress entries,
--           outcome metadata with target dates, and carry-forward chains.
--
-- Shared by: seed-local.sh, dev.sh (Docker fallback)
-- ---------------------------------------------------------------------------

-- ═══════════════════════════════════════════════════════════════════════════
-- Constants
-- ═══════════════════════════════════════════════════════════════════════════

-- Org
-- a0000000-0000-0000-0000-000000000001

-- Users
-- Alice:  c0000000-0000-0000-0000-000000000010
-- Bob:    c0000000-0000-0000-0000-000000000020
-- Carol:  c0000000-0000-0000-0000-000000000001
-- Dana:   c0000000-0000-0000-0000-000000000030

-- Outcome IDs (from RcdoDevDataInitializer):
--   e0000000-…-000001  Close 10 enterprise deals in Q1
--   30000000-…-000002  Launch enterprise demo environment
--   30000000-…-000003  Reduce sales cycle by 20%
--   30000000-…-000004  Sign 3 healthcare pilot customers
--   30000000-…-000005  Complete SOC2 Type II certification
--   e0000000-…-000002  Achieve 99.9% API uptime
--   30000000-…-000007  Reduce deploy-to-production time to < 15 min
--   30000000-…-000008  Increase unit test coverage to 85%
--   30000000-…-000009  Every engineer presents at a tech talk
--   30000000-…-000010  Implement weekly commitments module
--   30000000-…-000011  Launch proactive health-score alerting
--   30000000-…-000012  Achieve NPS > 60

-- ═══════════════════════════════════════════════════════════════════════════
-- Org policies
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO org_policies (org_id, chess_king_required, chess_max_king, chess_max_queen)
VALUES ('a0000000-0000-0000-0000-000000000001'::uuid, true, 1, 2)
ON CONFLICT (org_id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Helper: week offsets
--   W-7 = 7 weeks ago, W-6 = 6 weeks ago, … W-1 = last week, W0 = this week
-- ═══════════════════════════════════════════════════════════════════════════

-- We use date_trunc('week', CURRENT_DATE) as the current Monday.

-- ═══════════════════════════════════════════════════════════════════════════
-- ALICE CHEN — 8 weeks of history + current week DRAFT
-- Strong DELIVERY focus, good completion rate, slight overestimation pattern
-- ═══════════════════════════════════════════════════════════════════════════

-- ── W-7 (RECONCILED + APPROVED) ─────────────────────────────────────────

INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-a00000000007'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date - 49,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '52 days', 4, NOW() - interval '53 days', NOW() - interval '46 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-a070-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000007'::uuid,
   'Design SSO integration spec', 'KING', 'DELIVERY', 'e0000000-0000-0000-0000-000000000001'::uuid, 12.0, 0.80,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1', 2),
  ('d1000000-0000-0000-a070-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000007'::uuid,
   'Set up CI pipeline for onboarding service', 'QUEEN', 'DELIVERY', '30000000-0000-0000-0000-000000000007'::uuid, 8.0, 0.85,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   '30000000-0000-0000-0000-000000000007'::uuid, 'Reduce deploy-to-production time to < 15 min', 2),
  ('d1000000-0000-0000-a070-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000007'::uuid,
   'Weekly ops review and incident retro', 'ROOK', 'OPERATIONS', NULL, 3.0, 0.90,
   NULL, NULL, NULL, NULL, NULL, NULL, 2),
  ('d1000000-0000-0000-a070-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000007'::uuid,
   'Add unit tests for auth JWT validation', 'ROOK', 'DELIVERY', '30000000-0000-0000-0000-000000000008'::uuid, 6.0, 0.75,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   '30000000-0000-0000-0000-000000000008'::uuid, 'Increase unit test coverage to 85%', 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-a070-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'SSO spec completed and reviewed by security team.', 'DONE', 14.0, NOW() - interval '46 days', NOW() - interval '46 days'),
  ('d1000000-0000-0000-a070-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'CI pipeline running with staging deploy.', 'DONE', 10.0, NOW() - interval '46 days', NOW() - interval '46 days'),
  ('d1000000-0000-0000-a070-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Retro done, 2 action items created.', 'DONE', 2.5, NOW() - interval '46 days', NOW() - interval '46 days'),
  ('d1000000-0000-0000-a070-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Got 75% coverage, some edge cases remain.', 'PARTIALLY', 8.0, NOW() - interval '46 days', NOW() - interval '46 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Progress entries for W-7
INSERT INTO progress_entries (id, org_id, commit_id, status, note, note_source, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a070-000000000001'::uuid, 'ON_TRACK', 'Started requirements gathering with sales team.', 'USER_TYPED', NOW() - interval '51 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a070-000000000001'::uuid, 'ON_TRACK', 'First draft of SSO flow diagrams done.', 'USER_TYPED', NOW() - interval '49 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a070-000000000002'::uuid, 'ON_TRACK', 'Dockerfile and build config merged.', 'USER_TYPED', NOW() - interval '50 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a070-000000000004'::uuid, 'AT_RISK', 'JWT edge cases harder than expected.', 'USER_TYPED', NOW() - interval '48 days')
ON CONFLICT DO NOTHING;

-- ── W-6 through W-2: more RECONCILED plans for Alice ────────────────────

-- W-6
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-a00000000006'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date - 42,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '45 days', 4, NOW() - interval '46 days', NOW() - interval '39 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-a060-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000006'::uuid,
   'Implement SSO SAML flow', 'KING', 'DELIVERY', 'e0000000-0000-0000-0000-000000000001'::uuid, 16.0, 0.70,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1', 2),
  ('d1000000-0000-0000-a060-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000006'::uuid,
   'Write auth module integration tests', 'QUEEN', 'DELIVERY', '30000000-0000-0000-0000-000000000008'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   '30000000-0000-0000-0000-000000000008'::uuid, 'Increase unit test coverage to 85%', 2),
  ('d1000000-0000-0000-a060-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000006'::uuid,
   'Prep tech talk on event sourcing', 'KNIGHT', 'LEARNING', '30000000-0000-0000-0000-000000000009'::uuid, 4.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk', 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-a060-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'SAML flow working in staging. IDP metadata parsing edge case found.', 'PARTIALLY', 18.0, NOW() - interval '39 days', NOW() - interval '39 days'),
  ('d1000000-0000-0000-a060-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Integration tests complete, 88% coverage on auth module.', 'DONE', 9.0, NOW() - interval '39 days', NOW() - interval '39 days'),
  ('d1000000-0000-0000-a060-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Slides drafted, dry run scheduled for next week.', 'DONE', 3.5, NOW() - interval '39 days', NOW() - interval '39 days')
ON CONFLICT (commit_id) DO NOTHING;

INSERT INTO progress_entries (id, org_id, commit_id, status, note, note_source, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a060-000000000001'::uuid, 'ON_TRACK', 'SAML assertion builder working.', 'USER_TYPED', NOW() - interval '43 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a060-000000000001'::uuid, 'AT_RISK', 'IDP metadata parsing more complex than expected.', 'USER_TYPED', NOW() - interval '41 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a060-000000000002'::uuid, 'ON_TRACK', 'Test harness for auth module ready.', 'USER_TYPED', NOW() - interval '42 days')
ON CONFLICT DO NOTHING;

-- W-5
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-a00000000005'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date - 35,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '38 days', 4, NOW() - interval '39 days', NOW() - interval '32 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name,
    carried_from_commit_id, version)
VALUES
  ('d1000000-0000-0000-a050-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000005'::uuid,
   'Fix IDP metadata parsing edge cases', 'KING', 'DELIVERY', 'e0000000-0000-0000-0000-000000000001'::uuid, 10.0, 0.75,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1',
   'd1000000-0000-0000-a060-000000000001'::uuid, 2),
  ('d1000000-0000-0000-a050-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000005'::uuid,
   'Onboarding wizard wireframes', 'QUEEN', 'DELIVERY', 'e0000000-0000-0000-0000-000000000001'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1', NULL, 2),
  ('d1000000-0000-0000-a050-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000005'::uuid,
   'Update team wiki with new runbook', 'PAWN', 'OPERATIONS', NULL, 2.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-a050-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'All IDP metadata edge cases resolved. SSO flow end-to-end working.', 'DONE', 12.0, NOW() - interval '32 days', NOW() - interval '32 days'),
  ('d1000000-0000-0000-a050-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Wireframes approved by design team.', 'DONE', 7.0, NOW() - interval '32 days', NOW() - interval '32 days'),
  ('d1000000-0000-0000-a050-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Runbook updated.', 'DONE', 1.5, NOW() - interval '32 days', NOW() - interval '32 days')
ON CONFLICT (commit_id) DO NOTHING;

-- W-4
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-a00000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date - 28,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '31 days', 4, NOW() - interval '32 days', NOW() - interval '25 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-a040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000004'::uuid,
   'Build onboarding wizard step 1-3', 'KING', 'DELIVERY', 'e0000000-0000-0000-0000-000000000001'::uuid, 16.0, 0.70,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1', 2),
  ('d1000000-0000-0000-a040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000004'::uuid,
   'SOC2 evidence collection: access controls', 'ROOK', 'DELIVERY', '30000000-0000-0000-0000-000000000005'::uuid, 6.0, 0.85,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000005'::uuid, 'Complete SOC2 Type II certification', 2),
  ('d1000000-0000-0000-a040-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000004'::uuid,
   'Weekly ops review', 'PAWN', 'OPERATIONS', NULL, 2.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-a040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Steps 1-2 done, step 3 needs SSO callback handling.', 'PARTIALLY', 18.0, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d1000000-0000-0000-a040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Access control evidence collected and documented.', 'DONE', 5.0, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d1000000-0000-0000-a040-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Ops review done, no incidents.', 'DONE', 1.5, NOW() - interval '25 days', NOW() - interval '25 days')
ON CONFLICT (commit_id) DO NOTHING;

INSERT INTO progress_entries (id, org_id, commit_id, status, note, note_source, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a040-000000000001'::uuid, 'ON_TRACK', 'Step 1 form validation complete.', 'USER_TYPED', NOW() - interval '29 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a040-000000000001'::uuid, 'ON_TRACK', 'Step 2 API integration working.', 'USER_TYPED', NOW() - interval '27 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a040-000000000001'::uuid, 'AT_RISK', 'SSO callback integration more complex than planned.', 'USER_TYPED', NOW() - interval '26 days')
ON CONFLICT DO NOTHING;

-- W-3
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-a00000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date - 21,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '24 days', 4, NOW() - interval '25 days', NOW() - interval '18 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name,
    carried_from_commit_id, version)
VALUES
  ('d1000000-0000-0000-a030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000003'::uuid,
   'Complete onboarding wizard step 3 (SSO callback)', 'KING', 'DELIVERY', 'e0000000-0000-0000-0000-000000000001'::uuid, 10.0, 0.80,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1',
   'd1000000-0000-0000-a040-000000000001'::uuid, 2),
  ('d1000000-0000-0000-a030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000003'::uuid,
   'Deploy staging environment for demo', 'QUEEN', 'DELIVERY', '30000000-0000-0000-0000-000000000002'::uuid, 8.0, 0.85,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   '30000000-0000-0000-0000-000000000002'::uuid, 'Launch enterprise demo environment', NULL, 2),
  ('d1000000-0000-0000-a030-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000003'::uuid,
   'Deliver tech talk on event sourcing', 'KNIGHT', 'LEARNING', '30000000-0000-0000-0000-000000000009'::uuid, 3.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk', NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-a030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'SSO callback flow complete. Full onboarding wizard working end-to-end.', 'DONE', 11.0, NOW() - interval '18 days', NOW() - interval '18 days'),
  ('d1000000-0000-0000-a030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Staging deployed with 3 sample orgs.', 'DONE', 6.0, NOW() - interval '18 days', NOW() - interval '18 days'),
  ('d1000000-0000-0000-a030-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Tech talk delivered, positive feedback.', 'DONE', 3.5, NOW() - interval '18 days', NOW() - interval '18 days')
ON CONFLICT (commit_id) DO NOTHING;

-- W-2
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-a00000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date - 14,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '17 days', 4, NOW() - interval '18 days', NOW() - interval '11 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d1000000-0000-0000-a020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000002'::uuid,
   'Healthcare pilot: technical POC prep', 'KING', 'DELIVERY', '30000000-0000-0000-0000-000000000004'::uuid, 14.0, 0.65,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000004'::uuid, 'Sign 3 healthcare pilot customers', 2),
  ('d1000000-0000-0000-a020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000002'::uuid,
   'E2E test suite for onboarding flow', 'QUEEN', 'DELIVERY', 'e0000000-0000-0000-0000-000000000001'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   'e0000000-0000-0000-0000-000000000001'::uuid, 'Close 10 enterprise deals in Q1', 2),
  ('d1000000-0000-0000-a020-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b1000000-0000-0000-0000-a00000000002'::uuid,
   'Weekly ops review', 'PAWN', 'OPERATIONS', NULL, 2.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d1000000-0000-0000-a020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'POC environment ready but HIPAA data questions unresolved.', 'PARTIALLY', 16.0, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d1000000-0000-0000-a020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'E2E tests passing for steps 1-3 and SSO.', 'DONE', 9.0, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d1000000-0000-0000-a020-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Ops review done.', 'DONE', 1.5, NOW() - interval '11 days', NOW() - interval '11 days')
ON CONFLICT (commit_id) DO NOTHING;

INSERT INTO progress_entries (id, org_id, commit_id, status, note, note_source, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a020-000000000001'::uuid, 'ON_TRACK', 'POC infrastructure provisioned.', 'USER_TYPED', NOW() - interval '15 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a020-000000000001'::uuid, 'BLOCKED', 'Waiting on legal for HIPAA data handling approval.', 'USER_TYPED', NOW() - interval '13 days')
ON CONFLICT DO NOTHING;

-- W-1 (last week)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000011'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date - 7,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '10 days', 3, NOW() - interval '11 days', NOW() - interval '4 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name,
    carried_from_commit_id, version)
VALUES
  ('d0000000-0000-0000-0000-000000000014'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000011'::uuid,
   'Healthcare POC: resolve HIPAA data questions', 'KING', 'DELIVERY', '30000000-0000-0000-0000-000000000004'::uuid, 10.0, 0.70,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000004'::uuid, 'Sign 3 healthcare pilot customers',
   'd1000000-0000-0000-a020-000000000001'::uuid, 2),
  ('d1000000-0000-0000-a010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000011'::uuid,
   'Deploy-to-production pipeline optimization', 'QUEEN', 'DELIVERY', '30000000-0000-0000-0000-000000000007'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   '30000000-0000-0000-0000-000000000007'::uuid, 'Reduce deploy-to-production time to < 15 min', NULL, 2),
  ('d1000000-0000-0000-a010-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000011'::uuid,
   'Weekly ops review and incident retro', 'PAWN', 'OPERATIONS', NULL, 2.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d0000000-0000-0000-0000-000000000014'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Legal approved HIPAA handling. POC data pipeline working.', 'DONE', 12.0, NOW() - interval '4 days', NOW() - interval '4 days'),
  ('d1000000-0000-0000-a010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Pipeline optimized from 22 min to 13 min.', 'DONE', 7.0, NOW() - interval '4 days', NOW() - interval '4 days'),
  ('d1000000-0000-0000-a010-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'No incidents this week.', 'DONE', 1.0, NOW() - interval '4 days', NOW() - interval '4 days')
ON CONFLICT (commit_id) DO NOTHING;

INSERT INTO progress_entries (id, org_id, commit_id, status, note, note_source, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd0000000-0000-0000-0000-000000000014'::uuid, 'ON_TRACK', 'Legal call scheduled for Tuesday.', 'USER_TYPED', NOW() - interval '8 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd0000000-0000-0000-0000-000000000014'::uuid, 'ON_TRACK', 'Legal approved. Building data pipeline.', 'USER_TYPED', NOW() - interval '6 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd1000000-0000-0000-a010-000000000002'::uuid, 'ON_TRACK', 'Identified 3 slow stages in the pipeline.', 'USER_TYPED', NOW() - interval '7 days')
ON CONFLICT DO NOTHING;

-- W0 (current week — DRAFT)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, version, created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000010'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000010'::uuid, date_trunc('week', CURRENT_DATE)::date,
        'DRAFT', 'REVIEW_NOT_APPLICABLE', 1, NOW() - interval '1 day', NOW() - interval '1 day')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id, estimated_hours, expected_result, version)
VALUES
  ('d0000000-0000-0000-0000-000000000010'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000010'::uuid,
   'Build onboarding flow for enterprise customers', 'Design and implement the multi-step onboarding wizard with SSO integration.', 'KING', 'DELIVERY',
   'e0000000-0000-0000-0000-000000000001'::uuid, 14.0, 'Onboarding wizard deployed to staging with SSO working end-to-end.', 1),
  ('d0000000-0000-0000-0000-000000000011'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000010'::uuid,
   'Write integration tests for auth module', 'Cover the JWT validation, role extraction, and org-scoping paths.', 'ROOK', 'DELIVERY',
   '30000000-0000-0000-0000-000000000008'::uuid, 6.0, 'Auth module at 90%+ branch coverage.', 1),
  ('d0000000-0000-0000-0000-000000000012'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000010'::uuid,
   'Healthcare pilot: run first demo session', 'Schedule and run the initial demo for Acme Health.', 'QUEEN', 'DELIVERY',
   '30000000-0000-0000-0000-000000000004'::uuid, 6.0, 'Demo completed, feedback captured.', 1),
  ('d0000000-0000-0000-0000-000000000013'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000010'::uuid,
   'Update team wiki with new runbook', 'Document the incident response process and add links to dashboards.', 'PAWN', 'OPERATIONS',
   NULL, 2.0, NULL, 1)
ON CONFLICT DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- BOB MARTINEZ — 8 weeks of history + current week RECONCILED
-- Customer & ops focus, slightly lower completion rate, tends to underestimate
-- ═══════════════════════════════════════════════════════════════════════════

-- W-7
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b2000000-0000-0000-0000-b00000000007'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date - 49,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '52 days', 4, NOW() - interval '53 days', NOW() - interval '46 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d2000000-0000-0000-b070-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000007'::uuid,
   'Customer health-score API research', 'KING', 'CUSTOMER', '30000000-0000-0000-0000-000000000011'::uuid, 10.0, 0.75,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting', 2),
  ('d2000000-0000-0000-b070-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000007'::uuid,
   'Reduce API p95 latency audit', 'QUEEN', 'DELIVERY', '30000000-0000-0000-0000-000000000007'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   '30000000-0000-0000-0000-000000000007'::uuid, 'Reduce deploy-to-production time to < 15 min', 2),
  ('d2000000-0000-0000-b070-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000007'::uuid,
   'Upgrade CI runner fleet to ARM', 'PAWN', 'OPERATIONS', NULL, 4.0, 0.90,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d2000000-0000-0000-b070-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Identified health-score data sources. API design drafted.', 'DONE', 12.0, NOW() - interval '46 days', NOW() - interval '46 days'),
  ('d2000000-0000-0000-b070-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Profiled top 5 slow endpoints, identified N+1 queries.', 'DONE', 10.0, NOW() - interval '46 days', NOW() - interval '46 days'),
  ('d2000000-0000-0000-b070-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'ARM migration started, 2 of 5 runners converted.', 'PARTIALLY', 6.0, NOW() - interval '46 days', NOW() - interval '46 days')
ON CONFLICT (commit_id) DO NOTHING;

-- W-6 through W-2 for Bob (condensed)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at) VALUES
  ('b2000000-0000-0000-0000-b00000000006'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date - 42, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '45 days', 4, NOW() - interval '46 days', NOW() - interval '39 days'),
  ('b2000000-0000-0000-0000-b00000000005'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date - 35, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '38 days', 4, NOW() - interval '39 days', NOW() - interval '32 days'),
  ('b2000000-0000-0000-0000-b00000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date - 28, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '31 days', 4, NOW() - interval '32 days', NOW() - interval '25 days'),
  ('b2000000-0000-0000-0000-b00000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date - 21, 'RECONCILED', 'APPROVED', 'LATE_LOCK', NOW() - interval '23 days', 4, NOW() - interval '25 days', NOW() - interval '18 days'),
  ('b2000000-0000-0000-0000-b00000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date - 14, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '17 days', 4, NOW() - interval '18 days', NOW() - interval '11 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Bob W-6 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, carried_from_commit_id, version)
VALUES
  ('d2000000-0000-0000-b060-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000006'::uuid,
   'Build health-score alerting pipeline', 'KING', 'CUSTOMER', '30000000-0000-0000-0000-000000000011'::uuid, 14.0, 0.70,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting', NULL, 2),
  ('d2000000-0000-0000-b060-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000006'::uuid,
   'Fix N+1 queries on dashboard endpoint', 'QUEEN', 'DELIVERY', 'e0000000-0000-0000-0000-000000000002'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime', NULL, 2),
  ('d2000000-0000-0000-b060-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000006'::uuid,
   'Complete ARM runner migration', 'ROOK', 'OPERATIONS', NULL, 4.0, 0.85,
   NULL, NULL, NULL, NULL, NULL, NULL, 'd2000000-0000-0000-b070-000000000003'::uuid, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d2000000-0000-0000-b060-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Pipeline built but blocked on health-score API credentials.', 'NOT_DONE', 16.0, NOW() - interval '39 days', NOW() - interval '39 days'),
  ('d2000000-0000-0000-b060-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'N+1 queries fixed. Dashboard p95 dropped from 450ms to 180ms.', 'DONE', 9.0, NOW() - interval '39 days', NOW() - interval '39 days'),
  ('d2000000-0000-0000-b060-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'All runners migrated. Build times 15% faster.', 'DONE', 5.0, NOW() - interval '39 days', NOW() - interval '39 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Bob W-5 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, carried_from_commit_id, version)
VALUES
  ('d2000000-0000-0000-b050-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000005'::uuid,
   'Health-score alerting: get API credentials', 'KING', 'CUSTOMER', '30000000-0000-0000-0000-000000000011'::uuid, 8.0, 0.60,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting', 'd2000000-0000-0000-b060-000000000001'::uuid, 2),
  ('d2000000-0000-0000-b050-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000005'::uuid,
   'Add Redis caching for hot API paths', 'QUEEN', 'DELIVERY', 'e0000000-0000-0000-0000-000000000002'::uuid, 10.0, 0.75,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime', NULL, 2),
  ('d2000000-0000-0000-b050-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000005'::uuid,
   'NPS survey infrastructure setup', 'ROOK', 'CUSTOMER', '30000000-0000-0000-0000-000000000012'::uuid, 6.0, 0.80,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000012'::uuid, 'Achieve NPS > 60', NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d2000000-0000-0000-b050-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Credentials obtained. Pipeline integration tested.', 'DONE', 10.0, NOW() - interval '32 days', NOW() - interval '32 days'),
  ('d2000000-0000-0000-b050-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Redis caching deployed for 3 hot paths. Cache hit rate 92%.', 'DONE', 12.0, NOW() - interval '32 days', NOW() - interval '32 days'),
  ('d2000000-0000-0000-b050-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Survey tool integrated but email sending not tested.', 'PARTIALLY', 7.0, NOW() - interval '32 days', NOW() - interval '32 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Bob W-4 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d2000000-0000-0000-b040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000004'::uuid,
   'Health-score alerting: email integration', 'KING', 'CUSTOMER', '30000000-0000-0000-0000-000000000011'::uuid, 10.0, 0.75,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting', 2),
  ('d2000000-0000-0000-b040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000004'::uuid,
   'Customer feedback analysis sprint', 'QUEEN', 'CUSTOMER', '30000000-0000-0000-0000-000000000012'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000012'::uuid, 'Achieve NPS > 60', 2),
  ('d2000000-0000-0000-b040-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000004'::uuid,
   'On-call rotation and runbook updates', 'PAWN', 'OPERATIONS', NULL, 3.0, 0.90,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d2000000-0000-0000-b040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Email integration complete and tested with SES.', 'DONE', 12.0, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d2000000-0000-0000-b040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Analyzed 200 feedback entries, 5 themes identified.', 'DONE', 7.0, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d2000000-0000-0000-b040-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Runbooks updated.', 'DONE', 2.5, NOW() - interval '25 days', NOW() - interval '25 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Bob W-3 commits (late lock week)
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d2000000-0000-0000-b030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000003'::uuid,
   'Launch health-score alerting to 5 pilot accounts', 'KING', 'CUSTOMER', '30000000-0000-0000-0000-000000000011'::uuid, 12.0, 0.65,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting', 2),
  ('d2000000-0000-0000-b030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000003'::uuid,
   'Enterprise demo env: sample data seeding', 'QUEEN', 'DELIVERY', '30000000-0000-0000-0000-000000000002'::uuid, 6.0, 0.85,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   '30000000-0000-0000-0000-000000000002'::uuid, 'Launch enterprise demo environment', 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, delta_reason, created_at, updated_at) VALUES
  ('d2000000-0000-0000-b030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Launched to 3 of 5 pilot accounts. 2 accounts had data format issues.', 'PARTIALLY', 14.0, 'Data format issues with 2 pilot accounts required custom mapping.', NOW() - interval '18 days', NOW() - interval '18 days'),
  ('d2000000-0000-0000-b030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Sample data seeded for all 3 demo orgs.', 'DONE', 5.0, NULL, NOW() - interval '18 days', NOW() - interval '18 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Bob W-2 commits
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, carried_from_commit_id, version)
VALUES
  ('d2000000-0000-0000-b020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000002'::uuid,
   'Fix data format issues for remaining pilot accounts', 'KING', 'CUSTOMER', '30000000-0000-0000-0000-000000000011'::uuid, 8.0, 0.75,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting', 'd2000000-0000-0000-b030-000000000001'::uuid, 2),
  ('d2000000-0000-0000-b020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000002'::uuid,
   'API uptime monitoring dashboard', 'QUEEN', 'DELIVERY', 'e0000000-0000-0000-0000-000000000002'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime', NULL, 2),
  ('d2000000-0000-0000-b020-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000002'::uuid,
   'Vendor contract renewal reviews', 'PAWN', 'OPERATIONS', NULL, 3.0, 0.90,
   NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d2000000-0000-0000-b020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'All 5 pilot accounts now receiving health-score alerts.', 'DONE', 10.0, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d2000000-0000-0000-b020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Dashboard deployed with uptime, error rate, and latency panels.', 'DONE', 9.0, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d2000000-0000-0000-b020-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Reviewed 2 vendor contracts.', 'DONE', 2.0, NOW() - interval '11 days', NOW() - interval '11 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Bob W-1 (last week)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b2000000-0000-0000-0000-b00000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date - 7,
        'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '10 days', 4, NOW() - interval '11 days', NOW() - interval '4 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d2000000-0000-0000-b010-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000001'::uuid,
   'NPS survey: first batch send to 100 customers', 'KING', 'CUSTOMER', '30000000-0000-0000-0000-000000000012'::uuid, 10.0, 0.70,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000012'::uuid, 'Achieve NPS > 60', 2),
  ('d2000000-0000-0000-b010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b2000000-0000-0000-0000-b00000000001'::uuid,
   'Reduce API p95 latency below 200ms', 'QUEEN', 'DELIVERY', '30000000-0000-0000-0000-000000000007'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   '30000000-0000-0000-0000-000000000007'::uuid, 'Reduce deploy-to-production time to < 15 min', 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d2000000-0000-0000-b010-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Sent to 100 customers, 42 responses so far. NPS = 54.', 'DONE', 11.0, NOW() - interval '4 days', NOW() - interval '4 days'),
  ('d2000000-0000-0000-b010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Optimized 3 of 5 slow endpoints. Dashboard endpoint still at 280ms.', 'PARTIALLY', 9.0, NOW() - interval '4 days', NOW() - interval '4 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Bob W0 (current week — RECONCILED, awaiting review)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000020'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000020'::uuid, date_trunc('week', CURRENT_DATE)::date,
        'RECONCILED', 'REVIEW_PENDING', 'ON_TIME', NOW() - interval '4 days', 4, NOW() - interval '5 days', NOW() - interval '1 day')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id, estimated_hours,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name,
    expected_result, version)
VALUES
  ('d0000000-0000-0000-0000-000000000020'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000020'::uuid,
   'Launch enterprise demo environment', 'Deploy the multi-tenant demo instance with sample data and SSO bypass for sales.', 'KING', 'DELIVERY',
   '30000000-0000-0000-0000-000000000002'::uuid, 10.0,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000001'::uuid, 'Accelerate enterprise pipeline',
   '30000000-0000-0000-0000-000000000002'::uuid, 'Launch enterprise demo environment',
   'Demo env live with 3 sample orgs and working SSO bypass.', 3),
  ('d0000000-0000-0000-0000-000000000021'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000020'::uuid,
   'Reduce API p95 latency below 200ms', 'Optimize remaining slow endpoints.', 'QUEEN', 'DELIVERY',
   '30000000-0000-0000-0000-000000000007'::uuid, 8.0,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   '30000000-0000-0000-0000-000000000007'::uuid, 'Reduce deploy-to-production time to < 15 min',
   'p95 latency < 200ms on all critical endpoints.', 3),
  ('d0000000-0000-0000-0000-000000000022'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000020'::uuid,
   'Customer health-score alerting: expand to all accounts', 'Extend to remaining customer accounts beyond pilots.', 'QUEEN', 'CUSTOMER',
   '30000000-0000-0000-0000-000000000011'::uuid, 6.0,
   '10000000-0000-0000-0000-000000000003'::uuid, 'Customer obsession', '20000000-0000-0000-0000-000000000005'::uuid, 'Reduce churn to < 5%',
   '30000000-0000-0000-0000-000000000011'::uuid, 'Launch proactive health-score alerting',
   'Alerting pipeline deployed and sending to all active accounts.', 3),
  ('d0000000-0000-0000-0000-000000000023'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000020'::uuid,
   'Upgrade CI runner fleet to ARM', 'Migrate remaining GitHub Actions runners.', 'PAWN', 'OPERATIONS',
   NULL, 3.0,
   NULL, NULL, NULL, NULL, NULL, NULL,
   'CI runners migrated and build times validated.', 3)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, delta_reason, created_at, updated_at) VALUES
  ('d0000000-0000-0000-0000-000000000020'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Demo environment deployed with 3 sample orgs. SSO bypass works. Handed off to sales.', 'DONE', 8.0, NULL, NOW() - interval '1 day', NOW() - interval '1 day'),
  ('d0000000-0000-0000-0000-000000000021'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Optimized 3 of 5 slow endpoints. Dashboard endpoint still at 280ms.', 'PARTIALLY', 9.0, 'Dashboard aggregation query requires a materialized view — scheduled for next week.', NOW() - interval '1 day', NOW() - interval '1 day'),
  ('d0000000-0000-0000-0000-000000000022'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Blocked on access to the health-score API for non-pilot accounts.', 'NOT_DONE', 4.0, 'External dependency — platform team credential provisioning took longer than expected.', NOW() - interval '1 day', NOW() - interval '1 day'),
  ('d0000000-0000-0000-0000-000000000023'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'All runners migrated. Build times 15% faster.', 'DONE', 2.5, NULL, NOW() - interval '1 day', NOW() - interval '1 day')
ON CONFLICT (commit_id) DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- CAROL PARK — 6 weeks of history + current week LOCKED
-- Manager + IC, PEOPLE focus, high completion rate
-- ═══════════════════════════════════════════════════════════════════════════

-- W-5 through W-2 for Carol (condensed)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at) VALUES
  ('b3000000-0000-0000-0000-c00000000005'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000001'::uuid, date_trunc('week', CURRENT_DATE)::date - 35, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '38 days', 3, NOW() - interval '39 days', NOW() - interval '32 days'),
  ('b3000000-0000-0000-0000-c00000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000001'::uuid, date_trunc('week', CURRENT_DATE)::date - 28, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '31 days', 3, NOW() - interval '32 days', NOW() - interval '25 days'),
  ('b3000000-0000-0000-0000-c00000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000001'::uuid, date_trunc('week', CURRENT_DATE)::date - 21, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '24 days', 3, NOW() - interval '25 days', NOW() - interval '18 days'),
  ('b3000000-0000-0000-0000-c00000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000001'::uuid, date_trunc('week', CURRENT_DATE)::date - 14, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '17 days', 3, NOW() - interval '18 days', NOW() - interval '11 days'),
  ('b3000000-0000-0000-0000-c00000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000001'::uuid, date_trunc('week', CURRENT_DATE)::date - 7, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '10 days', 3, NOW() - interval '11 days', NOW() - interval '4 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Carol W-5: team alignment sprint
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  ('d3000000-0000-0000-c050-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000005'::uuid,
   'Q2 OKR workshop with leadership', 'KING', 'PEOPLE', 'e0000000-0000-0000-0000-000000000002'::uuid, 8.0, 0.85,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime', 2),
  ('d3000000-0000-0000-c050-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000005'::uuid,
   'Review and approve team reconciliations', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000010'::uuid, 4.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000010'::uuid, 'Implement weekly commitments module', 2),
  ('d3000000-0000-0000-c050-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000005'::uuid,
   'Expense reports and vendor reviews', 'PAWN', 'OPERATIONS', NULL, 3.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  ('d3000000-0000-0000-c050-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Workshop done, Q2 OKRs drafted.', 'DONE', 7.0, NOW() - interval '32 days', NOW() - interval '32 days'),
  ('d3000000-0000-0000-c050-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Both reconciliations reviewed with written feedback.', 'DONE', 3.5, NOW() - interval '32 days', NOW() - interval '32 days'),
  ('d3000000-0000-0000-c050-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Expense reports submitted.', 'DONE', 2.0, NOW() - interval '32 days', NOW() - interval '32 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Carol W-4, W-3, W-2, W-1 commits (similar manager pattern)
INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, chess_priority, category, outcome_id, estimated_hours, confidence,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name, version)
VALUES
  -- W-4
  ('d3000000-0000-0000-c040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000004'::uuid,
   'Finalize Q2 OKR alignment with board priorities', 'KING', 'PEOPLE', 'e0000000-0000-0000-0000-000000000002'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime', 2),
  ('d3000000-0000-0000-c040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000004'::uuid,
   'Team 1:1s and career development check-ins', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000009'::uuid, 5.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk', 2),
  -- W-3
  ('d3000000-0000-0000-c030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000003'::uuid,
   'SOC2 audit prep: review team evidence', 'KING', 'DELIVERY', '30000000-0000-0000-0000-000000000005'::uuid, 10.0, 0.75,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000005'::uuid, 'Complete SOC2 Type II certification', 2),
  ('d3000000-0000-0000-c030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000003'::uuid,
   'Review and approve team reconciliations', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000010'::uuid, 4.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000010'::uuid, 'Implement weekly commitments module', 2),
  -- W-2
  ('d3000000-0000-0000-c020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000002'::uuid,
   'Healthcare vertical go/no-go decision prep', 'KING', 'PEOPLE', '30000000-0000-0000-0000-000000000004'::uuid, 8.0, 0.80,
   '10000000-0000-0000-0000-000000000001'::uuid, 'Scale to $500M ARR', '20000000-0000-0000-0000-000000000002'::uuid, 'Expand into new verticals',
   '30000000-0000-0000-0000-000000000004'::uuid, 'Sign 3 healthcare pilot customers', 2),
  ('d3000000-0000-0000-c020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000002'::uuid,
   'Team capacity planning for Q2 sprint 1', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000010'::uuid, 5.0, 0.85,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000010'::uuid, 'Implement weekly commitments module', 2),
  -- W-1
  ('d3000000-0000-0000-c010-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000001'::uuid,
   'Sprint planning: align team to Q2 OKRs', 'KING', 'PEOPLE', '30000000-0000-0000-0000-000000000010'::uuid, 6.0, 0.85,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000010'::uuid, 'Implement weekly commitments module', 2),
  ('d3000000-0000-0000-c010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000001'::uuid,
   'Review and approve team reconciliations', 'QUEEN', 'PEOPLE', '30000000-0000-0000-0000-000000000010'::uuid, 4.0, 0.90,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000010'::uuid, 'Implement weekly commitments module', 2),
  ('d3000000-0000-0000-c010-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b3000000-0000-0000-0000-c00000000001'::uuid,
   'Expense reports and admin overhead', 'PAWN', 'OPERATIONS', NULL, 2.0, 0.95,
   NULL, NULL, NULL, NULL, NULL, NULL, 2)
ON CONFLICT DO NOTHING;

INSERT INTO weekly_commit_actuals (commit_id, org_id, actual_result, completion_status, actual_hours, created_at, updated_at) VALUES
  -- W-4
  ('d3000000-0000-0000-c040-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'OKRs finalized and presented to board.', 'DONE', 9.0, NOW() - interval '25 days', NOW() - interval '25 days'),
  ('d3000000-0000-0000-c040-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'All 1:1s completed. 2 development plans updated.', 'DONE', 4.5, NOW() - interval '25 days', NOW() - interval '25 days'),
  -- W-3
  ('d3000000-0000-0000-c030-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Evidence reviewed, 3 gaps identified for remediation.', 'DONE', 11.0, NOW() - interval '18 days', NOW() - interval '18 days'),
  ('d3000000-0000-0000-c030-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Reconciliations reviewed with feedback.', 'DONE', 3.0, NOW() - interval '18 days', NOW() - interval '18 days'),
  -- W-2
  ('d3000000-0000-0000-c020-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Decision packet prepared. Go decision pending legal review.', 'DONE', 7.0, NOW() - interval '11 days', NOW() - interval '11 days'),
  ('d3000000-0000-0000-c020-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Capacity plan drafted for Q2 sprint 1.', 'DONE', 4.0, NOW() - interval '11 days', NOW() - interval '11 days'),
  -- W-1
  ('d3000000-0000-0000-c010-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Sprint planning done. Team aligned to 3 OKR outcomes.', 'DONE', 5.0, NOW() - interval '4 days', NOW() - interval '4 days'),
  ('d3000000-0000-0000-c010-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Both reconciliations reviewed.', 'DONE', 3.0, NOW() - interval '4 days', NOW() - interval '4 days'),
  ('d3000000-0000-0000-c010-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'Expenses submitted.', 'DONE', 1.5, NOW() - interval '4 days', NOW() - interval '4 days')
ON CONFLICT (commit_id) DO NOTHING;

-- Carol W0 (current week — LOCKED)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid,
        'c0000000-0000-0000-0000-000000000001'::uuid, date_trunc('week', CURRENT_DATE)::date,
        'LOCKED', 'REVIEW_NOT_APPLICABLE', 'ON_TIME', NOW() - interval '3 days', 2, NOW() - interval '4 days', NOW() - interval '3 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

INSERT INTO weekly_commits (id, org_id, weekly_plan_id, title, description, chess_priority, category, outcome_id, estimated_hours,
    snapshot_rally_cry_id, snapshot_rally_cry_name, snapshot_objective_id, snapshot_objective_name, snapshot_outcome_id, snapshot_outcome_name,
    expected_result, version)
VALUES
  ('d0000000-0000-0000-0000-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid,
   'Finalize Q2 OKR alignment with leadership', 'Work with VP Eng to ensure Rally Cries map to board-level priorities.', 'KING', 'PEOPLE',
   'e0000000-0000-0000-0000-000000000002'::uuid, 8.0,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000003'::uuid, 'Ship reliable software faster',
   'e0000000-0000-0000-0000-000000000002'::uuid, 'Achieve 99.9% API uptime',
   'Q2 OKRs finalized and communicated to team.', 2),
  ('d0000000-0000-0000-0000-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid,
   'Review and approve team reconciliations', 'Review Bob and Alice weekly reconciliations, provide actionable feedback.', 'QUEEN', 'PEOPLE',
   '30000000-0000-0000-0000-000000000009'::uuid, 4.0,
   '10000000-0000-0000-0000-000000000002'::uuid, 'World-class engineering culture', '20000000-0000-0000-0000-000000000004'::uuid, 'Invest in team growth',
   '30000000-0000-0000-0000-000000000009'::uuid, 'Every engineer presents at a tech talk',
   'All pending reviews completed with written feedback.', 2),
  ('d0000000-0000-0000-0000-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid,
   'Expense reports and vendor contract renewals', 'Monthly administrative overhead.', 'PAWN', 'OPERATIONS',
   NULL, 2.0,
   NULL, NULL, NULL, NULL, NULL, NULL,
   'Expense reports submitted, contracts reviewed.', 2)
ON CONFLICT DO NOTHING;

-- Carol check-ins on current week
INSERT INTO progress_entries (id, org_id, commit_id, status, note, note_source, created_at) VALUES
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd0000000-0000-0000-0000-000000000001'::uuid, 'ON_TRACK', 'Scheduled alignment meeting with VP Eng for Wednesday.', 'USER_TYPED', NOW() - interval '2 days'),
  (gen_random_uuid(), 'a0000000-0000-0000-0000-000000000001'::uuid, 'd0000000-0000-0000-0000-000000000002'::uuid, 'ON_TRACK', 'Bob reconciliation reviewed. Alice still in draft.', 'USER_TYPED', NOW() - interval '1 day')
ON CONFLICT DO NOTHING;


-- ═══════════════════════════════════════════════════════════════════════════
-- DANA TORRES — 4 weeks of history (W-4 through W-1), all RECONCILED/APPROVED
-- ADMIN + MANAGER + IC, PEOPLE & OPERATIONS focus, high completion rate
-- userId: c0000000-0000-0000-0000-000000000030
-- Commit IDs: d1000000-0000-0000-d0XX-YYYYYYYYYYYY
-- ═══════════════════════════════════════════════════════════════════════════

-- Dana W-4 through W-1 (all RECONCILED/APPROVED)
INSERT INTO weekly_plans (id, org_id, owner_user_id, week_start_date, state, review_status, lock_type, locked_at, version, created_at, updated_at) VALUES
  ('b4000000-0000-0000-0000-d00000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 28, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '31 days', 3, NOW() - interval '32 days', NOW() - interval '25 days'),
  ('b4000000-0000-0000-0000-d00000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 21, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '24 days', 3, NOW() - interval '25 days', NOW() - interval '18 days'),
  ('b4000000-0000-0000-0000-d00000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 14, 'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '17 days', 3, NOW() - interval '18 days', NOW() - interval '11 days'),
  ('b4000000-0000-0000-0000-d00000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000030'::uuid, date_trunc('week', CURRENT_DATE)::date - 7,  'RECONCILED', 'APPROVED', 'ON_TIME', NOW() - interval '10 days', 3, NOW() - interval '11 days', NOW() - interval '4 days')
ON CONFLICT (org_id, owner_user_id, week_start_date) DO NOTHING;

-- Dana W-4 commits: SOC2 audit coordination, team tech-talk program, ops review
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

-- Dana W-3 commits: SOC2 evidence collection, health-score alerting exec sponsorship, hiring
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

-- Dana W-2 commits: SOC2 gap remediation, tech talk delivery, capacity planning
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

-- Dana W-1 commits: SOC2 pre-audit review, NPS exec briefing, performance reviews
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


-- ═══════════════════════════════════════════════════════════════════════════
-- OUTCOME METADATA — Target dates and progress for strategic intelligence
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- USER MODEL SNAPSHOTS — Pre-computed profiles so "My Profile" works immediately
-- ═══════════════════════════════════════════════════════════════════════════

-- Alice Chen — strong DELIVERY IC, slight overestimation pattern
INSERT INTO user_model_snapshots (org_id, user_id, computed_at, weeks_analyzed, model_json) VALUES
  ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000010'::uuid, NOW(), 8, '{
    "performanceProfile": {
      "estimationAccuracy": 0.72,
      "completionReliability": 0.79,
      "avgCommitsPerWeek": 3.4,
      "avgCarryForwardPerWeek": 0.5,
      "topCategories": ["DELIVERY", "LEARNING"],
      "categoryCompletionRates": {"DELIVERY": 0.78, "OPERATIONS": 0.95, "LEARNING": 0.88},
      "priorityCompletionRates": {"KING": 0.88, "QUEEN": 0.75, "ROOK": 0.82, "KNIGHT": 1.0, "PAWN": 1.0}
    },
    "preferences": {
      "typicalPriorityPattern": "1K-1Q-1R",
      "recurringCommitTitles": ["Weekly ops review", "Update team wiki with new runbook"],
      "avgCheckInsPerWeek": 2.6,
      "preferredUpdateDays": ["MONDAY", "WEDNESDAY", "FRIDAY"]
    },
    "trends": {
      "strategicAlignmentTrend": "STABLE",
      "completionTrend": "IMPROVING",
      "carryForwardTrend": "STABLE"
    }
  }')
ON CONFLICT (org_id, user_id) DO NOTHING;

-- Bob Martinez — customer-focused IC, underestimates, higher carry-forward
INSERT INTO user_model_snapshots (org_id, user_id, computed_at, weeks_analyzed, model_json) VALUES
  ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000020'::uuid, NOW(), 8, '{
    "performanceProfile": {
      "estimationAccuracy": 0.65,
      "completionReliability": 0.72,
      "avgCommitsPerWeek": 2.9,
      "avgCarryForwardPerWeek": 0.8,
      "topCategories": ["CUSTOMER", "DELIVERY"],
      "categoryCompletionRates": {"CUSTOMER": 0.70, "DELIVERY": 0.80, "OPERATIONS": 0.92},
      "priorityCompletionRates": {"KING": 0.75, "QUEEN": 0.70, "ROOK": 0.85, "PAWN": 1.0}
    },
    "preferences": {
      "typicalPriorityPattern": "1K-1Q-1R",
      "recurringCommitTitles": ["On-call rotation and runbook updates", "Vendor contract renewal reviews"],
      "avgCheckInsPerWeek": 1.8,
      "preferredUpdateDays": ["TUESDAY", "THURSDAY"]
    },
    "trends": {
      "strategicAlignmentTrend": "IMPROVING",
      "completionTrend": "STABLE",
      "carryForwardTrend": "WORSENING"
    }
  }')
ON CONFLICT (org_id, user_id) DO NOTHING;

-- Carol Park — high-performing manager, excellent completion rate
INSERT INTO user_model_snapshots (org_id, user_id, computed_at, weeks_analyzed, model_json) VALUES
  ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000001'::uuid, NOW(), 6, '{
    "performanceProfile": {
      "estimationAccuracy": 0.82,
      "completionReliability": 0.92,
      "avgCommitsPerWeek": 2.8,
      "avgCarryForwardPerWeek": 0.1,
      "topCategories": ["PEOPLE", "DELIVERY"],
      "categoryCompletionRates": {"PEOPLE": 0.95, "DELIVERY": 0.88, "OPERATIONS": 1.0},
      "priorityCompletionRates": {"KING": 0.95, "QUEEN": 0.90, "PAWN": 1.0}
    },
    "preferences": {
      "typicalPriorityPattern": "1K-1Q-1P",
      "recurringCommitTitles": ["Review and approve team reconciliations", "Expense reports and vendor contract renewals"],
      "avgCheckInsPerWeek": 2.2,
      "preferredUpdateDays": ["MONDAY", "WEDNESDAY"]
    },
    "trends": {
      "strategicAlignmentTrend": "STABLE",
      "completionTrend": "STABLE",
      "carryForwardTrend": "IMPROVING"
    }
  }')
ON CONFLICT (org_id, user_id) DO NOTHING;

-- Dana Torres — ADMIN + senior leader, PEOPLE & OPERATIONS focus, very high completion, IMPROVING strategic alignment
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
-- OUTCOME METADATA — Target dates and progress for strategic intelligence
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO outcome_metadata (org_id, outcome_id, target_date, progress_type, metric_name, target_value, current_value, unit, progress_pct, urgency_band, last_computed_at, created_at, updated_at) VALUES
  -- Close 10 enterprise deals in Q1 — metric-based, on track
  ('a0000000-0000-0000-0000-000000000001'::uuid, 'e0000000-0000-0000-0000-000000000001'::uuid,
   (date_trunc('quarter', CURRENT_DATE) + interval '3 months' - interval '1 day')::date,
   'METRIC', 'Enterprise deals closed', 10, 6, 'deals', 60.00, 'ON_TRACK', NOW(), NOW(), NOW()),
  -- Launch enterprise demo environment — milestone-based, done
  ('a0000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000002'::uuid,
   (CURRENT_DATE + interval '14 days')::date,
   'MILESTONE', NULL, NULL, NULL, NULL, 85.00, 'ON_TRACK', NOW(), NOW(), NOW()),
  -- Sign 3 healthcare pilot customers — metric-based, at risk
  ('a0000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000004'::uuid,
   (CURRENT_DATE + interval '45 days')::date,
   'METRIC', 'Healthcare pilots signed', 3, 1, 'pilots', 33.00, 'AT_RISK', NOW(), NOW(), NOW()),
  -- Complete SOC2 Type II certification — milestone-based, needs attention
  ('a0000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000005'::uuid,
   (CURRENT_DATE + interval '60 days')::date,
   'MILESTONE', NULL, NULL, NULL, NULL, 45.00, 'NEEDS_ATTENTION', NOW(), NOW(), NOW()),
  -- Achieve 99.9% API uptime — metric-based, on track
  ('a0000000-0000-0000-0000-000000000001'::uuid, 'e0000000-0000-0000-0000-000000000002'::uuid,
   (date_trunc('quarter', CURRENT_DATE) + interval '3 months' - interval '1 day')::date,
   'METRIC', 'API uptime %', 99.9, 99.82, '%', 88.00, 'ON_TRACK', NOW(), NOW(), NOW()),
  -- Reduce deploy time — activity-based, on track
  ('a0000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000007'::uuid,
   (CURRENT_DATE + interval '30 days')::date,
   'ACTIVITY', NULL, NULL, NULL, NULL, 70.00, 'ON_TRACK', NOW(), NOW(), NOW()),
  -- Launch health-score alerting — metric-based, critical (tight deadline)
  ('a0000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000011'::uuid,
   (CURRENT_DATE + interval '21 days')::date,
   'METRIC', 'Accounts with alerting', 200, 5, 'accounts', 2.50, 'CRITICAL', NOW(), NOW(), NOW()),
  -- Achieve NPS > 60 — metric-based, needs attention
  ('a0000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000012'::uuid,
   (date_trunc('quarter', CURRENT_DATE) + interval '3 months' - interval '1 day')::date,
   'METRIC', 'Net Promoter Score', 60, 54, 'NPS', 90.00, 'NEEDS_ATTENTION', NOW(), NOW(), NOW()),
  -- Increase unit test coverage — metric-based, on track
  ('a0000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000008'::uuid,
   (date_trunc('quarter', CURRENT_DATE) + interval '3 months' - interval '1 day')::date,
   'METRIC', 'Test coverage %', 85, 78, '%', 91.76, 'ON_TRACK', NOW(), NOW(), NOW())
ON CONFLICT (org_id, outcome_id) DO NOTHING;
