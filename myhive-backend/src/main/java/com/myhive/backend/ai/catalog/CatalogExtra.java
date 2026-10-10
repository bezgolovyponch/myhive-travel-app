package com.myhive.backend.ai.catalog;

import com.myhive.backend.entity.ExtraPricing;
import com.myhive.backend.entity.PricedExtra;

import java.math.BigDecimal;

/** An optional add-on of a catalog activity, named in the snapshot's language; priced by {@link PricedExtra}. */
public record CatalogExtra(String code, String name, BigDecimal price, ExtraPricing pricing) implements PricedExtra {
}
