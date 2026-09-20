package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.edit.AppliedEdit;
import com.myhive.backend.ai.edit.EditOp;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.model.DayEdge;
import com.myhive.backend.ai.model.Slot;
import com.myhive.backend.ai.model.Tier;
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
}
