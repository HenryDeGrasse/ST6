-- Wave 4: Jira/Linear Integration — external ticket links
-- Creates the external_ticket_links table for linking commits to tickets in
-- external issue-trackers (Jira, Linear, etc.) and enables RLS.

CREATE TABLE IF NOT EXISTS external_ticket_links (
    id                  UUID PRIMARY KEY,
    org_id              UUID NOT NULL,
    commit_id           UUID NOT NULL REFERENCES weekly_commits(id) ON DELETE CASCADE,
    provider            VARCHAR(20) NOT NULL,
    external_ticket_id  VARCHAR(200) NOT NULL,
    external_ticket_url VARCHAR(2000),
    external_status     VARCHAR(100),
    last_synced_at      TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_commit_provider_ticket UNIQUE (org_id, commit_id, provider, external_ticket_id),
    CONSTRAINT chk_provider CHECK (provider IN ('JIRA', 'LINEAR'))
);

CREATE INDEX idx_ext_links_commit ON external_ticket_links (org_id, commit_id);
CREATE INDEX idx_ext_links_provider ON external_ticket_links (org_id, provider);

ALTER TABLE external_ticket_links ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_ext_links ON external_ticket_links
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
