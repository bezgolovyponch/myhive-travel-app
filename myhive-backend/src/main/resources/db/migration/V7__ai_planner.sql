-- AI stag-party planner: API projection tables. Conversation state lives in the
-- langgraph4j checkpoint tables appended below (thread id = ai_sessions.token).
CREATE TABLE ai_sessions (
    id               UUID PRIMARY KEY,
    token            UUID NOT NULL UNIQUE,
    -- Planner chats are disposable (30-day TTL); a deleted destination takes its chats with it.
    destination_id   UUID NOT NULL REFERENCES destinations (id) ON DELETE CASCADE,
    locale           VARCHAR(8),
    status           VARCHAR(16) NOT NULL,
    message_count    INTEGER NOT NULL DEFAULT 0,
    generation_count INTEGER NOT NULL DEFAULT 0,
    client_ip_hash   VARCHAR(64),
    created_at       TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_ai_sessions_last_activity ON ai_sessions (last_activity_at);

CREATE TABLE ai_generations (
    id                   UUID PRIMARY KEY,
    session_id           UUID NOT NULL REFERENCES ai_sessions (id) ON DELETE CASCADE,
    status               VARCHAR(16) NOT NULL,
    brief_snapshot       TEXT NOT NULL,
    result               TEXT,
    degraded             BOOLEAN NOT NULL DEFAULT FALSE,
    selected_package_key VARCHAR(16),
    selected_at          TIMESTAMP,
    error_code           VARCHAR(32),
    model                VARCHAR(64),
    prompt_tokens        INTEGER,
    completion_tokens    INTEGER,
    latency_ms           INTEGER,
    attempt              SMALLINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL,
    started_at           TIMESTAMP,
    finished_at          TIMESTAMP
);
CREATE INDEX idx_ai_generations_session ON ai_generations (session_id, created_at);

-- langgraph4j PostgresSaver tables, mirrored by hand from
-- org.bsc.langgraph4j.checkpoint.PostgresSaver#initTable (langgraph4j 1.8.13). Prod builds the saver
-- with createTables(false) because this schema runs on ddl-auto=validate and every table in it is
-- versioned; the saver must not create its own behind Flyway's back. On a langgraph4j upgrade, diff
-- initTable against this block - PostgresCheckpointPersistenceTest compares the two schemas column by
-- column and fails when they drift. Unquoted identifiers: Postgres folds them to lg4jthread/lg4jcheckpoint.
CREATE TABLE IF NOT EXISTS LG4JThread (
    thread_id   UUID PRIMARY KEY,
    thread_name VARCHAR(255),
    is_released BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE IF NOT EXISTS LG4JCheckpoint (
    checkpoint_id        UUID PRIMARY KEY,
    parent_checkpoint_id UUID,
    thread_id            UUID NOT NULL,
    node_id              VARCHAR(255),
    next_node_id         VARCHAR(255),
    state_data           JSONB NOT NULL,
    state_content_type   VARCHAR(100) NOT NULL,
    saved_at             TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_thread
        FOREIGN KEY (thread_id)
        REFERENCES LG4JThread (thread_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint (thread_id);
CREATE INDEX IF NOT EXISTS idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint (thread_id, saved_at DESC);
-- Not cosmetic: the saver's checkpoint upsert says ON CONFLICT (thread_name) WHERE is_released = FALSE,
-- which Postgres can only resolve against exactly this partial unique index.
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_lg4jthread_thread_name_unreleased
    ON LG4JThread (thread_name) WHERE is_released = FALSE;
