import {initialState, reducer} from './CatalogContext';

describe('catalog reducer', () => {
    it('SET_DESTINATIONS sets destinations and clears loading', () => {
        const destinations = [{id: 'd1', name: 'Prague'}];
        const state = reducer(initialState, {type: 'SET_DESTINATIONS', destinations});
        expect(state.destinations).toEqual(destinations);
        expect(state.loading).toBe(false);
    });

    it('SET_ERROR sets the error and clears loading', () => {
        const state = reducer(initialState, {type: 'SET_ERROR', error: 'boom'});
        expect(state.error).toBe('boom');
        expect(state.loading).toBe(false);
    });

    it('SET_LOADING toggles loading', () => {
        const state = reducer({...initialState, loading: false}, {type: 'SET_LOADING', loading: true});
        expect(state.loading).toBe(true);
    });

    it('defaults: loading true, empty destinations, no error', () => {
        expect(initialState.loading).toBe(true);
        expect(initialState.destinations).toEqual([]);
        expect(initialState.error).toBeNull();
    });
});
