package com.myhive.backend.entity;

import java.math.BigDecimal;

/** Snapshot of an extra at booking time; later catalog edits never change it. */
public record BookingItemExtra(String code, String name, BigDecimal price, ExtraPricing pricing)
        implements PricedExtra {

    public static BookingItemExtra of(ActivityExtra extra) {
        return new BookingItemExtra(extra.code(), extra.name(), extra.price(), extra.pricing());
    }
}
