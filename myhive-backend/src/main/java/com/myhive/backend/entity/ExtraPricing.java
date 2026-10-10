package com.myhive.backend.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum ExtraPricing {
    PER_GROUP, PER_PERSON;

    /** What this extra costs for a group of {@code travelers}; a group of fewer than one is priced as one. */
    public BigDecimal amount(BigDecimal price, int travelers) {
        BigDecimal base = price == null ? BigDecimal.ZERO : price;
        BigDecimal amount = this == PER_PERSON ? base.multiply(BigDecimal.valueOf(Math.max(1, travelers))) : base;
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
