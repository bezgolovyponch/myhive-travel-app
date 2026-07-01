import {act, render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
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
    },
}));

jest.mock('../services/paymentApi', () => ({
    paymentApi: { createTripDepositSession: jest.fn() },
}));

const voteApi = require('../services/voteApi').default;
const { paymentApi } = require('../services/paymentApi');
// Captures the Turnstile success callback so tests can simulate a solved captcha.
let turnstileCallback;

jest.mock('../utils/analytics', () => ({ pushEvent: jest.fn() }));

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

function renderTripBuilder(tripState = buildTripState(), route = '/', dispatchMock = jest.fn()) {
    const result = render(
        <MemoryRouter initialEntries={[route]}>
            <TripContext.Provider value={{ state: tripState, dispatch: dispatchMock }}>
                <TripBuilder destinationId="dest-1" />
            </TripContext.Provider>
        </MemoryRouter>
    );
    return { ...result, dispatchMock };
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
    // Lead path ("we'll call you") — distinct from the deposit button which also says "Complete booking".
    await user.click(screen.getByRole('button', {name: /call you/i}));
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
    getAttribution.mockReturnValue({ utm_source: 'facebook', utm_medium: 'cpc' });
    getRef.mockReturnValue('ref-abc');
    generateUuid.mockReturnValue('fresh-uuid');

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
});

test('A17: booking_form_viewed uses voteSession as trip_id when param is present', async () => {
    const user = userEvent.setup();
    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/?voteSession=vote-tok-xyz');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));

    const viewedCalls = pushEvent.mock.calls.filter(([event]) => event === 'booking_form_viewed');
    expect(viewedCalls).toHaveLength(1);
    expect(viewedCalls[0][1].trip_id).toBe('vote-tok-xyz');
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
// Trip Builder 30% deposit — Stripe Checkout (Turnstile-gated)
// ---------------------------------------------------------------------------

test('deposit: solving Turnstile enables the deposit button, which opens Stripe Checkout with the booking + token', async () => {
    const user = userEvent.setup();
    paymentApi.createTripDepositSession.mockResolvedValue({ bookingId: 'dep-1', checkoutUrl: 'https://checkout/cs_dep' });
    const assign = jest.fn();
    Object.defineProperty(window, 'location', { configurable: true, value: { assign, href: '' } });

    renderTripBuilder(buildTripState({ tripId: 'ctx-trip-id' }), '/');

    await user.click(screen.getByRole('button', {name: /Complete Booking/i}));
    // Fill the contact fields (without submitting the lead).
    await user.type(screen.getByLabelText(/Full Name/i), 'Jane Smith');
    await user.type(screen.getByLabelText(/Email Address/i), 'jane@example.com');
    await user.type(screen.getByLabelText(/Phone Number/i), '+1 555 000 1111');
    await user.type(screen.getByTestId('date-from'), '2026-08-01');
    await user.type(screen.getByTestId('date-to'), '2026-08-07');

    const depositBtn = screen.getByRole('button', {name: /pay 30% deposit/i});
    expect(depositBtn).toBeDisabled(); // no captcha yet

    act(() => { turnstileCallback('tok-xyz'); });
    expect(depositBtn).toBeEnabled();

    await user.click(depositBtn);

    await waitFor(() => expect(paymentApi.createTripDepositSession).toHaveBeenCalledTimes(1));
    const [bookingData, token] = paymentApi.createTripDepositSession.mock.calls[0];
    expect(token).toBe('tok-xyz');
    expect(bookingData.tripId).toBe('ctx-trip-id');
    expect(bookingData.destinations[0].activities[0].activityId).toBe('act-1');
    expect(assign).toHaveBeenCalledWith('https://checkout/cs_dep');
    // The lead endpoint must NOT be called for a deposit checkout.
    expect(api.createBookingFromTrip).not.toHaveBeenCalled();
});
