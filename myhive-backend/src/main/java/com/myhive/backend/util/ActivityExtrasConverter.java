package com.myhive.backend.util;

import com.myhive.backend.entity.ActivityExtra;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;

import java.util.List;

@Converter
public class ActivityExtrasConverter extends JsonListConverter<ActivityExtra> {

    public ActivityExtrasConverter() {
        super(new TypeReference<List<ActivityExtra>>() {});
    }
}
