import {renderHook, waitFor} from '@testing-library/react';
import {usePlanFromPrice} from './usePlanFromPrice';

jest.mock('../services/pricingApi', () => ({__esModule: true, default: {quote: jest.fn()}}));

const pricingApi = require('../services/pricingApi').default;

test('sends each line with its package, so the server prices the package at its discount', async () => {
    pricingApi.quote.mockResolvedValue({fromPrice: 1492, fromPricePerPerson: 150});
    const items = [
        {id: 'act-1', name: 'Shooting', packageId: 'pkg-1', packageDiscountPct: 15},
        {id: 'act-2', name: 'Steak', packageId: 'pkg-1', packageDiscountPct: 15},
        {id: 'act-3', name: 'Boat'},
    ];

    const {result} = renderHook(() => usePlanFromPrice(items, 10));

    await waitFor(() => expect(result.current).toEqual({fromPrice: 1492, fromPricePerPerson: 150}));
    expect(pricingApi.quote).toHaveBeenCalledWith({
        items: [
            {activityId: 'act-1', packageId: 'pkg-1', extraCodes: []},
            {activityId: 'act-2', packageId: 'pkg-1', extraCodes: []},
            {activityId: 'act-3', packageId: null, extraCodes: []},
        ],
        travelers: 10,
    });
});

test('no price without items', () => {
    const {result} = renderHook(() => usePlanFromPrice([], 10));

    expect(result.current).toBeNull();
    expect(pricingApi.quote).not.toHaveBeenCalled();
});

test('sends the ticked extras and asks again when the selection changes', async () => {
    pricingApi.quote.mockResolvedValue({fromPrice: 700, fromPricePerPerson: 70});
    const expectedCodes = ['show'];
    const item = {id: 'act-1', name: 'Karting', extras: [{code: 'show'}, {code: 'photo'}], selectedExtras: expectedCodes};

    const {rerender} = renderHook(({items}) => usePlanFromPrice(items, 10), {initialProps: {items: [item]}});

    await waitFor(() => expect(pricingApi.quote).toHaveBeenCalledWith({
        items: [{activityId: 'act-1', packageId: null, extraCodes: expectedCodes}],
        travelers: 10,
    }));

    const expectedSortedCodes = ['photo', 'show'];
    rerender({items: [{...item, selectedExtras: ['show', 'photo']}]});

    await waitFor(() => expect(pricingApi.quote).toHaveBeenLastCalledWith({
        items: [{activityId: 'act-1', packageId: null, extraCodes: expectedSortedCodes}],
        travelers: 10,
    }));
});

test('a ticked code the activity no longer offers is not sent, as the booking would drop it', async () => {
    pricingApi.quote.mockResolvedValue({fromPrice: 700, fromPricePerPerson: 70});
    const expectedCodes = ['show'];
    const item = {id: 'act-1', name: 'Karting', extras: [{code: 'show'}], selectedExtras: ['show', 'removed-by-admin']};

    renderHook(() => usePlanFromPrice([item], 10));

    await waitFor(() => expect(pricingApi.quote).toHaveBeenCalledWith({
        items: [{activityId: 'act-1', packageId: null, extraCodes: expectedCodes}],
        travelers: 10,
    }));
});
