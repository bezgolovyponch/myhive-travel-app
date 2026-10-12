import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, useLocation} from 'react-router-dom';
import TripBuilderDropdown from './TripBuilderDropdown';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext, initialState as tripInitialState} from '../context/TripContext';

// Vote setup UI is exercised in TripSetupModal.test.js — keep it out of scope here.
jest.mock('./TripSetupModal', () => () => null);
jest.mock('../services/pricingApi', () => ({__esModule: true, default: {quote: jest.fn()}}));
const pricingApi = require('../services/pricingApi').default;

beforeEach(() => {
    pricingApi.quote.mockResolvedValue({fromPrice: null});
});

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

function renderDropdown(tripStateOverrides = {}, props = {}) {
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
                    <TripBuilderDropdown {...props}/>
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

describe('TripBuilderDropdown with extras ticked', () => {
    const extras = [
        {code: 'show', name: 'Private show', price: 250, pricing: 'PER_GROUP'},
        {code: 'photo', name: 'Photos', price: 15, pricing: 'PER_PERSON'},
    ];

    it('names the ticked extras on the row, with no amount and no price per activity', () => {
        renderDropdown({tripItems: [{...activity, extras, selectedExtras: ['show', 'photo']}], tripTravelers: 4});

        const row = screen.getByText('+ Private show, Photos').closest('.trip-modal-item');
        expect(row).not.toHaveTextContent('€');
    });

    it('shows one price for the plan, the Trip Builder’s own "from", and no total', async () => {
        pricingApi.quote.mockResolvedValue({fromPrice: 621});
        renderDropdown({tripItems: [{...activity, extras, selectedExtras: ['show']}], tripTravelers: 10});

        expect(await screen.findByText('from €621')).toBeInTheDocument();
        expect(screen.getByText('Your plan')).toBeInTheDocument();
        expect(screen.queryByText(/10\s?%|discount/i)).not.toBeInTheDocument();
        expect(screen.queryByText(/Total/)).not.toBeInTheDocument();
        expect(pricingApi.quote).toHaveBeenCalledWith(
            {items: [{activityId: activity.id, packageId: null, extraCodes: ['show']}], travelers: 10});
    });

    it('says nothing about extras when none is ticked', () => {
        renderDropdown({tripItems: [{...activity, extras, selectedExtras: []}]});

        expect(screen.queryByText(/^\+ /)).not.toBeInTheDocument();
    });

    it('opens the activity’s card from the row; the ticked extra reaches the plan with Update', async () => {
        const user = userEvent.setup();
        const {mockDispatch: dispatch} = renderDropdown({tripItems: [{...activity, extras, selectedExtras: []}]});

        await user.click(screen.getByRole('button', {name: 'Add extras'}));
        const card = screen.getByRole('dialog');
        await user.click(within(card).getAllByRole('checkbox')[0]);
        await user.click(await within(card).findByRole('button', {name: 'Update'}));

        expect(dispatch).toHaveBeenCalledWith(
            expect.objectContaining({type: 'SET_ITEM_EXTRAS', id: activity.id, selectedExtras: [extras[0].code]}));
    });
});

describe('TripBuilderDropdown for the organiser of a running vote', () => {
    afterEach(() => localStorage.clear());

    it('goes back to the Trip Builder dashboard of that vote', async () => {
        localStorage.setItem('myhive-trip-vote-session', 'tok-1');
        localStorage.setItem('myhive-manager-tok-1', 'mgr-1');
        const user = userEvent.setup();
        renderDropdown();

        await user.click(screen.getByRole('button', {name: 'Back to Trip Builder'}));

        expect(screen.getByTestId('location')).toHaveTextContent(
            '/destination/prague?tab=trip-builder&voteSession=tok-1');
    });

    it('a friend\'s vote (no manager token) keeps Continue', () => {
        localStorage.setItem('myhive-trip-vote-session', 'tok-1');
        renderDropdown();

        expect(screen.getByRole('button', {name: 'Continue'})).toBeInTheDocument();
    });
});

describe('TripBuilderDropdown with an empty cart', () => {
    it('keeps the vote CTA in the empty state', () => {
        renderDropdown({tripItems: []});

        expect(screen.getByRole('button', {name: /Vote together/i})).toBeInTheDocument();
        expect(screen.queryByRole('button', {name: 'Continue'})).not.toBeInTheDocument();
    });
});

// Mounted outside the SPA (the landings), the in-place vote setup cannot work:
// its confirm handler hands the setup to /vote/new/quiz through react-router
// location state, which the full page load out of a server-rendered page drops.
// voteHref makes the empty-state CTA navigate into the funnel instead — the same
// reason HomePage takes one.
describe('TripBuilderDropdown with voteHref', () => {
    it('turns the empty-state vote CTA into a link to the funnel', () => {
        renderDropdown({tripItems: []}, {voteHref: '/de/vote/new'});

        expect(screen.getByRole('link', {name: /Vote together/i})).toHaveAttribute('href', '/de/vote/new');
        expect(screen.queryByRole('button', {name: /Vote together/i})).not.toBeInTheDocument();
    });
});
