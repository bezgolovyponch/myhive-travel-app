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
            {activityId: 'act-1', packageId: 'pkg-1'},
            {activityId: 'act-2', packageId: 'pkg-1'},
            {activityId: 'act-3', packageId: null},
        ],
        travelers: 10,
    });
});

test('no price without items', () => {
    const {result} = renderHook(() => usePlanFromPrice([], 10));

    expect(result.current).toBeNull();
    expect(pricingApi.quote).not.toHaveBeenCalled();
});
