import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, useLocation} from 'react-router-dom';
import TripBuilder from './TripBuilder';
import {TripContext} from '../context/TripContext';

// The organiser dashboard (v3 4b): the Trip Builder tab of a browser that holds
// the vote's manager token.

jest.mock('../services/api', () => ({
    __esModule: true,
    default: {
        getCategoriesForDestination: jest.fn(),
        getActivities: jest.fn(),
        createBookingFromTrip: jest.fn(),
    },
}));

jest.mock('../services/voteApi', () => ({
    __esModule: true,
    default: {
        getResult: jest.fn(),
        getSession: jest.fn(),
        getTally: jest.fn(),
        getActivities: jest.fn(),
        excludeActivity: jest.fn(),
        restoreActivity: jest.fn(),
        addActivity: jest.fn(),
        closeSession: jest.fn(),
        buildPool: jest.fn(),
    },
}));

jest.mock('../services/paymentApi', () => ({paymentApi: {createBookingDepositSession: jest.fn()}}));
jest.mock('../services/pricingApi', () => ({__esModule: true, default: {quote: jest.fn(() => Promise.resolve({fromPrice: null}))}}));
jest.mock('../utils/analytics', () => ({pushEvent: jest.fn(), navigateAfterEvents: jest.fn()}));
jest.mock('../utils/openWhatsApp', () => ({openWhatsApp: jest.fn()}));
jest.mock('../context/CatalogContext', () => ({
    useCatalog: () => ({state: {destinations: [], loading: false, error: null}}),
}));

const api = require('../services/api').default;
const voteApi = require('../services/voteApi').default;
const {openWhatsApp} = require('../utils/openWhatsApp');

const shooting = {id: 'act-1', name: 'AK-47 shooting', price: 89, destinationSlug: 'prague', imageUrl: 'x'};
const steak = {id: 'act-2', name: 'Steak dinner', price: 59, destinationSlug: 'prague', imageUrl: 'y'};

const TALLY = {
    status: 'ACTIVE',
    participantCount: 3,
    numberOfTravelers: 10,
    rows: [
        {activityId: 'act-1', name: 'AK-47 shooting', price: 89, likeCount: 3, skipCount: 0, excluded: false},
        {activityId: 'act-2', name: 'Steak dinner', price: 59, likeCount: 1, skipCount: 2, excluded: false},
        {activityId: 'act-3', name: 'Tank driving', price: 149, likeCount: 0, skipCount: 3, excluded: true},
    ],
    recommendations: [
        {activityId: 'act-9', name: 'Pub golf', slug: 'pub-golf', imageUrl: null, price: 29, minPrice: null,
            duration: 120, recommendationCount: 2},
    ],
};

function tripState(overrides = {}) {
    return {
        tripId: null,
        tripItems: [shooting, steak],
        tripTravelers: 10,
        tripStartDate: '2026-10-16',
        tripEndDate: '2026-10-18',
        tripBudget: null,
        tripSetupModalOpen: false,
        tripBuilderModalOpen: false,
        restored: true,
        ...overrides,
    };
}

let currentLocation;
function LocationSpy() {
    currentLocation = useLocation();
    return null;
}

function renderDashboard({state = tripState(), route = '/destination/prague?tab=trip-builder&voteSession=tok-1'} = {}) {
    const dispatch = jest.fn();
    render(
        <MemoryRouter initialEntries={[route]}>
            <TripContext.Provider value={{state, dispatch}}>
                <TripBuilder destinationId="d-1" destinationSlug="prague" destinationName="Prague"/>
            </TripContext.Provider>
            <LocationSpy/>
        </MemoryRouter>,
    );
    return {dispatch};
}

beforeEach(() => {
    api.getCategoriesForDestination.mockResolvedValue([]);
    api.getActivities.mockResolvedValue([]);
    voteApi.getResult.mockRejectedValue(new Error('Result not available yet'));
    voteApi.getSession.mockResolvedValue({
        shareToken: 'tok-1', status: 'ACTIVE', voteMode: 'CART', numberOfTravelers: 10,
        startDate: '2026-10-16', endDate: '2026-10-18',
    });
    voteApi.getTally.mockResolvedValue(TALLY);
    voteApi.excludeActivity.mockResolvedValue();
    voteApi.restoreActivity.mockResolvedValue();
    voteApi.addActivity.mockResolvedValue();
    localStorage.setItem('myhive-manager-tok-1', 'mgr-1');
});

afterEach(() => {
    localStorage.clear();
});

test('shows who has voted, the invite link, yes/no on each activity and the group recommendations', async () => {
    renderDashboard();

    expect(await screen.findByText('3 of 10 voted')).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Your weekend'})).toBeInTheDocument();
    expect(screen.getByText('7 still to vote')).toBeInTheDocument();
    expect(screen.getByText('Organiser')).toBeInTheDocument();
    expect(screen.getByText('✓ 3 yes')).toBeInTheDocument();
    expect(screen.getByText('✗ 2 no')).toBeInTheDocument();
    expect(screen.getByRole('region', {name: 'Invite link for the group'})).toHaveTextContent(
        '/vote/tok-1/activities?ref=invite');
    expect(screen.getByText('Recommended by 2 friends')).toBeInTheDocument();
    expect(screen.getByText('Happy with the plan?')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Ask the group'})).not.toBeInTheDocument();
    expect(voteApi.getTally).toHaveBeenCalledWith('tok-1', {managerToken: 'mgr-1'});
});

test('Send to WhatsApp group opens the message with the invite link', async () => {
    renderDashboard();
    const invite = await screen.findByRole('region', {name: 'Invite link for the group'});

    await userEvent.click(within(invite).getByRole('button', {name: 'Send to WhatsApp group'}));

    const {webUrl} = openWhatsApp.mock.calls[0][0];
    expect(decodeURIComponent(webUrl)).toContain('/vote/tok-1/activities?ref=invite');
});

test('removing an activity drops it from the running vote', async () => {
    const {dispatch} = renderDashboard();
    await screen.findByText('3 of 10 voted');

    await userEvent.click(screen.getByRole('button', {name: 'Remove Steak dinner'}));

    expect(dispatch).toHaveBeenCalledWith({type: 'REMOVE_FROM_TRIP', activityId: 'act-2'});
    expect(voteApi.excludeActivity).toHaveBeenCalledWith('tok-1', 'mgr-1', 'act-2');
});

test('a dropped activity is listed struck through and Restore brings it back', async () => {
    const {dispatch} = renderDashboard();
    await screen.findByText('Tank driving');

    await userEvent.click(screen.getByRole('button', {name: 'Restore'}));

    expect(voteApi.restoreActivity).toHaveBeenCalledWith('tok-1', 'mgr-1', 'act-3');
    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({
        type: 'ADD_TO_TRIP', activity: expect.objectContaining({id: 'act-3'}),
    }));
});

test('adding a recommendation puts it in the plan and in the vote', async () => {
    const {dispatch} = renderDashboard();
    await screen.findByText('Pub golf');

    await userEvent.click(screen.getByRole('button', {name: 'Add'}));

    expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({
        type: 'ADD_TO_TRIP', activity: expect.objectContaining({id: 'act-9', name: 'Pub golf'}),
    }));
    expect(voteApi.addActivity).toHaveBeenCalledWith('tok-1', 'mgr-1', 'act-9');
});

test('the email link: the manager token is kept and taken out of the address bar', async () => {
    localStorage.clear();

    renderDashboard({route: '/destination/prague?tab=trip-builder&voteSession=tok-1&manager=mgr-from-email'});

    expect(await screen.findByText('3 of 10 voted')).toBeInTheDocument();
    expect(localStorage.getItem('myhive-manager-tok-1')).toBe('mgr-from-email');
    expect(localStorage.getItem('myhive-initiator-tok-1')).toBe('true');
    expect(currentLocation.search).not.toContain('manager=');
    expect(currentLocation.search).toContain('voteSession=tok-1');
});

test('on a device with an empty cart, the ballot becomes the plan', async () => {
    voteApi.getActivities.mockResolvedValue([shooting, steak]);

    const {dispatch} = renderDashboard({state: tripState({tripItems: [], tripTravelers: 1, tripStartDate: '', tripEndDate: ''})});

    await waitFor(() => expect(dispatch).toHaveBeenCalledWith({type: 'SET_TRIP_ITEMS', tripItems: [shooting, steak]}));
    expect(dispatch).toHaveBeenCalledWith({type: 'UPDATE_TRIP_TRAVELERS', travelers: 10});
    expect(dispatch).toHaveBeenCalledWith({type: 'UPDATE_TRIP_DATES', startDate: '2026-10-16', endDate: '2026-10-18'});
});

test('without the manager token there is no dashboard', async () => {
    localStorage.clear();

    renderDashboard();

    expect(await screen.findByRole('button', {name: 'Ask the group'})).toBeInTheDocument();
    expect(voteApi.getTally).not.toHaveBeenCalled();
    expect(screen.queryByText('Your weekend')).not.toBeInTheDocument();
});

test('a closed vote keeps the final counts but drops the invite link and the editing', async () => {
    voteApi.getSession.mockResolvedValue({shareToken: 'tok-1', status: 'COMPLETED', voteMode: 'CART'});
    voteApi.getTally.mockResolvedValue({...TALLY, status: 'COMPLETED'});

    renderDashboard();

    expect(await screen.findByText('Voting has closed')).toBeInTheDocument();
    expect(screen.queryByRole('region', {name: 'Invite link for the group'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Restore'})).not.toBeInTheDocument();
});
