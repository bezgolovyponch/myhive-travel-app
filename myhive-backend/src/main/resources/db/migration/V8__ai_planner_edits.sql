-- AI planner edits: an edit turn stores a new READY generation linked to its parent.
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS kind VARCHAR(16) NOT NULL DEFAULT 'GENERATED';
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES ai_generations (id) ON DELETE SET NULL;
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS edit_report TEXT;
ALTER TABLE ai_sessions ADD COLUMN IF NOT EXISTS edit_count INTEGER NOT NULL DEFAULT 0;
-- Not cosmetic: ON DELETE SET NULL has to find the children of a deleted generation, and the session
-- cleanup deletes whole chats at a time.
CREATE INDEX IF NOT EXISTS idx_ai_generations_parent_id ON ai_generations (parent_id);
