import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { TripProvider, useTrip } from '../context/TripContext';
import { CatalogContext } from '../context/CatalogContext';
import { useTripLeadRestore } from './useTripLeadRestore';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

const catalogValue = {
    state: {
        destinations: [{ id: 'dest-1', slug: 'prague', name: 'Prague' }],
        loading: false,
        error: null,
    },
};

let latestTripState;
let restoreApi;

function Harness({ onQuizFlowRestored }) {
    const { state } = useTrip();
    latestTripState = state;
    restoreApi = useTripLeadRestore(onQuizFlowRestored);
    return <div data-testid="pending">{restoreApi.pendingRestore ? 'pending' : 'idle'}</div>;
}

function renderAt(url, onQuizFlowRestored) {
    return render(
        <CatalogContext.Provider value={catalogValue}>
            <TripProvider>
                <MemoryRouter initialEntries={[url]}>
                    <Routes>
                        <Route path="/destination/:slug" element={<Harness onQuizFlowRestored={onQuizFlowRestored} />} />
                    </Routes>
                </MemoryRouter>
            </TripProvider>
        </CatalogContext.Provider>
    );
}

describe('useTripLeadRestore', () => {
    beforeEach(() => {
        localStorage.clear();
        sessionStorage.clear();
    });

    test('restores cart items into the trip when local cart is empty', async () => {
        leadApi.restoreLead.mockResolvedValue({
            leadId: 'lead-1',
            email: 'a@b.com',
            destinationId: 'dest-1',
            destinationSlug: 'prague',
            numberOfTravelers: 6,
            startDate: '2026-09-01',
            endDate: '2026-09-03',
            budget: null,
            quizResponsesJson: null,
            items: [{ activityId: 'act-1', name: 'Karting', price: 50, minPrice: null,
                      imageUrl: 'img', duration: 60, slug: 'karting', destinationSlug: 'prague',
                      description: '', includes: '' }],
        });

        renderAt('/destination/prague?tab=trip-builder&restore=tok-1');

        await waitFor(() => expect(latestTripState.tripItems).toHaveLength(1));
        expect(latestTripState.tripItems[0]).toEqual(expect.objectContaining({ id: 'act-1', name: 'Karting' }));
        expect(latestTripState.tripTravelers).toBe(6);
        expect(JSON.parse(localStorage.getItem('myhive-trip-lead'))).toEqual(
            { id: 'lead-1', restoreToken: 'tok-1' });
    });

    test('rebuilds the quiz flow when there are no items but quiz answers exist', async () => {
        const onQuizFlowRestored = jest.fn();
        leadApi.restoreLead.mockResolvedValue({
            leadId: 'lead-2',
            email: 'a@b.com',
            destinationId: 'dest-1',
            destinationSlug: 'prague',
            numberOfTravelers: 4,
            startDate: null,
            endDate: null,
            budget: null,
            quizResponsesJson: '[{"questionId":"q1","answerId":"a1"}]',
            items: [],
        });

        renderAt('/destination/prague?tab=trip-builder&restore=tok-2', onQuizFlowRestored);

        await waitFor(() => expect(onQuizFlowRestored).toHaveBeenCalled());
        const flow = JSON.parse(sessionStorage.getItem('myhive-quiz-flow'));
        expect(flow.responses).toEqual([{ questionId: 'q1', answerId: 'a1' }]);
        expect(flow.setup.destination.id).toBe('dest-1');
        expect(flow.setup.email).toBe('a@b.com');
    });

    test('asks before replacing a non-empty local cart', async () => {
        localStorage.setItem('myhive-trip-items', JSON.stringify([{ id: 'local-1', name: 'Local', price: 10 }]));
        leadApi.restoreLead.mockResolvedValue({
            leadId: 'lead-3', email: 'a@b.com', destinationId: 'dest-1', destinationSlug: 'prague',
            numberOfTravelers: 2, startDate: null, endDate: null, budget: null,
            quizResponsesJson: null,
            items: [{ activityId: 'act-9', name: 'Boat', price: 80, minPrice: null, imageUrl: '',
                      duration: 90, slug: 'boat', destinationSlug: 'prague', description: '', includes: '' }],
        });

        renderAt('/destination/prague?tab=trip-builder&restore=tok-3');

        await waitFor(() => expect(screen.getByTestId('pending')).toHaveTextContent('pending'));
        expect(latestTripState.tripItems[0].id).toBe('local-1');

        act(() => restoreApi.confirmRestore());
        await waitFor(() => expect(latestTripState.tripItems[0].id).toBe('act-9'));
    });

    test('an unknown token is ignored silently', async () => {
        leadApi.restoreLead.mockRejectedValue(new Error('Failed to restore trip'));

        renderAt('/destination/prague?tab=trip-builder&restore=bad');

        await waitFor(() => expect(leadApi.restoreLead).toHaveBeenCalled());
        expect(latestTripState.tripItems).toHaveLength(0);
    });
});
