package com.myhive.backend.ai.graph;

import java.util.UUID;

/**
 * Deletes a planner thread's checkpoint rows for good. {@link PlannerGraph#release(UUID)} only flags
 * the thread released — the saver keeps every row so a released thread can still be inspected — which
 * means a 30-day TTL that deletes the {@code ai_sessions} rows leaves the conversation itself, JSONB
 * state and all, in the database for ever. This is the second half of that cleanup.
 *
 * <p>Separate from {@link CheckpointSaverConfig}'s saver because only the Postgres saver has tables:
 * {@code MemorySaver.release} already drops the thread from its map, so everywhere but production the
 * implementation is a no-op.
 */
public interface CheckpointRetention {

    void deleteThread(UUID token);
}
