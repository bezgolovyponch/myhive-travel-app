-- Group vote v3. Prod runs Hibernate ddl-auto=validate, so this migration is the ONLY thing that
-- creates these columns and the table: keep the types in sync with the entities or startup fails.

-- The organiser can leave a WhatsApp number instead of an email (entity/VoteSession.java).
ALTER TABLE vote_sessions ADD COLUMN IF NOT EXISTS initiator_phone varchar(32);

-- The address book holds phone-only contacts too (entity/Contact.java): every row keeps an email,
-- a phone, or both. Postgres unique indexes allow many NULLs, so email-only rows never collide.
ALTER TABLE contacts ALTER COLUMN email DROP NOT NULL;
ALTER TABLE contacts ADD COLUMN IF NOT EXISTS phone varchar(32);
CREATE UNIQUE INDEX IF NOT EXISTS uk_contacts_phone ON contacts (phone);
ALTER TABLE contacts ADD CONSTRAINT ck_contacts_email_or_phone CHECK (email IS NOT NULL OR phone IS NOT NULL);

-- The organiser drops an activity from a running vote (entity/VoteSessionActivity.java): set while
-- dropped, cleared by Restore. Votes already cast on it are kept.
ALTER TABLE vote_session_activities ADD COLUMN IF NOT EXISTS excluded_at timestamp(6);

-- Activities a friend recommends while voting (entity/VoteRecommendation.java). Anonymous like the
-- votes: keyed by the voter token only. Deleting an activity drops its recommendations with it.
CREATE TABLE IF NOT EXISTS vote_recommendations (
    id          uuid PRIMARY KEY,
    session_id  uuid NOT NULL REFERENCES vote_sessions (id) ON DELETE CASCADE,
    voter_token uuid NOT NULL,
    activity_id uuid NOT NULL REFERENCES activities (id) ON DELETE CASCADE,
    created_at  timestamp(6) NOT NULL,
    CONSTRAINT uk_vote_recommendations_session_voter_activity UNIQUE (session_id, voter_token, activity_id)
);
CREATE INDEX IF NOT EXISTS idx_vote_recommendations_session ON vote_recommendations (session_id);
