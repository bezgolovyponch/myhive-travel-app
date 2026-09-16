package com.myhive.backend.ai.graph;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Checkpoints live in memory everywhere but production, where the Postgres saver takes over. */
@Configuration
public class CheckpointSaverConfig {

    @Bean
    @Profile("!prod")
    public BaseCheckpointSaver memoryCheckpointSaver() {
        return new MemorySaver();
    }
}
