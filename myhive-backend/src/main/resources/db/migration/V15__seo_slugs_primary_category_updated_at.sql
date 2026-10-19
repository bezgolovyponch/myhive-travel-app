-- SEO release (Prague final EN/DE URLs): German slugs, primary categories, updated_at for sitemap lastmod.
-- Idempotent: safe to re-run.

ALTER TABLE activities ADD COLUMN IF NOT EXISTS slug_de VARCHAR(300);
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS slug_de VARCHAR(300);
CREATE UNIQUE INDEX IF NOT EXISTS ux_activities_slug_de ON activities (slug_de);
CREATE UNIQUE INDEX IF NOT EXISTS ux_blog_posts_slug_de ON blog_posts (slug_de);

CREATE TABLE IF NOT EXISTS primary_categories (
    id           UUID PRIMARY KEY,
    slug         VARCHAR(120) NOT NULL UNIQUE,
    slug_de      VARCHAR(120) NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    translations TEXT,
    sort_order   INTEGER      NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

ALTER TABLE activities ADD COLUMN IF NOT EXISTS primary_category_id UUID;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_activities_primary_category') THEN
        ALTER TABLE activities
            ADD CONSTRAINT fk_activities_primary_category
                FOREIGN KEY (primary_category_id) REFERENCES primary_categories (id);
    END IF;
END $$;

ALTER TABLE activities   ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE blog_posts   ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE packages     ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE categories   ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000001', 'shooting', 'schiessen', 'Shooting', '{"de":{"name":"Schießen"}}', 1, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'shooting');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000002', 'driving', 'panzer-motorsport', 'Tanks & Motorsport', '{"de":{"name":"Panzer & Motorsport"}}', 2, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'driving');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000003', 'action', 'action-sport', 'Action & Sport', '{"de":{"name":"Action & Sport"}}', 3, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'action');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000004', 'beer', 'bier', 'Beer & Drinks', '{"de":{"name":"Bier & Drinks"}}', 4, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'beer');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000005', 'food', 'essen', 'Food', '{"de":{"name":"Essen"}}', 5, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'food');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000006', 'boats', 'boote', 'Boats', '{"de":{"name":"Boote"}}', 6, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'boats');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000007', 'nightlife', 'nachtleben', 'Nightlife', '{"de":{"name":"Nachtleben"}}', 7, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'nightlife');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000008', 'strippers', 'stripper', 'Strip Clubs (18+)', '{"de":{"name":"Stripclubs (18+)"}}', 8, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'strippers');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000009', 'pranks', 'streiche', 'Pranks & Shows', '{"de":{"name":"Streiche & Shows"}}', 9, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'pranks');
INSERT INTO primary_categories (id, slug, slug_de, name, translations, sort_order, created_at, updated_at)
SELECT '92222222-0000-0000-0000-000000000010', 'transfers', 'transfers', 'Transfers', '{"de":{"name":"Transfers"}}', 10, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM primary_categories WHERE slug = 'transfers');

UPDATE activities   SET updated_at = now() WHERE updated_at IS NULL;
UPDATE blog_posts   SET updated_at = now() WHERE updated_at IS NULL;
UPDATE destinations SET updated_at = now() WHERE updated_at IS NULL;
UPDATE packages     SET updated_at = now() WHERE updated_at IS NULL;
UPDATE categories   SET updated_at = now() WHERE updated_at IS NULL;
UPDATE primary_categories SET updated_at = now() WHERE updated_at IS NULL;
