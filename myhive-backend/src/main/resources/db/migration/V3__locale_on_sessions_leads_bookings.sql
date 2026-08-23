-- Locale the customer was browsing in when the record was created ("de");
-- null = English. Read by EmailService to pick the language of the outbound
-- emails (vote created/result, trip reminders, itinerary + payment
-- confirmations) and to locale-prefix the links inside them.
-- IF NOT EXISTS: prod also runs ddl-auto=update, which may add the column
-- before this migration lands on an existing database.
ALTER TABLE vote_sessions ADD COLUMN IF NOT EXISTS locale VARCHAR(8);
ALTER TABLE trip_leads    ADD COLUMN IF NOT EXISTS locale VARCHAR(8);
ALTER TABLE bookings      ADD COLUMN IF NOT EXISTS locale VARCHAR(8);
