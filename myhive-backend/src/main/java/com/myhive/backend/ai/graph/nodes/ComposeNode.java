package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.llm.PlanRequest;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Asks the planner model for the three packages. */
@Component
@RequiredArgsConstructor
public class ComposeNode implements NodeAction<PlannerState> {

    private static final String NODE = "compose";

    private final LlmGateway llm;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        PlanRequest request = DraftAttempt.planRequest(state);
        return DraftAttempt.run(NODE, state, () -> llm.composePlan(request));
    }
}
