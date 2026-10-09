package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.model.Wish;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogSnapshotter;
import com.myhive.backend.ai.catalog.CatalogSearch;
import com.myhive.backend.ai.catalog.OpeningReplies;
import com.myhive.backend.ai.edit.ActivityNameResolver;
import com.myhive.backend.ai.edit.EditMessages;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.PackagesView;
import com.myhive.backend.ai.edit.RejectedEdit;
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
        // The catalog is a JSON blob in the checkpoint, parsed on every call: once per turn, here.
        List<CatalogActivity> catalog = state.catalog();
        ChatTurnResult result = llm.chatTurn(request(state, packages, catalog));
        boolean asksNothing = result.reply() == null || !result.reply().contains(QUESTION_MARK);
        Brief collected = separateWishes(BriefMerger.merge(state.brief(), result.briefUpdate()),
                lastUserMessage(state));
        // Notes are taste only once the model has stopped asking for it: "my brother's stag" is a note,
        // and while the chat still asks what the group is into, the answer is on its way.
        // Wishes are taste in the organizer's own words too: the planner reads the vibe, so a model that
        // filed "tank on Saturday" under the wishes alone must not leave it with nothing to go by.
        Brief worded = !collected.wishes().isEmpty() && (collected.vibe() == null || collected.vibe().isBlank())
                && packages.isEmpty() ? collected.withVibe(firstChars(lastUserMessage(state), Brief.MAX_TEXT)) : collected;
        Brief read = asksNothing ? worded.withNotesAsTaste() : worded;
        if (!read.equals(worded)) {
            log.info("planner chat read the notes as taste");
        }
        // What the weekend is built around is asked once. Days and people were known before this message,
        // so that question has been put (by the greeting over the pickers, or by the turn before): whatever
        // came back, nothing more is asked and the options are built - open to anything when no taste came.
        // The organizer's first message does not count as the answer: with the pickers' days and people
        // preset it is the line the page sends for them ("Plan 2 nights for 10 of us, Fri 16 - Sun 18"),
        // and building on it skipped the question altogether.
        Brief before = state.brief();
        long saidByThem = state.messages().stream().filter(said -> ChatMessage.USER.equals(said.role())).count();
        boolean tasteWasAsked = before.days() != null && before.groupSize() != null && saidByThem > 1;
        boolean onlyTasteMissing = read.missingFields().equals(List.of(Brief.FIELD_PREFERENCES));
        Brief merged = tasteWasAsked && onlyTasteMissing ? read.withVibe(Brief.OPEN_TO_ANYTHING) : read;
        // Java decides when to generate: the brief is complete and differs from what the last generation used.
        // No confirmation step - the owner wants the three packages the moment the facts are known.
        String mergedJson = JsonCodec.write(merged);
        boolean changedSinceLastGeneration = !mergedJson.equals(state.lastGeneratedBrief().orElse(null));
        // A plan the organizer has made their own is not rebuilt because the talk about it shifted the
        // taste: a rebuild starts again from the ready-made packages and would throw away everything
        // they added, removed and moved. Only what changes the shape of the trip - its length, the
        // head-count the prices hang on - still rebuilds it.
        boolean keepsTheirPlan = packages.isPresent() && state.workingPackage().isPresent()
                && sameTripShape(merged, state.lastGeneratedBrief().orElse(null));
        boolean wouldRebuild = merged.isReady() && changedSinceLastGeneration && !keepsTheirPlan;
        // A change of days or head-count rebuilds from the ready-made packages and drops what the organizer
        // added, took out and moved. That is not done on one sentence: the first time it is said what it
        // costs and the trip stays as it is; asked for again on the next turn, it goes ahead.
        boolean warnsFirst = wouldRebuild && packages.isPresent() && state.workingPackage().isPresent()
                && !state.rebuildWarned();
        if (warnsFirst) {
            merged = merged.withShape(before.days(), before.groupSize());
            mergedJson = JsonCodec.write(merged);
        }
        boolean readyToBuild = wouldRebuild && !warnsFirst;
        Map<String, Object> update = new HashMap<>();
        // A report belongs to the turn that produced it. Cleared here, at the start of every turn, so that
        // last turn's rejections cannot be served again with this turn's answer; the branches below and
        // applyEdits write the new one over it. The service clears it before resuming as well - one of the
        // two owns the graph, the other owns the API, and neither can see the other's failure.
        update.put(PlannerState.EDIT_REPORT, "");
        // Same for a reply an earlier edit turn held back: it belongs to that turn and is never said later.
        update.put(PlannerState.PENDING_REPLY, "");
        update.put(PlannerState.REBUILD_WARNED, warnsFirst);
        boolean routesToEdit = false;
        // Under a draft a typed "add X" that names one catalog activity is carried out on the word, like
        // taking out and swapping are: asked for and not done reads as a chat that does not work. A name
        // that fits several rows, or none, is not guessed at: what comes closest is listed with Add.
        List<EditRequest> edits = result.edits();
        // What goes on the cards: only a catalog row can. What the organizer asked for and the catalog
        // lacks is said instead - "the top match is above" over an empty row would send them looking -
        // and a name that fits two rows is a question back. The model's own suggestions and alternatives
        // are offered when they resolve and dropped quietly when they do not: nobody asked for them.
        List<String> offered = new ArrayList<>();
        List<RejectedEdit> unanswered = new ArrayList<>();
        boolean splitWish = false;
        if (!packages.isEmpty() && !readyToBuild) {
            String asked = lastUserMessage(state);
            // An add is carried out when it was ordered ("add the boat") or picks from the list the chat
            // just showed ("the AK one"). A bare "river cruise" asks what there is: it gets the list.
            boolean ordered = CatalogSearch.asksToAdd(asked);
            List<String> shownBefore = state.recommendations();
            List<EditRequest> carriedOut = new ArrayList<>();
            for (EditRequest edit : edits) {
                boolean namesOne = edit.activity() != null && ActivityNameResolver
                        .resolve(edit.activity(), catalog) instanceof ActivityNameResolver.Found;
                boolean picksFromTheList = namesOne && shownBefore.stream()
                        .anyMatch(shown -> sameActivity(shown, edit.activity(), catalog));
                if (edit.op() != EditOp.ADD || (namesOne && (ordered || picksFromTheList))) {
                    carriedOut.add(edit);
                } else if (namesOne) {
                    offer(edit.activity(), catalog, offered);
                } else {
                    if (edit.activity() != null) {
                        unanswered.add(unanswered(edit.activity(), catalog));
                    }
                    edit.alternatives().forEach(alternative -> offer(alternative, catalog, offered));
                }
            }
            result.recommendations().forEach(name -> offer(name, catalog, offered));
            // What is listed is not the model's guess from the names: the organizer's words are read against
            // the catalog itself, and nothing is filled up with activities that merely share a category.
            CatalogSearch.Result found = CatalogSearch.find(asked, offered, catalog,
                    inThePlan(packages.get(), state.workingPackage().orElse(null)));
            boolean adds = carriedOut.stream().anyMatch(edit -> edit.op() == EditOp.ADD);
            if (found.split() && adds) {
                // "Add a river cruise with a private show": no activity is both, so adding the one the
                // model settled on would be half of what was asked. Both kinds are shown instead.
                carriedOut.removeIf(edit -> edit.op() == EditOp.ADD);
            }
            edits = carriedOut;
            // A question that names a kind ("what boats do you have?") gets its list even when the model
            // answered it with a question back ("Sure - which kind?").
            boolean asksForAKind = result.showPackage() == null && !warnsFirst && !asksNothing
                    && CatalogSearch.asksForAKind(asked);
            if (carriedOut.isEmpty() && (!offered.isEmpty() || !unanswered.isEmpty() || asksForAKind)
                    && !found.names().isEmpty()) {
                offered.clear();
                offered.addAll(found.names());
                splitWish = found.split();
            } else if (!carriedOut.isEmpty()) {
                // The turn changes the plan: its line is the answer, with no list next to it.
                offered.clear();
            }
        }
        String reply = PlanAssembler.clean(result.reply());
        List<String> suggestedReplies = result.suggestedReplies();
        if (warnsFirst) {
            reply = BuildingReply.rebuildWarning(state.locale(), read.days(), read.groupSize());
            suggestedReplies = BuildingReply.rebuildChoices(state.locale(), read.days(), read.groupSize());
            edits = List.of();
            offered.clear();
            unanswered.clear();
        }
        List<String> notes = new ArrayList<>();
        if (readyToBuild) {
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
                // The edit's own line ("Added X to your trip plan.") is the answer; the model's "Let me
                // check that." before it is one line too many in a chat that shows the last three.
                reply = "";
            } else if (splitWish) {
                reply = BuildingReply.splitWish(state.locale());
            } else if (!offered.isEmpty() && unanswered.isEmpty()) {
                reply = BuildingReply.recommending(state.locale());
            } else if (!offered.isEmpty() && !unanswered.isEmpty()) {
                // Asked for by a word the catalog has no row for, with rows that are that kind of thing
                // ("strippers"): not "I could not find it" over a list of exactly it.
                // A word that fits several rows is a question back, with those rows under it.
                reply = unanswered.get(0).reason() == EditRejectionReason.AMBIGUOUS_ACTIVITY
                        ? EditMessages.rejectionSummary(state.locale(), unanswered)
                        : EditMessages.closest(state.locale(), unanswered.get(0).activityName());
            } else if (!offered.isEmpty() || !unanswered.isEmpty()) {
                reply = unanswered.isEmpty() ? BuildingReply.recommending(state.locale())
                        : EditMessages.rejectionSummary(state.locale(), unanswered);
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
            suggestedReplies = chipsFor(merged, state, catalog);
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

    /** Same number of days and people as the brief the current packages were built from. */
    private static boolean sameTripShape(Brief merged, String lastGeneratedJson) {
        if (lastGeneratedJson == null || lastGeneratedJson.isBlank()) {
            return false;
        }
        Brief built = JsonCodec.read(lastGeneratedJson, Brief.class);
        return built != null && java.util.Objects.equals(built.days(), merged.days())
                && java.util.Objects.equals(built.groupSize(), merged.groupSize());
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
     * Tap-to-send answers: for the taste question, from what the catalog can deliver; none for the others
     * (travel times are never asked).
     */
    private static final java.util.regex.Pattern A_CHOICE =
            java.util.regex.Pattern.compile("\\b(or|oder|either)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Several names in one wish mean "one of these". The model files "beer spa and paintball" that way too,
     * which would be met by the paintball alone: unless the organizer's message offers a choice, each name
     * is a wish of its own.
     */
    private static Brief separateWishes(Brief brief, String asked) {
        boolean choice = A_CHOICE.matcher(asked).find();
        if (choice || brief.wishes().stream().noneMatch(wish -> wish.activities().size() > 1)) {
            return brief;
        }
        List<Wish> separate = new ArrayList<>();
        for (Wish wish : brief.wishes()) {
            wish.activities().forEach(name -> separate.add(new Wish(List.of(name), wish.dayNumber())));
        }
        return brief.withWishes(separate);
    }

    private static String firstChars(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String lastUserMessage(PlannerState state) {
        List<ChatMessage> said = state.messages();
        for (int i = said.size() - 1; i >= 0; i--) {
            if (ChatMessage.USER.equals(said.get(i).role())) {
                return said.get(i).content();
            }
        }
        return "";
    }

    /** The activities of the organizer's draft - of every package while no trim is theirs yet. */
    private static java.util.Set<java.util.UUID> inThePlan(ComposedPlan plan, Tier working) {
        java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
        if (working == null) {
            return ids;
        }
        plan.packages().stream().filter(pkg -> pkg.key() == working)
                .forEach(pkg -> pkg.days().forEach(day -> day.items().forEach(item -> ids.add(item.activityId()))));
        return ids;
    }

    private static boolean sameActivity(String shown, String named, List<CatalogActivity> catalog) {
        return ActivityNameResolver.resolve(shown, catalog) instanceof ActivityNameResolver.Found a
                && ActivityNameResolver.resolve(named, catalog) instanceof ActivityNameResolver.Found b
                && a.activity().id().equals(b.activity().id());
    }

    /** Puts a name on the cards when it is one catalog row; true when it is. */
    private static boolean offer(String name, List<CatalogActivity> catalog, List<String> offered) {
        if (ActivityNameResolver.resolve(name, catalog) instanceof ActivityNameResolver.Found) {
            offered.add(name);
            return true;
        }
        return false;
    }

    /** Why a typed name is not on a card: the catalog lacks it, or holds more than one of it. */
    private static RejectedEdit unanswered(String name, List<CatalogActivity> catalog) {
        String cleaned = PlanAssembler.clean(name);
        if (ActivityNameResolver.resolve(name, catalog) instanceof ActivityNameResolver.Ambiguous ambiguous) {
            return new RejectedEdit(EditOp.ADD, cleaned, null, EditRejectionReason.AMBIGUOUS_ACTIVITY,
                    String.join(", ", ambiguous.candidates()));
        }
        return new RejectedEdit(EditOp.ADD, cleaned, null, EditRejectionReason.UNKNOWN_ACTIVITY, cleaned);
    }

    private static List<String> chipsFor(Brief brief, PlannerState state, List<CatalogActivity> catalog) {
        String first = brief.missingFields().get(0);
        if (Brief.FIELD_PREFERENCES.equals(first)) {
            return OpeningReplies.forCatalog(catalog, state.locale());
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
    private static ChatTurnRequest request(PlannerState state, Optional<ComposedPlan> packages,
            List<CatalogActivity> catalog) {
        if (packages.isEmpty()) {
            return new ChatTurnRequest(state.locale(), state.destinationName(), state.categorySlugs(), state.brief(),
                    state.messages(), null, PackagesView.catalogNames(catalog));
        }
        String view = PackagesView.render(packages.get(), state.workingPackage().orElse(null),
                state.brief().groupSize());
        // The other ready-made weekends are an offer for all three options: once one trim is the
        // organizer's draft they are not on the table, and the chat is not told about them.
        String themes = state.workingPackage().isPresent() ? ""
                : PackagesView.themes(packages.get(), catalog, state.presets());
        return new ChatTurnRequest(state.locale(), state.destinationName(), state.categorySlugs(), state.brief(),
                state.messages(), themes.isEmpty() ? view : view + "\n" + themes,
                PackagesView.catalogNames(catalog));
    }
}
