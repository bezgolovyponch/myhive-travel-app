-- One row per distinct device that opened a vote link: the "invited (opened)"
-- proxy — true invite counts don't exist in a share-link model.
CREATE TABLE vote_session_opens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES vote_sessions(id),
    voter_token UUID NOT NULL,
    first_opened_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_vote_session_opens UNIQUE (session_id, voter_token)
);
