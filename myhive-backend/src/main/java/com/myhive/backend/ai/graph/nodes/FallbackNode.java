package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.FallbackPlanComposer;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The model is out of chances: compose the packages greedily from the catalog and mark the result degraded. */
@Component
public class FallbackNode implements NodeAction<PlannerState> {

    private final FallbackPlanComposer composer;
    private final PlanAssembler assembler;

    @Autowired
    public FallbackNode(FallbackPlanComposer composer, PlanAssembler assembler) {
        this.composer = composer;
        this.assembler = assembler;
    }

    /** The assembler is stateless; callers that do not manage it as a bean get their own. */
    public FallbackNode(FallbackPlanComposer composer) {
        this(composer, new PlanAssembler());
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        List<CatalogActivity> catalog = state.catalog();
        Map<UUID, CatalogActivity> byId = state.catalogById();
        PlanDraft draft = composer.compose(state.brief(), catalog, state.locale());
        ComposedPlan plan = assembler.assemble(draft, state.brief(), byId, true).plan();
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.DRAFT, JsonCodec.write(draft));
        update.put(PlannerState.RESULT, JsonCodec.write(plan));
        update.put(PlannerState.DEGRADED, true);
        return update;
    }
}
