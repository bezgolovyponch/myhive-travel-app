package com.myhive.backend.ai.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.Violation;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The whole planner conversation as one checkpointed state. Every value is JSON-friendly (String,
 * number, boolean, {@code List<String>}, {@code List<Map<String,String>>}) so the Postgres saver can
 * store it verbatim; anything richer goes through {@link JsonCodec} and is read back by an accessor.
 */
public class PlannerState extends AgentState {

    public static final String MESSAGES = "messages";
    public static final String BRIEF = "brief";
    public static final String LOCALE = "locale";
    public static final String DESTINATION_ID = "destinationId";
    public static final String DESTINATION_NAME = "destinationName";
    public static final String CATEGORY_SLUGS = "categorySlugs";
    public static final String CATALOG = "catalog";
    public static final String DRAFT = "draft";
    public static final String VIOLATIONS = "violations";
    public static final String ATTEMPT = "attempt";
    public static final String RESULT = "result";
    public static final String DEGRADED = "degraded";
    public static final String ACTION = "action";
    public static final String LAST_GENERATED_BRIEF = "lastGeneratedBrief";
    public static final String RESUME_REASON = "resumeReason";
    public static final String GENERATION_ID = "generationId";
    public static final String SELECTED_PACKAGE_KEY = "selectedPackageKey";
    public static final String MISSING_FIELDS = "missingFields";
    public static final String LAST_ERROR = "lastError";
    public static final String USAGE = "usage";

    /** Nothing to do but wait for the next user message. */
    public static final String ACTION_NONE = "NONE";
    /** The brief is complete and differs from the one the last generation used: build the packages. */
    public static final String ACTION_GENERATE = "GENERATE";
    /** Only {@link PlannerGraph#seedParked}: the chat node returns without calling the model. */
    public static final String ACTION_SEED = "SEED";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES, Channels.appender(ArrayList::new));

    private static final String MESSAGE_ROLE = "role";
    private static final String MESSAGE_CONTENT = "content";
    private static final String MESSAGE_AT = "at";

    private static final TypeReference<List<CatalogActivity>> CATALOG_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Violation>> VIOLATIONS_TYPE = new TypeReference<>() {};

    public PlannerState(Map<String, Object> initData) {
        super(initData);
    }

    public List<ChatMessage> messages() {
        List<Map<String, String>> raw = this.<List<Map<String, String>>>value(MESSAGES).orElse(List.of());
        return raw.stream()
                .map(m -> new ChatMessage(m.get(MESSAGE_ROLE), m.get(MESSAGE_CONTENT), m.get(MESSAGE_AT)))
                .toList();
    }

    public Brief brief() {
        return this.<String>value(BRIEF).map(json -> JsonCodec.read(json, Brief.class)).orElse(Brief.empty());
    }

    public String locale() {
        return this.<String>value(LOCALE).orElse("en");
    }

    public UUID destinationId() {
        return UUID.fromString(this.<String>value(DESTINATION_ID).orElseThrow(
                () -> new IllegalStateException("planner state has no destinationId")));
    }

    public String destinationName() {
        return this.<String>value(DESTINATION_NAME).orElse("");
    }

    public List<String> categorySlugs() {
        return this.<List<String>>value(CATEGORY_SLUGS).orElse(List.of());
    }

    public List<CatalogActivity> catalog() {
        return this.<String>value(CATALOG).map(json -> JsonCodec.read(json, CATALOG_TYPE)).orElse(List.of());
    }

    /** The snapshot keyed for the validator and the assembler; a duplicate id can only mean a corrupt snapshot. */
    public Map<UUID, CatalogActivity> catalogById() {
        return catalog().stream().collect(Collectors.toMap(CatalogActivity::id, Function.identity(),
                (first, duplicate) -> first, LinkedHashMap::new));
    }

    public Optional<PlanDraft> draft() {
        return this.<String>value(DRAFT).map(json -> JsonCodec.read(json, PlanDraft.class));
    }

    public List<Violation> violations() {
        return this.<String>value(VIOLATIONS).map(json -> JsonCodec.read(json, VIOLATIONS_TYPE)).orElse(List.of());
    }

    public Optional<ComposedPlan> result() {
        return this.<String>value(RESULT).map(json -> JsonCodec.read(json, ComposedPlan.class));
    }

    public LlmUsage usage() {
        return this.<String>value(USAGE).map(json -> JsonCodec.read(json, LlmUsage.class)).orElseGet(LlmUsage::none);
    }

    public int attempt() {
        return this.<Integer>value(ATTEMPT).orElse(0);
    }

    public boolean degraded() {
        return this.<Boolean>value(DEGRADED).orElse(false);
    }

    public String action() {
        return this.<String>value(ACTION).orElse(ACTION_NONE);
    }

    public Optional<String> lastGeneratedBrief() {
        return value(LAST_GENERATED_BRIEF);
    }

    /** Blank means "nothing pending": the nodes clear these keys by writing an empty string, never null. */
    public Optional<String> resumeReason() {
        return nonBlank(RESUME_REASON);
    }

    public Optional<String> lastError() {
        return nonBlank(LAST_ERROR);
    }

    public Optional<UUID> generationId() {
        return this.<String>value(GENERATION_ID).map(UUID::fromString);
    }

    public Optional<Tier> selectedPackageKey() {
        return this.<String>value(SELECTED_PACKAGE_KEY).map(Tier::valueOf);
    }

    public static Map<String, String> message(String role, String content) {
        return Map.of(MESSAGE_ROLE, role, MESSAGE_CONTENT, content, MESSAGE_AT, Instant.now().toString());
    }

    private Optional<String> nonBlank(String key) {
        return this.<String>value(key).filter(value -> !value.isBlank());
    }
}
