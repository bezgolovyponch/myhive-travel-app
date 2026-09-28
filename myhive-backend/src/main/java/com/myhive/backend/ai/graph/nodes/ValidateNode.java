package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.plan.Violation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Rules first, then pricing: a draft is only a result once the assembler also finds the tiers correctly ordered. */
@Component
@RequiredArgsConstructor
@Slf4j
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
        if (!violations.isEmpty()) {
            // The only trace of WHY a draft was sent to repair or fallback: the violations live in the
            // checkpoint until the next turn overwrites them and never reach a generation row. Codes with
            // tier and day only - no titles or "why" texts, which are model output.
            log.warn("planner draft rejected attempt={} violations={}", state.attempt(), summarize(violations));
        }
        update.put(PlannerState.VIOLATIONS, JsonCodec.write(violations));
        return update;
    }

    private static String summarize(List<Violation> violations) {
        return violations.stream()
                .map(v -> v.code() + "/" + v.packageKey() + (v.dayNumber() == null ? "" : "/d" + v.dayNumber()))
                .collect(Collectors.joining(","));
    }
}
