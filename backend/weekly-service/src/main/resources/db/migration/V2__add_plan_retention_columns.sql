-- PRD §14.7 – Plan / commit data retention
-- Adds soft-delete columns to weekly_plans and weekly_commits.
-- Plans are soft-deleted after 3 years and hard-deleted 90 days after soft-deletion.
-- Commits are hard-deleted via CASCADE when their parent plan is hard-deleted.

ALTER TABLE weekly_plans  ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE weekly_commits ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- Partial indices to speed up the retention job's queries
CREATE INDEX idx_plans_retention_cutoff
    ON weekly_plans (created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_commits_retention_cutoff
    ON weekly_commits (created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_plans_soft_deleted
    ON weekly_plans (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_commits_soft_deleted
    ON weekly_commits (deleted_at) WHERE deleted_at IS NOT NULL;
