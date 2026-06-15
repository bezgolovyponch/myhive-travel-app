import {initialState, reducer} from './DestinationModalContext';

describe('destination modal reducer', () => {
    it('OPEN_DESTINATION_MODAL opens with the selected destination', () => {
        const destination = {id: 'd1', name: 'Prague'};
        const state = reducer(initialState, {type: 'OPEN_DESTINATION_MODAL', destination});
        expect(state.destinationModalOpen).toBe(true);
        expect(state.selectedDestination).toEqual(destination);
    });

    it('CLOSE_DESTINATION_MODAL clears open flag and selection', () => {
        const open = {destinationModalOpen: true, selectedDestination: {id: 'd1'}};
        const state = reducer(open, {type: 'CLOSE_DESTINATION_MODAL'});
        expect(state.destinationModalOpen).toBe(false);
        expect(state.selectedDestination).toBeNull();
    });

    it('defaults: closed with no selection', () => {
        expect(initialState.destinationModalOpen).toBe(false);
        expect(initialState.selectedDestination).toBeNull();
    });
});
