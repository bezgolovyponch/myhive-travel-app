package com.myhive.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The plan's "from €X": the group total less the fixed margin, in whole euros, and the same figure
 * per traveller (rounded up, so the per-person price never reads lower than the group's share).
 * {@code lines} is the same per-person figure for each line of the plan, in the order asked, for the
 * organiser's dashboard: each line's activities less the margin (and its package's discount), plus its
 * extras in full, in whole euros.
 */
public record PriceQuoteResponse(BigDecimal fromPrice, BigDecimal fromPricePerPerson, List<LinePrice> lines) {

    public record LinePrice(UUID activityId, UUID packageId, BigDecimal fromPricePerPerson) {
    }
}
