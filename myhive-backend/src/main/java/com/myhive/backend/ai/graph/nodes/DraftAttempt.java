package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmOutputException;
import com.myhive.backend.ai.llm.LlmUnavailableException;
import com.myhive.backend.ai.llm.PlanDraftResult;
import com.myhive.backend.ai.llm.PlanRequest;
import com.myhive.backend.ai.plan.PlanDraft;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One draft-producing model call, shared by compose and repair. A failure is never fatal: the node
 * records the error code and hands an empty draft to validate, which routes on to repair or fallback.
 */
@Slf4j
final class DraftAttempt {

    private static final String ERROR_TIMEOUT = "LLM_TIMEOUT";
    private static final String ERROR_UNAVAILABLE = "LLM_UNAVAILABLE";
    private static final String ERROR_INVALID_OUTPUT = "LLM_INVALID_OUTPUT";
    private static final String ERROR_INTERNAL = "INTERNAL";
    private static final String TIMEOUT_MARKER = "timed out";

    private DraftAttempt() {}

    /** The request compose sends and repair quotes back to the model unchanged. */
    static PlanRequest planRequest(PlannerState state) {
        return new PlanRequest(state.locale(), state.destinationName(), state.brief(), state.catalog(),
                state.messages());
    }

    static Map<String, Object> run(String node, PlannerState state, Supplier<PlanDraftResult> call) {
        Map<String, Object> update = new HashMap<>();
        try {
            PlanDraftResult result = call.get();
            update.put(PlannerState.DRAFT, JsonCodec.write(result.draft()));
            update.put(PlannerState.USAGE, JsonCodec.write(result.usage()));
            // A timeout on compose followed by a clean repair is not a failed generation any more.
            update.put(PlannerState.LAST_ERROR, "");
        } catch (RuntimeException e) {
            log.warn("planner {} failed generation={} error={}", node, state.generationId().orElse(null),
                    e.getClass().getSimpleName());
            update.put(PlannerState.DRAFT, JsonCodec.write(new PlanDraft(List.of())));
            update.put(PlannerState.LAST_ERROR, errorCode(e));
        }
        return update;
    }

    static String errorCode(RuntimeException e) {
        if (e instanceof LlmUnavailableException) {
            return e.getMessage() != null && e.getMessage().contains(TIMEOUT_MARKER) ? ERROR_TIMEOUT : ERROR_UNAVAILABLE;
        }
        if (e instanceof LlmOutputException) {
            return ERROR_INVALID_OUTPUT;
        }
        return ERROR_INTERNAL;
    }
}
