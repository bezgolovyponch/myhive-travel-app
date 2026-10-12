package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.FallbackPlanComposer;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanTrimmer;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.model.Brief;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The model is out of chances: compose the packages greedily from the catalog and mark the result degraded.
 *
 * <p>Nothing checks a plan after this node, so it checks its own. A ready-made package laid over fewer
 * days than it was made for loses activities, and with them the price order and the difference between
 * the tiers (a fuzz run over the production catalogue: 1,394 of 40,600 brief shapes, most of them one-day
 * trips). So the composition is tried in three forms and the first one the validator and the assembler
 * both accept is the result: from the ready-made packages, the same after {@link PlanTrimmer}, and from
 * the catalog alone - which passed all 40,600.
 */
@Component
@Slf4j
public class FallbackNode implements NodeAction<PlannerState> {

    private final FallbackPlanComposer composer;
    private final PlanAssembler assembler;
    private final PlanValidator validator;
    private final PlanTrimmer trimmer;

    @Autowired
    public FallbackNode(FallbackPlanComposer composer, PlanAssembler assembler, PlanValidator validator,
            PlanTrimmer trimmer) {
        this.composer = composer;
        this.assembler = assembler;
        this.validator = validator;
        this.trimmer = trimmer;
    }

    /** The three helpers are stateless; callers that do not manage them as beans get their own. */
    public FallbackNode(FallbackPlanComposer composer) {
        this(composer, new PlanAssembler(), new PlanValidator());
    }

    private FallbackNode(FallbackPlanComposer composer, PlanAssembler assembler, PlanValidator validator) {
        this(composer, assembler, validator, new PlanTrimmer(validator, assembler));
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        List<CatalogActivity> catalog = state.catalog();
        Map<UUID, CatalogActivity> byId = state.catalogById();
        PlanDraft draft = soundDraft(state, catalog, byId);
        warnOnEmptyPackages(draft);
        ComposedPlan plan = assembler.assemble(draft, state.brief(), byId, true).plan();
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.DRAFT, JsonCodec.write(draft));
        update.put(PlannerState.RESULT, JsonCodec.write(plan));
        update.put(PlannerState.DEGRADED, true);
        return update;
    }

    /**
     * The first form of the fallback that breaks no rule; the ready-made one, as before, when none does -
     * a plan with a flaw still beats no plan, and the line below says which brief it was.
     */
    private PlanDraft soundDraft(PlannerState state, List<CatalogActivity> catalog, Map<UUID, CatalogActivity> byId) {
        Brief brief = state.brief();
        PlanDraft fromPresets = composer.compose(brief, catalog, state.presets(), state.locale());
        if (sound(fromPresets, brief, byId)) {
            return fromPresets;
        }
        try {
            Optional<PlanTrimmer.Trimmed> trimmed = trimmer.trim(fromPresets, brief, byId);
            if (trimmed.isPresent() && sound(trimmed.get().draft(), brief, byId)) {
                return trimmed.get().draft();
            }
        } catch (RuntimeException e) {
            log.warn("planner fallback not trimmed error={}", e.getClass().getName());
        }
        PlanDraft fromCatalog = composer.compose(brief, catalog, List.of(), state.locale());
        if (sound(fromCatalog, brief, byId)) {
            log.info("planner fallback composed from the catalog alone days={} group={}", brief.days(), brief.groupSize());
            return fromCatalog;
        }
        log.warn("planner fallback found no plan that passes validation days={} group={} catalog={}", brief.days(),
                brief.groupSize(), catalog.size());
        return fromPresets;
    }

    private boolean sound(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> byId) {
        return validator.validate(draft, brief, byId).isEmpty()
                && assembler.assemble(draft, brief, byId, true).violations().isEmpty();
    }

    /**
     * The fallback is the last stop: its draft goes straight to {@code persistResult} without passing
     * {@code validate} again, so an empty package would ship as a finished EUR 0 itinerary with nobody
     * the wiser. It cannot be turned into a violation here without losing the plan entirely, so it is
     * logged loudly instead - a line here means the catalog could not fill this brief at all.
     */
    private static void warnOnEmptyPackages(PlanDraft draft) {
        for (PlanDraft.PackageDraft p : draft.packages()) {
            if (p.days().stream().allMatch(day -> day.items().isEmpty())) {
                log.warn("planner fallback composed an empty package for tier {}", p.key());
            }
        }
    }
}
