package com.myhive.backend.ai.graph;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

/** Checkpoints live in memory everywhere but production, where the Postgres saver takes over. */
@Configuration
public class CheckpointSaverConfig {

    @Bean
    @Profile("!prod")
    public BaseCheckpointSaver memoryCheckpointSaver() {
        return new MemorySaver();
    }

    /** Nothing to delete: {@code MemorySaver.release} already drops the thread from its own map. */
    @Bean
    @Profile("!prod")
    public CheckpointRetention memoryCheckpointRetention() {
        return token -> {
            // no tables behind MemorySaver, so releasing the thread is the whole cleanup
        };
    }

    /**
     * Prod: checkpoints survive a restart, so a chat parked at an {@code await*} node is still there
     * after a deploy. The tables come from Flyway {@code V7__ai_planner.sql}, never from the saver —
     * prod runs on {@code ddl-auto=validate} and every other table in this schema is versioned, so the
     * saver must not create its own behind Flyway's back. Keep the V7 block in step with
     * {@code org.bsc.langgraph4j.checkpoint.PostgresSaver#initTable} on every langgraph4j upgrade;
     * {@code PostgresCheckpointPersistenceTest} compares the two schemas and fails when they drift.
     */
    @Bean
    @Profile("prod")
    public BaseCheckpointSaver postgresCheckpointSaver(DataSource dataSource) throws SQLException {
        return PostgresSaver.builder()
                .datasource(dataSource)
                .stateSerializer(new PlannerStateSerializer())
                .createTables(false)
                .build();
    }

    /**
     * Prod: {@code release} only flags a thread released, so without this the checkpoint tables would
     * grow for the life of the database while the {@code ai_sessions} rows behind them expire at 30 days.
     */
    @Bean
    @Profile("prod")
    public CheckpointRetention postgresCheckpointRetention(DataSource dataSource) {
        return new PostgresCheckpointRetention(new JdbcTemplate(dataSource));
    }
}
