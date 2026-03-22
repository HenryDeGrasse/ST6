-- V17: Data migration — weekly_commits → issues + weekly_assignments
--
-- ADDITIVE ONLY. All existing weekly_commits, weekly_commit_actuals,
-- progress_entries, and external_ticket_links rows are left untouched.
--
-- Algorithm:
--   1. Create one default 'General' team per org (for orgs that have commits).
--   2. Walk carried_from_commit_id chains to collapse each chain into one issue.
--   3. Insert issues (one per distinct carry-forward chain / standalone commit).
--   4. Insert weekly_assignments (one per commit, linking commit → issue → plan).
--   5. Back-populate weekly_commits.source_issue_id (bidirectional crosswalk).
--   6. Migrate weekly_commit_actuals → weekly_assignment_actuals.
--   7. Update issue status based on most-recent actual.
--   8. Update team.issue_sequence to max(sequence_number).
--
-- Idempotency: every INSERT uses ON CONFLICT DO NOTHING; every UPDATE uses
-- WHERE … IS NULL / checks to avoid re-applying on re-run.

-- ─── Step 1: Create default 'General' team per org ──────────────────────────
-- Owner is the user whose first plan was created earliest in the org.
-- ON CONFLICT skips orgs that already have a 'General' team (e.g., from seed-data).

INSERT INTO teams (
    id, org_id, name, key_prefix, description,
    owner_user_id, issue_sequence, version, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    wc_orgs.org_id,
    'General',
    'GEN',
    'Default team created by V17 data migration',
    (
        SELECT wp.owner_user_id
        FROM   weekly_plans wp
        WHERE  wp.org_id = wc_orgs.org_id
        ORDER  BY wp.created_at
        LIMIT  1
    ),
    0,
    1,
    NOW(),
    NOW()
FROM (SELECT DISTINCT org_id FROM weekly_commits) wc_orgs
ON CONFLICT (org_id, name) DO NOTHING;

-- Add the team owner as an OWNER member
INSERT INTO team_members (team_id, user_id, org_id, role, joined_at)
SELECT t.id, t.owner_user_id, t.org_id, 'OWNER', NOW()
FROM   teams t
WHERE  t.name = 'General'
ON CONFLICT (team_id, user_id) DO NOTHING;

-- ─── Step 2: Build chain mapping (recursive CTE → temp table) ───────────────
-- Each row maps a commit to its chain root (the oldest ancestor in the chain).
-- Standalone commits are their own root.

DROP TABLE IF EXISTS _v17_commit_chains;

CREATE TEMP TABLE _v17_commit_chains AS
WITH RECURSIVE chain_cte AS (
    -- Base: commits that are chain roots (no carried-from parent)
    SELECT
        id   AS commit_id,
        id   AS chain_root_id,
        0    AS depth
    FROM weekly_commits
    WHERE carried_from_commit_id IS NULL

    UNION ALL

    -- Recursive: follow the carried_from_commit_id link one hop at a time
    SELECT
        wc.id,
        cc.chain_root_id,
        cc.depth + 1
    FROM weekly_commits       wc
    JOIN chain_cte            cc ON wc.carried_from_commit_id = cc.commit_id
)
SELECT commit_id, chain_root_id, depth
FROM   chain_cte;

CREATE INDEX ON _v17_commit_chains (chain_root_id);
CREATE INDEX ON _v17_commit_chains (commit_id);

-- ─── Step 3: Build issue mapping (one issue per chain root) ─────────────────
-- Title, outcome_id, chess_priority, estimated_hours come from the MOST RECENT
-- commit in the chain (highest depth); effort_type is mapped from category.

DROP TABLE IF EXISTS _v17_issue_mapping;

CREATE TEMP TABLE _v17_issue_mapping AS
WITH most_recent AS (
    -- Pick the most recent commit's fields for each chain
    SELECT DISTINCT ON (cc.chain_root_id)
        cc.chain_root_id,
        wc.org_id,
        wc.title,
        wc.category,
        wc.outcome_id,
        wc.non_strategic_reason,
        wc.chess_priority,
        wc.estimated_hours,
        wp.owner_user_id AS creator_user_id
    FROM  _v17_commit_chains cc
    JOIN  weekly_commits wc ON wc.id = cc.commit_id
    JOIN  weekly_plans   wp ON wp.id = wc.weekly_plan_id
    ORDER BY cc.chain_root_id, cc.depth DESC
),
sequenced AS (
    -- Assign a monotonically-increasing sequence number per org
    SELECT
        mr.*,
        t.id        AS team_id,
        t.key_prefix,
        ROW_NUMBER() OVER (
            PARTITION BY mr.org_id
            ORDER BY     mr.chain_root_id
        )::integer  AS seq_num
    FROM  most_recent mr
    JOIN  teams t ON t.org_id = mr.org_id AND t.name = 'General'
)
SELECT
    gen_random_uuid()                   AS issue_id,
    chain_root_id,
    org_id,
    team_id,
    key_prefix || '-' || seq_num        AS issue_key,
    seq_num                             AS sequence_number,
    title,
    outcome_id,
    non_strategic_reason,
    chess_priority,
    estimated_hours,
    creator_user_id,
    CASE category
        WHEN 'DELIVERY'   THEN 'BUILD'
        WHEN 'GTM'        THEN 'BUILD'
        WHEN 'OPERATIONS' THEN 'MAINTAIN'
        WHEN 'TECH_DEBT'  THEN 'MAINTAIN'
        WHEN 'CUSTOMER'   THEN 'COLLABORATE'
        WHEN 'PEOPLE'     THEN 'COLLABORATE'
        WHEN 'LEARNING'   THEN 'LEARN'
        ELSE NULL
    END                                 AS effort_type
FROM sequenced;

CREATE INDEX ON _v17_issue_mapping (chain_root_id);
CREATE INDEX ON _v17_issue_mapping (issue_id);

-- ─── Step 4: Insert issues ────────────────────────────────────────────────────

INSERT INTO issues (
    id, org_id, team_id, issue_key, sequence_number,
    title, description, effort_type, estimated_hours, chess_priority,
    outcome_id, non_strategic_reason,
    creator_user_id, assignee_user_id,
    status, embedding_version, version, created_at, updated_at
)
SELECT
    im.issue_id,
    im.org_id,
    im.team_id,
    im.issue_key,
    im.sequence_number,
    im.title,
    '',                         -- description: empty placeholder
    im.effort_type,
    im.estimated_hours,
    im.chess_priority,
    im.outcome_id,
    im.non_strategic_reason,
    im.creator_user_id,
    NULL,                       -- assignee_user_id: none at migration time
    'OPEN',
    0,
    1,
    NOW(),
    NOW()
FROM _v17_issue_mapping im
ON CONFLICT (org_id, issue_key) DO NOTHING;

-- ─── Step 5: Insert weekly_assignments (one per commit) ──────────────────────
-- Each commit in a chain becomes a separate weekly_assignment pointing at the
-- chain's shared issue. The legacy_commit_id crosswalk links new → old.

INSERT INTO weekly_assignments (
    id, org_id, weekly_plan_id, issue_id,
    chess_priority_override, expected_result, confidence,
    snapshot_rally_cry_id,   snapshot_rally_cry_name,
    snapshot_objective_id,   snapshot_objective_name,
    snapshot_outcome_id,     snapshot_outcome_name,
    tags, legacy_commit_id,
    version, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    wc.org_id,
    wc.weekly_plan_id,
    im.issue_id,
    wc.chess_priority,
    COALESCE(wc.expected_result, ''),
    wc.confidence,
    wc.snapshot_rally_cry_id,   wc.snapshot_rally_cry_name,
    wc.snapshot_objective_id,   wc.snapshot_objective_name,
    wc.snapshot_outcome_id,     wc.snapshot_outcome_name,
    COALESCE(wc.tags, '{}'),
    wc.id,                      -- legacy_commit_id crosswalk
    1,
    wc.created_at,
    wc.updated_at
FROM  weekly_commits          wc
JOIN  _v17_commit_chains      cc ON cc.commit_id   = wc.id
JOIN  _v17_issue_mapping      im ON im.chain_root_id = cc.chain_root_id
ON CONFLICT (weekly_plan_id, issue_id) DO NOTHING;

-- ─── Step 6: Back-populate weekly_commits.source_issue_id ────────────────────
-- Bidirectional crosswalk: old commit → new issue.
-- Only updates rows that haven't been back-populated yet (source_issue_id IS NULL).

UPDATE weekly_commits wc
SET    source_issue_id = im.issue_id
FROM   _v17_commit_chains cc
JOIN   _v17_issue_mapping im ON im.chain_root_id = cc.chain_root_id
WHERE  wc.id               = cc.commit_id
  AND  wc.source_issue_id IS NULL;

-- ─── Step 7: Migrate actuals ─────────────────────────────────────────────────
-- For every weekly_assignment that was created from a legacy commit, copy
-- the commit's actual (if one exists).

INSERT INTO weekly_assignment_actuals (
    assignment_id, org_id,
    actual_result, completion_status, delta_reason, hours_spent,
    created_at, updated_at
)
SELECT
    wa.id,
    wa.org_id,
    COALESCE(wca.actual_result, ''),
    wca.completion_status,
    wca.delta_reason,
    COALESCE(
        wca.actual_hours,
        CASE WHEN wca.time_spent IS NOT NULL
             THEN (wca.time_spent::numeric(10,4) / 60.0)
             ELSE NULL
        END
    ),
    wca.created_at,
    wca.updated_at
FROM  weekly_assignments        wa
JOIN  weekly_commit_actuals     wca ON wca.commit_id = wa.legacy_commit_id
WHERE wa.legacy_commit_id IS NOT NULL
ON CONFLICT (assignment_id) DO NOTHING;

-- ─── Step 8: Update issue status from most-recent actual ─────────────────────

-- Mark DONE: most recent commit in chain has a DONE actual
UPDATE issues i
SET    status = 'DONE'
FROM   _v17_issue_mapping im
WHERE  im.issue_id = i.id
  AND  i.status   = 'OPEN'
  AND  EXISTS (
           SELECT 1
           FROM   _v17_commit_chains   cc2
           JOIN   weekly_commits       wc2 ON wc2.id = cc2.commit_id
           JOIN   weekly_commit_actuals wca ON wca.commit_id = wc2.id
           WHERE  cc2.chain_root_id = im.chain_root_id
             -- most recent commit in the chain has a DONE actual
             AND  cc2.depth = (
                     SELECT MAX(cc3.depth)
                     FROM   _v17_commit_chains   cc3
                     JOIN   weekly_commit_actuals wca3 ON wca3.commit_id = cc3.commit_id
                     WHERE  cc3.chain_root_id = im.chain_root_id
                 )
             AND  wca.completion_status = 'DONE'
       );

-- Mark IN_PROGRESS: issue has an assignment in the current week's non-final plan
UPDATE issues i
SET    status = 'IN_PROGRESS'
WHERE  i.status = 'OPEN'
  AND  EXISTS (
           SELECT 1
           FROM   weekly_assignments wa
           JOIN   weekly_plans       wp ON wp.id = wa.weekly_plan_id
           WHERE  wa.issue_id          = i.id
             AND  wp.week_start_date   = date_trunc('week', CURRENT_DATE)::date
             AND  wp.state NOT IN ('RECONCILED', 'CARRY_FORWARD')
       );

-- ─── Step 9: Update team issue_sequence to max(sequence_number) ─────────────

UPDATE teams t
SET    issue_sequence = sub.max_seq
FROM   (
           SELECT team_id, MAX(sequence_number) AS max_seq
           FROM   issues
           GROUP  BY team_id
       ) sub
WHERE  sub.team_id = t.id
  AND  sub.max_seq > t.issue_sequence;

-- ─── Cleanup ─────────────────────────────────────────────────────────────────

DROP TABLE IF EXISTS _v17_commit_chains;
DROP TABLE IF EXISTS _v17_issue_mapping;
