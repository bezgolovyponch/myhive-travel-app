package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.ComposedPlan;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersistResultNodeTest {

    private static final ComposedPlan PLAN = new ComposedPlan(List.of(), false);

    /** A finished generation's state: a plan, the id to store it under and the brief it was built for. */
    private static PlannerState stateWith(UUID generationId) {
        Map<String, Object> values = new HashMap<>();
        values.put(PlannerState.RESULT, JsonCodec.write(PLAN));
        values.put(PlannerState.LAST_GENERATED_BRIEF, JsonCodec.write(Brief.empty()));
        if (generationId != null) {
            values.put(PlannerState.GENERATION_ID, generationId.toString());
        }
        return new PlannerState(values);
    }

    @Test
    void aStoredPlan_parksTheThreadAndLeavesTheGeneratedBriefStamped() {
        UUID expectedGenerationId = UUID.randomUUID();
        PersistResultNode node = new PersistResultNode(
                (generationId, plan, degraded, usage, attempt) -> true);

        Map<String, Object> update = node.apply(stateWith(expectedGenerationId));

        assertThat(update).containsEntry(PlannerState.RESUME_REASON, "");
        assertThat(update).containsEntry(PlannerState.ACTION, PlannerState.ACTION_NONE);
        // the stamp belongs to the packages that are now on the group's screen
        assertThat(update).doesNotContainKey(PlannerState.LAST_GENERATED_BRIEF);
        // and so does the row an edit of those packages has to hang off
        assertThat(update).containsEntry(PlannerState.RESULT_GENERATION_ID, expectedGenerationId.toString());
    }

    /**
     * Nobody will ever show a plan the sink refused - the row was failed while this run was still
     * going. The chat must not go on believing it has packages for this brief, or it would never
     * rebuild them.
     */
    @Test
    void aRefusedPlan_clearsTheGeneratedBriefSoTheNextTurnCanRebuild() {
        PersistResultNode node = new PersistResultNode(
                (generationId, plan, degraded, usage, attempt) -> false);

        Map<String, Object> update = node.apply(stateWith(UUID.randomUUID()));

        assertThat(update).containsEntry(PlannerState.LAST_GENERATED_BRIEF, "");
        assertThat(update).containsEntry(PlannerState.RESUME_REASON, "");
        // an edit filed under a row nobody stored would describe packages that do not exist
        assertThat(update).doesNotContainKey(PlannerState.RESULT_GENERATION_ID);
    }

    /** No id to store it under is the same thing: the plan reaches nobody. */
    @Test
    void aPlanWithNoGenerationId_isTreatedAsDropped() {
        PersistResultNode node = new PersistResultNode(
                (generationId, plan, degraded, usage, attempt) -> true);

        Map<String, Object> update = node.apply(stateWith(null));

        assertThat(update).containsEntry(PlannerState.LAST_GENERATED_BRIEF, "");
        assertThat(update).doesNotContainKey(PlannerState.RESULT_GENERATION_ID);
    }
}
