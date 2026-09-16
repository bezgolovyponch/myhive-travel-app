package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.ChatTurnRequest;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.BriefMerger;
import com.myhive.backend.ai.plan.PlanAssembler;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One chat turn: ask the model, merge what it extracted into the brief, and decide whether to generate. */
@Component
@RequiredArgsConstructor
public class ChatTurnNode implements NodeAction<PlannerState> {

    private final LlmGateway llm;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        if (PlannerState.ACTION_SEED.equals(state.action())) {
            // Seeding a fresh thread only has to park it at awaitUser; there is nothing to reply to yet.
            return Map.of(PlannerState.ACTION, PlannerState.ACTION_NONE);
        }
        ChatTurnRequest request = new ChatTurnRequest(state.locale(), state.destinationName(), state.categorySlugs(),
                state.brief(), state.messages());
        ChatTurnResult result = llm.chatTurn(request);
        Brief merged = BriefMerger.merge(state.brief(), result.briefUpdate());
        // Java decides when to generate: the brief is complete and differs from what the last generation used.
        // No confirmation step - the owner wants the three packages the moment the facts are known.
        String mergedJson = JsonCodec.write(merged);
        boolean changedSinceLastGeneration = !mergedJson.equals(state.lastGeneratedBrief().orElse(null));
        String action = merged.isReady() && changedSinceLastGeneration
                ? PlannerState.ACTION_GENERATE : PlannerState.ACTION_NONE;
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.MESSAGES,
                List.of(PlannerState.message(ChatMessage.ASSISTANT, PlanAssembler.clean(result.reply()))));
        update.put(PlannerState.BRIEF, mergedJson);
        update.put(PlannerState.MISSING_FIELDS, merged.missingFields());
        update.put(PlannerState.ACTION, action);
        return update;
    }
}
