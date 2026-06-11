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
        total += priceOf(it) * travelers;
    });
    groups.forEach(g => {
        const sub = g.items.reduce((s, it) => s + priceOf(it) * travelers, 0);
        total += sub * (100 - g.packageDiscountPct) / 100;
    });
    return Math.round(total * 100) / 100;
}
