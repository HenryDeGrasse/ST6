-- Quick-update note provenance for nightly learning aggregation.
-- Stores whether a note was user-typed or accepted from a suggestion surface,
-- plus optional metadata about the accepted suggestion.

ALTER TABLE progress_entries
    ADD COLUMN IF NOT EXISTS note_source VARCHAR(25) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS selected_suggestion_text TEXT,
    ADD COLUMN IF NOT EXISTS selected_suggestion_source VARCHAR(50);

ALTER TABLE progress_entries
    ADD CONSTRAINT chk_progress_note_source
        CHECK (note_source IN ('UNKNOWN', 'USER_TYPED', 'SUGGESTION_ACCEPTED'));

CREATE INDEX IF NOT EXISTS idx_progress_entries_learning_window
    ON progress_entries (created_at, org_id);
