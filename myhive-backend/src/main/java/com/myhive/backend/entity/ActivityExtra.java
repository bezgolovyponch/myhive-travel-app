package com.myhive.backend.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.Map;

/** An optional add-on of an activity, stored as JSON on the activity row. */
public record ActivityExtra(String code, String name, String description, BigDecimal price, ExtraPricing pricing,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Map<String, String>> translations)
        implements PricedExtra {
}
