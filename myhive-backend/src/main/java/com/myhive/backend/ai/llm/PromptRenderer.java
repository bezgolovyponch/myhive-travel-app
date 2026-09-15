package com.myhive.backend.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myhive.backend.ai.catalog.CatalogActivity;
import com.myhive.backend.ai.plan.Violation;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/** Renders the three prompt templates plus the planner-user text that is built in code, not from a resource file. */
@Component
public class PromptRenderer {

    private static final int HISTORY_FOR_PLANNER = 10;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PromptTemplate chatSystem = new PromptTemplate(new ClassPathResource("prompts/ai/chat-system.st"));
    private final PromptTemplate plannerSystem = new PromptTemplate(new ClassPathResource("prompts/ai/planner-system.st"));
    private final PromptTemplate repairUser = new PromptTemplate(new ClassPathResource("prompts/ai/repair-user.st"));

    public String chatSystem(ChatTurnRequest r) {
        return chatSystem.render(Map.of(
                "destinationName", r.destinationName(),
                "locale", r.locale(),
                "briefJson", json(r.brief()),
                "categorySlugs", String.join(", ", r.categorySlugs())));
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

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize prompt data", e);
        }
    }
}
