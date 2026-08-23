-- Per-locale overrides of the quiz copy shown to voters: {"de": {"prompt": ...}}
-- on questions and {"de": {"label": ...}} on answers — same mechanism as the
-- catalog's translations column (V2). IF NOT EXISTS for the same reason.
ALTER TABLE quiz_questions ADD COLUMN IF NOT EXISTS translations TEXT;
ALTER TABLE quiz_answers   ADD COLUMN IF NOT EXISTS translations TEXT;
