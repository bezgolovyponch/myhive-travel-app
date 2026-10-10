package com.myhive.backend.util;

import jakarta.persistence.AttributeConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Persists a list as one JSON TEXT column; null, empty and blank columns all read back as "none" (null). */
public abstract class JsonListConverter<T> implements AttributeConverter<List<T>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TypeReference<List<T>> type;

    protected JsonListConverter(TypeReference<List<T>> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(List<T> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<T> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return MAPPER.readValue(dbData, type);
    }
}
