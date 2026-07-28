-- vote_sessions.initiator_email becomes optional: the Start Group Vote modal
-- no longer asks for the organizer's email; it is collected on the booking
-- page instead. Hibernate ddl-auto=update never relaxes NOT NULL, hence this
-- migration.
ALTER TABLE vote_sessions ALTER COLUMN initiator_email DROP NOT NULL;
