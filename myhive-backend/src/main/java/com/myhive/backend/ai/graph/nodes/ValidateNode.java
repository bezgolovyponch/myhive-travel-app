package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.AttemptDiagnostic;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanTrimmer;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rules first, then pricing: a draft is only a result once the assembler also finds the tiers correctly
 * ordered. A draft that fails is given to {@link PlanTrimmer} before it is given up on: what the trimmer
 * can correct - a day over its cap, a repeated activity, a slot conflict - is corrected here, and the
 * corrected draft is the result if it passes the very same two checks. Only a draft that still fails goes
 * on to the model's repair, and it goes as the model wrote it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidateNode implements NodeAction<PlannerState> {

    private final PlanValidator validator;
    private final PlanAssembler assembler;
    private final PlanTrimmer trimmer;

    /** A draft after both checks: {@code plan} is what it assembled to, set only when nothing was found. */
    private record Checked(PlanDraft draft, List<Violation> violations, ComposedPlan plan) {

        boolean accepted() {
            return violations.isEmpty();
        }
    }

    /** A corrected draft that passed, with what was corrected in it. */
    private record Corrected(Checked checked, List<String> fixes) {
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        Map<UUID, CatalogActivity> byId = state.catalogById();
        Brief brief = state.brief();
        Checked asWritten = check(state.draft().orElseGet(() -> new PlanDraft(List.of())), brief, byId);
        if (asWritten.accepted()) {
            return accepted(asWritten);
        }
        // The attempt log is what persistResult stores on the generation row for staff. VIOLATIONS is
        // overwritten by the next attempt, so every draft that failed as written is appended here. The
        // call's error code goes with it: an empty draft from a failed call reads as MISSING_TIER x3.
        List<AttemptDiagnostic> attempts = new ArrayList<>(state.attemptLog());
        String callError = state.lastError().orElse(null);
        Optional<Corrected> corrected = corrected(asWritten.draft(), state, byId);
        if (corrected.isPresent()) {
            List<String> fixes = corrected.get().fixes();
            // The fixes name catalog activities, tiers and days - nothing the model or the organizer wrote.
            log.info("planner draft corrected attempt={} violations={} fixes={}", state.attempt(),
                    summarize(asWritten.violations()), fixes);
            attempts.add(AttemptDiagnostic.of(state.attempt(), callError, asWritten.violations(), fixes));
            Map<String, Object> update = accepted(corrected.get().checked());
            update.put(PlannerState.DRAFT, JsonCodec.write(corrected.get().checked().draft()));
            update.put(PlannerState.ATTEMPT_LOG, JsonCodec.write(attempts));
            return update;
        }
        // Codes with tier and day only - no titles or "why" texts, which are model output.
        log.warn("planner draft rejected attempt={} violations={}", state.attempt(),
                summarize(asWritten.violations()));
        attempts.add(AttemptDiagnostic.of(state.attempt(), callError, asWritten.violations()));
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.ATTEMPT_LOG, JsonCodec.write(attempts));
        update.put(PlannerState.VIOLATIONS, JsonCodec.write(asWritten.violations()));
        return update;
    }

    private Checked check(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> byId) {
        List<Violation> violations = new ArrayList<>(validator.validate(draft, brief, byId));
        if (!violations.isEmpty()) {
            return new Checked(draft, violations, null);
        }
        PlanAssembler.AssemblyResult assembled = assembler.assemble(draft, brief, byId, false);
        violations.addAll(assembled.violations());
        return new Checked(draft, violations, violations.isEmpty() ? assembled.plan() : null);
    }

    /**
     * The draft as the trimmer corrected it, when that passes. Correcting is a second chance, never a new
     * way to fail: whatever goes wrong in it, the draft is rejected as it would have been without it.
     */
    private Optional<Corrected> corrected(PlanDraft draft, PlannerState state, Map<UUID, CatalogActivity> byId) {
        try {
            Optional<PlanTrimmer.Trimmed> trimmed = trimmer.trim(draft, state.brief(), byId);
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            Checked checked = check(trimmed.get().draft(), state.brief(), byId);
            if (!checked.accepted()) {
                log.info("planner draft corrected but still failing attempt={} violations={}", state.attempt(),
                        summarize(checked.violations()));
                return Optional.empty();
            }
            return Optional.of(new Corrected(checked, trimmed.get().fixes()));
        } catch (RuntimeException e) {
            log.warn("planner draft not corrected error={}", e.getClass().getName());
            return Optional.empty();
        }
    }

    private static Map<String, Object> accepted(Checked checked) {
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.RESULT, JsonCodec.write(checked.plan()));
        update.put(PlannerState.VIOLATIONS, JsonCodec.write(List.of()));
        return update;
    }

    private static String summarize(List<Violation> violations) {
        return violations.stream()
                .map(v -> v.code() + "/" + v.packageKey() + (v.dayNumber() == null ? "" : "/d" + v.dayNumber()))
                .collect(Collectors.joining(","));
    }
}
