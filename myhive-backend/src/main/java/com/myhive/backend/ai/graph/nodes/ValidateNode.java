package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.plan.Violation;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rules first, then pricing: a draft is only a result once the assembler also finds the tiers correctly ordered. */
@Component
@RequiredArgsConstructor
public class ValidateNode implements NodeAction<PlannerState> {

    private final PlanValidator validator;
    private final PlanAssembler assembler;

    @Override
    public Map<String, Object> apply(PlannerState state) {
        Map<UUID, CatalogActivity> byId = state.catalogById();
        PlanDraft draft = state.draft().orElseGet(() -> new PlanDraft(List.of()));
        List<Violation> violations = new ArrayList<>(validator.validate(draft, state.brief(), byId));
        Map<String, Object> update = new HashMap<>();
        if (violations.isEmpty()) {
            PlanAssembler.AssemblyResult assembled = assembler.assemble(draft, state.brief(), byId, false);
            violations.addAll(assembled.violations());
            if (violations.isEmpty()) {
                update.put(PlannerState.RESULT, JsonCodec.write(assembled.plan()));
            }
        }
        update.put(PlannerState.VIOLATIONS, JsonCodec.write(violations));
        return update;
    }
}
