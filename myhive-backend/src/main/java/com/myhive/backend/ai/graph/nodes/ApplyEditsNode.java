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
import com.myhive.backend.ai.graph.ResumeReason;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
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

    /**
     * The last-resort report, for the case where even {@code state.edits()} cannot be read back: it has
     * to be a constant, because everything that could build one from the state is what just failed.
     */
    private static final String INTERNAL_REPORT = "{\"applied\":[],\"rejected\":[{\"op\":null,"
            + "\"activityName\":null,\"packageKey\":null,\"reason\":\"INTERNAL\",\"detail\":null}],"
            + "\"tierRulesRelaxed\":false,\"textsRefreshed\":false}";

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

    /**
     * Nothing may escape this method. langgraph4j checkpoints a node <em>after</em> it returns, so an
     * exception thrown anywhere in here would leave the thread parked before {@code applyEdits} with the
     * batch still in state: every later message would re-enter this node, fail again, and the chat would
     * be wedged for good. The inner body guards its own failures where it can say something useful; this
     * catch is for the rest - a state value that will not parse, a report that will not serialise.
     */
    @Override
    public Map<String, Object> apply(PlannerState state) {
        try {
            return applyGuarded(state);
        } catch (RuntimeException e) {
            log.error("planner edit node failed error={}", e.getClass().getName(), e);
            return parkedWithInternalReport(state);
        }
    }

    private Map<String, Object> applyGuarded(PlannerState state) {
        List<EditRequest> edits = state.edits();
        String locale = state.locale();
        Optional<String> reply = state.pendingReply();
        if (state.editsLeft() <= 0) {
            return rejectAll(locale, edits, EditRejectionReason.EDIT_LIMIT);
        }
        Optional<ComposedPlan> current = state.result();
        if (current.isEmpty()) {
            // chatTurn only routes here with packages in state, so this is a guard, not a flow.
            return rejectAll(locale, edits, EditRejectionReason.NO_PACKAGES_YET);
        }
        Optional<UUID> parent = parentOf(state);
        if (parent.isEmpty()) {
            log.warn("planner edits dropped: no generationId in state; the edited plan has nowhere to hang");
            return rejectAll(locale, edits, EditRejectionReason.INTERNAL);
        }
        ComposedPlan plan = current.get();
        EditOutcome outcome = applyOrReject(state, plan, edits);
        // A tap in the draft is applied as it stands: the copy refresh is a model call the organizer would
        // wait on after every click, and the draft shows names and slots, not the rewritten copy.
        boolean tap = state.resumeReason().filter(ResumeReason.EDIT.name()::equals).isPresent();
        TextRefresher.Refreshed refreshed = outcome.anyApplied() && !tap
                ? refresher.refresh(outcome.plan(), outcome, locale, state.destinationName())
                // The edited plan as it stands (the unchanged one when nothing landed), copy untouched.
                : new TextRefresher.Refreshed(outcome.plan(), false, LlmUsage.none());
        EditReport report = EditReport.of(outcome, refreshed.refreshed());
        if (!outcome.anyApplied()) {
            return consumed(locale, report, reply);
        }
        Optional<UUID> stored = store(parent.get(), refreshed, report);
        if (stored.isEmpty()) {
            // Nothing reached the database, so the edited plan must not be served: the organizer keeps the
            // generation that is actually stored, and the batch is reported as INTERNAL rather than lost.
            return rejectAll(locale, edits, EditRejectionReason.INTERNAL);
        }
        Map<String, Object> update = consumed(locale, report, reply);
        update.put(PlannerState.RESULT, JsonCodec.write(refreshed.plan()));
        update.put(PlannerState.GENERATION_ID, stored.get().toString());
        // The edited row is now the one the plan in RESULT came from, so the next edit hangs off it.
        update.put(PlannerState.RESULT_GENERATION_ID, stored.get().toString());
        return update;
    }

    /**
     * The row whose plan is being edited, not whatever the last resume happened to stamp: a failed
     * regeneration leaves its own id in {@code GENERATION_ID} while {@code RESULT} still holds the
     * previous packages. {@code GENERATION_ID} is only the fallback, for threads checkpointed before
     * {@link PlannerState#RESULT_GENERATION_ID} existed.
     */
    private static Optional<UUID> parentOf(PlannerState state) {
        return state.resultGenerationId().or(state::generationId);
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
     * The one write this node does, guarded so that a failure costs the edit and nothing else: a row the
     * cleanup already deleted, a parent that is no longer READY, or a database that is simply down all
     * come back as an INTERNAL report one frame up. A sink that answers with no id counts as a failed
     * write too: there would be no generation to point the client at.
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
        return consumed(locale, EditReport.allRejected(edits, reason), Optional.empty());
    }

    /**
     * The minimal update that parks the thread cleanly whatever went wrong: the batch is consumed, the
     * action cleared, and the turn is reported as INTERNAL. It touches neither RESULT nor GENERATION_ID,
     * so the organizer keeps exactly the packages they had; its one message is a constant, because the
     * held-back reply is dropped like on every other failure and the turn must still answer.
     */
    private static Map<String, Object> parkedWithInternalReport(PlannerState state) {
        Map<String, Object> update = parked(internalReport(state));
        update.put(PlannerState.MESSAGES, List.of(PlannerState.message(ChatMessage.ASSISTANT,
                EditMessages.internalFailure(localeOrDefault(state)))));
        return update;
    }

    private static String localeOrDefault(PlannerState state) {
        try {
            return state.locale();
        } catch (RuntimeException e) {
            return "en";
        }
    }

    /**
     * The keys every path out of this node writes: the report it is answering with, the batch and the
     * held-back reply consumed, and a thread parked with nothing pending. The edits are cleared here and
     * nowhere else - a batch left in state would be applied again next turn.
     */
    private static Map<String, Object> parked(String reportJson) {
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.EDIT_REPORT, reportJson);
        update.put(PlannerState.EDITS, PlannerState.NO_EDITS);
        update.put(PlannerState.PENDING_REPLY, "");
        update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        update.put(PlannerState.RESUME_REASON, "");
        return update;
    }

    /** One entry per requested op where the batch can still be read, a single op-less entry where it cannot. */
    private static String internalReport(PlannerState state) {
        try {
            return JsonCodec.write(EditReport.allRejected(state.edits(), EditRejectionReason.INTERNAL));
        } catch (RuntimeException e) {
            // Reading the batch is one of the things that can have failed above, so this must not retry it
            // for real: a constant report still tells the client the turn went wrong.
            log.error("planner edit batch unreadable error={}", e.getClass().getName());
            return INTERNAL_REPORT;
        }
    }

    /**
     * A finished batch: {@link #parked} plus the line that says what landed where and what did not. This
     * line is the confirmation - and, because it names the package, it is what the model scopes a later
     * "yes, add it" by.
     *
     * <p>The model's own reply ({@code reply}, held back by the chat turn) was written before any op was
     * checked and usually announces them. It is said, ahead of the confirmation, only when every op landed;
     * with anything rejected it would contradict the rejection that follows it ("Swapping X for Y now." /
     * "X is not in the Medium package"), so Java's line replaces it.
     */
    private static Map<String, Object> consumed(String locale, EditReport report, Optional<String> reply) {
        log.info("planner edits applied={} rejected={} refreshed={}", report.applied().size(),
                report.rejected().size(), report.textsRefreshed());
        Map<String, Object> update = parked(JsonCodec.write(report));
        List<Map<String, String>> messages = new ArrayList<>();
        boolean everyOpLanded = report.anyApplied() && report.rejected().isEmpty();
        if (everyOpLanded && reply.isPresent()) {
            messages.add(PlannerState.message(ChatMessage.ASSISTANT, reply.get()));
        } else if (reply.isPresent()) {
            // The chips answer a question in the reply that is no longer said; offered alone they make no sense.
            update.put(PlannerState.SUGGESTED_REPLIES, List.of());
        }
        String summary = EditMessages.summary(locale, report);
        if (!summary.isEmpty()) {
            // The templates are ours but the names they interpolate can still be the model's spelling, so
            // the finished sentence goes through the same cleaning every other stored text does.
            messages.add(PlannerState.message(ChatMessage.ASSISTANT, PlanAssembler.clean(summary)));
        }
        if (!messages.isEmpty()) {
            update.put(PlannerState.MESSAGES, messages);
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
