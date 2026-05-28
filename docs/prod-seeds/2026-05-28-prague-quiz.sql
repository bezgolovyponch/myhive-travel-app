-- Prod seed: Prague quiz + non-votable Transfer category.
-- Idempotent: wipes any existing Prague quiz and reinserts fresh.
-- Categories used (must exist on prod with these slugs):
--   wellness, food-and-drink, nightlife, stag-hot-babies-and-pranks,
--   extreme, guns-and-bullets, czech-beer, transfer
-- Source: spec/2026-05-11-quiz-driven-voting-design.md Appendix A.

BEGIN;

-- 1. Mark Transfer as non-votable (logistics, not a travel preference).
UPDATE categories SET votable = false WHERE slug = 'transfer';

DO $$
DECLARE
    dest_id UUID;
    q_id    UUID;
    a_id    UUID;
    missing_cats TEXT;
BEGIN
    -- 2. Resolve Prague destination id.
    SELECT id INTO dest_id FROM destinations WHERE slug = 'prague';
    IF dest_id IS NULL THEN
        RAISE EXCEPTION 'Prague destination not found (slug=prague). Aborting.';
    END IF;

    -- 3. Sanity-check every category slug the quiz references exists.
    SELECT string_agg(slug, ', ') INTO missing_cats
    FROM (VALUES
        ('wellness'), ('food-and-drink'), ('nightlife'),
        ('stag-hot-babies-and-pranks'), ('extreme'),
        ('guns-and-bullets'), ('czech-beer')
    ) AS req(slug)
    WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.slug = req.slug);
    IF missing_cats IS NOT NULL THEN
        RAISE EXCEPTION 'Missing categories: %. Aborting.', missing_cats;
    END IF;

    -- 4. Wipe existing Prague quiz. FK constraints are NOT ON DELETE CASCADE at
    -- the DB level (cascade lives in JPA/orphanRemoval), so delete bottom-up:
    -- weights -> answers -> questions.
    DELETE FROM quiz_answer_weights
    WHERE answer_id IN (
        SELECT a.id FROM quiz_answers a
        JOIN quiz_questions q ON q.id = a.question_id
        WHERE q.destination_id = dest_id
    );
    DELETE FROM quiz_answers
    WHERE question_id IN (
        SELECT id FROM quiz_questions WHERE destination_id = dest_id
    );
    DELETE FROM quiz_questions WHERE destination_id = dest_id;

    --------------------------------------------------------------------
    -- Q1: Daytime hero or 4am legend?
    --------------------------------------------------------------------
    INSERT INTO quiz_questions (id, destination_id, prompt, sort_order, created_at)
    VALUES (gen_random_uuid(), dest_id, 'Daytime hero or 4am legend?', 0, NOW())
    RETURNING id INTO q_id;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Daytime hero - in bed by midnight', 0)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('wellness', 2), ('food-and-drink', 1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Mixed - a bit of both', 1);
    -- "Mixed" has no weights (neutral) — no INSERT into quiz_answer_weights.

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, '4am legend', 2)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('nightlife', 2), ('stag-hot-babies-and-pranks', 1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    --------------------------------------------------------------------
    -- Q2: Adrenaline rush or zero risk?
    --------------------------------------------------------------------
    INSERT INTO quiz_questions (id, destination_id, prompt, sort_order, created_at)
    VALUES (gen_random_uuid(), dest_id, 'Adrenaline rush or zero risk?', 1, NOW())
    RETURNING id INTO q_id;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Adrenaline', 0)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('extreme', 2), ('guns-and-bullets', 1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Mixed - some thrills, some downtime', 1);

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Zero risk', 2)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('wellness', 2)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    --------------------------------------------------------------------
    -- Q3: How central is beer and food?
    --------------------------------------------------------------------
    INSERT INTO quiz_questions (id, destination_id, prompt, sort_order, created_at)
    VALUES (gen_random_uuid(), dest_id, 'How central is beer and food?', 2, NOW())
    RETURNING id INTO q_id;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'All of it', 0)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('food-and-drink', 2), ('czech-beer', 2)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Some', 1)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('food-and-drink', 1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Not central', 2)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('food-and-drink', -1), ('czech-beer', -1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    --------------------------------------------------------------------
    -- Q4: Stag mood — classy or unhinged?
    --------------------------------------------------------------------
    INSERT INTO quiz_questions (id, destination_id, prompt, sort_order, created_at)
    VALUES (gen_random_uuid(), dest_id, 'Stag mood - classy or unhinged?', 3, NOW())
    RETURNING id INTO q_id;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Classy', 0)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('stag-hot-babies-and-pranks', -1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Spicy', 1)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('stag-hot-babies-and-pranks', 1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    INSERT INTO quiz_answers (id, question_id, label, sort_order)
    VALUES (gen_random_uuid(), q_id, 'Full send', 2)
    RETURNING id INTO a_id;
    INSERT INTO quiz_answer_weights (id, answer_id, category_id, weight)
    SELECT gen_random_uuid(), a_id, c.id, v.weight
    FROM (VALUES ('stag-hot-babies-and-pranks', 3), ('nightlife', 1)) AS v(slug, weight)
    JOIN categories c ON c.slug = v.slug;

    RAISE NOTICE 'Prague quiz seeded: % questions',
        (SELECT count(*) FROM quiz_questions WHERE destination_id = dest_id);
END $$;

COMMIT;

-- Verify (optional):
-- SELECT q.sort_order, q.prompt, count(a.id) AS answers
-- FROM quiz_questions q
-- JOIN destinations d ON d.id = q.destination_id
-- LEFT JOIN quiz_answers a ON a.question_id = q.id
-- WHERE d.slug = 'prague'
-- GROUP BY q.id, q.sort_order, q.prompt
-- ORDER BY q.sort_order;
