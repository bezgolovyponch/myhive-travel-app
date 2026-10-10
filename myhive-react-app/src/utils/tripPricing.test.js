import {
    baseLineTotal,
    computeTripTotal,
    extraAmount,
    extrasOf,
    extrasTotal,
    groupMinApplied,
    groupTripItems,
    lineTotal,
} from './tripPricing';

describe('extras', () => {
    test('extras are added on top of the floored base and never discounted', () => {
        const item = {id: 'a', price: 40, minPrice: 120, extras: [
            {code: 'show', name: 'Show', price: 250, pricing: 'PER_GROUP'},
            {code: 'photo', name: 'Photo', price: 15, pricing: 'PER_PERSON'}], selectedExtras: ['show', 'photo']};
        expect(lineTotal(item, 2)).toBe(400);          // 120 + 250 + 30
        expect(groupMinApplied(item, 2)).toBe(true);
        const packaged = {...item, id: 'b', packageId: 'p', packageDiscountPct: 50, minPrice: 0, price: 100};
        expect(computeTripTotal([packaged], 2)).toBe(380); // 200 × 50 % + 250 + 30
    });

    test('a legacy cart item without extras fields prices as before', () => {
        expect(lineTotal({id: 'a', price: 50}, 3)).toBe(150);
    });

    test('only the ticked extras count', () => {
        const expectedExtra = {code: 'show', name: 'Show', price: 250, pricing: 'PER_GROUP'};
        const item = {id: 'a', price: 40, extras: [
            expectedExtra,
            {code: 'photo', name: 'Photo', price: 15, pricing: 'PER_PERSON'}], selectedExtras: ['show']};
        expect(extrasOf(item)).toEqual([expectedExtra]);
        expect(extrasTotal(item, 4)).toBe(250);
        expect(extrasTotal({...item, selectedExtras: []}, 4)).toBe(0);
    });

    test('a per-person extra is multiplied by the travellers, a per-group one is flat', () => {
        expect(extraAmount({price: 15, pricing: 'PER_PERSON'}, 4)).toBe(60);
        expect(extraAmount({price: 250, pricing: 'PER_GROUP'}, 4)).toBe(250);
    });

    test('the group minimum is judged on the base alone', () => {
        const item = {id: 'a', price: 50, minPrice: 120, extras: [
            {code: 'show', name: 'Show', price: 250, pricing: 'PER_GROUP'}], selectedExtras: ['show']};
        expect(baseLineTotal(item, 3)).toBe(150);
        expect(groupMinApplied(item, 3)).toBe(false);
    });
});

describe('groupTripItems', () => {
    test('splits standalone items from package groups', () => {
        const items = [
            {id: 'a1', price: 100},
            {id: 'a2', price: 50, packageId: 'p1', packageName: 'Combo', packageDiscountPct: 10},
            {id: 'a3', price: 30, packageId: 'p1', packageName: 'Combo', packageDiscountPct: 10},
        ];
        const {standalone, groups} = groupTripItems(items);
        expect(standalone).toHaveLength(1);
        expect(groups).toHaveLength(1);
        expect(groups[0].packageName).toBe('Combo');
        expect(groups[0].packageDiscountPct).toBe(10);
        expect(groups[0].items).toHaveLength(2);
    });
});

describe('lineTotal', () => {
    test('floors a line to the group minimum', () => {
        expect(lineTotal({id: 'a1', price: 50, minPrice: 300}, 4)).toBe(300);
    });

    test('uses regular math once travelers clear the minimum', () => {
        expect(lineTotal({id: 'a1', price: 50, minPrice: 300}, 7)).toBe(350);
    });

    test('missing minPrice keeps legacy behavior (old localStorage carts)', () => {
        expect(lineTotal({id: 'a1', price: 50}, 2)).toBe(100);
    });
});

describe('groupMinApplied', () => {
    test('true only while the floor binds', () => {
        expect(groupMinApplied({id: 'a1', price: 50, minPrice: 300}, 4)).toBe(true);
        expect(groupMinApplied({id: 'a1', price: 50, minPrice: 300}, 7)).toBe(false);
        expect(groupMinApplied({id: 'a1', price: 50}, 2)).toBe(false);
    });
});

describe('computeTripTotal with group minimums', () => {
    test('floors a standalone line to the minimum', () => {
        const expectedTotal = 300;
        expect(computeTripTotal([{id: 'a1', price: 50, minPrice: 300}], 4)).toBe(expectedTotal);
    });

    test('floors package lines before the discount', () => {
        const items = [
            {id: 'a1', price: 50, minPrice: 300, packageId: 'p1', packageDiscountPct: 10},
            {id: 'a2', price: 40, packageId: 'p1', packageDiscountPct: 10},
        ];
        // max(2×50, 300) + 2×40 = 380, minus 10% = 342
        const expectedTotal = 342;
        expect(computeTripTotal(items, 2)).toBe(expectedTotal);
    });
});

describe('computeTripTotal', () => {
    test('multiplies standalone prices by travelers', () => {
        const expectedTotal = 300;
        expect(computeTripTotal([{id: 'a1', price: 100}], 3)).toBe(expectedTotal);
    });

    test('applies package discount to grouped items', () => {
        const items = [
            {id: 'a2', price: 50, packageId: 'p1', packageDiscountPct: 10},
            {id: 'a3', price: 30, packageId: 'p1', packageDiscountPct: 10},
        ];
        // (50 + 30) × 2 travelers = 160, minus 10% = 144
        const expectedTotal = 144;
        expect(computeTripTotal(items, 2)).toBe(expectedTotal);
    });

    test('mixed cart: standalone at full price plus discounted package', () => {
        const items = [
            {id: 'a1', price: 100},
            {id: 'a2', price: 50, packageId: 'p1', packageDiscountPct: 20},
        ];
        // 100 + 50 × 0.8 = 140
        const expectedTotal = 140;
        expect(computeTripTotal(items, 1)).toBe(expectedTotal);
    });

    test('non-numeric prices count as zero', () => {
        expect(computeTripTotal([{id: 'a1', price: null}], 2)).toBe(0);
    });

    test('parses currency-formatted string prices from legacy localStorage carts', () => {
        const expectedTotal = 240;
        expect(computeTripTotal([{id: 'a1', price: '€120'}], 2)).toBe(expectedTotal);
    });

    test('rounds to cents', () => {
        const items = [{id: 'a1', price: 33.335, packageId: 'p1', packageDiscountPct: 10}];
        expect(computeTripTotal(items, 1)).toBe(30);
    });
});
