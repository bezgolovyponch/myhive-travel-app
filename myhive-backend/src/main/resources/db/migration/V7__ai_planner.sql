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
