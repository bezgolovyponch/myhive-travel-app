package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Tier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The short assistant lines Java writes itself after an edit turn: what landed and where, then what
 * could not be done and why. They are templates rather than another model call: the organizer is
 * already waiting for the edited packages, and a confirmation or a rejection has to be honest, not
 * creative. English and German (informal "du"); any other locale falls back to English, as the rest of
 * the planner does.
 *
 * <p>The name in a rejection is the model's spelling when it could not be resolved — and for a failed
 * {@code REPLACE} it is the <em>replacement</em>'s spelling — so the unresolved reasons talk about the
 * catalog, never about "your package". A rejection that did reach a package names it: "already in the
 * Medium package" next to "added to the Premium package" is what makes a mixed outcome readable.
 */
public final class EditMessages {

    private static final String GERMAN = "de";

    private static final String EN_NO_PACKAGES_YET =
            "Let us build the packages first - then I can add or swap activities for you.";
    private static final String DE_NO_PACKAGES_YET =
            "Lass uns zuerst die Pakete bauen - danach kann ich Aktivitäten für dich hinzufügen oder tauschen.";

    private static final String EN_REBUILDING_FIRST =
            "I am rebuilding the packages with the new details first - ask me for those changes again once "
                    + "they are ready.";
    private static final String DE_REBUILDING_FIRST =
            "Ich baue die Pakete erst mit den neuen Angaben neu - frag danach noch mal nach diesen Änderungen.";

    /** The line for a batch that failed so early not even its activity names could be read back. */
    private static final String EN_INTERNAL_FAILURE = "Something went wrong while changing the packages - give it another try.";
    private static final String DE_INTERNAL_FAILURE = "Beim Ändern der Pakete ist etwas schiefgelaufen - probier es noch mal.";

    /** Stands in for a name the model left out; only a parser bug gets this far. */
    private static final String EN_UNNAMED = "that activity";
    private static final String DE_UNNAMED = "diese Aktivität";

    /**
     * For a name the catalog lacks when there is something close to offer. The offers themselves are the
     * row above the chat - the top match with Add, the rest as tags - so the line only points there; it
     * does not open with "I could not find" what the organizer can add with the next tap.
     */
    private static final String EN_CLOSEST = "Here is what comes closest to \"%s\" - tap a name to see it, or add it.";
    private static final String EN_MOVED = "Moved %s to day %d.";
    private static final String DE_MOVED = "%s auf Tag %d verschoben.";
    private static final String DE_CLOSEST = "Das kommt \"%s\" am nächsten - tippe einen Namen an, um es zu sehen, oder füg es hinzu.";

    /**
     * How a list of packages is phrased. English never declines, so only the German articles differ:
     * "aus dem Premium-Paket" / "aus den Basic- und Premium-Paketen", "ins" / "in die", "im" / "in den".
     */
    private enum PackagePhrase {
        FROM("aus dem", "aus den", "Paketen", "aus deinem Trip-Plan"),
        INTO("ins", "in die", "Pakete", "in deinen Trip-Plan"),
        IN("im", "in den", "Paketen", "in deinem Trip-Plan");

        private final String singularArticle;
        private final String pluralArticle;
        private final String pluralNoun;
        /** The organizer's own plan in this case: there is one, so no article to pick. */
        private final String tripPlan;

        PackagePhrase(String singularArticle, String pluralArticle, String pluralNoun, String tripPlan) {
            this.singularArticle = singularArticle;
            this.pluralArticle = pluralArticle;
            this.pluralNoun = pluralNoun;
            this.tripPlan = tripPlan;
        }
    }

    /**
     * A template that names packages, with the German case its preposition wants. Applied templates take
     * (activity, replacement, packages) by position; in-package rejection templates take (names, packages).
     */
    private record Packaged(String template, PackagePhrase phrase) {
    }

    private static final Map<EditOp, Packaged> EN_APPLIED = new EnumMap<>(EditOp.class);
    private static final Map<EditOp, Packaged> DE_APPLIED = new EnumMap<>(EditOp.class);

    /** Rejections that never reached a package (name resolution, limits) or whose package is beside the point. */
    private static final Map<EditRejectionReason, String> EN = new EnumMap<>(EditRejectionReason.class);
    private static final Map<EditRejectionReason, String> DE = new EnumMap<>(EditRejectionReason.class);

    /** Rejections that did reach a package; the others fall back to the plain template above. */
    private static final Map<EditRejectionReason, Packaged> EN_IN_PACKAGE = new EnumMap<>(EditRejectionReason.class);
    private static final Map<EditRejectionReason, Packaged> DE_IN_PACKAGE = new EnumMap<>(EditRejectionReason.class);

    static {
        EN_APPLIED.put(EditOp.REMOVE, new Packaged("Dropped %1$s from %3$s.", PackagePhrase.FROM));
        EN_APPLIED.put(EditOp.ADD, new Packaged("Added %1$s to %3$s.", PackagePhrase.INTO));
        EN_APPLIED.put(EditOp.REPLACE, new Packaged("Swapped %1$s for %2$s in %3$s.", PackagePhrase.IN));
        DE_APPLIED.put(EditOp.REMOVE, new Packaged("%1$s %3$s rausgenommen.", PackagePhrase.FROM));
        DE_APPLIED.put(EditOp.ADD, new Packaged("%1$s %3$s aufgenommen.", PackagePhrase.INTO));
        DE_APPLIED.put(EditOp.REPLACE, new Packaged("%1$s %3$s gegen %2$s getauscht.", PackagePhrase.IN));

        EN.put(EditRejectionReason.UNKNOWN_ACTIVITY, "I could not find \"%s\" in the catalog.");
        EN.put(EditRejectionReason.AMBIGUOUS_ACTIVITY, "\"%s\" matches more than one catalog entry - which one?");
        EN.put(EditRejectionReason.NOT_IN_PACKAGE, "%s is not in there, so there was nothing to change.");
        EN.put(EditRejectionReason.ALREADY_IN_PACKAGE, "%s is already in there.");
        EN.put(EditRejectionReason.WOULD_EMPTY_PACKAGE, "I could not drop %s: it would leave a package empty.");
        EN.put(EditRejectionReason.NO_FREE_SLOT, "I could not fit %s in: no free slot left.");
        EN.put(EditRejectionReason.WOULD_BREAK_SCHEDULE, "%s does not fit the schedule, so I left things as they were.");
        EN.put(EditRejectionReason.NO_PACKAGES_YET, "I cannot change %s yet - let us build the packages first.");
        EN.put(EditRejectionReason.EDIT_LIMIT, "You have used up the changes for this session, so %s stays as it is.");
        EN.put(EditRejectionReason.INTERNAL, "Something went wrong while changing %s - give it another try.");

        DE.put(EditRejectionReason.UNKNOWN_ACTIVITY, "\"%s\" habe ich im Katalog nicht gefunden.");
        DE.put(EditRejectionReason.AMBIGUOUS_ACTIVITY, "\"%s\" passt auf mehrere Katalogeinträge - welcher davon?");
        DE.put(EditRejectionReason.NOT_IN_PACKAGE, "%s ist nicht dabei, da gab es nichts zu ändern.");
        DE.put(EditRejectionReason.ALREADY_IN_PACKAGE, "%s ist schon dabei.");
        DE.put(EditRejectionReason.WOULD_EMPTY_PACKAGE, "%s konnte ich nicht rauswerfen: ein Paket bliebe leer.");
        DE.put(EditRejectionReason.NO_FREE_SLOT, "%s konnte ich nicht unterbringen: kein freier Slot mehr.");
        DE.put(EditRejectionReason.WOULD_BREAK_SCHEDULE, "%s passt nicht in den Ablauf, deshalb lasse ich alles so.");
        DE.put(EditRejectionReason.NO_PACKAGES_YET, "%s kann ich noch nicht ändern - lass uns zuerst die Pakete bauen.");
        DE.put(EditRejectionReason.EDIT_LIMIT, "Für diese Session sind die Änderungen aufgebraucht, %s bleibt so.");
        DE.put(EditRejectionReason.INTERNAL, "Beim Ändern von %s ist etwas schiefgelaufen - probier es noch mal.");

        EN_IN_PACKAGE.put(EditRejectionReason.NOT_IN_PACKAGE,
                new Packaged("%1$s is not in %2$s, so there was nothing to change.", PackagePhrase.IN));
        EN_IN_PACKAGE.put(EditRejectionReason.ALREADY_IN_PACKAGE,
                new Packaged("%1$s is already in %2$s.", PackagePhrase.IN));
        EN_IN_PACKAGE.put(EditRejectionReason.WOULD_EMPTY_PACKAGE,
                new Packaged("I could not drop %1$s: it would leave %2$s empty.", PackagePhrase.IN));
        EN_IN_PACKAGE.put(EditRejectionReason.NO_FREE_SLOT,
                new Packaged("I could not fit %1$s in %2$s: no free slot left.", PackagePhrase.IN));
        EN_IN_PACKAGE.put(EditRejectionReason.WOULD_BREAK_SCHEDULE,
                new Packaged("%1$s does not fit the schedule of %2$s, so I left it as it was.", PackagePhrase.IN));

        DE_IN_PACKAGE.put(EditRejectionReason.NOT_IN_PACKAGE,
                new Packaged("%1$s ist nicht %2$s, da gab es nichts zu ändern.", PackagePhrase.IN));
        DE_IN_PACKAGE.put(EditRejectionReason.ALREADY_IN_PACKAGE,
                new Packaged("%1$s ist schon %2$s.", PackagePhrase.IN));
        DE_IN_PACKAGE.put(EditRejectionReason.WOULD_EMPTY_PACKAGE,
                new Packaged("%1$s konnte ich nicht %2$s rauswerfen: es bliebe nichts übrig.", PackagePhrase.FROM));
        DE_IN_PACKAGE.put(EditRejectionReason.NO_FREE_SLOT,
                new Packaged("%1$s konnte ich %2$s nicht unterbringen: kein freier Slot mehr.", PackagePhrase.IN));
        DE_IN_PACKAGE.put(EditRejectionReason.WOULD_BREAK_SCHEDULE,
                new Packaged("%1$s passt %2$s nicht in den Ablauf, deshalb lasse ich es so.", PackagePhrase.IN));
    }

    private EditMessages() {
    }

    /** The note that follows the reply when the organizer asks for a change before any packages exist. */
    public static String noPackagesYet(String locale) {
        return german(locale) ? DE_NO_PACKAGES_YET : EN_NO_PACKAGES_YET;
    }

    /**
     * The note for the one message that both changes the brief and asks for changes: the regeneration
     * wins and builds every package afresh, so the ops are moot rather than rejected — and without a line
     * saying so they simply vanished, with the reply happily confirming a swap that never happened.
     */
    public static String rebuildingFirst(String locale) {
        return german(locale) ? DE_REBUILDING_FIRST : EN_REBUILDING_FIRST;
    }

    /** Said when an edit batch failed before even its names could be read; it names nothing for that reason. */
    public static String internalFailure(String locale) {
        return german(locale) ? DE_INTERNAL_FAILURE : EN_INTERNAL_FAILURE;
    }

    /**
     * Everything an edit turn has to say after the model's own reply: what landed, then what did not.
     * The model is told never to claim a change is done, so the applied part is the confirmation — and,
     * naming the package, it is what a later "yes, add it" can be scoped by.
     */
    public static String summary(String locale, EditReport report) {
        return summary(locale, report, false);
    }

    /**
     * {@code tripPlan}: the organizer has made one option their own, so there is one plan and it is
     * theirs - "your trip plan", never "the Medium package".
     */
    public static String summary(String locale, EditReport report, boolean tripPlan) {
        String applied = appliedSummary(locale, report.applied(), tripPlan);
        String rejected = rejectionSummary(locale, report.rejected(), tripPlan);
        if (applied.isEmpty()) {
            return rejected;
        }
        if (rejected.isEmpty()) {
            return applied;
        }
        return applied + ' ' + rejected;
    }

    /**
     * One sentence per op and activity, naming every package it landed in ("Added Karting to the Basic
     * and Premium packages."); empty when nothing was applied.
     */
    public static String appliedSummary(String locale, List<AppliedEdit> applied) {
        return appliedSummary(locale, applied, false);
    }

    public static String appliedSummary(String locale, List<AppliedEdit> applied, boolean tripPlan) {
        if (applied == null || applied.isEmpty()) {
            return "";
        }
        boolean german = german(locale);
        Map<AppliedKey, Set<Tier>> packagesByEdit = new LinkedHashMap<>();
        for (AppliedEdit edit : applied) {
            Set<Tier> tiers = packagesByEdit.computeIfAbsent(AppliedKey.of(edit), key -> EnumSet.noneOf(Tier.class));
            if (edit.packageKey() != null) {
                tiers.add(edit.packageKey());
            }
        }
        Map<EditOp, Packaged> templates = german ? DE_APPLIED : EN_APPLIED;
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<AppliedKey, Set<Tier>> entry : packagesByEdit.entrySet()) {
            if (entry.getKey().op() == EditOp.MOVE) {
                // A move says where to, not which package: the day is the news.
                int day = applied.stream().filter(edit -> AppliedKey.of(edit).equals(entry.getKey()))
                        .findFirst().map(AppliedEdit::dayNumber).orElse(0);
                appendSentence(summary, (german ? DE_MOVED : EN_MOVED).formatted(entry.getKey().activity(), day));
                continue;
            }
            Packaged template = templates.get(entry.getKey().op());
            appendSentence(summary, template.template().formatted(entry.getKey().activity(),
                    entry.getKey().replacement(), packages(entry.getValue(), german, template.phrase(), tripPlan)));
        }
        return summary.toString();
    }

    /**
     * One sentence per distinct reason, naming the activities it hit and — where the rejection reached a
     * package — the packages; then one offer per activity the catalog lacks for which the model could
     * name something close. Empty when nothing was rejected.
     */
    public static String rejectionSummary(String locale, List<RejectedEdit> rejected) {
        return rejectionSummary(locale, rejected, false);
    }

    public static String rejectionSummary(String locale, List<RejectedEdit> rejected, boolean tripPlan) {
        if (rejected == null || rejected.isEmpty()) {
            return "";
        }
        boolean german = german(locale);
        Map<EditRejectionReason, Packaged> inPackage = german ? DE_IN_PACKAGE : EN_IN_PACKAGE;
        Map<RejectionKey, RejectionGroup> groups = new LinkedHashMap<>();
        for (RejectedEdit edit : rejected) {
            if (hasOffer(edit)) {
                // Said once, further down, as an offer rather than a refusal.
                continue;
            }
            boolean packaged = edit.packageKey() != null && inPackage.containsKey(edit.reason());
            RejectionGroup group = groups.computeIfAbsent(new RejectionKey(edit.reason(), packaged),
                    key -> new RejectionGroup());
            group.names.add(nameOf(edit, german));
            if (packaged) {
                group.tiers.add(edit.packageKey());
            }
        }
        Map<EditRejectionReason, String> plain = german ? DE : EN;
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<RejectionKey, RejectionGroup> entry : groups.entrySet()) {
            String names = String.join(", ", entry.getValue().names);
            if (entry.getKey().inPackage()) {
                Packaged template = inPackage.get(entry.getKey().reason());
                appendSentence(summary, template.template()
                        .formatted(names, packages(entry.getValue().tiers, german, template.phrase(), tripPlan)));
            } else {
                String template = plain.get(entry.getKey().reason());
                if (template != null) {
                    appendSentence(summary, template.formatted(names));
                }
            }
        }
        appendAlternatives(summary, rejected, german);
        return summary.toString();
    }

    /** Applied edits are grouped by what was done, so one ADD across three packages is one sentence. */
    private record AppliedKey(EditOp op, String activity, String replacement) {

        static AppliedKey of(AppliedEdit edit) {
            return new AppliedKey(edit.op(), edit.activityName(), edit.replacementName());
        }
    }

    /**
     * The same reason in and out of a package is two sentences: "Karting is not in any package" must not
     * borrow the package name of "Beer Bike is not in the Premium package".
     */
    private record RejectionKey(EditRejectionReason reason, boolean inPackage) {
    }

    /** The activity names one reason hit and, when the rejections reached packages, those packages. */
    private static final class RejectionGroup {
        private final Set<String> names = new LinkedHashSet<>();
        private final Set<Tier> tiers = EnumSet.noneOf(Tier.class);
    }

    /**
     * "Not in the catalog" alone is a dead end; the names that are close to it turn the rejection into
     * a choice the organizer can answer with a plain "add X". One offer per unknown name, first one wins.
     */
    private static void appendAlternatives(StringBuilder summary, List<RejectedEdit> rejected, boolean german) {
        Set<String> offeredFor = new LinkedHashSet<>();
        for (RejectedEdit edit : rejected) {
            if (!hasOffer(edit)) {
                continue;
            }
            String name = nameOf(edit, german);
            if (offeredFor.add(name)) {
                appendSentence(summary, (german ? DE_CLOSEST : EN_CLOSEST).formatted(name));
            }
        }
    }

    /** A name the catalog lacks, with catalog names close to it to offer instead. */
    private static boolean hasOffer(RejectedEdit edit) {
        return edit.reason() == EditRejectionReason.UNKNOWN_ACTIVITY && !edit.alternatives().isEmpty();
    }

    /**
     * "the Premium package" / "the Basic, Medium and Premium packages" — in German declined by the
     * phrase's preposition ("aus dem Premium-Paket", "in die Basic-, Medium- und Premium-Pakete").
     * An {@link EnumSet} iterates in tier order, so the list always reads Basic, Medium, Premium.
     */
    private static String packages(Set<Tier> tiers, boolean german, PackagePhrase phrase, boolean tripPlan) {
        if (tripPlan) {
            return german ? phrase.tripPlan : "your trip plan";
        }
        List<String> labels = new ArrayList<>();
        for (Tier tier : tiers) {
            labels.add(label(tier));
        }
        if (labels.isEmpty()) {
            return german ? phrase.singularArticle + " Paket" : "the package";
        }
        if (labels.size() == 1) {
            return german ? phrase.singularArticle + " " + labels.get(0) + "-Paket" : "the " + labels.get(0) + " package";
        }
        List<String> parts = new ArrayList<>();
        for (String label : labels) {
            parts.add(german ? label + "-" : label);
        }
        String last = parts.remove(parts.size() - 1);
        String joined = String.join(", ", parts) + (german ? " und " : " and ") + last;
        return german ? phrase.pluralArticle + " " + joined + phrase.pluralNoun : "the " + joined + " packages";
    }

    private static String label(Tier tier) {
        String name = tier.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static void appendSentence(StringBuilder summary, String sentence) {
        if (!summary.isEmpty()) {
            summary.append(' ');
        }
        summary.append(sentence);
    }

    private static String nameOf(RejectedEdit edit, boolean german) {
        if (edit.activityName() == null || edit.activityName().isBlank()) {
            return german ? DE_UNNAMED : EN_UNNAMED;
        }
        return edit.activityName();
    }

    private static boolean german(String locale) {
        return GERMAN.equalsIgnoreCase(locale);
    }
}
