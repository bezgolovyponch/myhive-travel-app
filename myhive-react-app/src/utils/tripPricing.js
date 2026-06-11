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
        total += (Number(it.price) || 0) * travelers;
    });
    groups.forEach(g => {
        const sub = g.items.reduce((s, it) => s + (Number(it.price) || 0) * travelers, 0);
        total += sub * (100 - g.packageDiscountPct) / 100;
    });
    return Math.round(total * 100) / 100;
}
