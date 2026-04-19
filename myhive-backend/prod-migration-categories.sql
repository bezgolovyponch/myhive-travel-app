-- Prod migration: Activity.category (String) → Many-to-Many Category entity
-- Split into two phases to avoid any downtime / empty-category window.
--
-- Run all phases on Render Postgres instance `trivlu-postgres`
-- (dpg-d7id8orbc2fs73bagjkg-a, Frankfurt).
--
-- Phase 1 — PRE-DEPLOY: run while old backend is still running.
--   Creates new tables, parses the messy `activities.category` field
--   (splits comma-separated values, applies explicit aliases, drops placeholders),
--   and populates the M2M links.
-- Phase 2 — POST-DEPLOY (wait at least 1–2 days of stability):
--   Drops the legacy `activities.category` column after the deploy is confirmed.
--
-- Rollback script is in `prod-migration-categories-rollback.sql`.
--
-- Explicit aliases applied in Phase 1 (per user decision 2026-04-19):
--   'Activity'              → DROP (placeholder, no category)
--   'Datime'                → 'Daytime' (typo fix)
--   'Adventure. Activity'   → 'Adventure' (strip noise)
--   everything else         → split by comma + initcap + dedup by lowercase name

-- ============================================================================
-- PHASE 1 — PRE-DEPLOY
-- Run BEFORE deploying the new backend code.
-- ============================================================================

BEGIN;

-- 1.1  Create `categories` table (mirrors what Hibernate would create).
CREATE TABLE IF NOT EXISTS categories
(
    id
    UUID
    PRIMARY
    KEY,
    name
    VARCHAR
(
    100
) NOT NULL UNIQUE,
    slug VARCHAR
(
    120
) UNIQUE,
    created_at TIMESTAMP
(
    6
)
    );

-- 1.2  Create `activity_categories` join table.
CREATE TABLE IF NOT EXISTS activity_categories
(
    activity_id
    UUID
    NOT
    NULL,
    category_id
    UUID
    NOT
    NULL,
    PRIMARY
    KEY
(
    activity_id,
    category_id
),
    CONSTRAINT fk_ac_activity FOREIGN KEY
(
    activity_id
) REFERENCES activities
(
    id
) ON DELETE CASCADE,
    CONSTRAINT fk_ac_category FOREIGN KEY
(
    category_id
) REFERENCES categories
(
    id
)
  ON DELETE CASCADE
    );

-- 1.3  Build normalized (activity_id, canonical_name) rows.
--      Splits comma-separated tokens, applies aliases, drops placeholders.
CREATE
TEMP TABLE tmp_activity_category_map ON COMMIT DROP
AS
WITH exploded AS (
    SELECT
        a.id AS activity_id,
        TRIM(token) AS raw_token
    FROM activities a,
         LATERAL regexp_split_to_table(a.category, ',') AS token
    WHERE a.category IS NOT NULL AND TRIM(a.category) <> ''
)
SELECT activity_id,
       CASE lower(raw_token)
           WHEN 'activity' THEN NULL -- drop generic placeholder
           WHEN 'datime' THEN 'Daytime' -- typo fix
           WHEN 'adventure. activity' THEN 'Adventure' -- strip trailing noise
           ELSE initcap(raw_token)
           END AS canonical_name
FROM exploded
WHERE raw_token <> '';

-- 1.4  Insert distinct canonical categories (idempotent).
INSERT INTO categories (id, name, slug, created_at)
SELECT gen_random_uuid(),
       t.canonical_name,
       lower(regexp_replace(t.canonical_name, '[^a-zA-Z0-9]+', '-', 'g')),
       NOW()
FROM (SELECT DISTINCT canonical_name
      FROM tmp_activity_category_map
      WHERE canonical_name IS NOT NULL) t
WHERE NOT EXISTS (SELECT 1
                  FROM categories c
                  WHERE lower(c.name) = lower(t.canonical_name));

-- 1.5  Link activities to canonical categories via M2M (idempotent).
INSERT INTO activity_categories (activity_id, category_id)
SELECT DISTINCT t.activity_id, c.id
FROM tmp_activity_category_map t
         JOIN categories c ON lower(c.name) = lower(t.canonical_name)
WHERE t.canonical_name IS NOT NULL
  AND NOT EXISTS (SELECT 1
                  FROM activity_categories ac
                  WHERE ac.activity_id = t.activity_id
                    AND ac.category_id = c.id);

COMMIT;

-- Verify Phase 1 before deploying:
--   SELECT name, slug FROM categories ORDER BY name;
--   SELECT COUNT(*) FROM categories;           -- expect: ~27 canonical categories
--   SELECT COUNT(*) FROM activity_categories;  -- expect: one row per (activity, canonical_category) pair
--   -- Activities that end up with no categories (those that had only 'Activity'):
--   SELECT a.id, a.name, a.category AS legacy_value
--     FROM activities a
--     LEFT JOIN activity_categories ac ON ac.activity_id = a.id
--     WHERE ac.activity_id IS NULL;
--   -- Full breakdown per activity:
--   SELECT a.name, a.category AS legacy, array_agg(c.name ORDER BY c.name) AS new_categories
--     FROM activities a
--     LEFT JOIN activity_categories ac ON ac.activity_id = a.id
--     LEFT JOIN categories c ON c.id = ac.category_id
--     GROUP BY a.id, a.name, a.category
--     ORDER BY a.name;

-- >>>>>>>>>>>>>>>>>>>>  DEPLOY NEW BACKEND HERE  <<<<<<<<<<<<<<<<<<<<
-- At this point:
--   - New code reads categories via M2M — data already populated, no gap.
--   - New code does NOT write to activities.category anymore.
--   - Old data in activities.category is preserved as a fallback.
-- Wait at least 1–2 days of stability before running Phase 2.


-- ============================================================================
-- PHASE 2 — POST-DEPLOY CLEANUP (run after deploy is confirmed stable)
-- ============================================================================

-- BEGIN;
-- ALTER TABLE activities DROP COLUMN IF EXISTS category;
-- COMMIT;

-- Verify Phase 2:
--   \d activities       -- should no longer list the `category` column
