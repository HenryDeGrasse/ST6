-- Wave 2: AI Next-Work Suggestions feedback table
-- Stores user actions (ACCEPT, DEFER, DECLINE) on AI-generated next-work suggestions.
-- Used by NextWorkSuggestionService to filter recently declined suggestions.

CREATE TABLE IF NOT EXISTS ai_suggestion_feedback (
    id             UUID PRIMARY KEY,
    org_id         UUID NOT NULL,
    user_id        UUID NOT NULL,
    suggestion_id  UUID NOT NULL,
    action         VARCHAR(10) NOT NULL,
    reason         TEXT,
    source_type    VARCHAR(30),
    source_detail  TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_feedback_per_suggestion UNIQUE (org_id, user_id, suggestion_id),
    CONSTRAINT chk_feedback_action CHECK (action IN ('ACCEPT', 'DEFER', 'DECLINE'))
);

CREATE INDEX idx_feedback_user_recent
    ON ai_suggestion_feedback (org_id, user_id, created_at);

ALTER TABLE ai_suggestion_feedback ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation_feedback ON ai_suggestion_feedback
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), '')::uuid);
