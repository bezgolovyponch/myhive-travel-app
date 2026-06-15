import {initialState, reducer} from './TripContext';

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
