package com.myhive.backend.util;

import com.myhive.backend.dto.ActivityExtraDTO;
import com.myhive.backend.entity.ActivityExtra;
import com.myhive.backend.exception.BadRequestException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Validation, normalisation, localisation and (de)serialisation of an activity's extras. */
public final class ActivityExtras {

    public static final int MAX_PER_ACTIVITY = 10;

    private static final int MAX_NAME = 80;
    private static final int MAX_DESCRIPTION = 300;
    private static final int MAX_CODE = 40;
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ActivityExtras() {
    }

    /** Trims, derives codes, checks limits and fixes price scale; throws {@link IllegalArgumentException}. */
    public static List<ActivityExtra> normalize(List<ActivityExtra> extras) {
        if (extras == null) {
            return null;
        }
        if (extras.size() > MAX_PER_ACTIVITY) {
            throw new IllegalArgumentException("an activity can have at most " + MAX_PER_ACTIVITY + " extras");
        }
        List<ActivityExtra> result = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (ActivityExtra extra : extras) {
            ActivityExtra normalized = normalizeOne(extra);
            if (!codes.add(normalized.code())) {
                throw new IllegalArgumentException("duplicate extra code '" + normalized.code() + "'");
            }
            result.add(normalized);
        }
        return result;
    }

    private static ActivityExtra normalizeOne(ActivityExtra extra) {
        if (extra == null) {
            throw new IllegalArgumentException("extra must not be null");
        }
        String name = trimToNull(extra.name());
        if (name == null) {
            throw new IllegalArgumentException("extra name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("extra name must be at most " + MAX_NAME + " characters");
        }
        String description = trimToNull(extra.description());
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("extra description must be at most " + MAX_DESCRIPTION + " characters");
        }
        String rawCode = trimToNull(extra.code());
        String code = slugOf(rawCode == null ? name : rawCode);
        if (code.length() > MAX_CODE) {
            throw new IllegalArgumentException("extra code must be at most " + MAX_CODE + " characters");
        }
        BigDecimal price = extra.price();
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("extra '" + code + "' needs a price of zero or more");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("extra '" + code + "' price can have at most 2 decimals");
        }
        if (extra.pricing() == null) {
            throw new IllegalArgumentException("extra '" + code + "' needs a pricing mode (PER_GROUP or PER_PERSON)");
        }
        Map<String, Map<String, String>> translations =
                extra.translations() == null || extra.translations().isEmpty() ? null : extra.translations();
        return new ActivityExtra(code, name, description, price.setScale(2, RoundingMode.HALF_UP),
                extra.pricing(), translations);
    }

    private static String slugOf(String source) {
        try {
            return SlugUtils.generateSlug(source);
        } catch (BadRequestException e) {
            throw new IllegalArgumentException("extra '" + source + "' needs a code of letters or digits", e);
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** null = unchanged (returns null), [] = clear. */
    public static List<ActivityExtra> fromDtos(List<ActivityExtraDTO> dtos) {
        if (dtos == null) {
            return null;
        }
        List<ActivityExtra> extras = dtos.stream()
                .map(dto -> dto == null ? null : new ActivityExtra(dto.getCode(), dto.getName(), dto.getDescription(), dto.getPrice(),
                        dto.getPricing(), dto.getTranslations()))
                .toList();
        return normalize(extras);
    }

    /** Localised for {@code lc}; {@code lc == null} is the raw admin view including translations. Never null. */
    public static List<ActivityExtraDTO> toDtos(List<ActivityExtra> extras, String lc) {
        if (extras == null) {
            return new ArrayList<>();
        }
        return extras.stream()
                .map(extra -> new ActivityExtraDTO(extra.code(),
                        Translations.pick(extra.translations(), lc, "name", extra.name()),
                        Translations.pick(extra.translations(), lc, "description", extra.description()),
                        extra.price(), extra.pricing(),
                        lc == null ? extra.translations() : null))
                .toList();
    }

    public static List<ActivityExtra> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<ActivityExtra> parsed;
        try {
            parsed = MAPPER.readValue(json, new TypeReference<List<ActivityExtra>>() {});
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("extras is not a JSON array of extras: " + e.getMessage(), e);
        }
        return parsed == null ? List.of() : normalize(parsed);
    }

    /** Stable text form (used by the CSV round trip); empty string when there are no extras. */
    public static String canonicalJson(List<ActivityExtra> extras) {
        if (extras == null || extras.isEmpty()) {
            return "";
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ActivityExtra extra : extras) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", extra.code());
            row.put("name", extra.name());
            row.put("description", extra.description());
            row.put("price", extra.price().setScale(2, RoundingMode.HALF_UP));
            row.put("pricing", extra.pricing().name());
            if (extra.translations() != null && !extra.translations().isEmpty()) {
                row.put("translations", extra.translations());
            }
            rows.add(row);
        }
        return MAPPER.writeValueAsString(rows);
    }

    public static Optional<ActivityExtra> byCode(List<ActivityExtra> extras, String code) {
        if (extras == null || code == null) {
            return Optional.empty();
        }
        return extras.stream().filter(extra -> code.equals(extra.code())).findFirst();
    }
}
