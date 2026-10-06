package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.catalog.CatalogPreset;
import com.myhive.backend.ai.model.Brief;
import com.myhive.backend.ai.plan.ComposedPlan;
import com.myhive.backend.ai.plan.PlanAssembler;
import com.myhive.backend.ai.plan.PlanDraft;
import com.myhive.backend.ai.plan.PlanPricer;
import com.myhive.backend.ai.plan.PlanValidator;
import com.myhive.backend.ai.plan.Violation;
import java.math.BigDecimal;
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
    /**
     * Before the first generation: what is on offer, so a follow-up can name the real variants of what the
     * organizer picked ("with a show or on the boat?") instead of inventing them. No edit rules here -
     * there is nothing to edit yet.
     */
    private static final String OFFER_BLOCK = """
            Catalog activity names (the only things you may offer or name): %s
            """;

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
            - The system checks every edit and then says itself what changed and what could not be done, so the reply never announces or confirms an edit ("Swapping X for Y now" is wrong - it may not be possible): keep it to a short neutral line like "Let me check that." plus anything else worth saying.
            - Changes of trip length, group size, vibe or budget go into the brief as before, not into edits.
            """;

    /**
     * Only rendered when the destination has ready-made packages: without them the model composes freely,
     * and rules about a list that is not there would only confuse it. In code for the same reason as the
     * blocks above - the template engine has no conditionals.
     */
    private static final String PRESET_RULES = """

            READY-MADE PACKAGES are listed after the catalog, each made for one tier. They are the starting point:
            - For every tier pick the ready-made package of that tier that fits the brief best and use its activities. Do not mix two ready-made packages into one tier.
            - Change it only where the brief or the conversation asks for it: take out what they dislike, add or swap in what they asked for by name or by kind.
            - A tier has exactly as many activities as its ready-made package unless the organizer asked for a specific extra one. Never add one to fill a free slot or an empty day - free time is part of the trip, and a last day with nothing on it is fine.
            - What the brief says in general ("good food", "beer", "a big night out", "adrenaline") decides WHICH ready-made package you pick. It is not a request to add activities to it.
            - A tier's price per person stays within 15 percent of its ready-made package's price unless the organizer asked for more or less.
            - Place its own activities across the days so every day stays inside that tier's per-day limits below: what does not fit one day moves to another day, and is never left out while another day still has room.
            - Its activities are listed most important first. Only if all the days together cannot hold them, leave out from the end of the list.
            - A tier with no ready-made package is composed from the catalog as usual.
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
                "dayWindows", dayWindows(r.brief()),
                "maxTierSpread", String.valueOf(PlanAssembler.MAX_TIER_SPREAD),
                "presetRules", r.presets().isEmpty() ? "" : PRESET_RULES.stripTrailing()));
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
        appendPresets(sb, r);
        return sb.toString();
    }

    /**
     * "BASIC | Classic Stag: Essential | 95.00 EUR pp | A3, A9, A12" - the price is what the package costs
     * this group per person, group minimums included, so the model has the same number the assembler will
     * arrive at. Without a group size it is the plain sum of the per-person prices.
     */
    private static void appendPresets(StringBuilder sb, PlanRequest r) {
        if (r.presets().isEmpty()) {
            return;
        }
        Map<UUID, String> aliases = ActivityAliases.byId(r.catalog());
        Map<UUID, CatalogActivity> byId = new LinkedHashMap<>();
        for (CatalogActivity activity : r.catalog()) {
            byId.put(activity.id(), activity);
        }
        int travelers = r.brief().groupSize() == null ? 1 : r.brief().groupSize();
        sb.append("\nREADY-MADE PACKAGES (tier | name | price per person | number of activities | activities, most important first):\n");
        for (CatalogPreset preset : r.presets()) {
            BigDecimal total = BigDecimal.ZERO;
            List<String> codes = new ArrayList<>();
            for (UUID id : preset.activityIds()) {
                CatalogActivity a = byId.get(id);
                if (a == null) {
                    continue;
                }
                total = total.add(PlanPricer.lineTotal(a.price(), a.minPrice(), travelers));
                codes.add(aliases.get(id));
            }
            sb.append(preset.tier()).append(" | ").append(preset.name()).append(" | ")
                    .append(PlanPricer.perPerson(total, travelers)).append(" EUR pp | ")
                    .append(codes.size()).append(" | ").append(String.join(", ", codes)).append('\n');
        }
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

    /**
     * One block per edited package: what it now holds - names included, since an edit can leave the title
     * or a day title naming an activity that is gone - then the exact list of texts to rewrite for it.
     */
    public String textRefreshUser(TextRefreshRequest r) {
        StringBuilder sb = new StringBuilder();
        for (ComposedPlan.PackageResult p : r.packages()) {
            sb.append("PACKAGE ").append(p.key()).append(" \"").append(orDash(p.title())).append("\"\n");
            sb.append("TAGLINE ").append(orDash(p.tagline())).append('\n');
            sb.append(orDash(p.description())).append('\n');
            for (ComposedPlan.DayResult day : p.days()) {
                sb.append("DAY ").append(day.dayNumber()).append(" \"").append(orDash(day.title())).append("\" (")
                        .append(orDash(day.summary())).append(")\n");
                for (ComposedPlan.ItemResult item : day.items()) {
                    sb.append("- ").append(item.slot()).append(' ').append(item.activityId()).append(' ')
                            .append(item.name()).append('\n');
                }
            }
            sb.append("REWRITE: title; tagline; description; why for ").append(joinIds(r.newActivityIdsOf(p.key())))
                    .append("; dayTitle and summary for days ").append(joinDays(r.touchedDaysOf(p.key())))
                    .append("\n\n");
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

    /** Nothing to edit, nothing to say about editing: a turn before the first generation only sees the offer. */
    private static String packagesBlock(ChatTurnRequest r) {
        if (r.packagesView() == null || r.packagesView().isBlank()) {
            return r.catalogNames().isEmpty() ? "" : OFFER_BLOCK.formatted(String.join(", ", r.catalogNames()));
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
