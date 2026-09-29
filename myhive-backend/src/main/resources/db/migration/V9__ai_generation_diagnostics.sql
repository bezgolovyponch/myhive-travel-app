-- AI planner: why each planner draft of a generation was rejected (JSON list), shown to staff only.
ALTER TABLE ai_generations ADD COLUMN IF NOT EXISTS diagnostics TEXT;
