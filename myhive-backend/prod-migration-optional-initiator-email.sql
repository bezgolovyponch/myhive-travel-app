-- Prod migration: vote_sessions.initiator_email becomes optional.
-- The Start Group Vote modal no longer asks for the organizer's email;
-- it is collected later on the booking page. Hibernate ddl-auto=update
-- never relaxes an existing NOT NULL, so this must be run manually.
--
-- Run PRE-DEPLOY on Render Postgres instance `trivlu-postgres`
-- (dpg-d7id8orbc2fs73bagjkg-a, Frankfurt) — safe while the old backend
-- is still running (it always writes a value).

ALTER TABLE vote_sessions ALTER COLUMN initiator_email DROP NOT NULL;
