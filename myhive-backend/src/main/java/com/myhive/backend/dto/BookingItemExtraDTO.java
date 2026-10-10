package com.myhive.backend.dto;

import com.myhive.backend.entity.BookingItemExtra;
import com.myhive.backend.entity.ExtraPricing;

import java.math.BigDecimal;

/** An extra as booked on one booking item: the snapshot taken at booking time, never the live catalog. */
public record BookingItemExtraDTO(String code, String name, BigDecimal price, ExtraPricing pricing) {

    public static BookingItemExtraDTO of(BookingItemExtra extra) {
        return new BookingItemExtraDTO(extra.code(), extra.name(), extra.price(), extra.pricing());
    }
}
