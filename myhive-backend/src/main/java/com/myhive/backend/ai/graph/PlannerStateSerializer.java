package com.myhive.backend.ai.graph;

import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;

/** Plain JSON round-trip of the state map; safe because every value in {@link PlannerState} is JSON-native. */
public class PlannerStateSerializer extends JacksonStateSerializer<PlannerState> {

    public PlannerStateSerializer() {
        super(PlannerState::new);
    }
}
