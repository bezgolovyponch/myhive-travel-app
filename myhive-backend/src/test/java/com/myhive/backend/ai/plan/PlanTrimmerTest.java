package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTrimmerTest {

    private static final int TRAVELERS = 8;

    private final PlanValidator validator = new PlanValidator();
    private final PlanTrimmer trimmer = new PlanTrimmer(validator, new PlanAssembler());
    private final Map<UUID, CatalogActivity> catalog = new LinkedHashMap<>();

    private final CatalogActivity tasting = activity("Beer Tasting", 120, "35.00", "food");
    private final CatalogActivity bowling = activity("Bowling and Beers", 120, "30.00", "extreme");
    private final CatalogActivity pubCrawl = activity("Pub Crawl", 180, "25.00", "nightlife");
    private final CatalogActivity breakfast = activity("Big Breakfast", 60, "15.00", "food");
    private final CatalogActivity olympics = activity("Beer Olympics", 180, "45.00", "extreme");
    private final CatalogActivity limo = activity("VIP Limo", 60, "50.00", "nightlife");
    private final CatalogActivity tank = activity("Army Tank", 180, "120.00", "extreme");
    private final CatalogActivity cabaret = activity("Cabaret Night", 150, "60.00", "nightlife");

    private final CatalogActivity absinthBar = activity("Absinth Bar", 90, "50.00", "nightlife");
    private final CatalogActivity nightclub = activity("Nightclub VIP Experience", 300, "40.00", "nightlife");
    private final CatalogActivity walk = activity("City Walk", 60, "5.00", "other");

    /** Friday evening to Sunday afternoon: day 1 EVENING-NIGHT, day 2 all four slots, day 3 MORNING-AFTERNOON. */
    private final Brief brief = briefWanting();

    /** The day the model got wrong in every live run: four activities, 780 minutes, a cap of 540. */
    @Test
    void aDayOverItsMinutes_losesActivitiesUntilItFits_andKeepsWhatTheBriefAskedFor() {
        Brief nightOut = briefWanting("nightlife");
        PlanDraft draft = plan(basic(), medium(), pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                day(2, item(Slot.MORNING, tank), item(Slot.AFTERNOON, olympics), item(Slot.EVENING, cabaret),
                        item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast))));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, nightOut, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.PREMIUM, 2)).containsExactly(cabaret.name(), pubCrawl.name());
        assertThat(trimmed.fixes()).hasSize(2);
        assertThat(trimmed.fixes().get(0)).startsWith("dropped " + olympics.name() + " from PREMIUM day 2")
                .contains("780 min in 4 activities").contains("PREMIUM allows 540 min in 4");
        assertThat(trimmed.fixes().get(1)).startsWith("dropped " + tank.name() + " from PREMIUM day 2");
        assertThat(validator.validate(trimmed.draft(), nightOut, catalog)).isEmpty();
        assertThat(namesOn(trimmed.draft(), Tier.MEDIUM, 2)).isEqualTo(namesOn(draft, Tier.MEDIUM, 2));
    }

    /** Nothing in the brief to go by: the daytime activity another tier offers anyway is the one to lose. */
    @Test
    void withNothingToGoByInTheBrief_aDaytimeActivityAnotherTierOffersGoes_andTheNightOutStays() {
        PlanDraft draft = plan(basic(), pkg(Tier.MEDIUM, day(1, item(Slot.EVENING, tasting)),
                day(2, item(Slot.MORNING, bowling), item(Slot.AFTERNOON, olympics), item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast))), premium());

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.MEDIUM, 2)).containsExactly(olympics.name(), pubCrawl.name());
        assertThat(trimmed.fixes()).singleElement().asString().startsWith("dropped " + bowling.name());
    }

    /** The activity the brief cares least about is the only one that tells MEDIUM apart: it has to stay. */
    @Test
    void neverTakesTheActivityThatMakesATierDistinct() {
        CatalogActivity onlyInMedium = activity("Escape Bunker", 300, "80.00", "other");
        CatalogActivity alsoInBasic = activity("Quad Bikes", 200, "20.00", "extreme");
        Brief wantsExtreme = briefWanting("extreme");
        PlanDraft draft = plan(
                pkg(Tier.BASIC, day(1, item(Slot.EVENING, tasting)),
                        day(2, item(Slot.MORNING, bowling), item(Slot.AFTERNOON, alsoInBasic)),
                        day(3, item(Slot.MORNING, breakfast))),
                pkg(Tier.MEDIUM, day(1, item(Slot.EVENING, tasting)),
                        day(2, item(Slot.MORNING, onlyInMedium), item(Slot.AFTERNOON, alsoInBasic)),
                        day(3, item(Slot.MORNING, breakfast))),
                premium());

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, wantsExtreme, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.MEDIUM, 2)).containsExactly(onlyInMedium.name());
    }

    /** Nothing in the brief to go by, and dropping the club alone would do: two daytime activities go instead. */
    @Test
    void withNothingToGoByInTheBrief_theNightOutStays_evenWhenDroppingItAloneWouldDo() {
        CatalogActivity club = activity("Nightclub VIP Experience", 300, "70.00", "party");
        PlanDraft draft = plan(basic(), medium(), pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                day(2, item(Slot.MORNING, tank), item(Slot.AFTERNOON, bowling), item(Slot.EVENING, cabaret),
                        item(Slot.NIGHT, club)),
                day(3, item(Slot.MORNING, breakfast))));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.PREMIUM, 2)).containsExactly(cabaret.name(), club.name());
        assertThat(trimmed.fixes()).hasSize(2).noneMatch(fix -> fix.contains(club.name()));
        assertThat(validator.validate(trimmed.draft(), brief, catalog)).isEmpty();
    }

    /** The bar or the club, and not a word from the organizer: the one MEDIUM offers as well goes. */
    @Test
    void betweenTwoOfTheNight_withNoWordFromTheOrganizer_theOneAnotherTierOffersGoes() {
        assertThat(leftOfTheNight(organizerSaying(null, null))).containsExactly(tank.name(), absinthBar.name());
    }

    /** What the first live runs got wrong: the group asked for a club and PREMIUM came back without one. */
    @Test
    void whatTheOrganizerAskedForInTheirOwnWords_stays() {
        Brief clubNight = organizerSaying("Karting by day, club by night", null);

        assertThat(leftOfTheNight(clubNight)).containsExactly(tank.name(), nightclub.name());
    }

    /** The chat filed the request under the notes and left "wild" as the vibe: the notes count just the same. */
    @Test
    void whatTheOrganizerAskedFor_isReadFromTheNotesAsWell() {
        Brief wild = organizerSaying("wild", "strip club and a boat party");

        assertThat(leftOfTheNight(wild)).containsExactly(tank.name(), nightclub.name());
    }

    /** As the chat wrote it down in a live run: "clubbing" has to find the Nightclub. */
    @Test
    void theWordsOfTheRequest_areMatchedWithoutTheirEndings() {
        Brief clubbing = organizerSaying("high-energy", "Wants karting (not available) and clubbing");

        assertThat(leftOfTheNight(clubbing)).containsExactly(tank.name(), nightclub.name());
    }

    /** Both are filed under nightlife. Asked for a night out, the group means the one with the night in its name. */
    @Test
    void aWordOfTheRequestInTheName_outweighsOneInTheCategory() {
        Brief nightOut = organizerSaying("big night out", null);

        assertThat(leftOfTheNight(nightOut)).containsExactly(tank.name(), nightclub.name());
    }

    /**
     * MEDIUM is a day too full and dear for it. Held against that MEDIUM, PREMIUM could not give up its
     * tank and would lose the pub crawl; held against the MEDIUM that is left once it is trimmed, it can.
     */
    @Test
    void theTiersAreCorrectedFromTheBottomUp_soEachIsPricedAgainstWhatIsLeftOfTheOneBelow() {
        CatalogActivity segway = activity("Segway Tour", 120, "30.00", "extreme");
        PlanDraft draft = plan(basic(),
                pkg(Tier.MEDIUM, day(1, item(Slot.EVENING, tasting)),
                        day(2, item(Slot.MORNING, tank), item(Slot.AFTERNOON, olympics), item(Slot.NIGHT, pubCrawl)),
                        day(3, item(Slot.MORNING, breakfast))),
                pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                        day(2, item(Slot.MORNING, tank), item(Slot.AFTERNOON, segway), item(Slot.EVENING, cabaret),
                                item(Slot.NIGHT, pubCrawl)),
                        day(3, item(Slot.MORNING, breakfast))));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.MEDIUM, 2)).containsExactly(olympics.name(), pubCrawl.name());
        assertThat(namesOn(trimmed.draft(), Tier.PREMIUM, 2))
                .containsExactly(segway.name(), cabaret.name(), pubCrawl.name());
        assertThat(trimmed.fixes()).hasSize(2);
        assertThat(trimmed.fixes().get(0)).contains("MEDIUM day 2");
        assertThat(trimmed.fixes().get(1)).contains("PREMIUM day 2");
        assertThat(validator.validate(trimmed.draft(), brief, catalog)).isEmpty();
        assertThat(new PlanAssembler().assemble(trimmed.draft(), brief, catalog, false).violations()).isEmpty();
    }

    /** Losing the tank would do, and it is what the brief cares least about - but PREMIUM would cost less than MEDIUM. */
    @Test
    void neverTakesAnActivityWhoseLossPutsTheTiersOutOfPriceOrder() {
        Brief wantsNightlife = briefWanting("nightlife");
        PlanDraft draft = plan(basic(), medium(), pkg(Tier.PREMIUM,
                day(1),
                day(2, item(Slot.MORNING, tank), item(Slot.AFTERNOON, olympics), item(Slot.EVENING, cabaret),
                        item(Slot.NIGHT, pubCrawl)),
                day(3)));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, wantsNightlife, catalog).orElseThrow();

        // Still in the package, though not necessarily on day 2: once the day has lost what it had to lose,
        // the tank may move to a day that was left empty.
        PlanDraft.PackageDraft premiumAfter = trimmed.draft().packages().stream()
                .filter(p -> p.key() == Tier.PREMIUM).findFirst().orElseThrow();
        assertThat(PlanValidator.activityIds(premiumAfter)).contains(tank.id());
        assertThat(new PlanAssembler().assemble(trimmed.draft(), wantsNightlife, catalog, false).violations()).isEmpty();
        assertThat(validator.validate(trimmed.draft(), wantsNightlife, catalog)).isEmpty();
    }

    @Test
    void aDayOverItsItemCount_losesOne() {
        PlanDraft draft = plan(pkg(Tier.BASIC, day(1, item(Slot.EVENING, tasting)),
                day(2, item(Slot.MORNING, bowling), item(Slot.AFTERNOON, limo), item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast))), medium(), premium());

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.BASIC, 2)).containsExactly(bowling.name(), pubCrawl.name());
        assertThat(validator.validate(trimmed.draft(), brief, catalog)).isEmpty();
    }

    @Test
    void anActivityListedTwice_losesItsSecondOccurrence() {
        PlanDraft draft = plan(basic(), pkg(Tier.MEDIUM, day(1, item(Slot.EVENING, tasting)),
                day(2, item(Slot.AFTERNOON, olympics), item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast), item(Slot.AFTERNOON, olympics))), premium());

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.MEDIUM, 2)).contains(olympics.name());
        assertThat(namesOn(trimmed.draft(), Tier.MEDIUM, 3)).containsExactly(breakfast.name());
        assertThat(trimmed.fixes()).containsExactly("dropped the second " + olympics.name() + " from MEDIUM day 3");
    }

    @Test
    void twoActivitiesInOneSlot_theSecondMovesToTheNearestFreeOne_withoutItsStartHint() {
        Slot expectedSlot = Slot.AFTERNOON;
        PlanDraft draft = plan(basic(), medium(), pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                day(2, item(Slot.MORNING, tank), new PlanDraft.ItemDraft(Slot.MORNING, "09:30", cabaret.id(), null)),
                day(3, item(Slot.MORNING, breakfast))));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        PlanDraft.ItemDraft moved = itemsOn(trimmed.draft(), Tier.PREMIUM, 2).get(1);
        assertThat(moved.activityId()).isEqualTo(cabaret.id());
        assertThat(moved.slot()).isEqualTo(expectedSlot);
        assertThat(moved.startHint()).isNull();
        assertThat(trimmed.fixes()).containsExactly("moved " + cabaret.name() + " to " + expectedSlot
                + " on PREMIUM day 2");
    }

    /** Day 1 opens in the evening: a morning activity there moves into the window, it is not lost. */
    @Test
    void anActivityOutsideTheDaysWindow_movesIntoIt() {
        PlanDraft draft = plan(pkg(Tier.BASIC, day(1, item(Slot.MORNING, tasting)),
                day(2, item(Slot.MORNING, bowling), item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast))), medium(), premium());

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(itemsOn(trimmed.draft(), Tier.BASIC, 1)).singleElement()
                .satisfies(item -> assertThat(item.slot()).isEqualTo(Slot.EVENING));
        assertThat(validator.validate(trimmed.draft(), brief, catalog)).isEmpty();
    }

    /** The last day closes in the afternoon and both its slots are taken: the night activity has nowhere to go. */
    @Test
    void withNoFreeSlotInTheWindow_theActivityIsDropped() {
        PlanDraft draft = plan(basic(), medium(), pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                day(2, item(Slot.MORNING, tank), item(Slot.EVENING, cabaret)),
                day(3, item(Slot.MORNING, breakfast), item(Slot.AFTERNOON, bowling), item(Slot.NIGHT, tasting))));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.PREMIUM, 3)).containsExactly(breakfast.name(), bowling.name());
        assertThat(trimmed.fixes()).containsExactly("dropped " + tasting.name() + " from PREMIUM day 3 (no free slot)");
    }

    @Test
    void anActivityTheCatalogDoesNotList_isDropped() {
        PlanDraft draft = plan(basic(), medium(), pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                day(2, item(Slot.MORNING, tank), item(Slot.EVENING, cabaret),
                        new PlanDraft.ItemDraft(Slot.NIGHT, null, UUID.randomUUID(), null)),
                day(3, item(Slot.MORNING, breakfast))));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, brief, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.PREMIUM, 2)).containsExactly(tank.name(), cabaret.name());
        assertThat(validator.validate(trimmed.draft(), brief, catalog)).isEmpty();
    }

    /** A missing package or a wrong day count is the model's to repair; there is nothing here to trim. */
    @Test
    void whatIsWrongWithTheShapeOfAPlan_isNotForTheTrimmer() {
        PlanDraft missingPremium = plan(basic(), medium());

        Optional<PlanTrimmer.Trimmed> trimmed = trimmer.trim(missingPremium, brief, catalog);

        assertThat(trimmed).isEmpty();
    }

    /** The day could be trimmed, but the draft goes to the repair anyway: BASIC has a middle day with nothing in it. */
    @Test
    void aDraftTheModelHasToRepairAnyway_isNotTrimmedFirst() {
        PlanDraft draft = plan(pkg(Tier.BASIC, day(1, item(Slot.EVENING, tasting)), day(2),
                day(3, item(Slot.MORNING, breakfast))), medium(), pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                day(2, item(Slot.MORNING, tank), item(Slot.AFTERNOON, olympics), item(Slot.EVENING, cabaret),
                        item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast))));

        assertThat(validator.validate(draft, brief, catalog)).extracting(Violation::code)
                .contains(ViolationCode.EMPTY_DAY, ViolationCode.DAY_OVER_MINUTES);
        assertThat(trimmer.trim(draft, brief, catalog)).isEmpty();
    }

    @Test
    void aCleanDraft_isLeftAlone() {
        PlanDraft clean = plan(basic(), medium(), premium());

        assertThat(validator.validate(clean, brief, catalog)).isEmpty();
        assertThat(trimmer.trim(clean, brief, catalog)).isEmpty();
    }

    private static Brief organizerSaying(String vibe, String notes) {
        return new Brief(3, TRAVELERS, List.of(), vibe, null, null, DayEdge.EVENING, DayEdge.AFTERNOON, notes);
    }

    /**
     * PREMIUM's day 2 runs 630 minutes against 540 and one of three has to go. Not the tank: without it
     * PREMIUM would cost less than MEDIUM. So it is the bar or the club - both of the night, either one
     * enough - and what is left of the day says which one the brief spared. Days 1 and 3 each hold
     * something cheap: with a day left empty the trimmer would move an activity there and drop nothing.
     */
    private List<String> leftOfTheNight(Brief organizer) {
        PlanDraft draft = plan(basic(),
                pkg(Tier.MEDIUM, day(1, item(Slot.EVENING, tasting)), day(2, item(Slot.NIGHT, nightclub)),
                        day(3, item(Slot.MORNING, breakfast), item(Slot.AFTERNOON, olympics))),
                pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, walk)),
                        day(2, item(Slot.MORNING, tank), item(Slot.EVENING, absinthBar),
                                item(Slot.NIGHT, nightclub)),
                        day(3, item(Slot.MORNING, breakfast))));
        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, organizer, catalog).orElseThrow();
        assertThat(validator.validate(trimmed.draft(), organizer, catalog)).isEmpty();
        assertThat(trimmed.fixes()).hasSize(1);
        return namesOn(trimmed.draft(), Tier.PREMIUM, 2);
    }

    private PlanDraft.PackageDraft basic() {
        return pkg(Tier.BASIC, day(1, item(Slot.EVENING, tasting)),
                day(2, item(Slot.MORNING, bowling), item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast)));
    }

    private PlanDraft.PackageDraft medium() {
        return pkg(Tier.MEDIUM, day(1, item(Slot.EVENING, tasting)),
                day(2, item(Slot.AFTERNOON, olympics), item(Slot.NIGHT, pubCrawl)),
                day(3, item(Slot.MORNING, breakfast)));
    }

    private PlanDraft.PackageDraft premium() {
        return pkg(Tier.PREMIUM, day(1, item(Slot.EVENING, limo)),
                day(2, item(Slot.MORNING, tank), item(Slot.EVENING, cabaret)),
                day(3, item(Slot.MORNING, breakfast)));
    }

    private static Brief briefWanting(String... categorySlugs) {
        return new Brief(3, TRAVELERS, List.of(categorySlugs), null, null, null, DayEdge.EVENING, DayEdge.AFTERNOON,
                null);
    }

    private CatalogActivity activity(String name, int minutes, String price, String categorySlug) {
        CatalogActivity activity = new CatalogActivity(UUID.randomUUID(), name.toLowerCase().replace(' ', '-'), name,
                "line", minutes, true, new BigDecimal(price), null, null, List.of(categorySlug));
        catalog.put(activity.id(), activity);
        return activity;
    }

    private static PlanDraft.ItemDraft item(Slot slot, CatalogActivity activity) {
        return new PlanDraft.ItemDraft(slot, null, activity.id(), null);
    }

    private static PlanDraft.DayDraft day(int dayNumber, PlanDraft.ItemDraft... items) {
        return new PlanDraft.DayDraft(dayNumber, null, null, List.of(items));
    }

    private static PlanDraft.PackageDraft pkg(Tier tier, PlanDraft.DayDraft... days) {
        return new PlanDraft.PackageDraft(tier, null, null, null, List.of(days));
    }

    private static PlanDraft plan(PlanDraft.PackageDraft... packages) {
        return new PlanDraft(List.of(packages));
    }

    private static List<PlanDraft.ItemDraft> itemsOn(PlanDraft draft, Tier tier, int dayNumber) {
        return PlanDrafts.packageOf(draft, tier).orElseThrow().days().stream()
                .filter(day -> day.dayNumber() == dayNumber)
                .findFirst().orElseThrow().items();
    }

    private List<String> namesOn(PlanDraft draft, Tier tier, int dayNumber) {
        List<String> names = new ArrayList<>();
        for (PlanDraft.ItemDraft item : itemsOn(draft, tier, dayNumber)) {
            names.add(catalog.get(item.activityId()).name());
        }
        return names;
    }

    /** The model put a whole package on day 1 and left the last morning empty: the daytime one moves there, nothing is lost. */
    @Test
    void aDayOverItsItemCount_withAnEmptyDayToGoTo_movesAnActivityInsteadOfDroppingIt() {
        Brief weekend = new Brief(2, TRAVELERS, List.of(), null, null, null, DayEdge.AFTERNOON, DayEdge.MORNING, null);
        PlanDraft draft = plan(pkg(Tier.BASIC,
                        day(1, item(Slot.AFTERNOON, bowling), item(Slot.EVENING, tasting), item(Slot.NIGHT, pubCrawl)),
                        day(2)),
                pkg(Tier.MEDIUM, day(1, item(Slot.AFTERNOON, olympics), item(Slot.NIGHT, pubCrawl)),
                        day(2, item(Slot.MORNING, limo))),
                pkg(Tier.PREMIUM, day(1, item(Slot.AFTERNOON, tank), item(Slot.NIGHT, cabaret)),
                        day(2, item(Slot.MORNING, breakfast))));

        PlanTrimmer.Trimmed trimmed = trimmer.trim(draft, weekend, catalog).orElseThrow();

        assertThat(namesOn(trimmed.draft(), Tier.BASIC, 1)).containsExactly(tasting.name(), pubCrawl.name());
        assertThat(namesOn(trimmed.draft(), Tier.BASIC, 2)).containsExactly(bowling.name());
        assertThat(trimmed.fixes()).singleElement().asString()
                .startsWith("moved " + bowling.name() + " from BASIC day 1 to day 2 MORNING");
        assertThat(validator.validate(trimmed.draft(), weekend, catalog)).isEmpty();
    }
}
