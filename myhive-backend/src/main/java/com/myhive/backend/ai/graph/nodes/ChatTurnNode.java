package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.catalog.OpeningReplies;
import com.myhive.backend.ai.edit.ActivityNameResolver;
import com.myhive.backend.ai.edit.EditMessages;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.PackagesView;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.ChatTurnRequest;
import com.myhive.backend.ai.llm.ChatTurnResult;
import com.myhive.backend.ai.llm.LlmGateway;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.BriefMerger;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One chat turn: ask the model, merge what it extracted into the brief, and decide what happens next -
 * a full regeneration, an edit of the packages that already exist, or nothing but the reply.
 */
@Component
@Slf4j
public class ChatTurnNode implements NodeAction<PlannerState> {

    /** How many chips have to name a catalog activity for a question to count as one about its variants. */
    private static final int MIN_VARIANTS = 2;
    private static final String QUESTION_MARK = "?";

    private final LlmGateway llm;
    /** Null where no catalog is wired (unit tests): the chat then offers no catalog-backed follow-ups. */
    private final CatalogSnapshotter snapshotter;

    @Autowired
    public ChatTurnNode(LlmGateway llm, CatalogSnapshotter snapshotter) {
        this.llm = llm;
        this.snapshotter = snapshotter;
    }

    public ChatTurnNode(LlmGateway llm) {
        this(llm, null);
    }

    @Override
    public Map<String, Object> apply(PlannerState state) {
        if (PlannerState.ACTION_SEED.equals(state.action())) {
            return seed(state);
        }
        Optional<ComposedPlan> packages = state.result();
        ChatTurnResult result = llm.chatTurn(request(state, packages));
        Brief merged = BriefMerger.merge(state.brief(), result.briefUpdate());
        // Java decides when to generate: the brief is complete and differs from what the last generation used.
        // No confirmation step - the owner wants the three packages the moment the facts are known.
        String mergedJson = JsonCodec.write(merged);
        boolean changedSinceLastGeneration = !mergedJson.equals(state.lastGeneratedBrief().orElse(null));
        boolean readyToBuild = merged.isReady() && changedSinceLastGeneration;
        // The one thing worth waiting for: a question about which variant of a catalog product the group
        // wants. Only before the first packages, and only once - after that the chat edits, it does not ask.
        boolean heldForAnAnswer = readyToBuild && packages.isEmpty() && !state.pairingAsked()
                && asksAboutCatalogVariants(result, state.catalog());
        Map<String, Object> update = new HashMap<>();
        // A report belongs to the turn that produced it. Cleared here, at the start of every turn, so that
        // last turn's rejections cannot be served again with this turn's answer; the branches below and
        // applyEdits write the new one over it. The service clears it before resuming as well - one of the
        // two owns the graph, the other owns the API, and neither can see the other's failure.
        update.put(PlannerState.EDIT_REPORT, "");
        String reply = PlanAssembler.clean(result.reply());
        List<String> suggestedReplies = result.suggestedReplies();
        List<String> notes = new ArrayList<>();
        if (heldForAnAnswer) {
            update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
            update.put(PlannerState.PAIRING_ASKED, true);
        } else if (readyToBuild) {
            // A regeneration rebuilds every package from the brief, so an edit of the old ones is moot.
            update.put(PlannerState.ACTION, PlannerState.ACTION_GENERATE);
            // The build starts now and every message is refused until it lands, so a question in this
            // reply could not be answered and its chips could not be tapped: neither is passed on.
            reply = BuildingReply.of(reply, state.locale());
            suggestedReplies = List.of();
            if (!result.edits().isEmpty()) {
                // Deliberately no EDIT_REPORT: the ops were never attempted, so there is nothing to report
                // per op - but the group asked for something concrete and has to be told it is coming back
                // to them after the rebuild, not quietly dropped.
                notes.add(EditMessages.rebuildingFirst(state.locale()));
            }
        } else if (result.edits().isEmpty()) {
            update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        } else if (packages.isEmpty()) {
            // Nothing to edit yet: report the ops as rejected so the client sees why, and say so in chat.
            update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
            update.put(PlannerState.EDIT_REPORT, JsonCodec.write(
                    EditReport.allRejected(result.edits(), EditRejectionReason.NO_PACKAGES_YET)));
            notes.add(EditMessages.noPackagesYet(state.locale()));
        } else {
            update.put(PlannerState.ACTION, PlannerState.ACTION_EDIT);
            update.put(PlannerState.EDITS, JsonCodec.write(result.edits()));
        }
        update.put(PlannerState.MESSAGES, messages(reply, notes));
        update.put(PlannerState.SUGGESTED_REPLIES, suggestedReplies);
        update.put(PlannerState.BRIEF, mergedJson);
        update.put(PlannerState.MISSING_FIELDS, merged.missingFields());
        return update;
    }

    /**
     * Seeding a fresh thread only has to park it at awaitUser; there is nothing to reply to yet. The catalog
     * is snapshotted here so the chat can pair what the organizer asks for with what is actually on offer
     * before the first generation - and so the opening chips only promise what it can deliver;
     * snapshotCatalog re-takes it for the planner.
     */
    private Map<String, Object> seed(PlannerState state) {
        Map<String, Object> update = new HashMap<>();
        update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        seedCatalog(state).ifPresent(catalog -> {
            update.put(PlannerState.CATALOG, JsonCodec.write(catalog));
            update.put(PlannerState.SUGGESTED_REPLIES, OpeningReplies.forCatalog(catalog, state.locale()));
        });
        return update;
    }

    /**
     * Best effort: the chat works without a catalog - it just cannot name variants or offer opening chips -
     * and the planner takes its own snapshot, so a failed read here must not cost the organizer the chat
     * they just opened.
     */
    private Optional<List<CatalogActivity>> seedCatalog(PlannerState state) {
        if (snapshotter == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(snapshotter.snapshot(state.destinationId(), state.brief(), state.locale()));
        } catch (RuntimeException e) {
            log.warn("planner seed catalog snapshot failed destination={} error={}", state.destinationId(),
                    e.getClass().getName());
            return Optional.empty();
        }
    }

    /**
     * A question whose answers are things the catalog sells: that is the pairing follow-up ("dinner - just
     * steak, or with a show?"). Judged by the chips rather than by the model's word for it - at least two
     * of them have to name a catalog activity - so a question about dates or group size, with chips like
     * "Arrive evening", never holds a build back.
     */
    private static boolean asksAboutCatalogVariants(ChatTurnResult result, List<CatalogActivity> catalog) {
        if (result.reply() == null || !result.reply().contains(QUESTION_MARK) || catalog.isEmpty()) {
            return false;
        }
        long named = result.suggestedReplies().stream()
                .filter(chip -> ActivityNameResolver.resolve(chip, catalog) instanceof ActivityNameResolver.Found)
                .count();
        return named >= MIN_VARIANTS;
    }

    /** The reply first, then Java's own notes, stamped in that order. */
    private static List<Map<String, String>> messages(String reply, List<String> notes) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(PlannerState.message(ChatMessage.ASSISTANT, reply));
        for (String note : notes) {
            messages.add(PlannerState.message(ChatMessage.ASSISTANT, note));
        }
        return messages;
    }

    /**
     * The model may only propose edits when it can see what it would be editing. Before that it still gets
     * the catalog names, to offer the variants of what the organizer picked (see {@code chat-system.st}).
     */
    private static ChatTurnRequest request(PlannerState state, Optional<ComposedPlan> packages) {
        if (packages.isEmpty()) {
            return new ChatTurnRequest(state.locale(), state.destinationName(), state.categorySlugs(), state.brief(),
                    state.messages(), null, PackagesView.catalogNames(state.catalog()));
        }
        return new ChatTurnRequest(state.locale(), state.destinationName(), state.categorySlugs(), state.brief(),
                state.messages(), PackagesView.render(packages.get()), PackagesView.catalogNames(state.catalog()));
    }
}
