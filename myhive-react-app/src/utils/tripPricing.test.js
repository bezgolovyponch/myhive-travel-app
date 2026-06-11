import {computeTripTotal, groupTripItems} from './tripPricing';

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
