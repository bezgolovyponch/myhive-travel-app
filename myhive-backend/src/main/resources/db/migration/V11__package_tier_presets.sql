-- Ready-made packages with a price level. A package that carries a tier (BASIC / MEDIUM / PREMIUM) is the
-- preset the AI planner starts that tier from; every other package keeps tier NULL and is ignored by it.
ALTER TABLE packages ADD COLUMN IF NOT EXISTS tier VARCHAR(16);

-- Nine Prague presets: three themes at three price levels (about EUR 105 / 145 / 220 per person for a
-- group of ten). Three or four activities each, so a two-day trip holds a whole package. Everything is looked up by slug, so the statement is safe to re-run and inserts nothing
-- for a slug that is not there. The image is the first activity's.
INSERT INTO packages (id, slug, name, description, image_url, includes, duration, discount_pct, tier, seo_indexable,
                      destination_id, created_at)
SELECT gen_random_uuid(), v.slug, v.name, v.description,
       (SELECT a.image_url FROM activities a WHERE a.slug = v.image_from),
       v.includes, v.duration, 0.00, v.tier, FALSE, d.id, NOW()
FROM (VALUES
    ('prague-classic-stag-basic', 'Classic Stag: Essential',
     'The stag weekend basics, sorted: AK-47 and Glock shooting, a proper Czech dinner and a hosted cabaret club tour.',
     'AK-47 and Glock 17 Shooting; Czech Lunch or Dinner; Guided Cabaret Club Tour',
     5, 'BASIC', 'ak-47-glock-17-shooting'),
    ('prague-classic-stag-medium', 'Classic Stag: The Classic',
     'The weekend most groups book: AK-47 and Glock shooting by day, steak dinner with a private show, then a cocktails and karaoke night.',
     'AK-47 and Glock 17 Shooting; Steak & Private Show; Cocktails & Karaoke night',
     6, 'MEDIUM', 'ak-47-glock-17-shooting'),
    ('prague-classic-stag-premium', 'Classic Stag: Legend',
     'The classic, turned up: shooting and go-karting by day, steak with a private show, and a gentlemen''s club night with a bar tab.',
     'AK-47 and Glock 17 Shooting; Steak & Private Show; Gentlemen''s Club Night + Bar Tab; Go-Karting Experience',
     8, 'PREMIUM', 'ak-47-glock-17-shooting'),
    ('prague-adrenaline-basic', 'Adrenaline: Starter',
     'Action on a budget: a paintball battle, a Czech lunch to refuel and bowling with beers to settle the score.',
     'Paintball; Czech Lunch or Dinner; Bowling and Beers',
     6, 'BASIC', 'paintball'),
    ('prague-adrenaline-medium', 'Adrenaline: Full Throttle',
     'Guns and engines: AK-47 and Glock shooting and go-karting, with BBQ ribs for the whole group afterwards.',
     'AK-47 and Glock 17 Shooting; Go-Karting Experience; Succulent BBQ Ribs',
     5, 'MEDIUM', 'ak-47-glock-17-shooting'),
    ('prague-adrenaline-premium', 'Adrenaline: All Out',
     'The big toys: drive an army tank, race quad bikes and shoot an AK-47, then a Czech dinner to finish.',
     'Army Tank Experience; Quad Bikes; AK-47 and Glock 17 Shooting; Czech Lunch or Dinner',
     5, 'PREMIUM', 'army-tank-experience'),
    ('prague-beer-and-food-basic', 'Beer & Food: Taster',
     'Prague the easy way: a guided beer tasting, a beer sightseeing tour and a hearty Czech dinner.',
     'Beer Tasting Experience; Sight Beering; Czech Lunch or Dinner',
     7, 'BASIC', 'beer-tasting-experience'),
    ('prague-beer-and-food-medium', 'Beer & Food: Feast',
     'A swimming beer bike on the river, a grilled suckling pig dinner and a gin tasting.',
     'Swimming beer bike; Grilled Suckling Pig; Gin Tasting',
     5, 'MEDIUM', 'swimming-beer-bike'),
    ('prague-beer-and-food-premium', 'Beer & Food: Brewmaster',
     'The full treatment: a beer spa, a grilled suckling pig dinner and a whiskey tasting.',
     'Beer Spa; Grilled Suckling Pig; Whiskey Tasting',
     7, 'PREMIUM', 'beer-spa')
) AS v (slug, name, description, includes, duration, tier, image_from)
         JOIN destinations d ON d.slug = 'prague'
WHERE NOT EXISTS (SELECT 1 FROM packages p WHERE p.slug = v.slug);

-- Activities in package order, most important first: the planner drops from the end when the days run out.
INSERT INTO package_activities (package_id, activity_id, position)
SELECT p.id, a.id, v.position
FROM (VALUES
    ('prague-classic-stag-basic', 'ak-47-glock-17-shooting', 0),
    ('prague-classic-stag-basic', 'czech-lunch-or-dinner', 1),
    ('prague-classic-stag-basic', 'guided-cabaret-club-tour', 2),
    ('prague-classic-stag-medium', 'ak-47-glock-17-shooting', 0),
    ('prague-classic-stag-medium', 'steak-and-private-show', 1),
    ('prague-classic-stag-medium', 'cocktails-karaoke-night', 2),
    ('prague-classic-stag-premium', 'ak-47-glock-17-shooting', 0),
    ('prague-classic-stag-premium', 'steak-and-private-show', 1),
    ('prague-classic-stag-premium', 'gentlemens-club-night-bar-tab', 2),
    ('prague-classic-stag-premium', 'go-karting-experience', 3),
    ('prague-adrenaline-basic', 'paintball', 0),
    ('prague-adrenaline-basic', 'czech-lunch-or-dinner', 1),
    ('prague-adrenaline-basic', 'bowling-and-beers', 2),
    ('prague-adrenaline-medium', 'ak-47-glock-17-shooting', 0),
    ('prague-adrenaline-medium', 'go-karting-experience', 1),
    ('prague-adrenaline-medium', 'succulent-bbq-ribs', 2),
    ('prague-adrenaline-premium', 'army-tank-experience', 0),
    ('prague-adrenaline-premium', 'quad-bikes', 1),
    ('prague-adrenaline-premium', 'ak-47-glock-17-shooting', 2),
    ('prague-adrenaline-premium', 'czech-lunch-or-dinner', 3),
    ('prague-beer-and-food-basic', 'beer-tasting-experience', 0),
    ('prague-beer-and-food-basic', 'sight-beering', 1),
    ('prague-beer-and-food-basic', 'czech-lunch-or-dinner', 2),
    ('prague-beer-and-food-medium', 'swimming-beer-bike', 0),
    ('prague-beer-and-food-medium', 'grilled-suckling-pig', 1),
    ('prague-beer-and-food-medium', 'gin-tasting', 2),
    ('prague-beer-and-food-premium', 'beer-spa', 0),
    ('prague-beer-and-food-premium', 'grilled-suckling-pig', 1),
    ('prague-beer-and-food-premium', 'whiskey-tasting', 2)
) AS v (package_slug, activity_slug, position)
         JOIN packages p ON p.slug = v.package_slug
         JOIN activities a ON a.slug = v.activity_slug AND a.destination_id = p.destination_id
ON CONFLICT DO NOTHING;

INSERT INTO package_categories (package_id, category_id)
SELECT p.id, c.id
FROM (VALUES
    ('prague-classic-stag-basic', 'nightlife'),
    ('prague-classic-stag-basic', 'stag-hot-babies-and-pranks'),
    ('prague-classic-stag-basic', 'food-and-drink'),
    ('prague-classic-stag-medium', 'nightlife'),
    ('prague-classic-stag-medium', 'stag-hot-babies-and-pranks'),
    ('prague-classic-stag-medium', 'food-and-drink'),
    ('prague-classic-stag-premium', 'nightlife'),
    ('prague-classic-stag-premium', 'stag-hot-babies-and-pranks'),
    ('prague-classic-stag-premium', 'food-and-drink'),
    ('prague-adrenaline-basic', 'extreme'),
    ('prague-adrenaline-basic', 'guns-and-bullets'),
    ('prague-adrenaline-medium', 'extreme'),
    ('prague-adrenaline-medium', 'guns-and-bullets'),
    ('prague-adrenaline-premium', 'extreme'),
    ('prague-adrenaline-premium', 'guns-and-bullets'),
    ('prague-beer-and-food-basic', 'czech-beer'),
    ('prague-beer-and-food-basic', 'food-and-drink'),
    ('prague-beer-and-food-basic', 'wellness'),
    ('prague-beer-and-food-medium', 'czech-beer'),
    ('prague-beer-and-food-medium', 'food-and-drink'),
    ('prague-beer-and-food-medium', 'wellness'),
    ('prague-beer-and-food-premium', 'czech-beer'),
    ('prague-beer-and-food-premium', 'food-and-drink'),
    ('prague-beer-and-food-premium', 'wellness')
) AS v (package_slug, category_slug)
         JOIN packages p ON p.slug = v.package_slug
         JOIN categories c ON c.slug = v.category_slug
WHERE NOT EXISTS (SELECT 1 FROM package_categories pc WHERE pc.package_id = p.id AND pc.category_id = c.id);
