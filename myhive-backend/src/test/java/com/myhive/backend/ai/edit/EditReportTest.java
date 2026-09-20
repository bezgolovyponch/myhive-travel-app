package com.myhive.backend.ai.edit;

import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EditReportTest {

    @Test
    void of_carriesTheOutcomeAndSetsTierRulesRelaxedFromAnyApplied() {
        AppliedEdit expectedApplied = new AppliedEdit(EditOp.ADD, "Beer Bike", null, Tier.MEDIUM, 2, Slot.AFTERNOON,
                UUID.randomUUID());
        EditOutcome outcome = new EditOutcome(null, List.of(expectedApplied), List.of());

        EditReport report = EditReport.of(outcome, true);

        assertThat(report.applied()).containsExactly(expectedApplied);
        assertThat(report.rejected()).isEmpty();
        assertThat(report.tierRulesRelaxed()).isTrue();
        assertThat(report.textsRefreshed()).isTrue();
    }

    @Test
    void allRejected_buildsOneRejectionPerRequest_withTheRequestsPackageKeyAndNoDetail() {
        EditRequest first = new EditRequest(EditOp.REMOVE, "Beer Spa", null, Tier.BASIC, null, null);
        EditRequest second = new EditRequest(EditOp.ADD, "River Cruise", null, null, 1, Slot.MORNING);
        EditRejectionReason expectedReason = EditRejectionReason.EDIT_LIMIT;

        EditReport report = EditReport.allRejected(List.of(first, second), expectedReason);

        assertThat(report.applied()).isEmpty();
        assertThat(report.anyApplied()).isFalse();
        assertThat(report.tierRulesRelaxed()).isFalse();
        assertThat(report.textsRefreshed()).isFalse();
        assertThat(report.rejected()).containsExactly(
                new RejectedEdit(EditOp.REMOVE, "Beer Spa", Tier.BASIC, expectedReason, null),
                new RejectedEdit(EditOp.ADD, "River Cruise", null, expectedReason, null));
    }

    @Test
    void roundTripsThroughJsonCodec() {
        AppliedEdit applied = new AppliedEdit(EditOp.REPLACE, "Beer Spa", "Beer Bike", Tier.PREMIUM, 3, Slot.EVENING,
                UUID.randomUUID());
        RejectedEdit rejected = new RejectedEdit(EditOp.REMOVE, "Shooting Range", Tier.BASIC,
                EditRejectionReason.NOT_IN_PACKAGE, "not currently in the package");
        EditReport expectedReport = new EditReport(List.of(applied), List.of(rejected), true, false);

        String json = JsonCodec.write(expectedReport);
        EditReport roundTripped = JsonCodec.read(json, EditReport.class);

        assertThat(roundTripped).isEqualTo(expectedReport);
    }

    @Test
    void roundTripsThroughJsonCodec_withEmptyLists() {
        EditReport expectedReport = new EditReport(List.of(), List.of(), false, false);

        String json = JsonCodec.write(expectedReport);
        EditReport roundTripped = JsonCodec.read(json, EditReport.class);

        assertThat(roundTripped).isEqualTo(expectedReport);
    }
}
