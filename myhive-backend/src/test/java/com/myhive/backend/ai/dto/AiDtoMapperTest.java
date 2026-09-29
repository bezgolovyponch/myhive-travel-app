package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.edit.AppliedEdit;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditRejectionReason;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.RejectedEdit;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
import com.myhive.backend.ai.plan.AttemptDiagnostic;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationKind;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.repository.ActivityRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The generation mapping on its own, which is where a stored blob turns into API JSON. The edit report is
 * the only field here that is parsed rather than read off a column, so it is the only one that can fail.
 */
class AiDtoMapperTest {

    private static final int DURATION_MINUTES = 90;

    private final AiDtoMapper mapper = new AiDtoMapper(mock(ActivityRepository.class));

    @Test
    void generation_ofAnEditedRow_carriesItsKindParentAndOwnReport() {
        UUID expectedParentId = UUID.randomUUID();
        String expectedActivity = "Beer Bike";
        AiGeneration generation = editedGeneration(expectedParentId, JsonCodec.write(new EditReport(
                List.of(new AppliedEdit(EditOp.REPLACE, expectedActivity, "Club Crawl", Tier.BASIC, 1,
                        Slot.AFTERNOON, UUID.randomUUID())),
                List.of(), true, true)));

        GenerationDTO dto = mapper.generation(generation);

        assertThat(dto.kind()).isEqualTo(AiGenerationKind.EDITED.name());
        assertThat(dto.parentId()).isEqualTo(expectedParentId);
        assertThat(dto.editReport()).isNotNull();
        // The report is about this row, whatever the id of the one it was derived from.
        assertThat(dto.editReport().generationId()).isEqualTo(generation.getId());
        assertThat(dto.editReport().applied()).singleElement().satisfies(applied -> {
            assertThat(applied.op()).isEqualTo(EditOp.REPLACE.name());
            assertThat(applied.activity()).isEqualTo(expectedActivity);
            assertThat(applied.packageKey()).isEqualTo(Tier.BASIC.name());
            assertThat(applied.slot()).isEqualTo(Slot.AFTERNOON.name());
        });
        assertThat(dto.editReport().tierRulesRelaxed()).isTrue();
    }

    /**
     * This mapping is on the path of both the generation read and the session read, so a blob that will
     * not parse must cost the report and nothing else - never the packages the group is looking at.
     */
    @Test
    void generation_withACorruptEditReport_dropsTheReportAndKeepsThePackages() {
        AiGeneration generation = editedGeneration(UUID.randomUUID(), "{not even json");

        GenerationDTO dto = mapper.generation(generation);

        assertThat(dto.editReport()).isNull();
        assertThat(dto.kind()).isEqualTo(AiGenerationKind.EDITED.name());
        assertThat(dto.status()).isEqualTo(AiGenerationStatus.READY.name());
        assertThat(dto.packages()).singleElement()
                .satisfies(result -> assertThat(result.key()).isEqualTo(Tier.BASIC.name()));
    }

    /**
     * The realistic version of the same thing: a rejection reason that shipped in a later release, read
     * back by a rolled-back backend whose enum does not have it yet.
     */
    @Test
    void generation_withAnUnknownRejectionReason_dropsTheReportAndKeepsThePackages() {
        String reportFromTheFuture = """
                {"applied":[],"rejected":[{"op":"REMOVE","activityName":"Beer Bike","packageKey":"BASIC",
                "reason":"SOMETHING_NEW","detail":null}],"tierRulesRelaxed":false,"textsRefreshed":false}""";
        AiGeneration generation = editedGeneration(UUID.randomUUID(), reportFromTheFuture);

        GenerationDTO dto = mapper.generation(generation);

        assertThat(dto.editReport()).isNull();
        assertThat(dto.packages()).hasSize(1);
        assertThat(dto.brief().groupSize()).isEqualTo(brief().groupSize());
    }

    /** The offer the chat line makes is also served as data, so a client can render it as quick replies. */
    @Test
    void generation_servesTheAlternativesOfAnUnknownActivity() {
        List<String> expectedAlternatives = List.of("Night Club", "Beer Bike");
        RejectedEdit unknown = new RejectedEdit(EditOp.ADD, "strip shows", null, EditRejectionReason.UNKNOWN_ACTIVITY,
                "strip shows", expectedAlternatives);
        AiGeneration generation = editedGeneration(UUID.randomUUID(), JsonCodec.write(
                new EditReport(List.of(), List.of(unknown), false, false)));

        GenerationDTO dto = mapper.generation(generation);

        assertThat(dto.editReport().rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.reason()).isEqualTo(EditRejectionReason.UNKNOWN_ACTIVITY.name());
            assertThat(rejected.alternatives()).isEqualTo(expectedAlternatives);
        });
    }

    @Test
    void generation_ofAGeneratedRow_hasNoParentAndNoReport() {
        AiGeneration generation = editedGeneration(UUID.randomUUID(), JsonCodec.write(
                new EditReport(List.of(), List.of(), false, false)));
        generation.setKind(AiGenerationKind.GENERATED);
        generation.setParentId(null);

        GenerationDTO dto = mapper.generation(generation);

        assertThat(dto.kind()).isEqualTo(AiGenerationKind.GENERATED.name());
        assertThat(dto.parentId()).isNull();
        // The column is never written on a GENERATED row; a stray value is not served either way.
        assertThat(dto.editReport()).isNull();
    }

    /** The skeleton window: a RUNNING row that already holds packages serves them, flagged as texts pending. */
    @Test
    void generation_runningWithPackagesPublished_servesThemAsTextsPending() {
        AiGeneration generation = editedGeneration(null, null);
        generation.setKind(AiGenerationKind.GENERATED);
        generation.setStatus(AiGenerationStatus.RUNNING);

        GenerationDTO dto = mapper.generation(generation);

        assertThat(dto.status()).isEqualTo(AiGenerationStatus.RUNNING.name());
        assertThat(dto.textsPending()).isTrue();
        assertThat(dto.packages()).singleElement()
                .satisfies(result -> assertThat(result.key()).isEqualTo(Tier.BASIC.name()));
    }

    @Test
    void generation_runningWithoutPackagesYet_servesNoneAndIsNotTextsPending() {
        AiGeneration generation = editedGeneration(null, null);
        generation.setKind(AiGenerationKind.GENERATED);
        generation.setStatus(AiGenerationStatus.RUNNING);
        generation.setResult(null);

        GenerationDTO dto = mapper.generation(generation);

        assertThat(dto.packages()).isNull();
        assertThat(dto.textsPending()).isFalse();
    }

    @Test
    void generation_ready_isNeverTextsPending() {
        GenerationDTO dto = mapper.generation(editedGeneration(null, null));

        assertThat(dto.textsPending()).isFalse();
        assertThat(dto.packages()).hasSize(1);
    }

    private static AiGeneration editedGeneration(UUID parentId, String editReport) {
        AiSession session = new AiSession();
        session.setToken(UUID.randomUUID());
        AiGeneration generation = new AiGeneration();
        generation.setId(UUID.randomUUID());
        generation.setSession(session);
        generation.setKind(AiGenerationKind.EDITED);
        generation.setParentId(parentId);
        generation.setStatus(AiGenerationStatus.READY);
        generation.setBriefSnapshot(JsonCodec.write(brief()));
        generation.setResult(JsonCodec.write(plan()));
        generation.setEditReport(editReport);
        return generation;
    }

    private static Brief brief() {
        return new Brief(1, 4, List.of("nightlife"), null, null, null, DayEdge.AFTERNOON, DayEdge.EVENING, null);
    }

    private static ComposedPlan plan() {
        UUID activityId = UUID.randomUUID();
        ComposedPlan.ItemResult item = new ComposedPlan.ItemResult(Slot.AFTERNOON, null, activityId, "club-crawl",
                "Club Crawl", "https://example.com/club-crawl.jpg", DURATION_MINUTES, new BigDecimal("40.00"), null,
                new BigDecimal("160.00"), false, "why");
        ComposedPlan.DayResult day = new ComposedPlan.DayResult(1, "Day one", "summary", List.of(item));
        return new ComposedPlan(List.of(new ComposedPlan.PackageResult(Tier.BASIC, "Night out", "tag", "desc",
                new BigDecimal("40.00"), new BigDecimal("160.00"), ComposedPlan.CURRENCY, DURATION_MINUTES,
                List.of(activityId), List.of(day))), false);
    }


    @Test
    void generation_servesDiagnosticsToStaffOnly() {
        String expectedViolation = "SLOT_OUTSIDE_WINDOW BASIC d1: slot MORNING is outside the arrival/departure window on day 1";
        AiGeneration generation = editedGeneration(null, null);
        generation.setDiagnostics(JsonCodec.write(List.of(
                new AttemptDiagnostic(0, "LLM_INVALID_OUTPUT", List.of()),
                new AttemptDiagnostic(1, null, List.of(expectedViolation)))));
        AiDtoMapper staffMapper = new AiDtoMapper(mock(ActivityRepository.class), () -> true);

        GenerationDTO forStaff = staffMapper.generation(generation);
        GenerationDTO forPublic = mapper.generation(generation);

        assertThat(forStaff.diagnostics()).extracting(AttemptDiagnostic::errorCode)
                .containsExactly("LLM_INVALID_OUTPUT", null);
        assertThat(forStaff.diagnostics().get(1).violations()).containsExactly(expectedViolation);
        assertThat(forPublic.diagnostics()).isNull();
    }
}
