package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.catalog.CatalogActivity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseSchemasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static CatalogActivity activity(String name) {
        return new CatalogActivity(UUID.randomUUID(), name.toLowerCase(), name, "line", 90, true,
                new BigDecimal("30.00"), null, "img", List.of());
    }

    /** The model can only name an activity by a code the prompt handed out, so UNKNOWN_ACTIVITY cannot arise. */
    @Test
    void plan_restrictsActivityIdToTheCatalogCodes_andTiersAndSlotsToTheirEnums() throws Exception {
        JsonNode schema = mapper.readTree(ResponseSchemas.plan(List.of(activity("Crawl"), activity("Spa"))));

        JsonNode pkg = schema.at("/properties/packages/items");
        JsonNode item = pkg.at("/properties/days/items/properties/items/items");
        assertThat(pkg.at("/properties/key/enum")).extracting(JsonNode::asText)
                .containsExactly("BASIC", "MEDIUM", "PREMIUM");
        assertThat(item.at("/properties/slot/enum")).extracting(JsonNode::asText)
                .containsExactly("MORNING", "AFTERNOON", "EVENING", "NIGHT");
        assertThat(item.at("/properties/activityId/enum")).extracting(JsonNode::asText).containsExactly("A1", "A2");
    }

    /** Strict mode rejects a schema whose objects leave a property optional or allow extra ones. */
    @Test
    void everyObject_requiresAllItsPropertiesAndAllowsNoOthers() throws Exception {
        for (String json : List.of(ResponseSchemas.plan(List.of(activity("Crawl"))), ResponseSchemas.planTexts())) {
            assertStrict(mapper.readTree(json));
        }
    }

    private static void assertStrict(JsonNode node) {
        if (node.isObject() && "object".equals(node.path("type").asText())) {
            List<String> properties = iterate(node.path("properties").fieldNames());
            assertThat(node.path("required")).extracting(JsonNode::asText).containsExactlyElementsOf(properties);
            assertThat(node.path("additionalProperties").asBoolean(true)).isFalse();
        }
        node.elements().forEachRemaining(ResponseSchemasTest::assertStrict);
    }

    private static List<String> iterate(Iterator<String> names) {
        List<String> out = new ArrayList<>();
        names.forEachRemaining(out::add);
        return out;
    }
}
