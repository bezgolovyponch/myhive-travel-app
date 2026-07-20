import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import VoteResultPage from './VoteResultPage';
import voteApi from '../../services/voteApi';
import { paymentApi } from '../../services/paymentApi';
import { pushEvent } from '../../utils/analytics';
import { getAttribution, getRef } from '../../utils/attribution';
import { resolveUserRole } from '../../utils/userRole';
import { TripContext } from '../../context/TripContext';

jest.mock('../../services/voteApi');
jest.mock('../../services/paymentApi', () => ({
    paymentApi: { createDepositSession: jest.fn(), createConsultationLead: jest.fn() },
}));
jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));
jest.mock('../../utils/attribution', () => ({ getAttribution: jest.fn(), getRef: jest.fn() }));
jest.mock('../../utils/userRole', () => ({ resolveUserRole: jest.fn() }));

const DEFAULT_RESULT = {
    result: [
        { activityId: 'act1', name: 'Tank Driving', price: 150, likeCount: 3, skipCount: 0 },
        { activityId: 'act2', name: 'Spa Day', price: 80, likeCount: 2, skipCount: 1 },
    ],
    totalPrice: 230,
    budget: 500,
    numberOfTravelers: 2,
    suggestions: [],
    destinationSlug: 'bali',
    startDate: '2026-08-01',
    endDate: '2026-08-10',
};

function renderAt(entry, tripState = { tripItems: [] }) {
    return render(
        <TripContext.Provider value={{ state: tripState, dispatch: jest.fn() }}>
            <MemoryRouter initialEntries={[entry]}>
                <Routes>
                    <Route path="/vote/:shareToken/result" element={<VoteResultPage />} />
                </Routes>
            </MemoryRouter>
        </TripContext.Provider>
    );
}

beforeEach(() => {
    localStorage.clear();
    pushEvent.mockClear();
    resolveUserRole.mockReturnValue('organizer');
    voteApi.getResult.mockResolvedValue(DEFAULT_RESULT);
    getAttribution.mockReturnValue({});
    getRef.mockReturnValue(null);
    paymentApi.createConsultationLead.mockResolvedValue({ bookingId: 'b1', message: 'ok' });
    paymentApi.createDepositSession.mockResolvedValue({ bookingId: 'b1', checkoutUrl: 'https://checkout/cs_1' });
});

// --- A16: checkout_viewed ---

test('A16: checkout_viewed fires once after successful getResult with correct params', async () => {
    renderAt('/vote/tok-abc/result');

    // Wait for result data to appear on screen
    expect(await screen.findByText('Tank Driving')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', {
        trip_id: 'tok-abc',
        user_role: 'organizer',
        items_count: 2,
        value: 230,
        currency: 'EUR',
    });
    expect(resolveUserRole).toHaveBeenCalledWith('tok-abc');
});

test('A16: checkout_viewed does NOT fire when getResult rejects', async () => {
    voteApi.getResult.mockRejectedValue(new Error('Result not available yet'));

    renderAt('/vote/tok-err/result');

    // Wait for the error to render
    expect(await screen.findByText(/Result not available yet/i)).toBeInTheDocument();

    expect(pushEvent).not.toHaveBeenCalledWith('checkout_viewed', expect.anything());
});

test('A16: checkout_viewed fires only once even if the component re-renders', async () => {
    const { rerender } = renderAt('/vote/tok-once/result');

    expect(await screen.findByText('Tank Driving')).toBeInTheDocument();
    expect(pushEvent).toHaveBeenCalledTimes(1);

    // Trigger a re-render by providing a new tripState reference (simulates context update)
    rerender(
        <TripContext.Provider value={{ state: { tripItems: [] }, dispatch: jest.fn() }}>
            <MemoryRouter initialEntries={['/vote/tok-once/result']}>
                <Routes>
                    <Route path="/vote/:shareToken/result" element={<VoteResultPage />} />
                </Routes>
            </MemoryRouter>
        </TripContext.Provider>
    );

    // Still only one call
    expect(pushEvent).toHaveBeenCalledTimes(1);
});

test('A16: items_count reflects actual number of result activities', async () => {
    voteApi.getResult.mockResolvedValue({
        ...DEFAULT_RESULT,
        result: [
            { activityId: 'act1', name: 'Bungee Jumping', price: 99, likeCount: 4, skipCount: 0 },
        ],
        totalPrice: 99,
    });

    renderAt('/vote/tok-single/result');

    expect(await screen.findByText('Bungee Jumping')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', expect.objectContaining({
        items_count: 1,
        value: 99,
    }));
});

test('A16: value is 0 and items_count is 0 when result list is empty', async () => {
    voteApi.getResult.mockResolvedValue({
        ...DEFAULT_RESULT,
        result: [],
        totalPrice: 0,
    });

    renderAt('/vote/tok-empty/result');

    // Empty-state message is shown
    expect(await screen.findByText(/The group didn't agree on anything/i)).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', expect.objectContaining({
        items_count: 0,
        value: 0,
    }));
});

test('A16: user_role comes from resolveUserRole with the shareToken', async () => {
    resolveUserRole.mockReturnValue('participant');

    renderAt('/vote/tok-part/result');

    expect(await screen.findByText('Tank Driving')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledWith('checkout_viewed', expect.objectContaining({
        user_role: 'participant',
        trip_id: 'tok-part',
    }));
});

// --- UTM attribution on consultation-lead bookings ---

test('consultation-lead payload carries campaign attribution (utm_*, ref)', async () => {
    const user = userEvent.setup();
    // Only the initiator (manager token + initiator flag) sees the payment actions.
    localStorage.setItem('myhive-manager-tok-utm', 'mgr-1');
    localStorage.setItem('myhive-initiator-tok-utm', 'true');
    getAttribution.mockReturnValue({ utm_source: 'facebook', utm_campaign: 'summer2026', utm_content: 'video-ad-3' });
    getRef.mockReturnValue('partner42');

    renderAt('/vote/tok-utm/result');

    await user.click(await screen.findByRole('button', { name: /Contact our consultant/i }));
    await user.type(screen.getByLabelText(/Full Name/i), 'Jane Doe');
    await user.type(screen.getByLabelText(/Email Address/i), 'jane@example.com');
    await user.type(screen.getByLabelText(/Phone Number/i), '+1 555 123 4567');
    await user.click(screen.getByRole('button', { name: /Submit Booking/i }));

    expect(paymentApi.createConsultationLead).toHaveBeenCalledWith(
        'tok-utm',
        'mgr-1',
        expect.objectContaining({
            userEmail: 'jane@example.com',
            utm_source: 'facebook',
            utm_campaign: 'summer2026',
            utm_content: 'video-ad-3',
            ref: 'partner42',
        }),
    );
});

// --- CART branch: ranked tally result (no budget, no payments) ---

const CART_RESULT = {
    voteMode: 'CART',
    participantCount: 9,
    numberOfTravelers: 8,
    totalPrice: 105,
    budget: null,
    remaining: null,
    destinationName: 'Prague',
    destinationSlug: 'prague',
    suggestions: [],
    result: [
        { activityId: 'a-1', name: 'Bar Crawl', price: 45, likeCount: 8, skipCount: 0 },
        { activityId: 'a-2', name: 'Karting', price: 60, likeCount: 4, skipCount: 0 },
    ],
};

test('CART result renders the ranked tally without budget or payments', async () => {
    voteApi.getResult.mockResolvedValue(CART_RESULT);

    renderAt('/vote/t-1/result');

    expect(await screen.findByText('Bar Crawl')).toBeInTheDocument();
    expect(screen.getByText('9 mates have voted')).toBeInTheDocument();
    expect(screen.queryByText('Budget')).not.toBeInTheDocument();
    expect(screen.queryByText(/prepayment/i)).not.toBeInTheDocument();
});

test('CART result does NOT fire checkout_viewed (its real checkout funnel event fires in Trip Builder)', async () => {
    voteApi.getResult.mockResolvedValue(CART_RESULT);

    renderAt('/vote/t-1/result');

    expect(await screen.findByText('Bar Crawl')).toBeInTheDocument();
    expect(pushEvent).not.toHaveBeenCalledWith('checkout_viewed', expect.anything());
});

test('CART result shows Proceed to Checkout only for the initiator', async () => {
    voteApi.getResult.mockResolvedValue(CART_RESULT);
    localStorage.setItem('myhive-manager-t-1', 'm-1');
    localStorage.setItem('myhive-initiator-t-1', 'true');

    renderAt('/vote/t-1/result');

    expect(await screen.findByRole('button', { name: 'Proceed to Checkout' })).toBeInTheDocument();
});

test('CART result hides Proceed to Checkout for a non-initiator', async () => {
    voteApi.getResult.mockResolvedValue(CART_RESULT);

    renderAt('/vote/t-1/result');

    expect(await screen.findByText('Bar Crawl')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Proceed to Checkout' })).not.toBeInTheDocument();
});

// --- Option B: a self-added activity is bookable even with an empty voted result ---

test('B: consultant CTA shows on empty result when the initiator added an activity, and the payload includes it', async () => {
    const user = userEvent.setup();
    localStorage.setItem('myhive-manager-tok-add', 'mgr-9');
    localStorage.setItem('myhive-initiator-tok-add', 'true');
    Object.defineProperty(window, 'location', { configurable: true, value: { assign: jest.fn(), href: '' } });
    voteApi.getResult.mockResolvedValue({
        ...DEFAULT_RESULT,
        result: [], // the group didn't agree on anything
        totalPrice: 0,
        suggestions: [
            { activityId: 'sug1', name: 'Jet Ski', price: 60 },
            { activityId: 'sug2', name: 'Kayaking', price: 40 },
        ],
    });

    // The initiator added only sug1 to their trip (full activity shape, as TripContext stores it).
    renderAt('/vote/tok-add/result', { tripItems: [{ id: 'sug1', name: 'Jet Ski', price: 60 }] });

    // Online payment is temporarily disabled, so the deposit CTA is hidden; the consultant
    // CTA still appears because the initiator's trip has a payable activity.
    const consultBtn = await screen.findByRole('button', { name: /Contact our consultant/i });
    expect(consultBtn).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Book & pay 30% prepayment/i })).not.toBeInTheDocument();

    await user.click(consultBtn);
    await user.type(screen.getByLabelText(/Full Name/i), 'Joe Tester');
    await user.type(screen.getByLabelText(/Email Address/i), 'joe@example.com');
    await user.type(screen.getByLabelText(/Phone Number/i), '+1 555 000 1111');
    await user.click(screen.getByRole('button', { name: /Submit Booking/i }));

    // The lead carries exactly the added activity (sug1), not the un-added sug2.
    expect(paymentApi.createConsultationLead).toHaveBeenCalledWith(
        'tok-add',
        'mgr-9',
        expect.objectContaining({
            destinations: [expect.objectContaining({
                activities: [expect.objectContaining({ activityId: 'sug1', activityName: 'Jet Ski' })],
            })],
        }),
    );
});
