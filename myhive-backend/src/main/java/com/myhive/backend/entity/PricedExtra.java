package com.myhive.backend.entity;

import java.math.BigDecimal;

/** An extra with a price and a pricing mode, whether read from the catalog or snapshotted on a booking. */
public interface PricedExtra {

    BigDecimal price();

    ExtraPricing pricing();

    default BigDecimal amount(int travelers) {
        return pricing() == null ? BigDecimal.ZERO.setScale(2) : pricing().amount(price(), travelers);
    }
}
