package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.plan.Violation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            - Use exact catalog names when the activity exists.
            - Something NOT in the catalog: still emit the edit with the organizer's own words as "activity" - never drop it silently, never substitute another activity - and put the 1-3 catalog names closest in spirit into "alternatives" (empty when the activity is in the catalog); the system tells them it is not on offer and suggests those.
            - When the organizer picks one of the alternatives from an earlier reply ("the first one", "yes, add it"), emit it with the exact catalog name against the current packages above: an ADD when the activity they wanted out is already gone, a REPLACE only when it is still listed. Scope it to the package the earlier line says that activity was dropped from (packageKey), unless they name a package or say everywhere.
            - One edit per request: "remove X and add Y" is two edits, even if Y does not exist.
            - packageKey null means every package; set it only if the organizer names a package.
            - Reply with one short sentence saying you are doing it now; never claim it is done.
            - Changes of trip length, group size, vibe or budget go into the brief as before, not into edits.
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PromptTemplate chatSystem = new PromptTemplate(new ClassPathResource("prompts/ai/chat-system.st"));
    private final PromptTemplate plannerSystem = new PromptTemplate(new ClassPathResource("prompts/ai/planner-system.st"));
    private final PromptTemplate repairUser = new PromptTemplate(new ClassPathResource("prompts/ai/repair-user.st"));
    private final PromptTemplate textRefreshSystem = new PromptTemplate(new ClassPathResource("prompts/ai/text-refresh-system.st"));
    private final PromptTemplate textRefreshUser = new PromptTemplate(new ClassPathResource("prompts/ai/text-refresh-user.st"));
    private final PromptTemplate planTextsSystem = new PromptTemplate(new ClassPathResource("prompts/ai/plan-texts-system.st"));

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
                "departure", r.brief().departureOrDefault().slot().name(),
                "dayWindows", dayWindows(r.brief())));
    }

    /**
     * "day 1: EVENING, NIGHT; day 2: MORNING, ...; day 3: MORNING" - taken from the validator's own window
     * so the prompt can never promise a slot that validation then rejects as SLOT_OUTSIDE_WINDOW.
     */
    static String dayWindows(Brief brief) {
        if (brief.days() == null) {
            return "-";
        }
        List<String> days = new ArrayList<>();
        for (int n = 1; n <= brief.days(); n++) {
            String slots = PlanValidator.allowedSlots(n, brief).stream().map(Enum::name)
                    .collect(Collectors.joining(", "));
            days.add("day " + n + ": " + slots);
        }
        return String.join("; ", days);
    }

    public String plannerUser(PlanRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("BRIEF: ").append(json(r.brief())).append("\n\n");
        sb.append("RECENT CONVERSATION:\n");
        r.recentHistory().stream().skip(Math.max(0, r.recentHistory().size() - HISTORY_FOR_PLANNER))
                .forEach(m -> sb.append(m.role()).append(": ").append(wrapUser(m.content())).append('\n'));
        sb.append("\nCATALOG (activityId | name | duration | price per person | group minimum | categories | about | includes):\n");
        List<CatalogActivity> catalog = r.catalog();
        for (int i = 0; i < catalog.size(); i++) {
            CatalogActivity a = catalog.get(i);
            sb.append(ActivityAliases.of(i)).append(" | ").append(a.name()).append(" | ").append(a.durationMinutes()).append(" min | ")
                    .append(a.price()).append(" EUR pp | min ").append(a.minPrice() == null ? "-" : a.minPrice())
                    .append(" | ").append(String.join(",", a.categorySlugs())).append(" | ").append(a.oneLine())
                    .append(" | ").append(a.includes() == null ? "-" : a.includes()).append('\n');
        }
        return sb.toString();
    }

    public String repairUser(RepairRequest r) {
        String violations = r.violations().stream()
                .map(v -> "- [" + v.code() + "] " + (v.packageKey() == null ? "" : v.packageKey() + " ")
                        + (v.dayNumber() == null ? "" : "day " + v.dayNumber() + " ") + v.detail())
                .collect(Collectors.joining("\n"));
        return repairUser.render(Map.of("violations", violations,
                "draftJson", json(aliased(r.draft(), r.original().catalog()))));
    }

    /**
     * The draft quoted back with the codes the model wrote, not the UUIDs the parser turned them into:
     * a code is what the model knows the activity as, and what it has to answer with. Structure only,
     * like the compose answer - the texts are not the planner's business.
     */
    private static Map<String, Object> aliased(PlanDraft draft, List<CatalogActivity> catalog) {
        Map<UUID, String> aliases = ActivityAliases.byId(catalog);
        List<Map<String, Object>> packages = new ArrayList<>();
        for (PlanDraft.PackageDraft p : draft.packages()) {
            List<Map<String, Object>> days = new ArrayList<>();
            for (PlanDraft.DayDraft day : p.days()) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (PlanDraft.ItemDraft item : day.items()) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("slot", item.slot());
                    out.put("startHint", item.startHint());
                    out.put("activityId", item.activityId() == null ? null
                            : aliases.getOrDefault(item.activityId(), item.activityId().toString()));
                    items.add(out);
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("dayNumber", day.dayNumber());
                out.put("items", items);
                days.add(out);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("key", p.key());
            out.put("days", days);
            packages.add(out);
        }
        return Map.of("packages", packages);
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

    public String planTextsSystem(PlanTextsRequest r) {
        return planTextsSystem.render(Map.of(
                "destinationName", r.destinationName(),
                "locale", r.locale(),
                "days", String.valueOf(r.brief().days()),
                "groupSize", String.valueOf(r.brief().groupSize())));
    }

    /**
     * The brief, then the package: its price per person and every day with its items as
     * "- SLOT code name | duration | about | includes: ...", so the copy can say what an activity is instead of
     * repeating its name. Built in code like {@link #plannerUser}: it is all data, no prose.
     */
    public String planTextsUser(PlanTextsRequest r) {
        Map<UUID, String> aliases = ActivityAliases.byId(r.catalog());
        Map<UUID, CatalogActivity> byId = new LinkedHashMap<>();
        for (CatalogActivity activity : r.catalog()) {
            byId.put(activity.id(), activity);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("BRIEF: ").append(json(r.brief())).append("\n\n");
        for (ComposedPlan.PackageResult p : r.plan().packages()) {
            sb.append("PACKAGE ").append(p.key()).append(" (").append(p.pricePerPerson()).append(" EUR per person)\n");
            for (ComposedPlan.DayResult day : p.days()) {
                sb.append("DAY ").append(day.dayNumber()).append('\n');
                for (ComposedPlan.ItemResult item : day.items()) {
                    CatalogActivity activity = byId.get(item.activityId());
                    sb.append("- ").append(item.slot()).append(' ')
                            .append(aliases.getOrDefault(item.activityId(), String.valueOf(item.activityId()))).append(' ')
                            .append(item.name()).append(" | ").append(item.durationMinutes()).append(" min");
                    if (activity != null) {
                        sb.append(" | ").append(activity.oneLine());
                        if (activity.includes() != null) {
                            sb.append(" | includes: ").append(activity.includes());
                        }
                    }
                    sb.append('\n');
                }
            }
            sb.append('\n');
        }
        return sb.toString().strip();
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
