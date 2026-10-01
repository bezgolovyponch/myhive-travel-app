package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myhive.backend.ai.catalog.CatalogActivity;
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
    public static final int MAX_SUGGESTED_REPLIES = 4;
    public static final int MAX_SUGGESTED_REPLY_CHARS = 60;

    /** An activity name is a catalog label, not prose: past this the model is writing a sentence. */
    private static final int MAX_ACTIVITY_NAME_CHARS = 120;

    /** Nothing the catalog could have handed out: an unknown code lands here so the validator can name it. */
    private static final UUID UNKNOWN_ACTIVITY = new UUID(0L, 0L);

    /** How many alternatives one edit may name for an activity the catalog lacks: three read as a nudge, more as a list. */
    public static final int MAX_ALTERNATIVES_PER_EDIT = 3;

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
        return new ChatTurnResult(reply.asText().strip(), brief == null ? Brief.empty() : brief, missing, edits,
                LlmUsage.none(), suggestedReplies(root.path("suggestedReplies")));
    }

    /**
     * Chips are optional garnish: anything that is not a short non-blank string is dropped, never a reason
     * to lose the turn, and the list is capped so the UI never has to wrap a wall of buttons.
     */
    private static List<String> suggestedReplies(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> replies = new ArrayList<>();
        for (JsonNode element : node) {
            String text = element.isTextual() ? element.asText().strip() : "";
            if (!text.isEmpty() && text.length() <= MAX_SUGGESTED_REPLY_CHARS && !replies.contains(text)) {
                replies.add(text);
            }
            if (replies.size() == MAX_SUGGESTED_REPLIES) {
                break;
            }
        }
        return replies;
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
                edit.dayNumber(), edit.slot(), cappedAlternatives(edit.alternatives()));
    }

    /** The first few alternatives, each capped like any other name the model writes. */
    private static List<String> cappedAlternatives(List<String> alternatives) {
        List<String> kept = new ArrayList<>();
        for (String alternative : alternatives) {
            if (kept.size() == MAX_ALTERNATIVES_PER_EDIT) {
                break;
            }
            kept.add(capped(alternative));
        }
        return kept;
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

    /** The post-edit refresh reads the same shape as the first write; it names activities by id, not by code. */
    public Map<Tier, PackageTexts> parseTextRefresh(String raw) {
        return parsePackageTexts(raw, Map.of(), "text refresh");
    }

    /**
     * Whitelisted read of package copy: titles, taglines, descriptions, day titles, day summaries and
     * whys are taken and nothing else, so an answer can never move an id, a slot or a price. One
     * unusable entry (a hallucinated tier, an activity id that is not a UUID, a day number that is not
     * an int) is skipped rather than failing the read, since every field the model leaves out simply
     * keeps its previous text.
     */
    public Map<Tier, PackageTexts> parsePackageTexts(String raw) {
        return parsePackageTexts(raw, Map.of());
    }

    /** {@code aliases} are the codes the prompt handed out (see {@link ActivityAliases}); a why may name either. */
    public Map<Tier, PackageTexts> parsePackageTexts(String raw, Map<String, UUID> aliases) {
        return parsePackageTexts(raw, aliases, "package texts");
    }

    private Map<Tier, PackageTexts> parsePackageTexts(String raw, Map<String, UUID> aliases, String what) {
        JsonNode root = readTree(raw);
        JsonNode packages = root.path("packages");
        if (!packages.isArray()) {
            throw new LlmOutputException(what + ": 'packages' array is missing");
        }
        Map<Tier, PackageTexts> texts = new EnumMap<>(Tier.class);
        for (JsonNode node : packages) {
            Tier key = tierOrNull(node.path("key"));
            if (key == null) {
                // Unknown package key: nothing to write it to, so drop this block (see method comment above).
                continue;
            }
            texts.put(key, new PackageTexts(textOrNull(node.path("title")), textOrNull(node.path("tagline")),
                    textOrNull(node.path("description")), textByDay(node.path("dayTitles")),
                    whyByActivityId(node.path("why"), aliases), textByDay(node.path("summaries"))));
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
            // Hallucinated tier name: skip it (see parsePackageTexts).
            return null;
        }
    }

    private static Map<UUID, String> whyByActivityId(JsonNode node, Map<String, UUID> aliases) {
        Map<UUID, String> out = new LinkedHashMap<>();
        if (!node.isArray()) {
            return out;
        }
        for (JsonNode entry : node) {
            UUID activityId = idOrNull(entry.path("activityId"), aliases);
            String text = textOrNull(entry.path("text"));
            if (activityId != null && text != null) {
                out.put(activityId, text);
            }
        }
        return out;
    }

    private static Map<Integer, String> textByDay(JsonNode node) {
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

    /** A UUID, or one of the codes the prompt handed out; anything else is skipped (see parsePackageTexts). */
    private static UUID idOrNull(JsonNode node, Map<String, UUID> aliases) {
        if (!node.isTextual()) {
            return null;
        }
        UUID id = uuidOrNull(node);
        return id != null ? id : aliases.get(ActivityAliases.normalise(node.asText()));
    }

    private static UUID uuidOrNull(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        try {
            return UUID.fromString(node.asText().strip());
        } catch (IllegalArgumentException e) {
            // The model wrote a slug or a name instead of an id: skip it (see parsePackageTexts).
            return null;
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    public PlanDraft parsePlan(String raw) {
        return parsePlan(raw, List.of());
    }

    /**
     * Item ids come back as the codes the prompt handed out (A1, A2, ...) or, from a model that copies
     * a repair draft, as UUIDs; the codes are resolved here, before the strict read, so nothing
     * downstream ever meets one. A code the catalog never handed out becomes the nil UUID rather than a
     * failed parse: the validator then reports that one item as UNKNOWN_ACTIVITY and the repair fixes
     * it, where a thrown-away answer would have cost the whole draft.
     */
    public PlanDraft parsePlan(String raw, List<CatalogActivity> catalog) {
        JsonNode root = readTree(raw);
        if (!root.path("packages").isArray()) {
            throw new LlmOutputException("plan: 'packages' array is missing");
        }
        Map<String, UUID> aliases = ActivityAliases.toId(catalog);
        for (JsonNode pkg : root.path("packages")) {
            for (JsonNode day : pkg.path("days")) {
                for (JsonNode item : day.path("items")) {
                    resolveActivityId(item, aliases);
                }
            }
        }
        return convert(root, PlanDraft.class, "packages");
    }

    private static void resolveActivityId(JsonNode item, Map<String, UUID> aliases) {
        JsonNode id = item.path("activityId");
        if (!(item instanceof ObjectNode object) || !id.isTextual() || uuidOrNull(id) != null) {
            return;
        }
        UUID resolved = aliases.get(ActivityAliases.normalise(id.asText()));
        object.put("activityId", (resolved == null ? UNKNOWN_ACTIVITY : resolved).toString());
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
