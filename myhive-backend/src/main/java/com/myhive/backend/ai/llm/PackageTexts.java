package com.myhive.backend.ai.llm;

import java.util.Map;
import java.util.UUID;

/**
 * The rewritten copy of one package. Every field is optional: what the model left out keeps its previous
 * text, so a partial answer is still useful.
 */
public record PackageTexts(String description, Map<UUID, String> whyByActivityId, Map<Integer, String> summaryByDay) {

    public PackageTexts {
        whyByActivityId = whyByActivityId == null ? Map.of() : Map.copyOf(whyByActivityId);
        summaryByDay = summaryByDay == null ? Map.of() : Map.copyOf(summaryByDay);
    }
}
