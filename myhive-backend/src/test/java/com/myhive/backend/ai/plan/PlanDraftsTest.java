package com.myhive.backend.ai.plan;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanDraftsTest {

    private final PlanAssembler assembler = new PlanAssembler();
    private final Map<UUID, CatalogActivity> catalog = new HashMap<>();
    private final Brief brief = new Brief(2, 4, List.of(), "x", null, null, DayEdge.MORNING, DayEdge.EVENING, null);

    private UUID activity(String name, String price) {
        UUID id = UUID.randomUUID();
        catalog.put(id, new CatalogActivity(id, name.toLowerCase(), name, "line", 90, true,
                new BigDecimal(price), null, "img", List.of()));
        return id;
    }

    private static PlanDraft.PackageDraft pkg(Tier tier, UUID dayOneId, UUID dayTwoId) {
        List<PlanDraft.DayDraft> days = List.of(
                new PlanDraft.DayDraft(1, tier.name() + " day 1", "sum1",
                        List.of(new PlanDraft.ItemDraft(Slot.MORNING, "10:00", dayOneId, "why1"))),
                new PlanDraft.DayDraft(2, tier.name() + " day 2", "sum2",
                        List.of(new PlanDraft.ItemDraft(Slot.AFTERNOON, "14:00", dayTwoId, "why2"))));
        return new PlanDraft.PackageDraft(tier, tier.name() + " title", tier.name() + " tag", tier.name() + " desc", days);
    }

    @Test
    void fromComposed_roundTripsThroughTheAssembler() {
        UUID basicOne = activity("Basic One", "10.00");
        UUID basicTwo = activity("Basic Two", "12.00");
        UUID mediumOne = activity("Medium One", "20.00");
        UUID mediumTwo = activity("Medium Two", "22.00");
        UUID premiumOne = activity("Premium One", "30.00");
        UUID premiumTwo = activity("Premium Two", "32.00");
        PlanDraft draft = new PlanDraft(List.of(
                pkg(Tier.BASIC, basicOne, basicTwo),
                pkg(Tier.MEDIUM, mediumOne, mediumTwo),
                pkg(Tier.PREMIUM, premiumOne, premiumTwo)));

        ComposedPlan firstPass = assembler.assemble(draft, brief, catalog, false).plan();
        PlanDraft roundTripped = PlanDrafts.fromComposed(firstPass);
        ComposedPlan secondPass = assembler.assemble(roundTripped, brief, catalog, false).plan();

        assertThat(secondPass).isEqualTo(firstPass);
    }

    @Test
    void replacePackage_swapsOnlyThatTier() {
        UUID basicOne = activity("Basic One", "10.00");
        UUID basicTwo = activity("Basic Two", "12.00");
        UUID mediumOne = activity("Medium One", "20.00");
        UUID mediumTwo = activity("Medium Two", "22.00");
        UUID premiumOne = activity("Premium One", "30.00");
        UUID premiumTwo = activity("Premium Two", "32.00");
        PlanDraft.PackageDraft expectedBasic = pkg(Tier.BASIC, basicOne, basicTwo);
        PlanDraft.PackageDraft expectedPremium = pkg(Tier.PREMIUM, premiumOne, premiumTwo);
        PlanDraft draft = new PlanDraft(List.of(expectedBasic, pkg(Tier.MEDIUM, mediumOne, mediumTwo), expectedPremium));
        UUID replacementOne = activity("Replacement One", "40.00");
        PlanDraft.PackageDraft replacement = pkg(Tier.MEDIUM, replacementOne, replacementOne);

        PlanDraft replaced = PlanDrafts.replacePackage(draft, replacement);

        assertThat(replaced.packages()).containsExactly(expectedBasic, replacement, expectedPremium);
    }

    @Test
    void replacePackage_appendsWhenTierIsAbsent() {
        UUID basicOne = activity("Basic One", "10.00");
        UUID basicTwo = activity("Basic Two", "12.00");
        PlanDraft.PackageDraft expectedBasic = pkg(Tier.BASIC, basicOne, basicTwo);
        PlanDraft draft = new PlanDraft(List.of(expectedBasic));
        UUID premiumOne = activity("Premium One", "30.00");
        PlanDraft.PackageDraft premium = pkg(Tier.PREMIUM, premiumOne, premiumOne);

        PlanDraft replaced = PlanDrafts.replacePackage(draft, premium);

        assertThat(replaced.packages()).containsExactly(expectedBasic, premium);
    }

    @Test
    void packageOf_findsByTier_orIsEmpty() {
        UUID basicOne = activity("Basic One", "10.00");
        UUID basicTwo = activity("Basic Two", "12.00");
        PlanDraft.PackageDraft expectedBasic = pkg(Tier.BASIC, basicOne, basicTwo);
        PlanDraft draft = new PlanDraft(List.of(expectedBasic));

        Optional<PlanDraft.PackageDraft> found = PlanDrafts.packageOf(draft, Tier.BASIC);
        Optional<PlanDraft.PackageDraft> missing = PlanDrafts.packageOf(draft, Tier.PREMIUM);

        assertThat(found).contains(expectedBasic);
        assertThat(missing).isEmpty();
    }
}
