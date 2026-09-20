package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.llm.PlanRequest;
import com.myhive.backend.ai.llm.RepairRequest;
import com.myhive.backend.ai.plan.PlanDraft;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One more shot at the model, this time with the violations spelled out; the attempt counter caps the loop. */
@Component
@RequiredArgsConstructor
public class RepairNode implements NodeAction<PlannerState> {

    private static final String NODE = "repair";

    private final LlmGateway llm;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        PlanRequest original = DraftAttempt.planRequest(state);
        PlanDraft draft = state.draft().orElseGet(() -> new PlanDraft(List.of()));
        RepairRequest request = new RepairRequest(original, draft, state.violations());
        Map<String, Object> update = new HashMap<>(DraftAttempt.run(NODE, state, () -> llm.repairPlan(request)));
        update.put(PlannerState.ATTEMPT, state.attempt() + 1);
        return update;
    }
}
