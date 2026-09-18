-- AI planner edits: an edit turn stores a new READY generation linked to its parent.
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS kind VARCHAR(16) NOT NULL DEFAULT 'GENERATED';
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS parent_id UUID NULL
    REFERENCES ai_generations (id) ON DELETE SET NULL;
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS edit_report TEXT NULL;
ALTER TABLE ai_sessions ADD COLUMN IF NOT EXISTS edit_count INTEGER NOT NULL DEFAULT 0;
