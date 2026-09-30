package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Corrects the slips in a draft that are arithmetic, not judgement: a day that runs over its tier's cap,
 * an activity listed twice, two activities in one slot, a slot outside the arrival/departure window, an
 * id the catalog does not know. The model made one of these in every live generation - usually a day
 * well over its minutes - and a second call, told exactly which day and by how much, returned a day just
 * as long; the whole plan was then thrown away for the deterministic fallback, which knows the catalog
 * but not the conversation. Dropping an activity keeps everything else the model chose.
 *
 * <p>No rule is restated here. {@link PlanValidator} says what is wrong: every step asks it, corrects one
 * thing on its list that can be corrected, and asks again. {@link PlanAssembler} says whether the tiers
 * still rise in price. What comes out is a candidate, not a verdict - the caller validates and prices it
 * like any other draft.
 *
 * <p>The tiers are corrected from the bottom up. A package may only lose an activity while it stays
 * dearer than the tier below, and that comparison means something only once the tier below has lost
 * what it is going to lose: held against a MEDIUM still a day too full, PREMIUM could not give up
 * its castle tour and gave up the club night instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanTrimmer {

    private static final Set<ViolationCode> FIXABLE = EnumSet.of(ViolationCode.UNKNOWN_ACTIVITY,
            ViolationCode.DUPLICATE_ACTIVITY, ViolationCode.SLOT_OUTSIDE_WINDOW, ViolationCode.SLOT_TAKEN,
            ViolationCode.DAY_OVER_ITEMS, ViolationCode.DAY_OVER_MINUTES);

    /**
     * What neither a drop nor a move can cure: a draft with any of these is the model's to repair, and
     * trimming it first would be work thrown away. The two tier rules are left out on purpose - a day
     * trimmed in one package has more than once been what made another one distinct.
     */
    private static final Set<ViolationCode> BEYOND_TRIMMING = EnumSet.of(ViolationCode.MISSING_TIER,
            ViolationCode.WRONG_DAY_COUNT, ViolationCode.EMPTY_PACKAGE, ViolationCode.EMPTY_DAY,
            ViolationCode.TEXT_TOO_LONG, ViolationCode.INVALID_OUTPUT);

    /** BASIC before MEDIUM before PREMIUM, a tier's days in order; within a day, the validator's own order. */
    private static final Comparator<Violation> BOTTOM_UP = Comparator
            .comparing(Violation::packageKey, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Violation::dayNumber, Comparator.nullsLast(Comparator.naturalOrder()));

    /** Every fix removes or re-slots one item, so this is far more than a real draft can need. */
    private static final int MAX_FIXES = 24;

    private final PlanValidator validator;
    private final PlanAssembler assembler;

    /** The corrected draft and, for staff, what was done to it - one line per fix, in order. */
    public record Trimmed(PlanDraft draft, List<String> fixes) {

        public Trimmed {
            fixes = List.copyOf(fixes);
        }
    }

    private record Fix(PlanDraft draft, String note) {
    }

    /** Empty when there was nothing this class could correct, or when a correction could not be made. */
    public Optional<Trimmed> trim(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> catalog) {
        boolean beyondTrimming = validator.validate(draft, brief, catalog).stream()
                .anyMatch(violation -> BEYOND_TRIMMING.contains(violation.code()));
        if (beyondTrimming) {
            return Optional.empty();
        }
        PlanDraft working = draft;
        List<String> fixes = new ArrayList<>();
        for (int step = 0; step < MAX_FIXES; step++) {
            Optional<Violation> next = validator.validate(working, brief, catalog).stream()
                    .filter(violation -> FIXABLE.contains(violation.code()))
                    .min(BOTTOM_UP);
            if (next.isEmpty()) {
                break;
            }
            Optional<Fix> fix = fix(working, next.get(), brief, catalog);
            if (fix.isEmpty()) {
                // Usually a day from which nothing can go without the tiers losing their order or their
                // difference. Said here because the caller only sees a draft that was not corrected.
                log.info("planner draft not trimmed: nothing to take for {}/{}/d{} after {} fixes",
                        next.get().code(), next.get().packageKey(), next.get().dayNumber(), fixes.size());
                return Optional.empty();
            }
            working = fix.get().draft();
            fixes.add(fix.get().note());
        }
        return fixes.isEmpty() ? Optional.empty() : Optional.of(new Trimmed(working, fixes));
    }

    private Optional<Fix> fix(PlanDraft draft, Violation violation, Brief brief, Map<UUID, CatalogActivity> catalog) {
        if (violation.packageKey() == null || violation.dayNumber() == null) {
            return Optional.empty();
        }
        Optional<PlanDraft.PackageDraft> pkg = PlanDrafts.packageOf(draft, violation.packageKey());
        Optional<PlanDraft.DayDraft> day = pkg.flatMap(p -> p.days().stream()
                .filter(d -> d.dayNumber() == violation.dayNumber())
                .findFirst());
        if (day.isEmpty()) {
            return Optional.empty();
        }
        Spot spot = new Spot(draft, pkg.get(), day.get());
        return switch (violation.code()) {
            case UNKNOWN_ACTIVITY -> dropUnknown(spot, catalog);
            case DUPLICATE_ACTIVITY -> dropRepeated(spot, catalog);
            case SLOT_OUTSIDE_WINDOW, SLOT_TAKEN -> reslot(spot, brief, catalog);
            case DAY_OVER_ITEMS, DAY_OVER_MINUTES -> dropFromFullDay(spot, brief, catalog);
            default -> Optional.empty();
        };
    }

    /** Where a violation sits: the day, inside its package, inside the draft. */
    private record Spot(PlanDraft draft, PlanDraft.PackageDraft pkg, PlanDraft.DayDraft day) {

        String label() {
            return pkg.key() + " day " + day.dayNumber();
        }

        /** The draft with this day's items replaced, in slot order so the day still reads top to bottom. */
        PlanDraft withItems(List<PlanDraft.ItemDraft> items) {
            List<PlanDraft.ItemDraft> ordered = new ArrayList<>(items);
            ordered.sort(Comparator.comparingInt(PlanTrimmer::slotOrder));
            List<PlanDraft.DayDraft> days = new ArrayList<>();
            for (PlanDraft.DayDraft other : pkg.days()) {
                days.add(other.dayNumber() == day.dayNumber()
                        ? new PlanDraft.DayDraft(day.dayNumber(), day.title(), day.summary(), ordered)
                        : other);
            }
            return PlanDrafts.replacePackage(draft,
                    new PlanDraft.PackageDraft(pkg.key(), pkg.title(), pkg.tagline(), pkg.description(), days));
        }

        PlanDraft without(PlanDraft.ItemDraft item) {
            List<PlanDraft.ItemDraft> items = new ArrayList<>(day.items());
            items.remove(item);
            return withItems(items);
        }

        /** Whether the day is inside its tier's caps once this item is gone. */
        boolean fitsWithout(PlanDraft.ItemDraft item, Map<UUID, CatalogActivity> catalog) {
            List<PlanDraft.ItemDraft> rest = new ArrayList<>(day.items());
            rest.remove(item);
            PlanDraft.DayDraft lighter = new PlanDraft.DayDraft(day.dayNumber(), null, null, rest);
            return rest.size() <= pkg.key().maxItemsPerDay()
                    && PlanValidator.dayMinutes(lighter, catalog) <= pkg.key().maxMinutesPerDay();
        }

        /** {@code 780 min in 4 activities; PREMIUM allows 540 min in 4} - the day as it stands. */
        String overrun(Map<UUID, CatalogActivity> catalog) {
            Tier tier = pkg.key();
            return PlanValidator.dayMinutes(day, catalog) + " min in " + day.items().size() + " activities; "
                    + tier + " allows " + tier.maxMinutesPerDay() + " min in " + tier.maxItemsPerDay();
        }
    }

    /**
     * What the organizer asked for, as far as it can be held against a catalog activity: the categories
     * they picked, and the words of their own description. In production the description is usually all
     * there is - a destination with no categories assigned leaves the chat nothing to pick from. The
     * chat files that description under the vibe or under the notes as it sees fit ("wild" in one,
     * "strip club and a boat party" in the other), so both are read.
     */
    private record Wishes(Set<String> categorySlugs, Set<String> words) {

        private static final Pattern NOT_A_LETTER = Pattern.compile("[^\\p{L}]+");
        /** Shorter words are "a", "by", "and", "out": they match everything and mean nothing. */
        private static final int MIN_WORD = 4;
        private static final int IN_THE_NAME = 2;
        private static final int IN_A_CATEGORY = 1;

        static Wishes of(Brief brief) {
            Set<String> words = Stream.of(brief.vibe(), brief.notes())
                    .filter(Objects::nonNull)
                    .flatMap(text -> NOT_A_LETTER.splitAsStream(text.toLowerCase(Locale.ROOT)))
                    .filter(word -> word.length() >= MIN_WORD)
                    .map(Wishes::stem)
                    .collect(Collectors.toSet());
            return new Wishes(new HashSet<>(brief.categorySlugs()), words);
        }

        /**
         * "clubbing" has to find the Nightclub, "karting" a Go-Kart and "tanks" the Tank: the ending goes,
         * and with it the letter that was doubled before it. English only, and only these two endings -
         * a word left as it is simply finds less.
         */
        private static String stem(String word) {
            if (word.endsWith("ing") && word.length() >= MIN_WORD + 3) {
                String stem = word.substring(0, word.length() - 3);
                int last = stem.length() - 1;
                return stem.charAt(last) == stem.charAt(last - 1) ? stem.substring(0, last) : stem;
            }
            if (word.endsWith("s") && !word.endsWith("ss")) {
                return word.substring(0, word.length() - 1);
            }
            return word;
        }

        /**
         * A word counts when the activity's name or a category of it contains it - contains, not equals:
         * "club" has to find "Nightclub", "bier" a "Bierverkostung". A chance match only spares an
         * activity it need not have spared. In the name it counts double: asked for a big night out,
         * the group means the Nightclub before an absinth bar that is merely filed under nightlife.
         */
        int answeredBy(CatalogActivity activity) {
            String name = activity.name().toLowerCase(Locale.ROOT);
            String filedUnder = String.join(" ", activity.categorySlugs()).toLowerCase(Locale.ROOT);
            int score = (int) activity.categorySlugs().stream().filter(categorySlugs::contains).count();
            for (String word : words) {
                if (name.contains(word)) {
                    score += IN_THE_NAME;
                } else if (filedUnder.contains(word)) {
                    score += IN_A_CATEGORY;
                }
            }
            return score;
        }
    }

    private static Optional<Fix> dropUnknown(Spot spot, Map<UUID, CatalogActivity> catalog) {
        return spot.day().items().stream()
                .filter(item -> activityOf(item, catalog) == null)
                .findFirst()
                .map(item -> new Fix(spot.without(item), "dropped an activity the catalog does not list from "
                        + spot.label()));
    }

    /** The validator reads a package in the order its days are listed, so the repeat it reports is the later one. */
    private static Optional<Fix> dropRepeated(Spot spot, Map<UUID, CatalogActivity> catalog) {
        Set<UUID> seen = new HashSet<>();
        for (PlanDraft.DayDraft earlier : spot.pkg().days()) {
            if (earlier.dayNumber() == spot.day().dayNumber()) {
                break;
            }
            earlier.items().forEach(item -> seen.add(item.activityId()));
        }
        for (PlanDraft.ItemDraft item : spot.day().items()) {
            if (item.activityId() != null && !seen.add(item.activityId())) {
                return Optional.of(new Fix(spot.without(item),
                        "dropped the second " + nameOf(item, catalog) + " from " + spot.label()));
            }
        }
        return Optional.empty();
    }

    /**
     * An item in a slot it cannot have - outside the day's window, or already taken - moves to the free
     * slot nearest the one it asked for; with none free it is dropped. Its start hint goes with the old
     * slot: "20:00" would be wrong for a morning.
     */
    private static Optional<Fix> reslot(Spot spot, Brief brief, Map<UUID, CatalogActivity> catalog) {
        Set<Slot> allowed = PlanValidator.allowedSlots(spot.day().dayNumber(), brief);
        Set<Slot> taken = EnumSet.noneOf(Slot.class);
        PlanDraft.ItemDraft misplaced = null;
        for (PlanDraft.ItemDraft item : spot.day().items()) {
            boolean fits = item.slot() != null && allowed.contains(item.slot()) && taken.add(item.slot());
            if (!fits && misplaced == null) {
                misplaced = item;
            }
        }
        if (misplaced == null) {
            return Optional.empty();
        }
        PlanDraft.ItemDraft item = misplaced;
        Optional<Slot> free = allowed.stream()
                .filter(slot -> !taken.contains(slot))
                .min(Comparator.comparingInt(slot -> distance(slot, item.slot())));
        if (free.isEmpty()) {
            return Optional.of(new Fix(spot.without(item), "dropped " + nameOf(item, catalog) + " from "
                    + spot.label() + " (no free slot)"));
        }
        List<PlanDraft.ItemDraft> items = new ArrayList<>(spot.day().items());
        items.set(items.indexOf(item), new PlanDraft.ItemDraft(free.get(), null, item.activityId(), item.why()));
        return Optional.of(new Fix(spot.withItems(items), "moved " + nameOf(item, catalog) + " to "
                + free.get() + " on " + spot.label()));
    }

    private static int distance(Slot candidate, Slot wanted) {
        return wanted == null ? candidate.ordinal() : Math.abs(candidate.ordinal() - wanted.ordinal());
    }

    /**
     * One activity less on a day that is over its cap: the one the group will miss least whose removal
     * breaks nothing else. Least missed, in this order: it answers what the organizer asked for least;
     * it is a daytime activity, so the night out stays; its removal alone brings the day inside the
     * cap; another tier offers it too; its line is the cheapest. When one removal is not enough, the
     * next round takes another.
     *
     * <p>"One removal is enough" comes third on purpose. The club night is the longest activity of any
     * day, so dropping it alone always is enough - ranked first, that rule took the club out of the
     * packages of the very groups that had asked for one. A day two activities long with the night out
     * beats one three long without it.
     */
    private Optional<Fix> dropFromFullDay(Spot spot, Brief brief, Map<UUID, CatalogActivity> catalog) {
        Wishes wishes = Wishes.of(brief);
        Set<UUID> offeredElsewhere = offeredByOtherTiers(spot);
        int travelers = brief.groupSize() == null ? 1 : brief.groupSize();
        Comparator<PlanDraft.ItemDraft> leastMissed = Comparator
                .comparingInt((PlanDraft.ItemDraft item) -> wishes.answeredBy(activityOf(item, catalog)))
                .thenComparing(item -> slotOrder(item) >= Slot.EVENING.ordinal())
                .thenComparing(item -> !spot.fitsWithout(item, catalog))
                .thenComparing(item -> !offeredElsewhere.contains(item.activityId()))
                .thenComparing(item -> lineTotal(activityOf(item, catalog), travelers))
                .thenComparing(item -> activityOf(item, catalog).name());
        Set<String> alreadyWrong = unmendable(spot.draft(), brief, catalog);
        return spot.day().items().stream()
                .filter(item -> activityOf(item, catalog) != null)
                .sorted(leastMissed)
                .filter(item -> alreadyWrong.containsAll(unmendable(spot.without(item), brief, catalog)))
                .findFirst()
                .map(item -> new Fix(spot.without(item), "dropped " + nameOf(item, catalog) + " from "
                        + spot.label() + " (" + spot.overrun(catalog) + ")"));
    }

    /**
     * What is wrong with a draft that no trim can mend: an emptied middle day or package, a tier with
     * nothing the others lack, tiers that do not rise in price. A removal that adds to this list would
     * trade a day that is too long for a plan that is rejected outright, so it is not made.
     */
    private Set<String> unmendable(PlanDraft draft, Brief brief, Map<UUID, CatalogActivity> catalog) {
        Stream<Violation> scheduling = validator.validate(draft, brief, catalog).stream()
                .filter(violation -> !FIXABLE.contains(violation.code()));
        // The assembler prices per traveller and says so loudly when there is no group size to price with.
        Stream<Violation> pricing = brief.groupSize() == null ? Stream.empty()
                : assembler.assemble(draft, brief, catalog, false).violations().stream();
        return Stream.concat(scheduling, pricing)
                .map(violation -> violation.code() + "/" + violation.packageKey() + "/" + violation.dayNumber())
                .collect(Collectors.toSet());
    }

    private static Set<UUID> offeredByOtherTiers(Spot spot) {
        Set<UUID> ids = new HashSet<>();
        for (PlanDraft.PackageDraft other : spot.draft().packages()) {
            if (other.key() != spot.pkg().key()) {
                ids.addAll(PlanValidator.activityIds(other));
            }
        }
        return ids;
    }

    private static int slotOrder(PlanDraft.ItemDraft item) {
        return item.slot() == null ? Integer.MAX_VALUE : item.slot().ordinal();
    }

    private static BigDecimal lineTotal(CatalogActivity activity, int travelers) {
        return PlanPricer.lineTotal(activity.price(), activity.minPrice(), travelers);
    }

    private static CatalogActivity activityOf(PlanDraft.ItemDraft item, Map<UUID, CatalogActivity> catalog) {
        return item.activityId() == null ? null : catalog.get(item.activityId());
    }

    private static String nameOf(PlanDraft.ItemDraft item, Map<UUID, CatalogActivity> catalog) {
        CatalogActivity activity = activityOf(item, catalog);
        return activity == null ? "an unknown activity" : activity.name();
    }
}
