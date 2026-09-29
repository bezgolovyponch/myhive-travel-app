package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict JSON schemas for the planner legs, sent as {@code response_format: json_schema}. They mirror
 * the shapes {@code planner-system.st} and {@code plan-texts-system.st} spell out, so the model cannot
 * answer with a missing key, a tier or slot that does not exist, or - for the plan - an activity code
 * the catalog never handed out. Scheduling rules are beyond a schema; {@code PlanValidator} still owns them.
 *
 * <p>Strict mode wants every property listed in {@code required} and {@code additionalProperties: false};
 * an optional value is typed {@code [T, "null"]} instead of left out.
 */
public final class ResponseSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponseSchemas() {
    }

    /** {@code {"packages":[{"key","days":[{"dayNumber","items":[{"slot","startHint","activityId"}]}]}]}}. */
    public static String plan(List<CatalogActivity> catalog) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) {
            codes.add(ActivityAliases.of(i));
        }
        Map<String, Object> item = object(orderedOf(
                "slot", enumOf(names(Slot.values())),
                "startHint", Map.of("type", List.of("string", "null")),
                "activityId", enumOf(codes)));
        Map<String, Object> day = object(orderedOf(
                "dayNumber", Map.of("type", "integer"),
                "items", arrayOf(item)));
        Map<String, Object> pkg = object(orderedOf(
                "key", enumOf(names(Tier.values())),
                "days", arrayOf(day)));
        return write(object(orderedOf("packages", arrayOf(pkg))));
    }

    /** The copy of one package: title, tagline, description, per-day title and summary, per-activity why. */
    public static String planTexts() {
        Map<String, Object> dayText = object(orderedOf(
                "dayNumber", Map.of("type", "integer"),
                "text", Map.of("type", "string")));
        Map<String, Object> why = object(orderedOf(
                "activityId", Map.of("type", "string"),
                "text", Map.of("type", "string")));
        Map<String, Object> pkg = object(orderedOf(
                "key", enumOf(names(Tier.values())),
                "title", Map.of("type", "string"),
                "tagline", Map.of("type", "string"),
                "description", Map.of("type", "string"),
                "dayTitles", arrayOf(dayText),
                "summaries", arrayOf(dayText),
                "why", arrayOf(why)));
        return write(object(orderedOf("packages", arrayOf(pkg))));
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static Map<String, Object> orderedOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String write(Object schema) {
        try {
            return MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize response schema", e);
        }
    }
}
