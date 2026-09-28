package com.myhive.backend.ai.llm;

import com.myhive.backend.ai.catalog.CatalogActivity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The codes activities go by in the planner and texts prompts: {@code A1}, {@code A2}, ... by position
 * in the catalog snapshot. A UUID costs the model about 25 tokens every time it writes one, and a plan
 * names an activity twenty-odd times; a code costs two. The prompts hand the codes out and the parser
 * turns them back into ids before anything else sees them, so the rest of the planner never meets one.
 */
public final class ActivityAliases {

    private static final String PREFIX = "A";

    private ActivityAliases() {
    }

    public static String of(int index) {
        return PREFIX + (index + 1);
    }

    /** id -> code, in catalog order. */
    public static Map<UUID, String> byId(List<CatalogActivity> catalog) {
        Map<UUID, String> aliases = new LinkedHashMap<>();
        for (int i = 0; i < catalog.size(); i++) {
            aliases.put(catalog.get(i).id(), of(i));
        }
        return aliases;
    }

    /** code -> id, in catalog order; the lookup side of {@link #byId}. */
    public static Map<String, UUID> toId(List<CatalogActivity> catalog) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (int i = 0; i < catalog.size(); i++) {
            ids.put(of(i), catalog.get(i).id());
        }
        return ids;
    }

    /** The code as the model wrote it, normalised the way {@link #of} spells it ("a12 " reads as A12). */
    public static String normalise(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }
}
