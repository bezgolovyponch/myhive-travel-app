package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.Violation;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Renders the prompt templates plus the user texts that are built in code, not from a resource file. */
@Component
public class PromptRenderer {

    private static final int HISTORY_FOR_PLANNER = 10;

    /**
     * Only rendered once packages exist: before that the model has nothing to edit, and the rules would
     * only tempt it to invent activities. Kept here rather than in the template because the template
     * engine has no conditionals.
     */
    private static final String PACKAGES_BLOCK = """
            Current packages (the organizer can change them):
            %s
            Catalog activity names (use these exact names in edits): %s
            Edit rules:
            - If the organizer asks to add, remove or swap a specific activity, put it in "edits" and do not change the brief for it.
            - Use exact catalog names. packageKey null means every package; set it only if the organizer names a package.
            - Reply with one short sentence saying you are doing it now; never claim it is done.
            - Changes of trip length, group size, vibe or budget go into the brief as before, not into edits.
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PromptTemplate chatSystem = new PromptTemplate(new ClassPathResource("prompts/ai/chat-system.st"));
    private final PromptTemplate plannerSystem = new PromptTemplate(new ClassPathResource("prompts/ai/planner-system.st"));
    private final PromptTemplate repairUser = new PromptTemplate(new ClassPathResource("prompts/ai/repair-user.st"));
    private final PromptTemplate textRefreshSystem = new PromptTemplate(new ClassPathResource("prompts/ai/text-refresh-system.st"));
    private final PromptTemplate textRefreshUser = new PromptTemplate(new ClassPathResource("prompts/ai/text-refresh-user.st"));

    public String chatSystem(ChatTurnRequest r) {
        return chatSystem.render(Map.of(
                "destinationName", r.destinationName(),
                "locale", r.locale(),
                "briefJson", json(r.brief()),
                "categorySlugs", String.join(", ", r.categorySlugs()),
                "packagesBlock", packagesBlock(r)));
    }

    /** History is passed as real chat messages by the gateway; the latest user message is wrapped as data. */
    public String wrapUser(String content) {
        return "<user>" + content.replace("<", "&lt;") + "</user>";
    }

    public String plannerSystem(PlanRequest r) {
        return plannerSystem.render(Map.of(
                "destinationName", r.destinationName(),
                "locale", r.locale(),
                "days", String.valueOf(r.brief().days()),
                "groupSize", String.valueOf(r.brief().groupSize()),
                "arrival", r.brief().arrivalOrDefault().slot().name(),
                "departure", r.brief().departureOrDefault().slot().name()));
    }

    public String plannerUser(PlanRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("BRIEF: ").append(json(r.brief())).append("\n\n");
        sb.append("RECENT CONVERSATION:\n");
        r.recentHistory().stream().skip(Math.max(0, r.recentHistory().size() - HISTORY_FOR_PLANNER))
                .forEach(m -> sb.append(m.role()).append(": ").append(wrapUser(m.content())).append('\n'));
        sb.append("\nCATALOG (id | name | duration | price per person | group minimum | categories | about):\n");
        for (CatalogActivity a : r.catalog()) {
            sb.append(a.id()).append(" | ").append(a.name()).append(" | ").append(a.durationMinutes()).append(" min | ")
                    .append(a.price()).append(" EUR pp | min ").append(a.minPrice() == null ? "-" : a.minPrice())
                    .append(" | ").append(String.join(",", a.categorySlugs())).append(" | ").append(a.oneLine()).append('\n');
        }
        return sb.toString();
    }

    public String repairUser(RepairRequest r) {
        String violations = r.violations().stream()
                .map(v -> "- [" + v.code() + "] " + (v.packageKey() == null ? "" : v.packageKey() + " ")
                        + (v.dayNumber() == null ? "" : "day " + v.dayNumber() + " ") + v.detail())
                .collect(Collectors.joining("\n"));
        return repairUser.render(Map.of("violations", violations, "draftJson", json(r.draft())));
    }

    public String textRefreshSystem(TextRefreshRequest r) {
        return textRefreshSystem.render(Map.of(
                "destinationName", r.destinationName(),
                "locale", r.locale()));
    }

    /** One block per edited package: what it now holds, then the exact list of texts to rewrite for it. */
    public String textRefreshUser(TextRefreshRequest r) {
        StringBuilder sb = new StringBuilder();
        for (ComposedPlan.PackageResult p : r.packages()) {
            sb.append("PACKAGE ").append(p.key()).append(" \"").append(orDash(p.title())).append("\"\n");
            sb.append(orDash(p.description())).append('\n');
            for (ComposedPlan.DayResult day : p.days()) {
                sb.append("DAY ").append(day.dayNumber()).append(" (").append(orDash(day.summary())).append(")\n");
                for (ComposedPlan.ItemResult item : day.items()) {
                    sb.append("- ").append(item.slot()).append(' ').append(item.activityId()).append(' ')
                            .append(item.name()).append('\n');
                }
            }
            sb.append("REWRITE: description; why for ").append(joinIds(r.newActivityIdsOf(p.key())))
                    .append("; summary for days ").append(joinDays(r.touchedDaysOf(p.key()))).append("\n\n");
        }
        return textRefreshUser.render(Map.of("packages", sb.toString().strip()));
    }

    /** Nothing to edit, nothing to say about editing: a turn before the first generation gets no block. */
    private static String packagesBlock(ChatTurnRequest r) {
        if (r.packagesView() == null || r.packagesView().isBlank()) {
            return "";
        }
        return PACKAGES_BLOCK.formatted(r.packagesView(), String.join(", ", r.catalogNames()));
    }

    /** Package texts are nullable on a ComposedPlan; a dash tells the model "nothing there yet", "null" does not. */
    private static String orDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String joinIds(Set<UUID> ids) {
        return ids.isEmpty() ? "-" : ids.stream().map(UUID::toString).collect(Collectors.joining(", "));
    }

    private static String joinDays(Set<Integer> days) {
        return days.isEmpty() ? "-" : days.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize prompt data", e);
        }
    }
}
