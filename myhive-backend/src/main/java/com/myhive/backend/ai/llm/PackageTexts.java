package com.myhive.backend.ai.llm;

import java.util.Map;
import java.util.UUID;

/**
 * The copy of one package as the model wrote it. Every field is optional: what the model left out keeps
 * its previous text (or its placeholder), so a partial answer is still useful. The first write after a
 * generation fills all six; the post-edit refresh only ever fills {@code description},
 * {@code whyByActivityId} and {@code summaryByDay}.
 */
public record PackageTexts(String title, String tagline, String description, Map<Integer, String> titleByDay,
                           Map<UUID, String> whyByActivityId, Map<Integer, String> summaryByDay) {

    public PackageTexts {
        titleByDay = titleByDay == null ? Map.of() : Map.copyOf(titleByDay);
        whyByActivityId = whyByActivityId == null ? Map.of() : Map.copyOf(whyByActivityId);
        summaryByDay = summaryByDay == null ? Map.of() : Map.copyOf(summaryByDay);
    }

    /** The shape the post-edit refresh writes: description, whys and day summaries only. */
    public PackageTexts(String description, Map<UUID, String> whyByActivityId, Map<Integer, String> summaryByDay) {
        this(null, null, description, Map.of(), whyByActivityId, summaryByDay);
    }
}
