-- Watermark for the daily new-contacts digest: null = not yet reported to sales.
-- Prod runs Hibernate ddl-auto=validate, so this column must exist before the entity does.
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS digest_sent_at timestamp(6);
