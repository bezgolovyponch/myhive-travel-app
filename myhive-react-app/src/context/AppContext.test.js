import {initialState, reducer} from './AppContext';

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

    describe('CANCEL_TRIP_SETUP', () => {
        it('clears trip items and closes setup modal', () => {
            const prev = {
                ...initialState,
                tripItems: [activity1, activity2],
                tripSetupModalOpen: true
            };
            const state = reducer(prev, {type: 'CANCEL_TRIP_SETUP'});

            expect(state.tripItems).toEqual([]);
            expect(state.tripSetupModalOpen).toBe(false);
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
    });
});
