-- Phase 4: Capacity Planning — hour tracking fields
-- Adds estimated_hours to weekly_commits for pre-commit effort estimation.
-- Adds actual_hours to weekly_commit_actuals as the preferred replacement
-- for the existing time_spent INTEGER column (kept for backward compatibility).

ALTER TABLE weekly_commits ADD COLUMN estimated_hours NUMERIC(5,1);

-- NOTE: actual_hours (NUMERIC(5,1)) is the preferred field for recording
-- actual time spent on a commitment. The legacy time_spent (INTEGER) column
-- is retained for backward compatibility with existing data and clients.
ALTER TABLE weekly_commit_actuals ADD COLUMN actual_hours NUMERIC(5,1);
