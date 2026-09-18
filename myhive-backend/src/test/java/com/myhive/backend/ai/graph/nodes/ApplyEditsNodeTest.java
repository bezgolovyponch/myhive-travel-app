package com.myhive.backend.ai.graph.nodes;

import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.edit.EditMessages;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.EditRequest;
import com.myhive.backend.ai.edit.GenerationEditSink;
import com.myhive.backend.ai.edit.PackageEditor;
import com.myhive.backend.ai.edit.RejectedEdit;
import com.myhive.backend.ai.edit.TextRefresher;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.FakeLlmGateway;
import com.myhive.backend.ai.llm.LlmUnavailableException;
import com.myhive.backend.ai.llm.LlmUsage;
import com.myhive.backend.ai.llm.PackageTexts;
import com.myhive.backend.ai.llm.TextRefreshResult;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanValidator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

class ApplyEditsNodeTest {

    private static final String LOCALE = "en";
    private static final String DESTINATION_NAME = "Prague";
    private static final String EXPECTED_CLEARED_EDITS = "[]";
    private static final LlmUsage EXPECTED_REFRESH_USAGE = new LlmUsage("fake-chat", 5, 7, 3L);

    private final PlanAssembler assembler = new PlanAssembler();
    private final PackageEditor editor = new PackageEditor(new PlanValidator(), assembler);
    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final TextRefresher refresher = new TextRefresher(llm);
    private final RecordingEditSink sink = new RecordingEditSink();
    private final ApplyEditsNode node = new ApplyEditsNode(editor, refresher, sink);

    private final UUID parentGenerationId = UUID.randomUUID();
    private final Brief brief = new Brief(2, 4, List.of(), "stag weekend", null, null,
            DayEdge.MORNING, DayEdge.EVENING, null);
    private final List<CatalogActivity> catalog = new ArrayList<>();
    private final CatalogActivity beerSpa = activity("Beer Spa", "40.00", 90);
    private final CatalogActivity beerBike = activity("Beer Bike", "45.00", 120);
    private final CatalogActivity riverCruise = activity("River Cruise", "25.00", 60);
    private final CatalogActivity karting = activity("Karting", "55.00", 90);

    static class RecordingEditSink implements GenerationEditSink {
        private final UUID editedGenerationId = UUID.randomUUID();
        private int calls;
        private UUID lastParent;
        private ComposedPlan lastPlan;
        private EditReport lastReport;
        private LlmUsage lastUsage;
        private RuntimeException nextFailure;
        private boolean answersWithNoId;

        @Override
        public UUID edited(UUID parentGenerationId, ComposedPlan plan, EditReport report, LlmUsage usage) {
            calls++;
            lastParent = parentGenerationId;
            lastPlan = plan;
            lastReport = report;
            lastUsage = usage;
            if (nextFailure != null) {
                throw nextFailure;
            }
            return answersWithNoId ? null : editedGenerationId;
        }
    }

    @Test
    void appliedEdit_persistsANewGeneration_andWritesResultAndId() {
        String expectedDescription = "Now with karting";
        String expectedWhy = "Because someone has to drive";
        llm.queueRefresh(new TextRefreshResult(Map.of(Tier.BASIC, new PackageTexts(expectedDescription,
                Map.of(karting.id(), expectedWhy), Map.of())), EXPECTED_REFRESH_USAGE));

        Map<String, Object> update = node.apply(state(List.of(replace(beerBike.name(), karting.name()))));

        assertThat(sink.calls).isEqualTo(1);
        assertThat(sink.lastParent).isEqualTo(parentGenerationId);
        assertThat(sink.lastUsage).isEqualTo(EXPECTED_REFRESH_USAGE);
        assertThat(sink.lastReport.applied()).singleElement().satisfies(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.REPLACE);
            assertThat(applied.activityName()).isEqualTo(beerBike.name());
            assertThat(applied.replacementName()).isEqualTo(karting.name());
        });
        assertThat(update.get(PlannerState.GENERATION_ID)).isEqualTo(sink.editedGenerationId.toString());
        ComposedPlan edited = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
        assertThat(namesIn(edited)).contains(karting.name()).doesNotContain(beerBike.name());
        assertThat(edited.packages().get(0).description()).isEqualTo(expectedDescription);
        assertThat(whyOf(edited, karting.id())).isEqualTo(expectedWhy);
        EditReport report = reportOf(update);
        assertThat(report.textsRefreshed()).isTrue();
        assertThat(report.tierRulesRelaxed()).isTrue();
        assertThat(report.rejected()).isEmpty();
        assertThat(update.get(PlannerState.EDITS)).isEqualTo(EXPECTED_CLEARED_EDITS);
        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(update.get(PlannerState.RESUME_REASON)).isEqualTo("");
        assertThat(update).doesNotContainKey(PlannerState.MESSAGES);
    }

    @Test
    void refreshFailure_keepsTextsAndFlagsNotRefreshed() {
        String expectedDescription = description(plan());
        llm.failNextRefresh(new LlmUnavailableException("model down", new RuntimeException("connection reset")));

        Map<String, Object> update = node.apply(state(List.of(replace(beerBike.name(), karting.name()))));

        assertThat(reportOf(update).textsRefreshed()).isFalse();
        assertThat(sink.calls).isEqualTo(1);
        assertThat(sink.lastUsage).isEqualTo(LlmUsage.none());
        // the edit itself still landed; only the copy is the one the previous generation had
        ComposedPlan edited = JsonCodec.read((String) update.get(PlannerState.RESULT), ComposedPlan.class);
        assertThat(namesIn(edited)).contains(karting.name()).doesNotContain(beerBike.name());
        assertThat(description(edited)).isEqualTo(expectedDescription);
    }

    @Test
    void rejectedOnly_createsNoRow_andAddsARejectionMessage() {
        String expectedMessage = EditMessages.rejectionSummary(LOCALE, List.of(new RejectedEdit(EditOp.ADD,
                beerSpa.name(), Tier.BASIC, EditRejectionReason.ALREADY_IN_PACKAGE, null)));

        Map<String, Object> update = node.apply(state(List.of(add(beerSpa.name()))));

        assertThat(sink.calls).isZero();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(update).doesNotContainKey(PlannerState.RESULT).doesNotContainKey(PlannerState.GENERATION_ID);
        EditReport report = reportOf(update);
        assertThat(report.applied()).isEmpty();
        assertThat(report.rejected()).singleElement().satisfies(rejected ->
                assertThat(rejected.reason()).isEqualTo(EditRejectionReason.ALREADY_IN_PACKAGE));
        assertThat(messagesOf(update)).singleElement().satisfies(message ->
                assertThat(message.get("content")).isEqualTo(expectedMessage));
    }

    @Test
    void editLimitReached_rejectsAllWithoutCallingTheModelOrSink() {
        Map<String, Object> initData = stateMap(List.of(replace(beerBike.name(), karting.name())));
        initData.put(PlannerState.EDITS_LEFT, 0);

        Map<String, Object> update = node.apply(new PlannerState(initData));

        assertThat(sink.calls).isZero();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(update).doesNotContainKey(PlannerState.RESULT).doesNotContainKey(PlannerState.GENERATION_ID);
        assertThat(reportOf(update).rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.EDIT_LIMIT);
            assertThat(rejected.activityName()).isEqualTo(beerBike.name());
        });
        assertThat(update.get(PlannerState.EDITS)).isEqualTo(EXPECTED_CLEARED_EDITS);
        assertThat(messagesOf(update)).singleElement().satisfies(message ->
                assertThat(message.get("content")).asString().contains(beerBike.name()));
    }

    @Test
    void editorThrows_isInternal_previousResultKept() {
        PlanAssembler throwing = Mockito.spy(new PlanAssembler());
        Mockito.doThrow(new IllegalStateException("boom")).when(throwing)
                .assemble(any(), any(), any(), anyBoolean());
        ApplyEditsNode nodeWithFailingAssembler =
                new ApplyEditsNode(new PackageEditor(new PlanValidator(), throwing), refresher, sink);

        Map<String, Object> update = nodeWithFailingAssembler.apply(
                state(List.of(replace(beerBike.name(), karting.name()))));

        assertThat(sink.calls).isZero();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(update).doesNotContainKey(PlannerState.RESULT).doesNotContainKey(PlannerState.GENERATION_ID);
        assertThat(reportOf(update).rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL);
            assertThat(rejected.activityName()).isEqualTo(beerBike.name());
        });
        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
    }

    @Test
    void sinkThrows_isInternal_previousResultAndGenerationKept() {
        sink.nextFailure = new IllegalStateException("generation " + parentGenerationId + " is gone");
        queueRefresh("Now with karting");

        Map<String, Object> update = node.apply(state(List.of(replace(beerBike.name(), karting.name()))));

        // The save is the only thing that failed, and nothing reached the database: serving the edited plan
        // would show the organizer packages no generation holds, so the turn reports INTERNAL instead.
        assertThat(sink.calls).isEqualTo(1);
        assertThat(update).doesNotContainKey(PlannerState.RESULT).doesNotContainKey(PlannerState.GENERATION_ID);
        EditReport report = reportOf(update);
        assertThat(report.applied()).isEmpty();
        assertThat(report.rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL);
            assertThat(rejected.activityName()).isEqualTo(beerBike.name());
        });
        assertThat(update.get(PlannerState.EDITS)).isEqualTo(EXPECTED_CLEARED_EDITS);
        assertThat(update.get(PlannerState.ACTION)).isEqualTo(PlannerState.ACTION_NONE);
        assertThat(messagesOf(update)).singleElement().satisfies(message ->
                assertThat(message.get("content")).asString().contains(beerBike.name()));
    }

    @Test
    void sinkAnswersWithoutAnId_isInternal_ratherThanNull() {
        sink.answersWithNoId = true;
        queueRefresh("Now with karting");

        Map<String, Object> update = node.apply(state(List.of(replace(beerBike.name(), karting.name()))));

        assertThat(update).doesNotContainKey(PlannerState.RESULT).doesNotContainKey(PlannerState.GENERATION_ID);
        assertThat(reportOf(update).rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL));
    }

    @Test
    void withoutASinkBean_isInternal_ratherThanAFakedSave() {
        @SuppressWarnings("unchecked")
        ObjectProvider<GenerationEditSink> noBean = Mockito.mock(ObjectProvider.class);
        ApplyEditsNode nodeWithoutSink = new ApplyEditsNode(editor, refresher, noBean);
        queueRefresh("Now with karting");

        Map<String, Object> update = nodeWithoutSink.apply(state(List.of(replace(beerBike.name(), karting.name()))));

        assertThat(update).doesNotContainKey(PlannerState.RESULT).doesNotContainKey(PlannerState.GENERATION_ID);
        assertThat(reportOf(update).rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL));
    }

    @Test
    void withoutAParentGeneration_isInternal_andPersistsNothing() {
        Map<String, Object> initData = stateMap(List.of(replace(beerBike.name(), karting.name())));
        initData.remove(PlannerState.GENERATION_ID);

        Map<String, Object> update = node.apply(new PlannerState(initData));

        assertThat(sink.calls).isZero();
        assertThat(update).doesNotContainKey(PlannerState.RESULT);
        assertThat(reportOf(update).rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo(EditRejectionReason.INTERNAL));
    }

    @Test
    void withoutPackages_isNoPackagesYet() {
        Map<String, Object> initData = stateMap(List.of(replace(beerBike.name(), karting.name())));
        initData.remove(PlannerState.RESULT);

        Map<String, Object> update = node.apply(new PlannerState(initData));

        assertThat(sink.calls).isZero();
        assertThat(llm.refreshRequests).isEmpty();
        assertThat(reportOf(update).rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).isEqualTo(EditRejectionReason.NO_PACKAGES_YET));
    }

    private void queueRefresh(String description) {
        llm.queueRefresh(new TextRefreshResult(
                Map.of(Tier.BASIC, new PackageTexts(description, Map.of(), Map.of())), EXPECTED_REFRESH_USAGE));
    }

    private PlannerState state(List<EditRequest> edits) {
        return new PlannerState(stateMap(edits));
    }

    private Map<String, Object> stateMap(List<EditRequest> edits) {
        Map<String, Object> initData = new HashMap<>();
        initData.put(PlannerState.LOCALE, LOCALE);
        initData.put(PlannerState.DESTINATION_NAME, DESTINATION_NAME);
        initData.put(PlannerState.BRIEF, JsonCodec.write(brief));
        initData.put(PlannerState.CATALOG, JsonCodec.write(catalog));
        initData.put(PlannerState.RESULT, JsonCodec.write(plan()));
        initData.put(PlannerState.GENERATION_ID, parentGenerationId.toString());
        initData.put(PlannerState.EDITS, JsonCodec.write(edits));
        return initData;
    }

    private CatalogActivity activity(String name, String price, int durationMinutes) {
        CatalogActivity created = new CatalogActivity(UUID.randomUUID(), name.toLowerCase(Locale.ROOT).replace(' ', '-'),
                name, "one line", durationMinutes, true, new BigDecimal(price), null, "img", List.of("sightseeing"));
        catalog.add(created);
        return created;
    }

    /** One BASIC package over two days; day 1 is at the tier's two-item cap, day 2 has room. */
    private ComposedPlan plan() {
        PlanDraft.PackageDraft basic = new PlanDraft.PackageDraft(Tier.BASIC, "Basic", "tagline", "Old description",
                List.of(new PlanDraft.DayDraft(1, "Day 1", "Old day one", List.of(
                                item(Slot.MORNING, beerSpa), item(Slot.AFTERNOON, beerBike))),
                        new PlanDraft.DayDraft(2, "Day 2", "Old day two", List.of(item(Slot.MORNING, riverCruise)))));
        return assembler.assemble(new PlanDraft(List.of(basic)), brief, byId(), false).plan();
    }

    private Map<UUID, CatalogActivity> byId() {
        Map<UUID, CatalogActivity> index = new LinkedHashMap<>();
        for (CatalogActivity entry : catalog) {
            index.put(entry.id(), entry);
        }
        return index;
    }

    private static PlanDraft.ItemDraft item(Slot slot, CatalogActivity activity) {
        return new PlanDraft.ItemDraft(slot, "10:00", activity.id(), "why");
    }

    private static EditRequest add(String activity) {
        return new EditRequest(EditOp.ADD, activity, null, Tier.BASIC, null, null);
    }

    private static EditRequest replace(String activity, String replacement) {
        return new EditRequest(EditOp.REPLACE, activity, replacement, null, null, null);
    }

    private static EditReport reportOf(Map<String, Object> update) {
        return JsonCodec.read((String) update.get(PlannerState.EDIT_REPORT), EditReport.class);
    }

    private static String description(ComposedPlan plan) {
        return plan.packages().get(0).description();
    }

    private static List<String> namesIn(ComposedPlan plan) {
        return plan.packages().get(0).days().stream().flatMap(day -> day.items().stream())
                .map(ComposedPlan.ItemResult::name).toList();
    }

    private static String whyOf(ComposedPlan plan, UUID activityId) {
        return plan.packages().get(0).days().stream().flatMap(day -> day.items().stream())
                .filter(item -> item.activityId().equals(activityId)).findFirst().orElseThrow().why();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> messagesOf(Map<String, Object> update) {
        return (List<Map<String, String>>) update.get(PlannerState.MESSAGES);
    }
}
