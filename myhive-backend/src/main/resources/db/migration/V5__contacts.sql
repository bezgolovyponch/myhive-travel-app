-- Sales/ops address book: one row per normalized email ever typed into the site.
-- Hibernate `update` would create the table too; shipping it here keeps prod DDL explicit
-- and lets us backfill from the tables that already hold addresses.
CREATE TABLE IF NOT EXISTS contacts (
    id            uuid PRIMARY KEY,
    email         varchar(255) NOT NULL,
    name          varchar(255),
    locale        varchar(8),
    first_source  varchar(20) NOT NULL,
    last_source   varchar(20) NOT NULL,
    first_seen_at timestamp(6) NOT NULL,
    last_seen_at  timestamp(6) NOT NULL,
    touch_count   integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_contacts_email UNIQUE (email)
);

-- One-time backfill from every place that already stores an address. Idempotent via ON CONFLICT.
WITH touches AS (
    SELECT lower(trim(user_email)) AS email, customer_name AS name, locale,
           'BOOKING' AS source, created_at AS seen_at
    FROM bookings
    WHERE user_email IS NOT NULL AND trim(user_email) <> ''
    UNION ALL
    SELECT lower(trim(initiator_email)), NULL, locale,
           'VOTE', COALESCE(email_captured_at, created_at)
    FROM vote_sessions
    WHERE initiator_email IS NOT NULL AND trim(initiator_email) <> ''
    UNION ALL
    SELECT email, NULL, locale, 'TRIP_BUILDER', created_at
    FROM trip_leads
    UNION ALL
    SELECT lower(trim(payer_email)), NULL, NULL, 'PAYMENT', paid_at
    FROM booking_payment_shares
    WHERE payer_email IS NOT NULL AND trim(payer_email) <> ''
),
ranked AS (
    SELECT email, name, locale, source, seen_at,
           row_number() OVER (PARTITION BY email ORDER BY seen_at ASC NULLS LAST)  AS rn_first,
           row_number() OVER (PARTITION BY email ORDER BY seen_at DESC NULLS LAST) AS rn_last,
           count(*)     OVER (PARTITION BY email)                                  AS touches
    FROM touches
)
INSERT INTO contacts (id, email, name, locale, first_source, last_source, first_seen_at, last_seen_at, touch_count)
SELECT gen_random_uuid(),
       f.email,
       (SELECT max(t.name) FROM touches t WHERE t.email = f.email AND t.name IS NOT NULL AND trim(t.name) <> ''),
       COALESCE(l.locale, f.locale),
       f.source,
       l.source,
       COALESCE(f.seen_at, now()),
       COALESCE(l.seen_at, now()),
       f.touches
FROM ranked f
JOIN ranked l ON l.email = f.email AND l.rn_last = 1
WHERE f.rn_first = 1
ON CONFLICT (email) DO NOTHING;
