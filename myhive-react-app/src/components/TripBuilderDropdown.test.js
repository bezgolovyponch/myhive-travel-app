import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, useLocation} from 'react-router-dom';
import TripBuilderDropdown from './TripBuilderDropdown';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext, initialState as tripInitialState} from '../context/TripContext';

// Vote setup UI is exercised in TripSetupModal.test.js — keep it out of scope here.
jest.mock('./TripSetupModal', () => () => null);

const activity = {
    id: 'act-1',
    name: 'Kayaking',
    price: 60,
    imageUrl: 'http://img/kayak.jpg',
    destinationSlug: 'prague',
};

function LocationProbe() {
    const location = useLocation();
    return <div data-testid="location">{location.pathname + location.search}</div>;
}

function renderDropdown(tripStateOverrides = {}) {
    const mockDispatch = jest.fn();
    const tripState = {
        ...tripInitialState,
        tripItems: [activity],
        tripBuilderModalOpen: true,
        ...tripStateOverrides,
    };
    const result = render(
        <MemoryRouter initialEntries={['/']}>
            <CatalogContext.Provider value={{state: {destinations: []}, dispatch: jest.fn()}}>
                <TripContext.Provider value={{state: tripState, dispatch: mockDispatch}}>
                    <TripBuilderDropdown/>
                    <LocationProbe/>
                </TripContext.Provider>
            </CatalogContext.Provider>
        </MemoryRouter>
    );
    return {...result, mockDispatch};
}

describe('TripBuilderDropdown with items in the cart', () => {
    it('shows Continue as the only action button', () => {
        renderDropdown();

        expect(screen.getByRole('button', {name: 'Continue'})).toBeInTheDocument();
        expect(screen.queryByRole('button', {name: /Complete Booking/i})).not.toBeInTheDocument();
        expect(screen.queryByRole('button', {name: /Vote together/i})).not.toBeInTheDocument();
    });

    it('navigates to the trip builder and closes the dropdown on Continue', async () => {
        const user = userEvent.setup();
        const {mockDispatch} = renderDropdown();

        await user.click(screen.getByRole('button', {name: 'Continue'}));

        expect(mockDispatch).toHaveBeenCalledWith({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        expect(screen.getByTestId('location')).toHaveTextContent('/destination/prague?tab=trip-builder');
    });
});

describe('TripBuilderDropdown with an empty cart', () => {
    it('keeps the vote CTA in the empty state', () => {
        renderDropdown({tripItems: []});

        expect(screen.getByRole('button', {name: /Vote together/i})).toBeInTheDocument();
        expect(screen.queryByRole('button', {name: 'Continue'})).not.toBeInTheDocument();
    });
});
