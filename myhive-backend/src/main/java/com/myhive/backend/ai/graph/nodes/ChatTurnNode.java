package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.model.Tier;
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
        boolean asksNothing = result.reply() == null || !result.reply().contains(QUESTION_MARK);
        Brief collected = BriefMerger.merge(state.brief(), result.briefUpdate());
        // Notes are taste only once the model has stopped asking for it: "my brother's stag" is a note,
        // and while the chat still asks what the group is into, the answer is on its way.
        Brief merged = asksNothing ? collected.withNotesAsTaste() : collected;
        if (!merged.equals(collected)) {
            log.info("planner chat read the notes as taste");
        }
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
        // Same for a reply an earlier edit turn held back: it belongs to that turn and is never said later.
        update.put(PlannerState.PENDING_REPLY, "");
        boolean routesToEdit = false;
        // Under a draft a typed "add X" - or a bare "ak 47" - is not carried out. What it names is offered
        // above the chat, the best match on a card with Add, and the organizer puts it in with a tap: the
        // draft only grows by their own hand. Taking out and swapping are still done on the word.
        List<EditRequest> edits = result.edits();
        List<String> offered = new ArrayList<>();
        if (!packages.isEmpty() && !readyToBuild) {
            List<EditRequest> carriedOut = new ArrayList<>();
            for (EditRequest edit : edits) {
                if (edit.op() == EditOp.ADD) {
                    if (edit.activity() != null) {
                        offered.add(edit.activity());
                    }
                    offered.addAll(edit.alternatives());
                } else {
                    carriedOut.add(edit);
                }
            }
            edits = carriedOut;
            offered.addAll(result.recommendations());
        }
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
        } else if (edits.isEmpty()) {
            update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
        } else if (packages.isEmpty()) {
            // Nothing to edit yet: report the ops as rejected so the client sees why, and say so in chat.
            update.put(PlannerState.ACTION, PlannerState.ACTION_NONE);
            update.put(PlannerState.EDIT_REPORT, JsonCodec.write(
                    EditReport.allRejected(result.edits(), EditRejectionReason.NO_PACKAGES_YET)));
            notes.add(EditMessages.noPackagesYet(state.locale()));
        } else {
            update.put(PlannerState.ACTION, PlannerState.ACTION_EDIT);
            update.put(PlannerState.EDITS, JsonCodec.write(intoTheDraft(edits, state)));
            routesToEdit = true;
        }
        if (!packages.isEmpty() && !readyToBuild) {
            // The draft exists and this turn rebuilds nothing. What the chat says about it is not left to
            // the model: with activities offered above it, it points at them; and it never says it is
            // building options - before an edit's own line that reply is simply dropped.
            if (routesToEdit) {
                reply = BuildingReply.announcesABuild(reply) ? "" : reply;
            } else if (!offered.isEmpty()) {
                reply = BuildingReply.recommending(state.locale());
            } else if (BuildingReply.announcesABuild(reply)) {
                reply = BuildingReply.nothingToBuild(state.locale());
            }
        }
        Optional<String> question = asksNothing
                ? MissingFieldQuestion.of(merged.missingFields(), state.locale()) : Optional.empty();
        if (question.isPresent()) {
            // Nothing is being built and nothing was asked: the organizer has no move left. A model that
            // reported no gap was announcing a build, which would be a promise Java is not keeping, so its
            // reply goes; one that knew of the gap said something else worth keeping, and the question
            // follows it.
            boolean announcedABuild = result.missingFields().isEmpty();
            if (announcedABuild) {
                reply = question.get();
            } else {
                notes.add(question.get());
            }
            suggestedReplies = chipsFor(merged, state);
            // Field names only: neither the reply nor the brief's texts belong in a log.
            log.info("planner chat asked nothing with the brief incomplete gap={} question={}",
                    merged.missingFields(), announcedABuild ? "replaced the reply" : "followed the reply");
        }
        if (routesToEdit) {
            // The model wrote this reply in the same breath as the ops, before Java checked any of them, so
            // it tends to announce them ("Swapping X for Y now."). Said here, it would stand next to the
            // rejection of that very swap; applyEdits says it only when every op actually landed.
            update.put(PlannerState.PENDING_REPLY, reply == null ? "" : reply);
            if (!notes.isEmpty()) {
                update.put(PlannerState.MESSAGES, messages(notes));
            }
        } else {
            List<String> all = new ArrayList<>();
            all.add(reply);
            all.addAll(notes);
            update.put(PlannerState.MESSAGES, messages(all));
        }
        update.put(PlannerState.SUGGESTED_REPLIES, suggestedReplies);
        // Only for a draft that exists and stays: before the first build there is nothing to add them to,
        // and a rebuild replaces the packages they would be added to. Replaced every turn either way.
        update.put(PlannerState.RECOMMENDATIONS,
                packages.isEmpty() || readyToBuild ? List.of() : List.copyOf(offered));
        update.put(PlannerState.SHOW_PACKAGE,
                packages.isEmpty() || readyToBuild || result.showPackage() == null ? "" : result.showPackage());
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

    /**
     * Tap-to-send answers: for the taste question, from what the catalog can deliver; none for the others
     * (travel times are never asked).
     */
    private static List<String> chipsFor(Brief brief, PlannerState state) {
        String first = brief.missingFields().get(0);
        if (Brief.FIELD_PREFERENCES.equals(first)) {
            return OpeningReplies.forCatalog(state.catalog(), state.locale());
        }
        return List.of();
    }


    /**
     * Once the organizer works on one trim, that trim is the only package there is: every edit lands in it,
     * whatever package the model named or left out ("add ak" used to add to all three).
     */
    private static List<EditRequest> intoTheDraft(List<EditRequest> edits, PlannerState state) {
        Optional<Tier> draft = state.workingPackage();
        if (draft.isEmpty()) {
            return edits;
        }
        return edits.stream()
                .map(edit -> new EditRequest(edit.op(), edit.activity(), edit.replacement(), draft.get(),
                        edit.dayNumber(), edit.slot(), edit.alternatives()))
                .toList();
    }

    /** The reply first, then Java's own notes, stamped in that order. */
    private static List<Map<String, String>> messages(List<String> texts) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (String text : texts) {
            messages.add(PlannerState.message(ChatMessage.ASSISTANT, text));
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
                state.messages(), PackagesView.render(packages.get(), state.workingPackage().orElse(null)),
                PackagesView.catalogNames(state.catalog()));
    }
}
