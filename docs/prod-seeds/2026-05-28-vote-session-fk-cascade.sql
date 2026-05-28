-- Prod fix: make vote_sessions child FKs ON DELETE CASCADE.
--
-- Why: the nightly cleanupOldSessions job runs a bulk SQL DELETE on
-- vote_sessions, which bypasses JPA cascade. Three child tables were created
-- with ON DELETE NO ACTION, so the delete would fail with a FK violation and
-- roll back — completed sessions older than 7 days would never get purged.
--
-- This rewrites those three FKs to ON DELETE CASCADE. Idempotent: finds the
-- existing FK by (table, session_id) regardless of its Hibernate-generated
-- name, drops it, and recreates it with the cascade rule.
--
-- ddl-auto=update will NOT do this automatically (it never alters existing
-- constraints), so this one-off script is required on prod.

BEGIN;

DO $$
DECLARE
    rec RECORD;
    fk_name TEXT;
BEGIN
    FOR rec IN
        SELECT * FROM (VALUES
            ('vote_session_activities'),
            ('vote_session_quiz_responses'),
            ('vote_session_liked_categories')
        ) AS t(table_name)
    LOOP
        -- Find the FK on this table whose referenced table is vote_sessions
        -- and whose local column is session_id.
        SELECT tc.constraint_name INTO fk_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
            ON kcu.constraint_name = tc.constraint_name
        JOIN information_schema.constraint_column_usage ccu
            ON ccu.constraint_name = tc.constraint_name
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND tc.table_name = rec.table_name
          AND kcu.column_name = 'session_id'
          AND ccu.table_name = 'vote_sessions'
        LIMIT 1;

        IF fk_name IS NULL THEN
            RAISE NOTICE 'No session_id FK found on %, skipping', rec.table_name;
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', rec.table_name, fk_name);
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (session_id) '
            || 'REFERENCES vote_sessions(id) ON DELETE CASCADE',
            rec.table_name, fk_name);
        RAISE NOTICE 'Rebuilt % FK % with ON DELETE CASCADE', rec.table_name, fk_name;
    END LOOP;
END $$;

COMMIT;

-- Verify (optional):
-- SELECT tc.table_name AS child_table, rc.delete_rule
-- FROM information_schema.referential_constraints rc
-- JOIN information_schema.table_constraints tc ON tc.constraint_name = rc.constraint_name
-- JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = rc.constraint_name
-- WHERE ccu.table_name = 'vote_sessions'
-- ORDER BY tc.table_name;
