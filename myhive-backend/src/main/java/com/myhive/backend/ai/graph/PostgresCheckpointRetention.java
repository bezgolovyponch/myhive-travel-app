package com.myhive.backend.ai.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Deletes a released thread's rows from the langgraph4j checkpoint tables. One statement is enough:
 * {@code lg4jcheckpoint.thread_id} references {@code lg4jthread.thread_id} {@code ON DELETE CASCADE}
 * (see the saver block of {@code V7__ai_planner.sql}), so removing the thread takes its checkpoints
 * with it.
 *
 * <p>The session token is the thread <em>name</em>, not the surrogate {@code thread_id} the saver
 * generates, which is why the delete keys on {@code thread_name}. A name can have several rows once
 * threads have been released — the unique index on it is partial, {@code WHERE is_released = FALSE} —
 * and all of them belong to the same expired chat, so all of them go.
 */
@RequiredArgsConstructor
@Slf4j
public class PostgresCheckpointRetention implements CheckpointRetention {

    static final String DELETE_THREAD_SQL = "DELETE FROM lg4jthread WHERE thread_name = ?";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void deleteThread(UUID token) {
        int deleted = jdbcTemplate.update(DELETE_THREAD_SQL, token.toString());
        log.debug("deleted {} planner checkpoint thread rows for {}", deleted, token);
    }
}
