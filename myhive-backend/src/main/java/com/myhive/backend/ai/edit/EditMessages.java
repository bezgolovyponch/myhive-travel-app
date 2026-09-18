package com.myhive.backend.ai.edit;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The short assistant lines Java writes itself when an edit turn cannot do what was asked. They are
 * templates rather than another model call: the organizer is already waiting for the edited packages,
 * and a rejection has to be honest, not creative. English and German (informal "du"); any other locale
 * falls back to English, as the rest of the planner does.
 *
 * <p>The name in a rejection is the model's spelling when it could not be resolved — and for a failed
 * {@code REPLACE} it is the <em>replacement</em>'s spelling — so the unresolved reasons talk about the
 * catalog, never about "your package".
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

    /** Stands in for a name the model left out; only a parser bug gets this far. */
    private static final String EN_UNNAMED = "that activity";
    private static final String DE_UNNAMED = "diese Aktivität";

    private static final Map<EditRejectionReason, String> EN = new EnumMap<>(EditRejectionReason.class);
    private static final Map<EditRejectionReason, String> DE = new EnumMap<>(EditRejectionReason.class);

    static {
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

    /** One sentence per distinct reason, naming the activities it hit; empty when nothing was rejected. */
    public static String rejectionSummary(String locale, List<RejectedEdit> rejected) {
        if (rejected == null || rejected.isEmpty()) {
            return "";
        }
        boolean german = german(locale);
        Map<EditRejectionReason, Set<String>> namesByReason = new LinkedHashMap<>();
        for (RejectedEdit edit : rejected) {
            namesByReason.computeIfAbsent(edit.reason(), reason -> new LinkedHashSet<>())
                    .add(nameOf(edit, german));
        }
        Map<EditRejectionReason, String> templates = german ? DE : EN;
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<EditRejectionReason, Set<String>> entry : namesByReason.entrySet()) {
            String template = templates.get(entry.getKey());
            if (template == null) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(' ');
            }
            summary.append(template.formatted(String.join(", ", entry.getValue())));
        }
        return summary.toString();
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
