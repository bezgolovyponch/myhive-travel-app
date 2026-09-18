package com.myhive.backend.ai.dto;

import com.myhive.backend.ai.edit.AppliedEdit;
import com.myhive.backend.ai.edit.EditReport;
import com.myhive.backend.ai.edit.RejectedEdit;
import com.myhive.backend.ai.graph.JsonCodec;
import com.myhive.backend.ai.graph.PlannerState;
import com.myhive.backend.ai.llm.ChatMessage;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.service.AiSessionService;
import com.myhive.backend.dto.VotePoolActivityDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.AiGeneration;
import com.myhive.backend.entity.AiGenerationKind;
import com.myhive.backend.entity.AiGenerationStatus;
import com.myhive.backend.entity.AiSession;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.util.Translations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Turns what {@link AiSessionService} hands back into the JSON of {@code docs/api/ai-planner-api.md}. */
@Component
@RequiredArgsConstructor
public class AiDtoMapper {

    /** Everything else is a transport hiccup the group can simply retry; an internal fault is not. */
    private static final String NON_RETRYABLE_ERROR_CODE = "INTERNAL";

    private final ActivityRepository activityRepository;

    public SessionStateDTO sessionState(AiSessionService.SessionView view) {
        AiSession session = view.session();
        PlannerState state = view.state();
        Brief brief = state.brief();
        return new SessionStateDTO(session.getToken(), session.getDestination().getSlug(), session.getLocale(),
                session.getStatus().name(), brief, brief.missingFields(), brief.isReady(),
                state.messages().stream().map(AiDtoMapper::message).toList(),
                // The session's own token, never the generation's lazy session proxy: a view is built
                // outside any transaction and initialising that proxy there would fail.
                view.latest().map(generation -> generation(generation, session.getToken())).orElse(null),
                view.latestReady().map(generation -> generation(generation, session.getToken())).orElse(null),
                firstTurnError(view),
                new SessionStateDTO.LimitsDTO(AiSessionService.MAX_MESSAGES - session.getMessageCount(),
                        AiSessionService.MAX_GENERATIONS - session.getGenerationCount(),
                        AiSessionService.MAX_EDITS_PER_SESSION - session.getEditCount()));
    }

    public TurnResponseDTO turn(AiSessionService.TurnOutcome outcome) {
        AiSessionService.SessionView view = outcome.view();
        Brief brief = view.state().brief();
        // The generation the turn produced, whichever kind it is: a queued one to poll, or the edited one
        // that is already READY. They are never both there, so the order only settles a case that cannot
        // happen, and the client reads the same field either way.
        AiGeneration generation = outcome.startedGeneration().or(outcome::editedGeneration).orElse(null);
        return new TurnResponseDTO(lastAssistantMessage(view.state()), brief, brief.missingFields(), brief.isReady(),
                generation == null ? null : generation(generation, view.session().getToken()),
                outcome.editReport()
                        .map(report -> edit(report, outcome.editedGeneration().map(AiGeneration::getId).orElse(null)))
                        .orElse(null));
    }

    /** For a generation loaded with its session attached; {@link #sessionState} uses the private overload. */
    public GenerationDTO generation(AiGeneration generation) {
        return generation(generation, generation.getSession().getToken());
    }

    /**
     * Resolves the picked activities into Trip Builder rows, in the order the package lists them.
     * Transactional because {@code destination} and {@code categories} are lazy, and localized in
     * place the same way the quiz pool is, so both surfaces name an activity identically.
     */
    @Transactional(readOnly = true)
    public List<VotePoolActivityDTO> tripItems(List<UUID> activityIds, String locale) {
        String lc = Translations.normalize(locale);
        Map<UUID, Activity> byId = new HashMap<>();
        for (Activity activity : activityRepository.findAllById(activityIds)) {
            byId.put(activity.getId(), activity);
        }
        return activityIds.stream()
                .map(byId::get)
                // An activity retired between composing the plan and picking it simply drops out; the
                // rest of the itinerary is still worth handing to the cart.
                .filter(Objects::nonNull)
                .map(activity -> tripItem(activity, lc))
                .toList();
    }

    private static SessionStateDTO.FirstTurnErrorDTO firstTurnError(AiSessionService.SessionView view) {
        return view.firstTurnErrorCode() == null
                ? null
                : new SessionStateDTO.FirstTurnErrorDTO(view.firstTurnErrorCode());
    }

    private GenerationDTO generation(AiGeneration generation, UUID sessionToken) {
        boolean ready = generation.getStatus() == AiGenerationStatus.READY;
        List<PackageDTO> packages = ready
                ? JsonCodec.read(generation.getResult(), ComposedPlan.class).packages().stream()
                        .map(AiDtoMapper::plannedPackage)
                        .toList()
                : null;
        return new GenerationDTO(generation.getId(), sessionToken, generation.getStatus().name(),
                generation.isDegraded(), generation.getSelectedPackageKey(),
                JsonCodec.read(generation.getBriefSnapshot(), Brief.class), packages, error(generation),
                generation.getCreatedAt(), generation.getFinishedAt(), generation.getKind().name(),
                generation.getParentId(), editReport(generation));
    }

    /** Only an {@code EDITED} row stores a report, and the only id that belongs in it is its own. */
    private static EditDTO editReport(AiGeneration generation) {
        if (generation.getKind() != AiGenerationKind.EDITED || generation.getEditReport() == null) {
            return null;
        }
        return edit(JsonCodec.read(generation.getEditReport(), EditReport.class), generation.getId());
    }

    private static EditDTO edit(EditReport report, UUID generationId) {
        return new EditDTO(generationId, report.applied().stream().map(AiDtoMapper::appliedEdit).toList(),
                report.rejected().stream().map(AiDtoMapper::rejectedEdit).toList(), report.tierRulesRelaxed(),
                report.textsRefreshed());
    }

    private static EditDTO.AppliedEditDTO appliedEdit(AppliedEdit applied) {
        return new EditDTO.AppliedEditDTO(name(applied.op()), applied.activityName(), applied.replacementName(),
                name(applied.packageKey()), applied.dayNumber(), name(applied.slot()));
    }

    private static EditDTO.RejectedEditDTO rejectedEdit(RejectedEdit rejected) {
        return new EditDTO.RejectedEditDTO(name(rejected.op()), rejected.activityName(), name(rejected.packageKey()),
                name(rejected.reason()), rejected.detail());
    }

    /** Most of these are optional somewhere in a report (an untargeted package, a removed item's slot). */
    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static GenerationDTO.ErrorDTO error(AiGeneration generation) {
        if (generation.getStatus() != AiGenerationStatus.FAILED || generation.getErrorCode() == null) {
            return null;
        }
        return new GenerationDTO.ErrorDTO(generation.getErrorCode(),
                !NON_RETRYABLE_ERROR_CODE.equals(generation.getErrorCode()));
    }

    private static PackageDTO plannedPackage(ComposedPlan.PackageResult result) {
        return new PackageDTO(result.key().name(), result.title(), result.tagline(), result.description(),
                result.pricePerPerson(), result.totalPrice(), result.currency(), result.totalDurationMinutes(),
                result.activityIds(), result.days().stream().map(AiDtoMapper::plannedDay).toList());
    }

    private static PackageDTO.DayDTO plannedDay(ComposedPlan.DayResult result) {
        return new PackageDTO.DayDTO(result.dayNumber(), result.title(), result.summary(),
                result.items().stream().map(AiDtoMapper::plannedItem).toList());
    }

    private static PackageDTO.ItemDTO plannedItem(ComposedPlan.ItemResult result) {
        return new PackageDTO.ItemDTO(result.slot() == null ? null : result.slot().name(), result.startHint(),
                result.activityId(), result.slug(), result.name(), result.imageUrl(), result.durationMinutes(),
                result.price(), result.minPrice(), result.lineTotal(), result.groupMinApplied(), result.why());
    }

    private static VotePoolActivityDTO tripItem(Activity activity, String lc) {
        List<String> categories = activity.getCategories().stream()
                .map(category -> Translations.pick(category.getTranslations(), lc, "name", category.getName()))
                .sorted()
                .toList();
        String destinationSlug = activity.getDestination() == null ? null : activity.getDestination().getSlug();
        Map<String, Map<String, String>> translations = activity.getTranslations();
        return new VotePoolActivityDTO(activity.getId(),
                Translations.pick(translations, lc, "name", activity.getName()),
                activity.getPrice(), activity.getMinPrice(), activity.getImageUrl(),
                activity.getSlug(), destinationSlug, categories,
                Translations.pick(translations, lc, "description", activity.getDescription()),
                activity.getDuration());
    }

    private static MessageDTO lastAssistantMessage(PlannerState state) {
        List<ChatMessage> messages = state.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (ChatMessage.ASSISTANT.equals(messages.get(i).role())) {
                return message(messages.get(i));
            }
        }
        return null;
    }

    private static MessageDTO message(ChatMessage message) {
        return new MessageDTO(message.role(), message.content(), message.at());
    }
}
