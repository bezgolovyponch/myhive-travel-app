package com.myhive.backend.dto;

import java.math.BigDecimal;

/**
 * The plan's "from €X": the group total less the fixed margin, in whole euros, and the same figure
 * per traveller (rounded up, so the per-person price never reads lower than the group's share).
 */
public record PriceQuoteResponse(BigDecimal fromPrice, BigDecimal fromPricePerPerson) {
}
