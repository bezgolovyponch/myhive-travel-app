-- Per-locale overrides of the translatable content fields, stored as one JSON
-- document per row: {"de": {"name": "...", "description": "..."}}. Base columns
-- stay English; a missing locale/field falls back to the base value at read
-- time (see com.myhive.backend.util.Translations). TEXT rather than jsonb on
-- purpose — the entity reads it through an AttributeConverter, so the same
-- mapping runs on the H2 dev profile; the app never queries inside the JSON.
-- IF NOT EXISTS: prod also runs ddl-auto=update, which may add the column
-- before this migration lands on an existing database.
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS translations TEXT;
ALTER TABLE activities   ADD COLUMN IF NOT EXISTS translations TEXT;
ALTER TABLE packages     ADD COLUMN IF NOT EXISTS translations TEXT;
ALTER TABLE blog_posts   ADD COLUMN IF NOT EXISTS translations TEXT;
ALTER TABLE categories   ADD COLUMN IF NOT EXISTS translations TEXT;
