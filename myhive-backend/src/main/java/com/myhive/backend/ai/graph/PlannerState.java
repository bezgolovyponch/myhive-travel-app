package com.myhive.backend.ai.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.EditRequest;
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
    public static final String RESULT_GENERATION_ID = "resultGenerationId";
    public static final String SELECTED_PACKAGE_KEY = "selectedPackageKey";
    public static final String MISSING_FIELDS = "missingFields";
    public static final String LAST_ERROR = "lastError";
    public static final String USAGE = "usage";
    public static final String EDITS = "edits";
    public static final String EDIT_REPORT = "editReport";
    public static final String EDITS_LEFT = "editsLeft";

    /** Nothing to do but wait for the next user message. */
    public static final String ACTION_NONE = "NONE";
    /** The brief is complete and differs from the one the last generation used: build the packages. */
    public static final String ACTION_GENERATE = "GENERATE";
    /** Only {@link PlannerGraph#seedParked}: the chat node returns without calling the model. */
    public static final String ACTION_SEED = "SEED";
    /** The turn asked for concrete changes to packages that already exist: edit them, do not regenerate. */
    public static final String ACTION_EDIT = "EDIT";

    /**
     * The cleared form of {@link #EDITS}: an empty batch rather than a blank, so the JSON in state is
     * always readable. {@link #edits()} answers {@code []} for either.
     */
    public static final String NO_EDITS = "[]";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES, Channels.appender(ArrayList::new));

    private static final String MESSAGE_ROLE = "role";
    private static final String MESSAGE_CONTENT = "content";
    private static final String MESSAGE_AT = "at";

    private static final TypeReference<List<CatalogActivity>> CATALOG_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Violation>> VIOLATIONS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<EditRequest>> EDITS_TYPE = new TypeReference<>() {};

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
        return nonBlank(DRAFT).map(json -> JsonCodec.read(json, PlanDraft.class));
    }

    public List<Violation> violations() {
        return nonBlank(VIOLATIONS).map(json -> JsonCodec.read(json, VIOLATIONS_TYPE)).orElse(List.of());
    }

    public Optional<ComposedPlan> result() {
        return this.<String>value(RESULT).map(json -> JsonCodec.read(json, ComposedPlan.class));
    }

    /** The edits the chat turn extracted, still to be applied; the edit node clears them when it is done. */
    public List<EditRequest> edits() {
        return nonBlank(EDITS).map(json -> JsonCodec.read(json, EDITS_TYPE)).orElse(List.of());
    }

    public Optional<EditReport> editReport() {
        return nonBlank(EDIT_REPORT).map(json -> JsonCodec.read(json, EditReport.class));
    }

    /** No allowance in state means unlimited: only the service that owns the per-session cap stamps one. */
    public int editsLeft() {
        return this.<Integer>value(EDITS_LEFT).orElse(Integer.MAX_VALUE);
    }

    public LlmUsage usage() {
        return nonBlank(USAGE).map(json -> JsonCodec.read(json, LlmUsage.class)).orElseGet(LlmUsage::none);
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
        return nonBlank(LAST_GENERATED_BRIEF);
    }

    /** Blank means "nothing pending": the nodes clear these keys by writing an empty string, never null. */
    public Optional<String> resumeReason() {
        return nonBlank(RESUME_REASON);
    }

    public Optional<String> lastError() {
        return nonBlank(LAST_ERROR);
    }

    /**
     * The row a resume is working on, whatever it ends up producing: the job stamps it before the
     * generation runs and a selection stamps whatever the client picked.
     */
    public Optional<UUID> generationId() {
        return this.<String>value(GENERATION_ID).map(UUID::fromString);
    }

    /**
     * The generation that actually produced the plan in {@link #RESULT} - which is not always
     * {@link #generationId()}: a regeneration that dies before it composes anything leaves its own
     * (FAILED) id stamped over a state whose packages still belong to the last good row. An edit has
     * to hang off <em>this</em> one, or the edited plan is filed under a generation that never made it.
     */
    public Optional<UUID> resultGenerationId() {
        return this.<String>value(RESULT_GENERATION_ID).map(UUID::fromString);
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
