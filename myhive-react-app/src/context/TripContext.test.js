import {act, renderHook} from '@testing-library/react';
import {initialState, reducer, TripProvider, useTrip} from './TripContext';

function renderTripHook() {
    return renderHook(() => useTrip(), {wrapper: TripProvider});
}

const activity1 = {id: '1', name: 'Kayaking', price: 38};
const activity2 = {id: '2', name: 'Pub Crawl', price: 25};

describe('reducer — trip setup actions', () => {
    describe('ADD_TO_TRIP', () => {
        it('opens setup modal on first item', () => {
            const state = reducer(initialState, {type: 'ADD_TO_TRIP', activity: activity1});

            expect(state.tripItems).toEqual([activity1]);
            expect(state.tripSetupModalOpen).toBe(true);
            expect(state.tripBuilderModalOpen).toBe(false);
        });

        it('opens trip builder dropdown on second item', () => {
            const stateWithOne = {...initialState, tripItems: [activity1]};
            const state = reducer(stateWithOne, {type: 'ADD_TO_TRIP', activity: activity2});

            expect(state.tripItems).toEqual([activity1, activity2]);
            expect(state.tripSetupModalOpen).toBe(false);
            expect(state.tripBuilderModalOpen).toBe(true);
        });

        it('does not open setup modal when silent', () => {
            const state = reducer(initialState, {type: 'ADD_TO_TRIP', activity: activity1, silent: true});

            expect(state.tripItems).toEqual([activity1]);
            expect(state.tripSetupModalOpen).toBe(false);
        });

        it('does not add duplicate activity', () => {
            const stateWithOne = {...initialState, tripItems: [activity1]};
            const state = reducer(stateWithOne, {type: 'ADD_TO_TRIP', activity: activity1});

            expect(state.tripItems).toEqual([activity1]);
        });
    });

    describe('SET_TRIP_SETUP', () => {
        it('saves travelers and dates, closes setup modal, opens trip builder', () => {
            const prev = {...initialState, tripSetupModalOpen: true};
            const state = reducer(prev, {
                type: 'SET_TRIP_SETUP',
                travelers: 4,
                startDate: '2026-06-01',
                endDate: '2026-06-10'
            });

            expect(state.tripTravelers).toBe(4);
            expect(state.tripStartDate).toBe('2026-06-01');
            expect(state.tripEndDate).toBe('2026-06-10');
            expect(state.tripSetupModalOpen).toBe(false);
            expect(state.tripBuilderModalOpen).toBe(true);
        });
    });

    describe('UPDATE_TRIP_TRAVELERS', () => {
        it('updates only travelers count', () => {
            const prev = {...initialState, tripTravelers: 2, tripStartDate: '2026-06-01'};
            const state = reducer(prev, {type: 'UPDATE_TRIP_TRAVELERS', travelers: 5});

            expect(state.tripTravelers).toBe(5);
            expect(state.tripStartDate).toBe('2026-06-01');
        });
    });

    describe('UPDATE_TRIP_DATES', () => {
        it('updates only dates', () => {
            const prev = {...initialState, tripTravelers: 3};
            const state = reducer(prev, {
                type: 'UPDATE_TRIP_DATES',
                startDate: '2026-07-01',
                endDate: '2026-07-15'
            });

            expect(state.tripStartDate).toBe('2026-07-01');
            expect(state.tripEndDate).toBe('2026-07-15');
            expect(state.tripTravelers).toBe(3);
        });
    });

    describe('UPDATE_TRIP_BUDGET', () => {
        it('updates only the budget', () => {
            const prev = {...initialState, tripTravelers: 3, tripStartDate: '2026-06-01'};
            const state = reducer(prev, {type: 'UPDATE_TRIP_BUDGET', budget: 3000});

            expect(state.tripBudget).toBe(3000);
            expect(state.tripTravelers).toBe(3);
            expect(state.tripStartDate).toBe('2026-06-01');
        });

        it('accepts null to clear the budget', () => {
            const prev = {...initialState, tripBudget: 3000};
            const state = reducer(prev, {type: 'UPDATE_TRIP_BUDGET', budget: null});

            expect(state.tripBudget).toBeNull();
        });
    });

    describe('CANCEL_TRIP_SETUP', () => {
        it('clears trip items, closes setup modal, and clears budget', () => {
            const prev = {
                ...initialState,
                tripItems: [activity1, activity2],
                tripSetupModalOpen: true,
                tripBudget: 3000
            };
            const state = reducer(prev, {type: 'CANCEL_TRIP_SETUP'});

            expect(state.tripItems).toEqual([]);
            expect(state.tripSetupModalOpen).toBe(false);
            expect(state.tripBudget).toBeNull();
        });

        it('does NOT clear tripId', () => {
            const expectedId = 'keep-me-uuid';
            const prev = {
                ...initialState,
                tripId: expectedId,
                tripItems: [activity1],
                tripSetupModalOpen: true,
                tripBudget: 2000
            };
            const state = reducer(prev, {type: 'CANCEL_TRIP_SETUP'});

            expect(state.tripId).toBe(expectedId);
            expect(state.tripItems).toEqual([]);
        });
    });

    describe('SET_TRIP_ID', () => {
        it('sets tripId and leaves other state untouched', () => {
            const expectedId = 'new-uuid-abc';
            const prev = {...initialState, tripTravelers: 3, tripItems: [activity1]};
            const state = reducer(prev, {type: 'SET_TRIP_ID', tripId: expectedId});

            expect(state.tripId).toBe(expectedId);
            expect(state.tripTravelers).toBe(3);
            expect(state.tripItems).toEqual([activity1]);
        });

        it('can be overwritten by dispatching SET_TRIP_ID again', () => {
            const expectedId = 'second-uuid';
            const prev = {...initialState, tripId: 'first-uuid'};
            const state = reducer(prev, {type: 'SET_TRIP_ID', tripId: expectedId});

            expect(state.tripId).toBe(expectedId);
        });
    });

    describe('REMOVE_FROM_TRIP', () => {
        it('removes activity by id', () => {
            const prev = {...initialState, tripItems: [activity1, activity2]};
            const state = reducer(prev, {type: 'REMOVE_FROM_TRIP', activityId: '1'});

            expect(state.tripItems).toEqual([activity2]);
        });
    });
});

describe('initialState defaults', () => {
    it('has correct trip setup defaults', () => {
        expect(initialState.tripTravelers).toBe(1);
        expect(initialState.tripStartDate).toBe('');
        expect(initialState.tripEndDate).toBe('');
        expect(initialState.tripSetupModalOpen).toBe(false);
        expect(initialState.tripItems).toEqual([]);
        expect(initialState.tripBudget).toBeNull();
    });

    it('defaults tripId to null', () => {
        expect(initialState.tripId).toBeNull();
    });
});

describe('reducer — trip builder, items and packages', () => {
    it('OPEN_TRIP_BUILDER_MODAL opens the builder dropdown', () => {
        const state = reducer(initialState, {type: 'OPEN_TRIP_BUILDER_MODAL'});
        expect(state.tripBuilderModalOpen).toBe(true);
    });

    it('CLOSE_TRIP_BUILDER_MODAL closes the builder dropdown', () => {
        const prev = {...initialState, tripBuilderModalOpen: true};
        const state = reducer(prev, {type: 'CLOSE_TRIP_BUILDER_MODAL'});
        expect(state.tripBuilderModalOpen).toBe(false);
    });

    it('CLOSE_TRIP_SETUP_MODAL closes only the setup modal', () => {
        const prev = {...initialState, tripSetupModalOpen: true, tripItems: [activity1]};
        const state = reducer(prev, {type: 'CLOSE_TRIP_SETUP_MODAL'});
        expect(state.tripSetupModalOpen).toBe(false);
        expect(state.tripItems).toEqual([activity1]);
    });

    it('SET_TRIP_ITEMS replaces the items array', () => {
        const items = [activity1, activity2];
        const state = reducer(initialState, {type: 'SET_TRIP_ITEMS', tripItems: items});
        expect(state.tripItems).toEqual(items);
    });

    it('ADD_PACKAGE_TO_TRIP adds package activities and replaces standalone copies', () => {
        const pkg = {
            id: 'p1', name: 'Weekend', discountPct: 10, destinationSlug: 'bali',
            activities: [
                {activityId: '1', name: 'Kayaking', price: 38},
                {activityId: '3', name: 'Diving', price: 50},
            ],
        };
        const prev = {...initialState, tripItems: [activity1]}; // standalone activity id '1'
        const state = reducer(prev, {type: 'ADD_PACKAGE_TO_TRIP', pkg});
        expect(state.tripItems.map(i => i.id).sort()).toEqual(['1', '3']);
        expect(state.tripItems.every(i => i.packageId === 'p1')).toBe(true);
    });

    it('REMOVE_PACKAGE_FROM_TRIP removes all items belonging to a package', () => {
        const prev = {
            ...initialState,
            tripItems: [{id: '1', packageId: 'p1'}, {id: '2', packageId: 'p1'}, {id: '3'}],
        };
        const state = reducer(prev, {type: 'REMOVE_PACKAGE_FROM_TRIP', packageId: 'p1'});
        expect(state.tripItems).toEqual([{id: '3'}]);
    });
});

describe('TripProvider — tripId localStorage persistence and hydration', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    it('SET_TRIP_ID writes tripId to localStorage["myhive-trip-id"]', () => {
        const expectedId = 'test-uuid-1234';
        const {result} = renderTripHook();

        act(() => {
            result.current.dispatch({type: 'SET_TRIP_ID', tripId: expectedId});
        });

        expect(result.current.state.tripId).toBe(expectedId);
        expect(localStorage.getItem('myhive-trip-id')).toBe(expectedId);
    });

    it('tripId persists through CANCEL_TRIP_SETUP and tripItems is cleared', () => {
        const expectedId = 'persist-uuid-5678';
        const {result} = renderTripHook();

        act(() => {
            result.current.dispatch({type: 'SET_TRIP_ID', tripId: expectedId});
        });
        act(() => {
            result.current.dispatch({
                type: 'ADD_TO_TRIP',
                activity: {id: 'act-1', name: 'Surfing', price: 50},
                silent: true,
            });
        });
        act(() => {
            result.current.dispatch({type: 'CANCEL_TRIP_SETUP'});
        });

        expect(result.current.state.tripId).toBe(expectedId);
        expect(result.current.state.tripItems).toHaveLength(0);
    });

    it('hydrates tripId from localStorage["myhive-trip-id"] on mount', () => {
        const expectedId = 'hydrated-uuid-9999';
        localStorage.setItem('myhive-trip-id', expectedId);

        const {result} = renderTripHook();

        expect(result.current.state.tripId).toBe(expectedId);
    });
});

describe('TripProvider — restore must win over the first persist', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    // The setItem spy below replaces the real writer, and CRA's resetMocks
    // leaves that stub in place for the next test (silently dropping its
    // writes) unless the original is put back here.
    afterEach(() => {
        jest.restoreAllMocks();
    });

    // Regression: the persist effects used to be gated by a ref flipped inside
    // the restore *layout* effect. Layout effects commit before the passive
    // ones of the same render, so the guard was already true when the writer
    // from the pre-restore render ran — and wrote the empty initial cart over
    // the saved one.
    it('never writes the empty initial cart over the saved one', () => {
        localStorage.setItem('myhive-trip-items', JSON.stringify([activity1, activity2]));
        const carts = [];
        const setItem = Storage.prototype.setItem;
        jest.spyOn(Storage.prototype, 'setItem').mockImplementation(function (key, value) {
            if (key === 'myhive-trip-items') {
                carts.push(JSON.parse(value));
            }
            setItem.call(this, key, value);
        });

        renderTripHook();

        // Asserted per write, not as an exact list: an effect invoked twice
        // (StrictMode) would write the same cart twice, and the claim here is
        // that no write is ever the empty one — not how many writes happen.
        expect(carts).not.toHaveLength(0);
        carts.forEach(cart => expect(cart).toEqual([activity1, activity2]));
    });
});
