package com.myhive.backend.ai.edit;

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

    private static RejectedEdit rejected(String activityName, EditRejectionReason reason) {
        return new RejectedEdit(EditOp.ADD, activityName, null, reason, "detail");
    }
}
