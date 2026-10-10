package com.myhive.backend.ai.graph;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanDraft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plans, drafts and catalog snapshots live in checkpoints and {@code ai_generations} rows written before
 * extras existed; they have to keep reading, with no extras rather than a null list.
 */
class JsonCodecTest {

    @Test
    void read_anItemResultStoredBeforeExtras_hasNoExtras() {
        String stored = """
                {"slot":"EVENING","startHint":null,"activityId":"6f1c2a3e-0000-4000-8000-000000000001",
                 "slug":"cruise","name":"River Boat Cruise","imageUrl":null,"durationMinutes":90,"price":40.00,
                 "minPrice":null,"lineTotal":160.00,"groupMinApplied":false,"why":"On the water",
                 "includes":"Two hours on the Vltava"}""";

        ComposedPlan.ItemResult item = JsonCodec.read(stored, ComposedPlan.ItemResult.class);

        assertThat(item.name()).isEqualTo("River Boat Cruise");
        assertThat(item.extras()).isEmpty();
    }

    @Test
    void read_anItemDraftStoredBeforeExtras_hasNoExtraCodes() {
        String stored = """
                {"slot":"EVENING","startHint":null,"activityId":"6f1c2a3e-0000-4000-8000-000000000001","why":"x"}""";

        PlanDraft.ItemDraft item = JsonCodec.read(stored, PlanDraft.ItemDraft.class);

        assertThat(item.extraCodes()).isEmpty();
    }

    @Test
    void read_aCatalogActivityStoredBeforeExtras_offersNone() {
        String stored = """
                {"id":"6f1c2a3e-0000-4000-8000-000000000001","slug":"cruise","name":"River Boat Cruise",
                 "oneLine":"On the water","durationMinutes":90,"durationKnown":true,"price":40.00,"minPrice":null,
                 "imageUrl":null,"categorySlugs":["boat"],"includes":null}""";

        CatalogActivity activity = JsonCodec.read(stored, CatalogActivity.class);

        assertThat(activity.extras()).isEmpty();
    }
}
