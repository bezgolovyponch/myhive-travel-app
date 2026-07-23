import { render, act } from '@testing-library/react';
import { TripProvider, useTrip } from '../context/TripContext';
import { useTripLeadSync } from './useTripLeadSync';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

let tripDispatch;

function Harness() {
    const { dispatch } = useTrip();
    tripDispatch = dispatch;
    useTripLeadSync();
    return null;
}

function renderHarness() {
    return render(
        <TripProvider>
            <Harness />
        </TripProvider>
    );
}

describe('useTripLeadSync', () => {
    beforeEach(() => {
        jest.useFakeTimers();
        localStorage.clear();
        sessionStorage.clear();
        leadApi.syncLead.mockResolvedValue();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    test('debounces a PATCH with the cart snapshot after a change', async () => {
        localStorage.setItem('myhive-trip-lead', JSON.stringify({ id: 'lead-1', restoreToken: 'tok-1' }));
        renderHarness();

        act(() => {
            tripDispatch({ type: 'ADD_TO_TRIP', silent: true, activity: { id: 'act-1', name: 'Karting', price: 50 } });
        });
        act(() => {
            jest.advanceTimersByTime(2000);
        });

        expect(leadApi.syncLead).toHaveBeenCalledWith('lead-1', expect.objectContaining({
            restoreToken: 'tok-1',
            items: [{ activityId: 'act-1', sortOrder: 0 }],
        }));
    });

    test('does nothing without a stored lead', () => {
        renderHarness();

        act(() => {
            tripDispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 4 });
        });
        act(() => {
            jest.advanceTimersByTime(3000);
        });

        expect(leadApi.syncLead).not.toHaveBeenCalled();
    });

    test('clears the stored lead when the server says it is gone', async () => {
        localStorage.setItem('myhive-trip-lead', JSON.stringify({ id: 'lead-1', restoreToken: 'tok-1' }));
        leadApi.syncLead.mockRejectedValue(new Error('LEAD_GONE'));
        renderHarness();

        act(() => {
            tripDispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 4 });
        });
        await act(async () => {
            jest.advanceTimersByTime(2000);
        });

        expect(localStorage.getItem('myhive-trip-lead')).toBeNull();
    });
});
