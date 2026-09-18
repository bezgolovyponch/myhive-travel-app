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
