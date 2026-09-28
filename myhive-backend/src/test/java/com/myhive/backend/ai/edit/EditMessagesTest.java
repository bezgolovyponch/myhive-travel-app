package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EditMessagesTest {

    @Test
    void noPackagesYet_isLocalised() {
        assertThat(EditMessages.noPackagesYet("en")).contains("packages");
        assertThat(EditMessages.noPackagesYet("de")).contains("Pakete");
    }

    @Test
    void rebuildingFirst_isLocalised_andInformalInGerman() {
        assertThat(EditMessages.rebuildingFirst("en")).contains("packages");
        assertThat(EditMessages.rebuildingFirst("de")).contains("Pakete").contains("frag");
        assertThat(EditMessages.rebuildingFirst("fr")).isEqualTo(EditMessages.rebuildingFirst("en"));
    }

    @Test
    void rejectionSummary_noFreeSlot_namesTheActivityInBothLanguages() {
        String expectedActivityName = "Karting";
        List<RejectedEdit> rejected = List.of(rejected(expectedActivityName, EditRejectionReason.NO_FREE_SLOT));

        assertThat(EditMessages.rejectionSummary("en", rejected))
                .contains(expectedActivityName).contains("no free slot");
        assertThat(EditMessages.rejectionSummary("de", rejected))
                .contains(expectedActivityName).contains("kein freier Slot");
    }

    /** The name in an unresolved rejection may be the replacement's spelling, so it is never "in your package". */
    @Test
    void rejectionSummary_unknownActivity_talksAboutTheCatalogNotThePackages() {
        String expectedActivityName = "Sauna Tour";
        List<RejectedEdit> rejected = List.of(rejected(expectedActivityName, EditRejectionReason.UNKNOWN_ACTIVITY));

        assertThat(EditMessages.rejectionSummary("en", rejected))
                .contains(expectedActivityName).contains("catalog").doesNotContain("package");
        assertThat(EditMessages.rejectionSummary("de", rejected))
                .contains(expectedActivityName).contains("Katalog").doesNotContain("Paket");
    }

    /** "Not in the catalog" alone is a dead end; the offer that follows turns it into a plain "add X". */
    @Test
    void rejectionSummary_unknownActivityWithAlternatives_offersThemAfterTheNotFoundLine() {
        String expectedActivityName = "strip shows";
        String expectedAlternatives = "Nightclub VIP Experience, Rooftop Jazz Night";
        int expectedSentences = 2;
        List<RejectedEdit> rejected = List.of(new RejectedEdit(EditOp.ADD, expectedActivityName, null,
                EditRejectionReason.UNKNOWN_ACTIVITY, expectedActivityName,
                List.of("Nightclub VIP Experience", "Rooftop Jazz Night")));

        String english = EditMessages.rejectionSummary("en", rejected);
        String german = EditMessages.rejectionSummary("de", rejected);

        assertThat(english.split("(?<=[.?]) ")).hasSize(expectedSentences);
        assertThat(english).startsWith("I could not find \"" + expectedActivityName + "\"")
                .endsWith("Closest to \"" + expectedActivityName + "\": " + expectedAlternatives + " - want one of those?");
        assertThat(german).contains("Am nächsten an \"" + expectedActivityName + "\": " + expectedAlternatives);
    }

    @Test
    void rejectionSummary_unknownActivityWithoutAlternatives_makesNoOffer() {
        List<RejectedEdit> rejected = List.of(rejected("Sauna Tour", EditRejectionReason.UNKNOWN_ACTIVITY));

        assertThat(EditMessages.rejectionSummary("en", rejected)).doesNotContain("Closest");
        assertThat(EditMessages.rejectionSummary("de", rejected)).doesNotContain("nächsten");
    }

    /** The same unknown name twice (one op per package, say) gets one offer, not one per op. */
    @Test
    void rejectionSummary_offersEachUnknownNameOnce() {
        String expectedActivityName = "strip shows";
        RejectedEdit unknown = new RejectedEdit(EditOp.ADD, expectedActivityName, null,
                EditRejectionReason.UNKNOWN_ACTIVITY, expectedActivityName, List.of("Night Club"));

        String summary = EditMessages.rejectionSummary("en", List.of(unknown, unknown));

        assertThat(summary).containsOnlyOnce("Closest to");
    }

    @Test
    void rejectionSummary_unknownLocale_fallsBackToEnglish() {
        List<RejectedEdit> rejected = List.of(rejected("Beer Bike", EditRejectionReason.UNKNOWN_ACTIVITY));

        assertThat(EditMessages.rejectionSummary("fr", rejected))
                .isEqualTo(EditMessages.rejectionSummary("en", rejected));
    }

    @Test
    void rejectionSummary_writesOneSentencePerDistinctReason() {
        int expectedSentences = 2;
        String summary = EditMessages.rejectionSummary("en", List.of(
                rejected("Karting", EditRejectionReason.NO_FREE_SLOT),
                rejected("Beer Bike", EditRejectionReason.NO_FREE_SLOT),
                rejected("Sauna Tour", EditRejectionReason.UNKNOWN_ACTIVITY)));

        assertThat(summary.split("(?<=[.?]) ")).hasSize(expectedSentences);
        assertThat(summary).contains("Karting, Beer Bike").contains("Sauna Tour");
    }

    @Test
    void rejectionSummary_withoutRejections_isEmpty() {
        assertThat(EditMessages.rejectionSummary("en", List.of())).isEmpty();
    }

    @Test
    void appliedSummary_namesTheOpTheActivityAndEveryPackageItLandedIn() {
        String expectedActivity = "Karting";
        List<AppliedEdit> applied = List.of(applied(EditOp.ADD, expectedActivity, null, Tier.BASIC),
                applied(EditOp.ADD, expectedActivity, null, Tier.PREMIUM),
                applied(EditOp.REMOVE, "Beer Bike", null, Tier.MEDIUM));

        assertThat(EditMessages.appliedSummary("en", applied)).isEqualTo(
                "Added Karting to the Basic and Premium packages. Dropped Beer Bike from the Medium package.");
        assertThat(EditMessages.appliedSummary("de", applied)).isEqualTo(
                "Karting in die Basic- und Premium-Pakete aufgenommen. Beer Bike aus dem Medium-Paket rausgenommen.");
    }

    @Test
    void appliedSummary_replace_namesBothActivities() {
        List<AppliedEdit> applied = List.of(applied(EditOp.REPLACE, "Beer Bike", "Karting", Tier.MEDIUM));

        assertThat(EditMessages.appliedSummary("en", applied))
                .isEqualTo("Swapped Beer Bike for Karting in the Medium package.");
        assertThat(EditMessages.appliedSummary("de", applied))
                .isEqualTo("Beer Bike im Medium-Paket gegen Karting getauscht.");
    }

    @Test
    void appliedSummary_threePackages_listsThemInTierOrderWithAnAnd() {
        List<AppliedEdit> applied = List.of(applied(EditOp.ADD, "Karting", null, Tier.PREMIUM),
                applied(EditOp.ADD, "Karting", null, Tier.BASIC), applied(EditOp.ADD, "Karting", null, Tier.MEDIUM));

        assertThat(EditMessages.appliedSummary("en", applied))
                .isEqualTo("Added Karting to the Basic, Medium and Premium packages.");
        assertThat(EditMessages.appliedSummary("de", applied))
                .isEqualTo("Karting in die Basic-, Medium- und Premium-Pakete aufgenommen.");
    }

    @Test
    void appliedSummary_withoutAppliedEdits_isEmpty() {
        assertThat(EditMessages.appliedSummary("en", List.of())).isEmpty();
    }

    /** A rejection that reached a package says which one, so it can stand next to "added to the Premium package". */
    @Test
    void rejectionSummary_inAPackage_namesThePackage() {
        String expectedActivity = "Nightclub VIP Experience";
        List<RejectedEdit> rejected = List.of(
                new RejectedEdit(EditOp.ADD, expectedActivity, Tier.BASIC, EditRejectionReason.NO_FREE_SLOT, "detail"),
                new RejectedEdit(EditOp.ADD, expectedActivity, Tier.MEDIUM, EditRejectionReason.ALREADY_IN_PACKAGE,
                        "detail"));

        assertThat(EditMessages.rejectionSummary("en", rejected)).isEqualTo(
                "I could not fit Nightclub VIP Experience in the Basic package: no free slot left. "
                        + "Nightclub VIP Experience is already in the Medium package.");
        assertThat(EditMessages.rejectionSummary("de", rejected)).isEqualTo(
                "Nightclub VIP Experience konnte ich im Basic-Paket nicht unterbringen: kein freier Slot mehr. "
                        + "Nightclub VIP Experience ist schon im Medium-Paket.");
    }

    /** The same reason in and out of a package is two sentences: "not in any package" must not borrow a package name. */
    @Test
    void rejectionSummary_sameReasonWithAndWithoutPackage_isTwoSentences() {
        List<RejectedEdit> rejected = List.of(
                new RejectedEdit(EditOp.REMOVE, "Karting", null, EditRejectionReason.NOT_IN_PACKAGE, "detail"),
                new RejectedEdit(EditOp.REMOVE, "Beer Bike", Tier.PREMIUM, EditRejectionReason.NOT_IN_PACKAGE, "detail"));

        assertThat(EditMessages.rejectionSummary("en", rejected)).isEqualTo(
                "Karting is not in there, so there was nothing to change. "
                        + "Beer Bike is not in the Premium package, so there was nothing to change.");
    }

    /** The mixed outcome that read as a failure live: one package took it, one already had it. */
    @Test
    void summary_putsWhatLandedBeforeWhatDidNot() {
        String expectedActivity = "Nightclub VIP Experience";
        EditReport report = new EditReport(List.of(applied(EditOp.ADD, expectedActivity, null, Tier.PREMIUM)),
                List.of(new RejectedEdit(EditOp.ADD, expectedActivity, Tier.MEDIUM,
                        EditRejectionReason.ALREADY_IN_PACKAGE, null)), true, true);

        assertThat(EditMessages.summary("en", report)).isEqualTo(
                "Added Nightclub VIP Experience to the Premium package. "
                        + "Nightclub VIP Experience is already in the Medium package.");
    }

    @Test
    void summary_ofAFullyRejectedBatch_isTheRejectionSummary() {
        EditReport report = EditReport.allRejected(
                List.of(new EditRequest(EditOp.ADD, "Karting", null, null, null, null)), EditRejectionReason.EDIT_LIMIT);

        assertThat(EditMessages.summary("en", report)).isEqualTo(EditMessages.rejectionSummary("en", report.rejected()));
    }

    private static RejectedEdit rejected(String activityName, EditRejectionReason reason) {
        return new RejectedEdit(EditOp.ADD, activityName, null, reason, "detail");
    }

    private static AppliedEdit applied(EditOp op, String activityName, String replacementName, Tier packageKey) {
        return new AppliedEdit(op, activityName, replacementName, packageKey, 1, Slot.EVENING, null);
    }
}
