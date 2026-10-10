package com.myhive.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.myhive.backend.entity.ExtraPricing;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityExtraDTO {
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private ExtraPricing pricing;

    /** Raw per-locale overrides, present on the admin (no-locale) view only. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Map<String, String>> translations;
}
