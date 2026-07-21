import {act, render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, useLocation} from 'react-router-dom';
import TripBuilder from './TripBuilder';
import {TripContext} from '../context/TripContext';

// ---------------------------------------------------------------------------
// Module mocks
// ---------------------------------------------------------------------------

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
        createSession: jest.fn(),
        buildPool: jest.fn(),
    },
}));

jest.mock('../services/paymentApi', () => ({
    paymentApi: { createBookingDepositSession: jest.fn() },
}));

const voteApi = require('../services/voteApi').default;
const { paymentApi } = require('../services/paymentApi');
// Captures the Turnstile success callback so tests can simulate a solved captcha.
let turnstileCallback;

jest.mock('../utils/analytics', () => ({ pushEvent: jest.fn() }));

jest.mock('../utils/userRole', () => ({ resolveUserRole: jest.fn() }));

jest.mock('../utils/attribution', () => ({
    getAttribution: jest.fn(),
    getRef: jest.fn(),
}));

jest.mock('../utils/uuid', () => ({ generateUuid: jest.fn() }));

// Replace DayPicker-based DateRangePicker with simple inputs.
jest.mock('./DateRangePicker', () =>
    function MockDateRangePicker({ from, to, onChange }) {
        return (
            <>
                <input
                    data-testid="date-from"
                    value={from}
                    onChange={e => onChange(e.target.value, to)}
                />
                <input
                    data-testid="date-to"
                    value={to}
                    onChange={e => onChange(from, e.target.value)}
                />
            </>
        );
    }
);

// ---------------------------------------------------------------------------
// Imports of mocked modules (resolved after jest.mock hoisting)
// ---------------------------------------------------------------------------

const api = require('../services/api').default;
const { pushEvent } = require('../utils/analytics');
const { resolveUserRole } = require('../utils/userRole');
const { getAttribution, getRef } = require('../utils/attribution');
const { generateUuid } = require('../utils/uuid');

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const activity1 = {
    id: 'act-1',
    name: 'Kayaking',
    price: 60,
    imageUrl: 'http://img/kayak.jpg',
    destinationSlug: 'prague',
    duration: 120,
    description: 'Fun on the river',
    categories: [],
};

const activity2 = {
    id: 'act-2',
    name: 'Pub Crawl',
    price: 40,
    imageUrl: 'http://img/pub.jpg',
    destinationSlug: 'prague',
    duration: 180,
    description: 'Bar tour',
    categories: [],
};

function buildTripState(overrides = {}) {
    return {
        tripId: 'ctx-trip-id',
        tripItems: [activity1],
        tripTravelers: 2,
        tripStartDate: '',
        tripEndDate: '',
        tripBudget: null,
        tripSetupModalOpen: false,
        tripBuilderModalOpen: true,
        ...overrides,
    };
}

function renderTripBuilder(tripState = buildTripState(), route = '/', dispatchMock = jest.fn(), destinationSlug = 'prague') {
    const result = render(
        <MemoryRouter initialEntries={[route]}>
            <TripContext.Provider value={{ state: tripState, dispatch: dispatchMock }}>
                <TripBuilder destinationId="dest-1" destinationSlug={destinationSlug} />
            </TripContext.Provider>
        </MemoryRouter>
    );
    return { ...result, dispatchMock };
}

// Minimal render helper for the cart-annotation tests: a plain tripItems array
// plus a dispatch spy so tests can assert no hydration dispatch was fired.
function renderTripBuilderWithDispatch(tripItems, dispatch, { route = '/' } = {}) {
    const state = {
        tripId: null,
        tripItems,
        tripTravelers: 4,
        tripStartDate: '2026-08-01',
        tripEndDate: '2026-08-03',
        tripBudget: null,
        tripSetupModalOpen: false,
        tripBuilderModalOpen: false,
    };
    return render(
        <MemoryRouter initialEntries={[route]}>
            <TripContext.Provider value={{ state, dispatch }}>
                <TripBuilder destinationId="d-1" destinationSlug="prague" />
            </TripContext.Provider>
        </MemoryRouter>,
    );
}

// ---------------------------------------------------------------------------
// Fill the ContactForm with valid data and submit
// ---------------------------------------------------------------------------

async function fillAndSubmitContactForm(user) {
    await user.type(screen.getByLabelText(/Full Name/i), 'Jane Smith');
    await user.type(screen.getByLabelText(/Email Address/i), 'jane@example.com');
    await user.type(screen.getByLabelText(/Phone Number/i), '+1 555 000 1111');
    // Dates (mocked DateRangePicker exposes two testid inputs)
    await user.type(screen.getByTestId('date-from'), '2026-08-01');
    await user.type(screen.getByTestId('date-to'), '2026-08-07');
    await user.click(screen.getByRole('button', {name: 'Confirm'}));
}

// ---------------------------------------------------------------------------
// beforeEach — reset all mock returns (CRA resetMocks: true clears them)
// ---------------------------------------------------------------------------

beforeEach(() => {
    api.getCategoriesForDestination.mockResolvedValue([]);
    api.getActivities.mockResolvedValue([]);
    api.createBookingFromTrip.mockResolvedValue({ id: 'booking-123' });
    // voteApi.getResult is called when ?voteSession= is in the URL; resolve to empty
    // result so the voteSession-based tests don't throw from the effect.
    voteApi.getResult.mockResolvedValue({ result: [], suggestions: [] });
    voteApi.createSession.mockResolvedValue({ shareToken: 'quiz-tok-1', managerToken: 'mgr-1' });
    voteApi.buildPool.mockResolvedValue({ pool: [] });
    getAttribution.mockReturnValue({ utm_source: 'facebook', utm_medium: 'cpc' });
    getRef.mockReturnValue('ref-abc');
    generateUuid.mockReturnValue('fresh-uuid');
    resolveUserRole.mockReturnValue('organizer');

    // Turnstile is available immediately; capture its success callback so tests can solve the captcha.
    turnstileCallback = undefined;
    window.turnstile = {
        render: (el, opts) => { turnstileCallback = opts.callback; return 'w1'; },
        remove: jest.fn(),
    };

    // Clear sessionStorage between tests
    sessionStorage.clear();

    // jsdom does not implement matchMedia or ResizeObserver — mock both.
    window.matchMedia = jest.fn().mockReturnValue({
        matches: false,
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
    });
    global.ResizeObserver = jest.fn().mockImplementation(() => ({
        observe: jest.fn(),
        disconnect: jest.fn(),
    }));
});

afterEach(() => {
    localStorage.clear();
});

// ---------------------------------------------------------------------------
// A16b — trip_builder_viewed (→ Meta InitiateCheckout)
// Fires when the user lands on the trip-builder/checkout screen with a
// non-empty trip — one funnel step earlier than the "Complete Booking" click.
// ---------------------------------------------------------------------------

test('A16b: trip_builder_viewed fires once on mount when the trip has items', async () => {
    renderTripBuilder(); // default state: 1 item, 2 travelers, tripId 'ctx-trip-id'

    await waitFor(() => {
        const calls = pushEvent.mock.calls.filter(([event]) => event === 'trip_builder_viewed');
        expect(calls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'trip_builder_viewed');
    expect(params.trip_id).toBe('ctx-trip-id');
    expect(params.value).toBe(120); // 60 * 2 travelers
    expect(params.currency).toBe('EUR');
    expect(params.items_count).toBe(1);
    // Plain trip — no vote token involved, so no user_role field at all.
    expect(params).not.toHaveProperty('user_role');
    expect(resolveUserRole).not.toHaveBeenCalled();
});

test('A16b: trip_builder_viewed does NOT fire when the trip is empty', async () => {
    renderTripBuilder(buildTripState({ tripItems: [] }));

    // Let the mount effects run (browse activities fetch fires on mount).
    await waitFor(() => expect(api.getActivities).toHaveBeenCalled());

    const calls = pushEvent.mock.calls.filter(([event]) => event === 'trip_builder_viewed');
    expect(calls).toHaveLength(0);
});

test('A16b: trip_builder_viewed uses voteSession as trip_id when present', async () => {
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/?voteSession=vote-tok-xyz');

    await waitFor(() => {
        const calls = pushEvent.mock.calls.filter(([event]) => event === 'trip_builder_viewed');
        expect(calls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'trip_builder_viewed');
    expect(params.trip_id).toBe('vote-tok-xyz');
    expect(params.user_role).toBe('organizer');
    expect(resolveUserRole).toHaveBeenCalledWith('vote-tok-xyz');
});

test('A16b: trip_builder_viewed uses the stored CART vote session as trip_id when there is no ?voteSession param', async () => {
    localStorage.setItem('myhive-trip-vote-session', 'stored-tok');
    resolveUserRole.mockReturnValue('participant');
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/');

    await waitFor(() => {
        const calls = pushEvent.mock.calls.filter(([event]) => event === 'trip_builder_viewed');
        expect(calls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'trip_builder_viewed');
    expect(params.trip_id).toBe('stored-tok');
    expect(params.user_role).toBe('participant');
    expect(resolveUserRole).toHaveBeenCalledWith('stored-tok');
});

test('A16b: trip_builder_viewed mints a trip_id when none exists', async () => {
    const { dispatchMock } = renderTripBuilder(buildTripState({ tripId: null }), '/');

    await waitFor(() => {
        const calls = pushEvent.mock.calls.filter(([event]) => event === 'trip_builder_viewed');
        expect(calls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'trip_builder_viewed');
    expect(params.trip_id).toBe('fresh-uuid');
    expect(dispatchMock).toHaveBeenCalledWith({ type: 'SET_TRIP_ID', tripId: 'fresh-uuid' });
});

test('A16b: trip_builder_viewed fires only once across re-renders', async () => {
    const state = buildTripState();
    const dispatch = jest.fn();
    const { rerender } = render(
        <MemoryRouter initialEntries={['/']}>
            <TripContext.Provider value={{ state, dispatch }}>
                <TripBuilder destinationId="dest-1" />
            </TripContext.Provider>
        </MemoryRouter>
    );

    await waitFor(() => {
        expect(pushEvent.mock.calls.filter(([e]) => e === 'trip_builder_viewed')).toHaveLength(1);
    });

    rerender(
        <MemoryRouter initialEntries={['/']}>
            <TripContext.Provider value={{ state, dispatch }}>
                <TripBuilder destinationId="dest-1" />
            </TripContext.Provider>
        </MemoryRouter>
    );

    expect(pushEvent.mock.calls.filter(([e]) => e === 'trip_builder_viewed')).toHaveLength(1);
});

// ---------------------------------------------------------------------------
// A17 — booking_form_viewed
// ---------------------------------------------------------------------------

test('A17: booking_form_viewed fires once when the contact form opens', async () => {
    const user = userEvent.setup();
    renderTripBuilder();

    // Click "Complete Booking"
    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

    const viewedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_form_viewed');
    expect(viewedCalls).toHaveLength(1);
    const [, params] = viewedCalls[0];
    // value = 60 * 2 travelers = 120
    expect(params.value).toBe(120);
    expect(params.currency).toBe('EUR');
});

test('A17: booking_form_viewed fires only once even if re-rendered after open', async () => {
    const user = userEvent.setup();
    const state = buildTripState();
    const dispatch = jest.fn();
    const { rerender } = render(
        <MemoryRouter initialEntries={['/']}>
            <TripContext.Provider value={{ state, dispatch }}>
                <TripBuilder destinationId="dest-1" />
            </TripContext.Provider>
        </MemoryRouter>
    );

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

    // Re-render with same state
    rerender(
        <MemoryRouter initialEntries={['/']}>
            <TripContext.Provider value={{ state, dispatch }}>
                <TripBuilder destinationId="dest-1" />
            </TripContext.Provider>
        </MemoryRouter>
    );

    const viewedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_form_viewed');
    expect(viewedCalls).toHaveLength(1);
});

test('A17: booking_form_viewed includes trip_id from context when no voteSession', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

    const viewedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_form_viewed');
    expect(viewedCalls).toHaveLength(1);
    expect(viewedCalls[0][1].trip_id).toBe('ctx-trip-id');
    // Plain trip — no vote token involved, so no user_role field at all.
    expect(viewedCalls[0][1]).not.toHaveProperty('user_role');
});

test('A17: booking_form_viewed uses voteSession as trip_id when param is present', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/?voteSession=vote-tok-xyz');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

    const viewedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_form_viewed');
    expect(viewedCalls).toHaveLength(1);
    expect(viewedCalls[0][1].trip_id).toBe('vote-tok-xyz');
    expect(viewedCalls[0][1].user_role).toBe('organizer');
    expect(resolveUserRole).toHaveBeenCalledWith('vote-tok-xyz');
});

test('A17: booking_form_viewed does not fire a second time on rapid double-click (sessionStorage dedup)', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }));

    // First click — fires the event and sets the sessionStorage flag
    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

    // Close the form and click again for the same trip_id
    await user.keyboard('{Escape}');
    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

    const viewedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_form_viewed');
    expect(viewedCalls).toHaveLength(1);
});

// ---------------------------------------------------------------------------
// A18 — booking_submitted
// ---------------------------------------------------------------------------

test('A18: booking_submitted fires after a successful createBookingFromTrip', async () => {
    const user = userEvent.setup();
    renderTripBuilder();

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_submitted');
        expect(submittedCalls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'booking_submitted');
    // value = 60 * 2 = 120
    expect(params.value).toBe(120);
    expect(params.currency).toBe('EUR');
    expect(params.activities_count).toBe(1);
    expect(params.destination).toBe('prague');
    expect(params.group_size).toBe(2);
    expect(params.trip_id).toBe('ctx-trip-id');
    expect(params.utm_source).toBe('facebook');
    expect(params.utm_medium).toBe('cpc');
    expect(params.ref).toBe('ref-abc');
});

test('A18: booking_submitted does NOT fire on createBookingFromTrip failure', async () => {
    api.createBookingFromTrip.mockRejectedValue(new Error('Server error'));
    const user = userEvent.setup();
    renderTripBuilder();

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        // Error should appear (may render in multiple places — form-error + submitError div)
        expect(screen.getAllByText(/Server error/i).length).toBeGreaterThan(0);
    });

    const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_submitted');
    expect(submittedCalls).toHaveLength(0);
});

test('A18: booking_submitted uses voteSession as trip_id when param is present', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/?voteSession=vote-tok-xyz');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_submitted');
        expect(submittedCalls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'booking_submitted');
    expect(params.trip_id).toBe('vote-tok-xyz');
    expect(params.user_role).toBe('organizer');
    expect(resolveUserRole).toHaveBeenCalledWith('vote-tok-xyz');
});

test('A18: booking_submitted uses context tripId when no voteSession', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_submitted');
        expect(submittedCalls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'booking_submitted');
    expect(params.trip_id).toBe('ctx-trip-id');
    // Plain trip — no vote token involved, so no user_role field at all.
    expect(params).not.toHaveProperty('user_role');
});

test('A18: booking_submitted mints a fresh uuid and dispatches SET_TRIP_ID when both voteSession and context tripId are absent', async () => {
    const user = userEvent.setup();
    const dispatch = jest.fn();
    render(
        <MemoryRouter initialEntries={['/']}>
            <TripContext.Provider value={{ state: buildTripState({ tripId: null }), dispatch }}>
                <TripBuilder destinationId="dest-1" />
            </TripContext.Provider>
        </MemoryRouter>
    );

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_submitted');
        expect(submittedCalls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'booking_submitted');
    expect(params.trip_id).toBe('fresh-uuid');

    expect(dispatch).toHaveBeenCalledWith({ type: 'SET_TRIP_ID', tripId: 'fresh-uuid' });
});

test('A18: booking_submitted sessionStorage dedup prevents double-fire on second submit with same trip_id', async () => {
    const user = userEvent.setup();
    renderTripBuilder();

    // First submit
    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        expect(pushEvent.mock.calls.filter(([e]) => e === 'booking_submitted')).toHaveLength(1);
    });

    // Manually re-open the form (success modal closes the form in the real flow,
    // but we can simulate by clicking Complete Booking again after clearing via context).
    // The key check: the sessionStorage flag is set so a second submit for the same
    // trip_id does NOT re-fire the event.
    expect(sessionStorage.getItem('myhive-booked-ctx-trip-id')).toBe('1');

    // Simulate a second successful API call (same trip_id) — booking_submitted must NOT fire again.
    // We call the flag-check logic directly via the sessionStorage key.
    // Fire pushEvent manually to confirm our dedup logic: if the key is set, no event.
    const key = 'myhive-booked-ctx-trip-id';
    expect(sessionStorage.getItem(key)).toBe('1');

    // Submit again (re-open form)
    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        // Still only 1 call — dedup prevents the second
        expect(pushEvent.mock.calls.filter(([e]) => e === 'booking_submitted')).toHaveLength(1);
    });
});

// ---------------------------------------------------------------------------
// A19 — booking request body includes tripId, attribution, ref
// ---------------------------------------------------------------------------

test('A19: createBookingFromTrip is called with tripId, attribution, and ref', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        expect(api.createBookingFromTrip).toHaveBeenCalledTimes(1);
    });

    const [bookingData] = api.createBookingFromTrip.mock.calls[0];
    expect(bookingData.tripId).toBe('ctx-trip-id');
    expect(bookingData.utm_source).toBe('facebook');
    expect(bookingData.utm_medium).toBe('cpc');
    expect(bookingData.ref).toBe('ref-abc');
});

test('A19: createBookingFromTrip uses voteSession as tripId when param is set', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/?voteSession=vote-tok-xyz');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        expect(api.createBookingFromTrip).toHaveBeenCalledTimes(1);
    });

    const [bookingData] = api.createBookingFromTrip.mock.calls[0];
    expect(bookingData.tripId).toBe('vote-tok-xyz');
});

test('A19: createBookingFromTrip includes ref: null when getRef returns null', async () => {
    getRef.mockReturnValue(null);
    const user = userEvent.setup();
    renderTripBuilder();

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        expect(api.createBookingFromTrip).toHaveBeenCalledTimes(1);
    });

    const [bookingData] = api.createBookingFromTrip.mock.calls[0];
    expect(bookingData.ref).toBeNull();
});

// ---------------------------------------------------------------------------
// A19 — multi-activity trip: value uses computeTripTotal
// ---------------------------------------------------------------------------

test('A18+A19: multi-activity trip computes correct value and activities_count', async () => {
    const user = userEvent.setup();
    const state = buildTripState({
        tripItems: [activity1, activity2],
        tripTravelers: 3,
        tripId: 'ctx-trip-id',
    });
    renderTripBuilder(state, '/');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    await waitFor(() => {
        const submittedCalls = pushEvent.mock.calls.filter(([e]) => e === 'booking_submitted');
        expect(submittedCalls).toHaveLength(1);
    });

    const [, params] = pushEvent.mock.calls.find(([e]) => e === 'booking_submitted');
    // (60 + 40) * 3 = 300
    expect(params.value).toBe(300);
    expect(params.activities_count).toBe(2);
    expect(params.group_size).toBe(3);
});

// ---------------------------------------------------------------------------
// Inline booking form — replaces the browse column (was a popup modal)
// ---------------------------------------------------------------------------

describe('inline booking form in the browse column', () => {
    test('Complete Booking swaps Browse More Activities for the form; Cancel restores it', async () => {
        const user = userEvent.setup();
        renderTripBuilder();

        expect(screen.getByText('Browse More Activities')).toBeInTheDocument();

        await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

        expect(screen.queryByText('Browse More Activities')).not.toBeInTheDocument();
        expect(screen.getByText('Complete Your Booking')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); // inline panel, not a popup

        await user.click(screen.getByRole('button', {name: 'Cancel'}));

        expect(screen.getByText('Browse More Activities')).toBeInTheDocument();
        expect(screen.queryByText('Complete Your Booking')).not.toBeInTheDocument();
    });

    test('opening the form on mobile scrolls it to sit right below the fixed header', async () => {
        const user = userEvent.setup();
        window.matchMedia = jest.fn().mockReturnValue({
            matches: true, // stacked mobile layout
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
        });
        window.scrollTo = jest.fn();
        // Fixed site header (main bar + breadcrumbs row) — its real height must be
        // subtracted so the section top lands exactly below it, not underneath it.
        const expectedHeaderHeight = 93;
        const header = document.createElement('header');
        header.className = 'header';
        Object.defineProperty(header, 'offsetHeight', {value: expectedHeaderHeight});
        document.body.appendChild(header);

        renderTripBuilder();
        await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

        // jsdom geometry: column top = 0, scrollY = 0 → target = 0 - headerHeight.
        expect(window.scrollTo).toHaveBeenCalledWith({top: -expectedHeaderHeight, behavior: 'smooth'});
        header.remove();
    });

    test('every Complete Booking click re-scrolls on mobile, even while the form is already open', async () => {
        const user = userEvent.setup();
        window.matchMedia = jest.fn().mockReturnValue({
            matches: true,
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
        });
        window.scrollTo = jest.fn();

        renderTripBuilder();

        await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
        expect(window.scrollTo).toHaveBeenCalledTimes(1);

        // The form stays open; a second click must scroll back down to it again.
        await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
        expect(window.scrollTo).toHaveBeenCalledTimes(2);
    });

    test('desktop does not auto-scroll — the form is already visible in the right column', async () => {
        const user = userEvent.setup();
        window.scrollTo = jest.fn();

        renderTripBuilder(); // default matchMedia mock: matches=false (desktop)
        await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

        expect(window.scrollTo).not.toHaveBeenCalled();
    });
});

// ---------------------------------------------------------------------------
// Trip Builder 30% deposit — offered on the success screen after the lead submit
// ---------------------------------------------------------------------------

test('deposit: the success screen hides the 30% deposit CTA while payment is disabled', async () => {
    const user = userEvent.setup();

    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    await fillAndSubmitContactForm(user);

    // The lead is still created on submit...
    await waitFor(() => expect(api.createBookingFromTrip).toHaveBeenCalledTimes(1));

    // ...but online payment is temporarily disabled, so the success screen offers no deposit CTA.
    expect(await screen.findByText(/What happens next/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /pay 30% deposit/i})).not.toBeInTheDocument();
    expect(paymentApi.createBookingDepositSession).not.toHaveBeenCalled();
});

// ---------------------------------------------------------------------------
// Group minimum — floored itinerary line display
// ---------------------------------------------------------------------------

describe('itinerary line price label', () => {
    test('floored line shows the group minimum total with a marker', async () => {
        renderTripBuilder(buildTripState({
            tripItems: [{id: 'a1', name: 'Boat Rental', price: 50, minPrice: 300, destinationSlug: 'tenerife'}],
            tripTravelers: 4,
            tripBuilderModalOpen: true,
        }));

        // 4 × €50 = €200 -> floored to €300
        expect(await screen.findByText('€50 × 4 = €300 (group min)')).toBeInTheDocument();
    });

    test('regular line keeps the per-person math', async () => {
        renderTripBuilder(buildTripState({
            tripItems: [{id: 'a1', name: 'Bar Crawl', price: 40, destinationSlug: 'tenerife'}],
            tripTravelers: 2,
            tripBuilderModalOpen: true,
        }));

        expect(await screen.findByText('€40 × 2 = €80')).toBeInTheDocument();
    });
});

// ---------------------------------------------------------------------------
// "Let your mates vote" button — visibility + enabled/disabled state
// ---------------------------------------------------------------------------

describe('Let your mates vote button', () => {
    test('shows an enabled button when the cart has standalone activities', () => {
        renderTripBuilder(buildTripState({ tripItems: [activity1] }));

        expect(screen.getByRole('button', { name: 'Let your mates vote' })).toBeEnabled();
    });

    test('hides the button when the cart only contains package items', () => {
        renderTripBuilder(buildTripState({
            tripItems: [
                { id: 'a-1', name: 'Karting', price: 50, packageId: 'p-1', packageName: 'Mayhem', packageDiscountPct: 10 },
            ],
        }));

        expect(screen.queryByRole('button', { name: 'Let your mates vote' })).not.toBeInTheDocument();
    });

    test('disables the button when standalone items span another destination', () => {
        renderTripBuilder(buildTripState({
            tripItems: [
                activity1,
                { id: 'a-2', name: 'Techno Tour', price: 50, destinationSlug: 'berlin' },
            ],
        }));

        const voteButton = screen.getByRole('button', { name: 'Let your mates vote' });
        expect(voteButton).toBeDisabled();
        expect(voteButton).toHaveAttribute(
            'title',
            'Group voting works for one destination at a time — remove activities from other destinations first.'
        );
    });
});

// ---------------------------------------------------------------------------
// cta_click on the "Let your mates vote" button
// ---------------------------------------------------------------------------

describe('cta_click on "Let your mates vote"', () => {
    test('fires cta_click with cta_label and block on every click', async () => {
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripItems: [activity1, activity2] }));

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(pushEvent).toHaveBeenCalledWith('cta_click', {
            cta_label: 'Let your mates vote',
            block: 'trip_builder',
        });
    });
});

// ---------------------------------------------------------------------------
// Active vote guard — block starting a new vote while one is already running
// ---------------------------------------------------------------------------

describe('active vote guard', () => {
    test('an ACTIVE CART session blocks the create-modal and shows the active-vote modal instead (still firing cta_click)', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getSession.mockResolvedValue({ status: 'ACTIVE', voteMode: 'CART' });
        const user = userEvent.setup();
        renderTripBuilder();

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByText('A vote is already running')).toBeInTheDocument();
        expect(screen.queryByLabelText('Your email')).not.toBeInTheDocument();
        // cta_click fires on every click regardless of which modal ends up opening.
        expect(pushEvent).toHaveBeenCalledWith('cta_click', {
            cta_label: 'Let your mates vote',
            block: 'trip_builder',
        });
    });

    test('a COMPLETED session lets the create-modal open as usual', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getSession.mockResolvedValue({ status: 'COMPLETED', voteMode: 'CART' });
        const user = userEvent.setup();
        renderTripBuilder();

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByLabelText('Your email')).toBeInTheDocument();
        expect(screen.queryByText('A vote is already running')).not.toBeInTheDocument();
    });

    test('a rejected session lookup self-heals: the stored key is dropped and the create-modal opens', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getSession.mockRejectedValue(new Error('Failed to fetch vote session'));
        const user = userEvent.setup();
        renderTripBuilder();

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByLabelText('Your email')).toBeInTheDocument();
        expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
    });

    test('no stored session opens the create-modal directly without calling getSession', async () => {
        const user = userEvent.setup();
        renderTripBuilder();

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByLabelText('Your email')).toBeInTheDocument();
        expect(voteApi.getSession).not.toHaveBeenCalled();
    });
});

// ---------------------------------------------------------------------------
// Cart vote annotation — badges, vote-descending sort, no-hydration guard
// ---------------------------------------------------------------------------

describe('cart vote annotation', () => {
    const cartResult = {
        voteMode: 'CART',
        participantCount: 9,
        result: [
            { activityId: 'a-low', name: 'Tiki Boat', price: 60, likeCount: 2 },
            { activityId: 'a-high', name: 'Bar Crawl', price: 45, likeCount: 8 },
        ],
    };

    test('badges standalone items and sorts them by votes descending', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getResult.mockResolvedValue(cartResult);

        renderTripBuilder(buildTripState({
            tripItems: [
                { id: 'a-low', name: 'Tiki Boat', price: 60, destinationSlug: 'prague' },
                { id: 'a-high', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' },
            ],
        }));

        expect(await screen.findByText('♥ 8')).toBeInTheDocument();
        const titles = document.querySelectorAll('.itinerary-item .itinerary-item-title');
        expect(titles[0]).toHaveTextContent('Bar Crawl');
        expect(titles[1]).toHaveTextContent('Tiki Boat');
    });

    test('CART result never dispatches ADD_TO_TRIP (no cart hydration)', async () => {
        voteApi.getResult.mockResolvedValue(cartResult);
        const dispatch = jest.fn();
        // Render with ?voteSession=t-1 in the router entry and the dispatch spy:
        renderTripBuilderWithDispatch(
            [{ id: 'a-high', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' }],
            dispatch,
            { route: '/?voteSession=t-1' },
        );

        expect(await screen.findByText('♥ 8')).toBeInTheDocument();
        expect(dispatch).not.toHaveBeenCalledWith(
            expect.objectContaining({ type: 'ADD_TO_TRIP' }),
        );
    });

    test('self-heals a stale stored vote session (backend 7-day cleanup) without showing an error', async () => {
        // Storage-only token (no ?voteSession= URL param) whose session the backend
        // has already cleaned up — getResult now reports it as gone.
        localStorage.setItem('myhive-trip-vote-session', 't-stale');
        voteApi.getResult.mockRejectedValue(new Error('Vote session not found'));

        renderTripBuilder(buildTripState({
            tripItems: [{ id: 'a-1', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' }],
        }));

        await waitFor(() => {
            expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
        });
        expect(screen.queryByText(/Couldn't load your group's vote results/i)).not.toBeInTheDocument();
    });
});

// ---------------------------------------------------------------------------
// Completed cart vote parks the vote button until booking or an emptied cart
// ---------------------------------------------------------------------------

describe('vote button after a completed cart vote', () => {
    const cartResult = {
        voteMode: 'CART',
        participantCount: 9,
        result: [{ activityId: 'a-high', name: 'Bar Crawl', price: 45, likeCount: 8 }],
    };
    const cartItem = { id: 'a-high', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' };

    function LocationProbe() {
        const location = useLocation();
        return <div data-testid="location">{location.pathname + location.search}</div>;
    }

    // Tree builder (not a render helper) so tests can rerender with a changed
    // cart while MemoryRouter keeps its history across the rerender.
    function buildTree(tripItems, dispatch, route = '/') {
        const state = {
            tripId: null,
            tripItems,
            tripTravelers: 2,
            tripStartDate: '',
            tripEndDate: '',
            tripBudget: null,
            tripSetupModalOpen: false,
            tripBuilderModalOpen: false,
        };
        return (
            <MemoryRouter initialEntries={[route]}>
                <TripContext.Provider value={{ state, dispatch }}>
                    <TripBuilder destinationId="dest-1" destinationSlug="prague" />
                    <LocationProbe />
                </TripContext.Provider>
            </MemoryRouter>
        );
    }

    test('is hidden once the completed vote result is annotated', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getResult.mockResolvedValue(cartResult);

        renderTripBuilder(buildTripState({ tripItems: [cartItem] }));

        expect(await screen.findByText('♥ 8')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Let your mates vote' })).not.toBeInTheDocument();
    });

    test('emptying the cart drops the finished session and re-enables voting for the next trip', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getResult.mockResolvedValue(cartResult);
        const dispatch = jest.fn();
        const { rerender } = render(buildTree([cartItem], dispatch));
        expect(await screen.findByText('♥ 8')).toBeInTheDocument();

        rerender(buildTree([], dispatch));

        await waitFor(() => {
            expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
        });

        rerender(buildTree([cartItem], dispatch));
        expect(screen.getByRole('button', { name: 'Let your mates vote' })).toBeEnabled();
    });

    test('emptying the cart strips the voteSession URL param so a refresh stays reset', async () => {
        voteApi.getResult.mockResolvedValue(cartResult);
        const dispatch = jest.fn();
        const { rerender } = render(buildTree([cartItem], dispatch, '/?voteSession=t-1'));
        expect(await screen.findByText('♥ 8')).toBeInTheDocument();
        expect(screen.getByTestId('location').textContent).toBe('/?voteSession=t-1');

        rerender(buildTree([], dispatch, '/?voteSession=t-1'));

        await waitFor(() => {
            expect(screen.getByTestId('location').textContent).toBe('/');
        });
    });
});

// ---------------------------------------------------------------------------
// Quiz mode — Start Over (organizer arrived from the quiz/swipe flow)
// ---------------------------------------------------------------------------

describe('quiz mode: Start Over', () => {
    const quizSetup = {
        destination: { id: 'dest-1', slug: 'prague' },
        travelers: 4,
        startDate: '2026-09-01',
        endDate: '2026-09-05',
        email: 'organizer@example.com',
        budget: 2000,
    };

    function seedQuizFlow(setupOverrides = {}) {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: { ...quizSetup, ...setupOverrides },
            responses: [{ questionId: 'q1', answerId: 'a1' }],
        }));
    }

    function QuizLocationProbe() {
        const location = useLocation();
        return <div data-testid="quiz-location">{location.pathname}</div>;
    }

    function renderQuizTripBuilder(tripState = buildTripState(), dispatch = jest.fn()) {
        render(
            <MemoryRouter initialEntries={['/']}>
                <TripContext.Provider value={{ state: tripState, dispatch }}>
                    <TripBuilder destinationId="dest-1" destinationSlug="prague" />
                    <QuizLocationProbe />
                </TripContext.Provider>
            </MemoryRouter>
        );
        return dispatch;
    }

    test('Start Over shows in quiz mode, clears the trip, and restarts the quiz', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        const dispatch = renderQuizTripBuilder();

        await user.click(screen.getByRole('button', { name: 'Start Over' }));

        expect(pushEvent).toHaveBeenCalledWith('cta_click', {
            cta_label: 'Start Over',
            block: 'trip_builder',
        });
        expect(dispatch).toHaveBeenCalledWith({ type: 'CANCEL_TRIP_SETUP' });
        expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 1 });
        expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_DATES', startDate: '', endDate: '' });
        expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
        expect(screen.getByTestId('quiz-location')).toHaveTextContent('/vote/new/quiz');
    });

    test('Start Over is absent without a stored quiz flow', () => {
        renderQuizTripBuilder();
        expect(screen.queryByRole('button', { name: 'Start Over' })).not.toBeInTheDocument();
    });

    test('Start Over is absent when the stored quiz flow is for another destination', () => {
        seedQuizFlow({ destination: { id: 'other-dest', slug: 'berlin' } });
        renderQuizTripBuilder();
        expect(screen.queryByRole('button', { name: 'Start Over' })).not.toBeInTheDocument();
    });

    test('Start Over still shows when the cart is empty in quiz mode', async () => {
        seedQuizFlow();
        renderQuizTripBuilder(buildTripState({ tripItems: [] }));
        // findByRole (not getByRole): quiz mode now also kicks off the
        // Recommended-for-you pool fetch on mount — await it settling so the
        // effect's state update lands inside act() like the other quiz-mode tests.
        expect(await screen.findByRole('button', { name: 'Start Over' })).toBeInTheDocument();
    });
});

// ---------------------------------------------------------------------------
// Quiz mode — one-click vote creation (no modal; QUIZ session from the cart)
// ---------------------------------------------------------------------------

describe('quiz mode: one-click vote', () => {
    const quizSetup = {
        destination: { id: 'dest-1', slug: 'prague' },
        travelers: 4,
        startDate: '2026-09-01',
        endDate: '2026-09-05',
        email: 'organizer@example.com',
        budget: 2000,
    };
    const quizResponses = [{ questionId: 'q1', answerId: 'a1' }];

    function seedQuizFlow() {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: quizSetup,
            responses: quizResponses,
        }));
    }

    function VoteLocationProbe() {
        const location = useLocation();
        return <div data-testid="vote-location">{location.pathname}</div>;
    }

    function renderQuizTripBuilder(tripState, dispatch = jest.fn()) {
        render(
            <MemoryRouter initialEntries={['/']}>
                <TripContext.Provider value={{ state: tripState, dispatch }}>
                    <TripBuilder destinationId="dest-1" destinationSlug="prague" />
                    <VoteLocationProbe />
                </TripContext.Provider>
            </MemoryRouter>
        );
    }

    test('creates a QUIZ session from the current cart without a modal', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState({
            tripItems: [activity1, activity2],
            tripTravelers: 3,
            tripStartDate: '2026-09-01',
            tripEndDate: '2026-09-05',
            tripBudget: 2000,
        }));

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        // No email modal — the session is created directly.
        expect(screen.queryByLabelText('Your email')).not.toBeInTheDocument();
        await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledTimes(1));

        const arg = voteApi.createSession.mock.calls[0][0];
        expect(arg).toMatchObject({
            destinationId: 'dest-1',
            initiatorEmail: 'organizer@example.com',
            numberOfTravelers: 3,
            startDate: '2026-09-01',
            endDate: '2026-09-05',
            budget: 2000,
            quizResponses,
            activityIds: ['act-1', 'act-2'],
        });
        expect(typeof arg.voterToken).toBe('string');

        // cta_click intent + A12 vote_launched conversion, same payloads as before.
        expect(pushEvent).toHaveBeenCalledWith('cta_click', {
            cta_label: 'Let your mates vote',
            block: 'trip_builder',
        });
        expect(pushEvent).toHaveBeenCalledWith('vote_launched', {
            trip_id: 'quiz-tok-1',
            user_role: 'organizer',
            selected_count: 2,
        });

        // Organizer markers + context cleanup + waiting-page navigation.
        expect(localStorage.getItem('myhive-initiator-quiz-tok-1')).toBe('true');
        expect(localStorage.getItem('myhive-manager-quiz-tok-1')).toBe('mgr-1');
        expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
        await waitFor(() => {
            expect(screen.getByTestId('vote-location')).toHaveTextContent('/vote/quiz-tok-1/waiting');
        });
    });

    test('createSession failure surfaces the error and keeps the quiz context', async () => {
        // console.error now fires on this path (parity with handleContactSubmit) —
        // expected noise for this failure-path test, silenced like other tests
        // that deliberately exercise a logged error branch.
        const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
        seedQuizFlow();
        voteApi.createSession.mockRejectedValue(new Error('Server exploded'));
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState());

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByText('Server exploded')).toBeInTheDocument();
        expect(pushEvent).not.toHaveBeenCalledWith('vote_launched', expect.anything());
        expect(sessionStorage.getItem('myhive-quiz-flow')).not.toBeNull();
        expect(screen.getByTestId('vote-location')).toHaveTextContent('/');

        consoleErrorSpy.mockRestore();
    });

    test('outside quiz mode the button still opens the CART modal', async () => {
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState());

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByLabelText('Your email')).toBeInTheDocument();
        expect(voteApi.createSession).not.toHaveBeenCalled();
    });

    test('an ACTIVE CART session blocks quiz-mode vote creation and shows the active-vote modal instead', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getSession.mockResolvedValue({ status: 'ACTIVE', voteMode: 'CART' });
        seedQuizFlow();
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState());

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        expect(await screen.findByText('A vote is already running')).toBeInTheDocument();
        expect(voteApi.createSession).not.toHaveBeenCalled();
        expect(sessionStorage.getItem('myhive-quiz-flow')).not.toBeNull();
    });

    test('a stored token whose session lookup fails self-heals: stale key dropped, quiz creation proceeds', async () => {
        localStorage.setItem('myhive-trip-vote-session', 't-1');
        voteApi.getSession.mockRejectedValue(new Error('Failed to fetch vote session'));
        seedQuizFlow();
        const user = userEvent.setup();
        renderQuizTripBuilder(buildTripState());

        await user.click(screen.getByRole('button', { name: 'Let your mates vote' }));

        await waitFor(() => expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull());
        await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledTimes(1));
    });
});

// ---------------------------------------------------------------------------
// Quiz mode — A13 vote_skipped moves to the Complete Booking click
// ---------------------------------------------------------------------------

describe('quiz mode: vote_skipped on Complete Booking', () => {
    function seedQuizFlow() {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: {
                destination: { id: 'dest-1', slug: 'prague' },
                travelers: 4,
                startDate: '2026-09-01',
                endDate: '2026-09-05',
                email: 'organizer@example.com',
                budget: 2000,
            },
            responses: [{ questionId: 'q1', answerId: 'a1' }],
        }));
    }

    test('A13: fires with trip_id and selected_count when quiz mode is active', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id', tripItems: [activity1, activity2] }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));

        expect(pushEvent).toHaveBeenCalledWith('vote_skipped', {
            trip_id: 'ctx-trip-id',
            selected_count: 2,
        });
    });

    test('A13: dedups per trip_id across repeat clicks', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));
        await user.keyboard('{Escape}');
        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));

        expect(pushEvent.mock.calls.filter(([e]) => e === 'vote_skipped')).toHaveLength(1);
    });

    test('A13: does not fire outside quiz mode', async () => {
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));

        expect(pushEvent).not.toHaveBeenCalledWith('vote_skipped', expect.anything());
    });

    test('booking submit clears the quiz-flow context', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));
        await fillAndSubmitContactForm(user);

        await waitFor(() => {
            expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
        });
    });

    test('A13: selected_count counts standalone picks only — package items do not skew it', async () => {
        seedQuizFlow();
        const user = userEvent.setup();
        const packageItem = {
            id: 'p-a',
            name: 'Pkg Act',
            price: 50,
            packageId: 'p-1',
            packageName: 'Mayhem',
            packageDiscountPct: 10,
        };
        renderTripBuilder(buildTripState({
            tripId: 'ctx-trip-id',
            tripItems: [activity1, packageItem],
        }));

        await user.click(screen.getByRole('button', { name: /Complete Booking/i }));

        expect(pushEvent).toHaveBeenCalledWith('vote_skipped', {
            trip_id: 'ctx-trip-id',
            selected_count: 1,
        });
    });
});

// ---------------------------------------------------------------------------
// Quiz mode — Recommended for you (quiz-matched pool above Browse)
// ---------------------------------------------------------------------------

describe('quiz mode: Recommended for you', () => {
    const quizResponses = [{ questionId: 'q1', answerId: 'a1' }];

    function seedQuizFlow() {
        sessionStorage.setItem('myhive-quiz-flow', JSON.stringify({
            setup: {
                destination: { id: 'dest-1', slug: 'prague' },
                travelers: 4,
                startDate: '2026-09-01',
                endDate: '2026-09-05',
                email: 'organizer@example.com',
                budget: 2000,
            },
            responses: quizResponses,
        }));
    }

    const recommendedPool = [
        // Already in the default cart (activity1 = act-1 "Kayaking") → Added state.
        { activityId: 'act-1', name: 'Kayaking', price: 60, imageUrl: null, slug: 'kayak', destinationSlug: 'prague', categories: ['Water'] },
        { activityId: 'rec-9', name: 'Beer Spa', price: 90, imageUrl: null, slug: 'beer-spa', destinationSlug: 'prague', description: 'Bathe in beer.', categories: ['Chillout'] },
    ];

    test('renders the quiz-matched pool with Add / Added states', async () => {
        seedQuizFlow();
        voteApi.buildPool.mockResolvedValue({ pool: recommendedPool });
        renderTripBuilder();

        expect(await screen.findByText('Recommended for you')).toBeInTheDocument();
        expect(voteApi.buildPool).toHaveBeenCalledWith({
            destinationId: 'dest-1',
            responses: quizResponses,
        });

        expect(screen.getByRole('button', { name: 'Beer Spa' })).toBeInTheDocument();
        const addButtons = screen.getAllByRole('button', { name: 'Add' });
        expect(addButtons).toHaveLength(1); // rec-9 only
        expect(screen.getByRole('button', { name: 'Added' })).toBeDisabled(); // act-1 is in the cart
    });

    test('Add dispatches a silent ADD_TO_TRIP with mapped categories', async () => {
        seedQuizFlow();
        voteApi.buildPool.mockResolvedValue({ pool: recommendedPool });
        const user = userEvent.setup();
        const { dispatchMock } = renderTripBuilder();

        await screen.findByText('Recommended for you');
        await user.click(screen.getByRole('button', { name: 'Add' }));

        expect(dispatchMock).toHaveBeenCalledWith({
            type: 'ADD_TO_TRIP',
            silent: true,
            activity: expect.objectContaining({
                id: 'rec-9',
                name: 'Beer Spa',
                categories: [{ name: 'Chillout' }],
            }),
        });
    });

    test('clicking a recommendation name opens the preview modal instead of navigating', async () => {
        seedQuizFlow();
        voteApi.buildPool.mockResolvedValue({ pool: recommendedPool });
        const user = userEvent.setup();
        renderTripBuilder();

        await screen.findByText('Recommended for you');
        await user.click(screen.getByRole('button', { name: 'Beer Spa' }));

        expect(screen.getByText('Bathe in beer.')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /View full page/i }))
            .toHaveAttribute('href', '/destination/prague/activity/beer-spa');
    });

    test('no section and no pool fetch outside quiz mode', async () => {
        renderTripBuilder();

        await waitFor(() => expect(api.getActivities).toHaveBeenCalled());
        expect(screen.queryByText('Recommended for you')).not.toBeInTheDocument();
        expect(voteApi.buildPool).not.toHaveBeenCalled();
    });
});

// ---------------------------------------------------------------------------
// Vote button after a completed QUIZ vote — hidden until booking or empty cart
// ---------------------------------------------------------------------------

describe('vote button after a completed QUIZ vote', () => {
    const quizResult = {
        voteMode: 'QUIZ',
        participantCount: 3,
        numberOfTravelers: 2,
        result: [{ activityId: 'a-1', name: 'Bar Crawl', price: 45, slug: 'bar', destinationSlug: 'prague' }],
        suggestions: [],
    };
    const cartItem = { id: 'a-1', name: 'Bar Crawl', price: 45, destinationSlug: 'prague' };

    function QuizEndedLocationProbe() {
        const location = useLocation();
        return <div data-testid="quiz-ended-location">{location.pathname + location.search}</div>;
    }

    // Tree builder (not a render helper) so tests can rerender with a changed
    // cart while MemoryRouter keeps its history across the rerender.
    function buildTree(tripItems, dispatch, route = '/?voteSession=q-1') {
        const state = {
            tripId: null,
            tripItems,
            tripTravelers: 2,
            tripStartDate: '',
            tripEndDate: '',
            tripBudget: null,
            tripSetupModalOpen: false,
            tripBuilderModalOpen: false,
        };
        return (
            <MemoryRouter initialEntries={[route]}>
                <TripContext.Provider value={{ state, dispatch }}>
                    <TripBuilder destinationId="dest-1" destinationSlug="prague" />
                    <QuizEndedLocationProbe />
                </TripContext.Provider>
            </MemoryRouter>
        );
    }

    test('is hidden while the finished vote result is loaded', async () => {
        voteApi.getResult.mockResolvedValue(quizResult);
        const dispatch = jest.fn();
        render(buildTree([cartItem], dispatch));

        // QUIZ hydration ran (result rows seed the cart via ADD_TO_TRIP).
        await waitFor(() => {
            expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: 'ADD_TO_TRIP' }));
        });
        expect(screen.queryByRole('button', { name: 'Let your mates vote' })).not.toBeInTheDocument();
    });

    test('emptying the cart strips the param and restores the button for the next trip', async () => {
        voteApi.getResult.mockResolvedValue(quizResult);
        const dispatch = jest.fn();
        const { rerender } = render(buildTree([cartItem], dispatch));
        await waitFor(() => {
            expect(dispatch).toHaveBeenCalledWith(expect.objectContaining({ type: 'ADD_TO_TRIP' }));
        });

        rerender(buildTree([], dispatch));
        await waitFor(() => {
            expect(screen.getByTestId('quiz-ended-location').textContent).toBe('/');
        });

        rerender(buildTree([cartItem], dispatch));
        expect(screen.getByRole('button', { name: 'Let your mates vote' })).toBeEnabled();
    });
});
