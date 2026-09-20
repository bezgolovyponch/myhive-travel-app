package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.PlanDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Strict, tolerant-of-noise parsing of model JSON: strips code fences, ignores unknown fields, names the offending field. */
@Component
@Slf4j
public class LlmOutputParser {

    /**
     * How many ops one turn may carry. A conversational request fans out to one entry per package on top
     * of this, and every op costs a validation pass, so a model that answers a vague "change everything"
     * with fifty of them would spend the turn's whole budget on work nobody asked for. Ten is far above
     * what a real message needs; the rest are dropped, not rejected, since the chat reply still stands.
     */
    public static final int MAX_EDITS_PER_TURN = 10;

    /** An activity name is a catalog label, not prose: past this the model is writing a sentence. */
    private static final int MAX_ACTIVITY_NAME_CHARS = 120;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // A hallucinated enum value is the model's mistake on one field, not a reason to throw the
            // turn away: budget "MEDIUM" or arrival "NIGHT" reads as null, and BriefMerger then keeps
            // whatever the brief already had. Failing the whole turn instead cost the user a message
            // and an answer over a word the agent can simply ask again.
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

    public ChatTurnResult parseChatTurn(String raw) {
        JsonNode root = readTree(raw);
        JsonNode reply = root.get("reply");
        if (reply == null || !reply.isTextual() || reply.asText().isBlank()) {
            throw new LlmOutputException("chat turn: 'reply' is missing or empty");
        }
        Brief brief = convert(root.path("brief"), Brief.class, "brief");
        List<String> missing = root.path("missingFields").isArray()
                ? mapper.convertValue(root.get("missingFields"), mapper.getTypeFactory().constructCollectionType(List.class, String.class))
                : List.of();
        List<EditRequest> edits = new ArrayList<>();
        JsonNode editsNode = root.path("edits");
        if (editsNode.isArray()) {
            for (JsonNode editNode : editsNode) {
                EditRequest edit = editOrNull(editNode);
                if (edit != null) {
                    edits.add(edit);
                }
            }
        }
        if (edits.size() > MAX_EDITS_PER_TURN) {
            // Count only: an edit carries user-derived text and nothing model-written is logged here.
            log.info("chat turn asked for {} edits, keeping the first {}", edits.size(), MAX_EDITS_PER_TURN);
            edits = new ArrayList<>(edits.subList(0, MAX_EDITS_PER_TURN));
        }
        return new ChatTurnResult(reply.asText().strip(), brief == null ? Brief.empty() : brief, missing, edits, LlmUsage.none());
    }

    /**
     * A malformed edit element (a hallucinated op, a blank activity, a REPLACE missing its
     * replacement) is that one element's mistake, not a reason to lose the whole turn: the brief and
     * reply are still useful to the user, so the element is dropped rather than failing the parse.
     */
    private EditRequest editOrNull(JsonNode node) {
        EditRequest edit;
        try {
            edit = mapper.treeToValue(node, EditRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // Malformed edit element: skip it rather than fail the whole turn (see method comment above).
            return null;
        }
        if (edit.op() == null || edit.activity() == null || edit.activity().isBlank()) {
            return null;
        }
        if (edit.op() == EditOp.REPLACE && (edit.replacement() == null || edit.replacement().isBlank())) {
            return null;
        }
        return new EditRequest(edit.op(), capped(edit.activity()), capped(edit.replacement()), edit.packageKey(),
                edit.dayNumber(), edit.slot());
    }

    /**
     * Names are stored in the edit report and read back into the chat when they cannot be resolved, so a
     * model that pastes a paragraph into {@code activity} must not get a paragraph-long rejection line.
     * Truncated rather than dropped: the head of it is still the best guess at what was meant.
     */
    private static String capped(String name) {
        if (name == null || name.length() <= MAX_ACTIVITY_NAME_CHARS) {
            return name;
        }
        return name.substring(0, MAX_ACTIVITY_NAME_CHARS).strip();
    }

    /**
     * Whitelisted read of the post-edit copy: only descriptions, whys and day summaries are taken, so a
     * refresh can never move an id, a slot or a price. One unusable entry (a hallucinated tier, an
     * activity id that is not a UUID, a day number that is not an int) is skipped rather than failing the
     * refresh, since every field the model leaves out simply keeps its previous text.
     */
    public Map<Tier, PackageTexts> parseTextRefresh(String raw) {
        JsonNode root = readTree(raw);
        JsonNode packages = root.path("packages");
        if (!packages.isArray()) {
            throw new LlmOutputException("text refresh: 'packages' array is missing");
        }
        Map<Tier, PackageTexts> texts = new EnumMap<>(Tier.class);
        for (JsonNode node : packages) {
            Tier key = tierOrNull(node.path("key"));
            if (key == null) {
                // Unknown package key: nothing to write it to, so drop this block (see method comment above).
                continue;
            }
            texts.put(key, new PackageTexts(textOrNull(node.path("description")),
                    whyByActivityId(node.path("why")), summaryByDay(node.path("summaries"))));
        }
        return texts;
    }

    private static Tier tierOrNull(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        try {
            return Tier.valueOf(node.asText().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Hallucinated tier name: skip it (see parseTextRefresh).
            return null;
        }
    }

    private static Map<UUID, String> whyByActivityId(JsonNode node) {
        Map<UUID, String> out = new LinkedHashMap<>();
        if (!node.isArray()) {
            return out;
        }
        for (JsonNode entry : node) {
            UUID activityId = uuidOrNull(entry.path("activityId"));
            String text = textOrNull(entry.path("text"));
            if (activityId != null && text != null) {
                out.put(activityId, text);
            }
        }
        return out;
    }

    private static Map<Integer, String> summaryByDay(JsonNode node) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (!node.isArray()) {
            return out;
        }
        for (JsonNode entry : node) {
            JsonNode dayNumber = entry.path("dayNumber");
            String text = textOrNull(entry.path("text"));
            if (dayNumber.isInt() && text != null) {
                out.put(dayNumber.asInt(), text);
            }
        }
        return out;
    }

    private static UUID uuidOrNull(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText().strip());
        } catch (IllegalArgumentException e) {
            // The model wrote a slug or a name instead of an id: skip it (see parseTextRefresh).
            return null;
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    public PlanDraft parsePlan(String raw) {
        JsonNode root = readTree(raw);
        if (!root.path("packages").isArray()) {
            throw new LlmOutputException("plan: 'packages' array is missing");
        }
        return convert(root, PlanDraft.class, "packages");
    }

    private JsonNode readTree(String raw) {
        String json = stripFences(raw);
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new LlmOutputException("model output is not a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new LlmOutputException("model output is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    private <T> T convert(JsonNode node, Class<T> type, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(node, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new LlmOutputException("field '" + field + "' is malformed: " + e.getMessage(), e);
        }
    }

    static String stripFences(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            s = firstNewline > 0 ? s.substring(firstNewline + 1) : s.substring(3);
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
        }
        return s.strip();
    }
}
