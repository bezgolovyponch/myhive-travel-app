package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.LlmUsage;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Freezes the catalog one generation works from and resets the per-generation counters. */
@Component
@RequiredArgsConstructor
public class SnapshotCatalogNode implements NodeAction<PlannerState> {

    private final CatalogSnapshotter snapshotter;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        List<CatalogActivity> catalog = snapshotter.snapshot(state.destinationId(), state.brief(), state.locale());
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.CATALOG, JsonCodec.write(catalog));
        // Stamped here, not in chatTurn: this is the brief the packages about to be built are based on,
        // and the next chat turn regenerates only when the merged brief no longer matches it.
        update.put(PlannerState.LAST_GENERATED_BRIEF, JsonCodec.write(state.brief()));
        update.put(PlannerState.ATTEMPT, 0);
        update.put(PlannerState.DEGRADED, false);
        update.put(PlannerState.VIOLATIONS, JsonCodec.write(List.of()));
        // A regeneration must not inherit the previous one's accounting: a fallback that never reached
        // the model would otherwise report the earlier generation's tokens and failure reason as its own.
        update.put(PlannerState.USAGE, JsonCodec.write(LlmUsage.none()));
        update.put(PlannerState.LAST_ERROR, "");
        return update;
    }
}
