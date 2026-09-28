package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.PlanDraft;
import org.springframework.stereotype.Component;

import java.util.List;

/** Strict, tolerant-of-noise parsing of model JSON: strips code fences, ignores unknown fields, names the offending field. */
@Component
public class LlmOutputParser {

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
        return new ChatTurnResult(reply.asText().strip(), brief == null ? Brief.empty() : brief, missing, LlmUsage.none());
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
