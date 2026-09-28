package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanTextWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The second model call of a generation: the copy for the packages the skeleton already fixed. Adds its
 * tokens to the run's accounting so the row shows what the whole generation cost.
 *
 * <p>Never throws: the writer swallows model failures itself and answers with the placeholders, and
 * anything escaping past that is a bug in the merge - which must not cost the packages either, since
 * the plan in RESULT already carries its placeholder titles.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WriteTextsNode implements NodeAction<PlannerState> {

    private final PlanTextWriter writer;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        Optional<ComposedPlan> result = state.result();
        if (result.isEmpty()) {
            return Map.of();
        }
        try {
            PlanTextWriter.Written written = writer.write(result.get(), state.brief(), state.locale(),
                    state.destinationName(), state.catalog());
            if (!written.written()) {
                log.warn("planner texts kept the placeholders generation={}", state.generationId().orElse(null));
            }
            Map<String, Object> update = new HashMap<>();
            update.put(PlannerState.RESULT, JsonCodec.write(written.plan()));
            update.put(PlannerState.USAGE, JsonCodec.write(state.usage().plus(written.usage())));
            return update;
        } catch (RuntimeException e) {
            log.error("planner texts failed generation={} error={}", state.generationId().orElse(null),
                    e.getClass().getName(), e);
            return Map.of();
        }
    }
}
