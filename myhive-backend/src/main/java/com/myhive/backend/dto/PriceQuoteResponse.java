package com.myhive.backend.dto;

import java.math.BigDecimal;

/** The plan's "from €X": the group total less the fixed margin, in whole euros. */
public record PriceQuoteResponse(BigDecimal fromPrice) {
}
