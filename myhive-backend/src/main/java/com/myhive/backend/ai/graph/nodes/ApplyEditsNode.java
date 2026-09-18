package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.edit.EditMessages;
import com.myhive.backend.ai.edit.EditOutcome;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.edit.GenerationEditSink;
import com.myhive.backend.ai.edit.PackageEditor;
import com.myhive.backend.ai.edit.TextRefresher;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.plan.ComposedPlan;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Applies the edits the chat turn extracted to the packages that already exist, without regenerating:
 * {@link PackageEditor} does the work, {@link TextRefresher} rewrites the copy of what it touched, and the
 * result is stored as a new {@code EDITED} generation. Nothing here is fatal - an edit that cannot be
 * applied, or cannot be stored, is reported and never thrown, so the organizer keeps the packages they
 * already have and the thread parks at the selection screen either way.
 */
@Slf4j
public class ApplyEditsNode implements NodeAction<PlannerState> {

    /** An empty batch rather than a blank: the accessor reads both as "nothing pending". */
    private static final String NO_EDITS = "[]";

    private final PackageEditor editor;
    private final TextRefresher refresher;

    /** Resolves to {@code null} while no sink bean exists; never called during bean construction. */
    private final Supplier<GenerationEditSink> sinks;

    /**
     * The service implementing the sink depends on the graph, so the sink must be looked up when an edit
     * lands rather than when this node is built - otherwise the two would form a cycle.
     */
    public ApplyEditsNode(PackageEditor editor, TextRefresher refresher, ObjectProvider<GenerationEditSink> sinks) {
        this.editor = editor;
        this.refresher = refresher;
        this.sinks = sinks::getIfUnique;
    }

    public ApplyEditsNode(PackageEditor editor, TextRefresher refresher, GenerationEditSink sink) {
        this.editor = editor;
        this.refresher = refresher;
        this.sinks = () -> sink;
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        List<EditRequest> edits = state.edits();
        String locale = state.locale();
        if (state.editsLeft() <= 0) {
            return rejectAll(locale, edits, EditRejectionReason.EDIT_LIMIT);
        }
        Optional<ComposedPlan> current = state.result();
        if (current.isEmpty()) {
            // chatTurn only routes here with packages in state, so this is a guard, not a flow.
            return rejectAll(locale, edits, EditRejectionReason.NO_PACKAGES_YET);
        }
        Optional<UUID> parent = state.generationId();
        if (parent.isEmpty()) {
            log.warn("planner edits dropped: no generationId in state; the edited plan has nowhere to hang");
            return rejectAll(locale, edits, EditRejectionReason.INTERNAL);
        }
        ComposedPlan plan = current.get();
        EditOutcome outcome = applyOrReject(state, plan, edits);
        TextRefresher.Refreshed refreshed = outcome.anyApplied()
                ? refresher.refresh(outcome.plan(), outcome, locale, state.destinationName())
                : new TextRefresher.Refreshed(plan, false, LlmUsage.none());
        EditReport report = EditReport.of(outcome, refreshed.refreshed());
        if (!outcome.anyApplied()) {
            return consumed(locale, report);
        }
        Optional<UUID> stored = store(parent.get(), refreshed, report);
        if (stored.isEmpty()) {
            // Nothing reached the database, so the edited plan must not be served: the organizer keeps the
            // generation that is actually stored, and the batch is reported as INTERNAL rather than lost.
            return rejectAll(locale, edits, EditRejectionReason.INTERNAL);
        }
        Map<String, Object> update = consumed(locale, report);
        update.put(PlannerState.RESULT, JsonCodec.write(refreshed.plan()));
        update.put(PlannerState.GENERATION_ID, stored.get().toString());
        return update;
    }

    private EditOutcome applyOrReject(PlannerState state, ComposedPlan plan, List<EditRequest> edits) {
        try {
            return editor.apply(plan, state.brief(), state.catalog(), edits);
        } catch (RuntimeException e) {
            // The editor guards every single edit, but its closing re-assembly sits outside that guard: a
            // bug there must cost the batch, never the generation the organizer is already looking at.
            log.error("planner edit failed generation={} error={}", state.generationId().orElse(null),
                    e.getClass().getName(), e);
            return new EditOutcome(plan, List.of(),
                    EditReport.allRejected(edits, EditRejectionReason.INTERNAL).rejected());
        }
    }

    /**
     * The one write this node does, and the one that must never throw: langgraph4j checkpoints a node
     * <em>after</em> it returns, so an exception escaping here would leave the thread parked before
     * {@code applyEdits} with the batch still in state - every later message would re-enter this node,
     * fail again, and the chat would be wedged for good. A row the cleanup already deleted, or a database
     * that is simply down, costs the edit and nothing else. A sink that answers with no id counts as a
     * failed write too: there would be no generation to point the client at.
     */
    private Optional<UUID> store(UUID parent, TextRefresher.Refreshed refreshed, EditReport report) {
        try {
            return Optional.ofNullable(sink().edited(parent, refreshed.plan(), report, refreshed.usage()));
        } catch (RuntimeException e) {
            log.error("planner edit not stored generation={} error={}", parent, e.getClass().getName(), e);
            return Optional.empty();
        }
    }

    /** Every op rejected for the same reason, before the editor ever saw them. */
    private static Map<String, Object> rejectAll(String locale, List<EditRequest> edits, EditRejectionReason reason) {
        return consumed(locale, EditReport.allRejected(edits, reason));
    }

    /**
     * The half of the update every path writes: the report, the consumed edits and a parked thread. The
     * edits are cleared here and nowhere else - a batch left in state would be applied again next turn.
     */
    private static Map<String, Object> consumed(String locale, EditReport report) {
        log.info("planner edits applied={} rejected={} refreshed={}", report.applied().size(),
                report.rejected().size(), report.textsRefreshed());
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.EDIT_REPORT, JsonCodec.write(report));
        update.put(PlannerState.EDITS, NO_EDITS);
        update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        update.put(PlannerState.RESUME_REASON, "");
        if (!report.rejected().isEmpty()) {
            update.put(PlannerState.MESSAGES, List.of(PlannerState.message(ChatMessage.ASSISTANT,
                    EditMessages.rejectionSummary(locale, report.rejected()))));
        }
        return update;
    }

    private GenerationEditSink sink() {
        GenerationEditSink resolved = sinks.get();
        if (resolved != null) {
            return resolved;
        }
        // Handing the parent id back would fake a save: the edited plan would be served while the stored
        // generation still held the old one. Failing instead turns into an INTERNAL report one frame up.
        return (parentGenerationId, plan, report, usage) -> {
            log.warn("planner edit dropped generation={}: no GenerationEditSink bean", parentGenerationId);
            throw new IllegalStateException("no GenerationEditSink bean");
        };
    }
}
