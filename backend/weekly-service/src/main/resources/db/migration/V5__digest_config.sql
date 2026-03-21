-- Wave 3: Weekly Digest Notifications — digest schedule configuration
-- Adds per-org configurable digest schedule to org_policies.
-- The digest job honours these columns to decide which day/time to fire
-- the weekly summary notification for each manager.
--
-- Defaults: Friday at 17:00 (end-of-week summary). Orgs that prefer a
-- Monday morning start-of-week summary can set digest_day = 'MONDAY'
-- and digest_time = '08:00'.

ALTER TABLE org_policies
    ADD COLUMN IF NOT EXISTS digest_day  VARCHAR(10) NOT NULL DEFAULT 'FRIDAY',
    ADD COLUMN IF NOT EXISTS digest_time VARCHAR(5)  NOT NULL DEFAULT '17:00';
