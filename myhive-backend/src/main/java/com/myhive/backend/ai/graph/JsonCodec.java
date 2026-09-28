package com.myhive.backend.ai.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Complex state values are stored as JSON strings so any checkpoint saver can persist them without custom serializers. */
public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private JsonCodec() {}

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize " + value.getClass().getSimpleName(), e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot deserialize " + type.getSimpleName(), e);
        }
    }

    public static <T> T read(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot deserialize " + type.getType(), e);
        }
    }
}
