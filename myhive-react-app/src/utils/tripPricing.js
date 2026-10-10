// Trip items are hydrated from localStorage, where carts written by older app
// versions can carry display-formatted string prices ("€120") — strip the
// formatting instead of silently treating them as zero.
function priceOf(item) {
    if (typeof item.price === 'number') {
        return item.price;
    }
    const parsed = parseFloat(String(item.price ?? '').replace(/[^0-9.]/g, ''));
    return Number.isFinite(parsed) ? parsed : 0;
}

// Old localStorage carts have no minPrice — treat missing/invalid values as 0 (no floor).
function minPriceOf(item) {
    const n = Number(item.minPrice);
    return Number.isFinite(n) && n > 0 ? n : 0;
}

// Carts saved before extras existed have neither field: nothing is ticked.
function selectedCodes(item) {
    return Array.isArray(item.selectedExtras) ? item.selectedExtras : [];
}

// The extras the organizer ticked on this line, in the activity's own order.
export function extrasOf(item) {
    const selected = new Set(selectedCodes(item));
    return (Array.isArray(item.extras) ? item.extras : []).filter(extra => selected.has(extra.code));
}

export function extraAmount(extra, travelers) {
    const price = Number(extra.price) || 0;
    return extra.pricing === 'PER_PERSON' ? price * Math.max(1, travelers) : price;
}

export function extrasTotal(item, travelers) {
    return extrasOf(item).reduce((sum, extra) => sum + extraAmount(extra, travelers), 0);
}

// The group-minimum floor: the base of a line never totals below the activity's minPrice.
export function baseLineTotal(item, travelers) {
    return Math.max(priceOf(item) * travelers, minPriceOf(item));
}

// Mirrors BookingService.lineBase + lineExtras and PlanPricer: floor the base, add extras at list price.
export function lineTotal(item, travelers) {
    return baseLineTotal(item, travelers) + extrasTotal(item, travelers);
}

export function groupMinApplied(item, travelers) {
    return baseLineTotal(item, travelers) > priceOf(item) * travelers;
}

export function groupTripItems(tripItems) {
    const standalone = tripItems.filter(i => !i.packageId);
    const packageGroups = tripItems.reduce((acc, item) => {
        if (!item.packageId) {
            return acc;
        }
        if (!acc[item.packageId]) {
            acc[item.packageId] = {
                packageId: item.packageId,
                packageName: item.packageName,
                packageDiscountPct: Number(item.packageDiscountPct) || 0,
                destinationSlug: item.destinationSlug,
                items: [],
            };
        }
        acc[item.packageId].items.push(item);
        return acc;
    }, {});
    return {standalone, groups: Object.values(packageGroups)};
}

export function computeTripTotal(tripItems, travelers) {
    const {standalone, groups} = groupTripItems(tripItems);
    let total = 0;
    standalone.forEach(it => {
        total += lineTotal(it, travelers);
    });
    groups.forEach(g => {
        // Floor each base first, discount the group's bases, then add extras undiscounted.
        const bases = g.items.reduce((s, it) => s + baseLineTotal(it, travelers), 0);
        const extras = g.items.reduce((s, it) => s + extrasTotal(it, travelers), 0);
        total += bases * (100 - g.packageDiscountPct) / 100 + extras;
    });
    return Math.round(total * 100) / 100;
}
