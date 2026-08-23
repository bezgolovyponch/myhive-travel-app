package com.myhive.backend.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Persists the per-locale field overrides of a content row as one JSON text
 * column: {@code {"de": {"name": "...", "description": "..."}}}.
 * A plain TEXT column + explicit (de)serialization rather than Hibernate's
 * native JSON mapping so the same entity runs unchanged on prod Postgres and
 * the H2 dev/test profiles, with no dependency on which JSON format mapper
 * Hibernate detects. Batch SQL fills are plain string literals either way.
 */
@Converter
public class TranslationsConverter implements AttributeConverter<Map<String, Map<String, String>>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Map<String, String>>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Map<String, String>> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public Map<String, Map<String, String>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return MAPPER.readValue(dbData, TYPE);
    }
}
