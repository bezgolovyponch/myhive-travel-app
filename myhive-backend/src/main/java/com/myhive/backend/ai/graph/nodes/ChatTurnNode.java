package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.edit.EditMessages;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.PackagesView;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.ChatTurnRequest;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.BriefMerger;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One chat turn: ask the model, merge what it extracted into the brief, and decide what happens next -
 * a full regeneration, an edit of the packages that already exist, or nothing but the reply.
 */
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
        Optional<ComposedPlan> packages = state.result();
        ChatTurnResult result = llm.chatTurn(request(state, packages));
        Brief merged = BriefMerger.merge(state.brief(), result.briefUpdate());
        // Java decides when to generate: the brief is complete and differs from what the last generation used.
        // No confirmation step - the owner wants the three packages the moment the facts are known.
        String mergedJson = JsonCodec.write(merged);
        boolean changedSinceLastGeneration = !mergedJson.equals(state.lastGeneratedBrief().orElse(null));
        Map<String, Object> update = new HashMap<>();
        List<Map<String, String>> replies = new ArrayList<>();
        replies.add(PlannerState.message(ChatMessage.ASSISTANT, PlanAssembler.clean(result.reply())));
        if (merged.isReady() && changedSinceLastGeneration) {
            // A regeneration rebuilds every package from the brief, so an edit of the old ones is moot.
            update.put(PlannerState.ACTION, PlannerState.ACTION_GENERATE);
        } else if (result.edits().isEmpty()) {
            update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        } else if (packages.isEmpty()) {
            // Nothing to edit yet: report the ops as rejected so the client sees why, and say so in chat.
            update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
            update.put(PlannerState.EDIT_REPORT, JsonCodec.write(
                    EditReport.allRejected(result.edits(), EditRejectionReason.NO_PACKAGES_YET)));
            replies.add(PlannerState.message(ChatMessage.ASSISTANT, EditMessages.noPackagesYet(state.locale())));
        } else {
            update.put(PlannerState.ACTION, PlannerState.ACTION_EDIT);
            update.put(PlannerState.EDITS, JsonCodec.write(result.edits()));
        }
        update.put(PlannerState.MESSAGES, replies);
        update.put(PlannerState.BRIEF, mergedJson);
        update.put(PlannerState.MISSING_FIELDS, merged.missingFields());
        return update;
    }

    /** The model may only propose edits when it can see what it would be editing. */
    private static ChatTurnRequest request(PlannerState state, Optional<ComposedPlan> packages) {
        if (packages.isEmpty()) {
            return new ChatTurnRequest(state.locale(), state.destinationName(), state.categorySlugs(), state.brief(),
                    state.messages());
        }
        return new ChatTurnRequest(state.locale(), state.destinationName(), state.categorySlugs(), state.brief(),
                state.messages(), PackagesView.render(packages.get()), PackagesView.catalogNames(state.catalog()));
    }
}
