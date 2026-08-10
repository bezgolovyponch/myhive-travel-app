import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PaymentActions from './PaymentActions';
import {paymentApi} from '../services/paymentApi';

jest.mock('../services/paymentApi', () => ({
    paymentApi: {
        createDepositSession: jest.fn(),
        createConsultationLead: jest.fn(),
    },
}));

jest.mock('../utils/analytics', () => ({
    pushEvent: jest.fn(),
    navigateAfterEvents: jest.fn(),
}));

const {pushEvent, navigateAfterEvents} = require('../utils/analytics');

function renderActions() {
    const makeBookingPayload = jest.fn((cd) => ({userEmail: cd.email, customerName: cd.fullName}));
    render(
        <PaymentActions
            voteShareToken="share-1"
            managerToken="mgr-1"
            tripData={{tripItems: [{id: 'a1', price: 50}]}}
            initialValues={{numberOfTravelers: 4, startDate: '2026-07-01', endDate: '2026-07-05'}}
            makeBookingPayload={makeBookingPayload}
        />
    );
    return {makeBookingPayload};
}

async function fillContact(user) {
    await user.type(screen.getByLabelText(/Full Name/i), 'Jane Doe');
    await user.type(screen.getByLabelText(/Email Address/i), 'jane@example.com');
    await user.type(screen.getByLabelText(/Phone Number/i), '+1 555 123 4567');
}

const mockAssign = jest.fn();

beforeAll(() => {
    Object.defineProperty(window, 'location', {
        value: {assign: mockAssign},
        writable: true,
    });
});

beforeEach(() => {
    mockAssign.mockReset();
    pushEvent.mockClear();
    navigateAfterEvents.mockClear();
    localStorage.clear();
    paymentApi.createDepositSession.mockResolvedValue({bookingId: 'b1', checkoutUrl: 'https://checkout/cs_1'});
    paymentApi.createConsultationLead.mockResolvedValue({bookingId: 'b1', message: 'ok'});
});

test('deposit flow starts a checkout session and redirects', async () => {
    const user = userEvent.setup();
    renderActions();

    await user.click(screen.getByRole('button', {name: /Book & pay 30% prepayment/i}));
    await fillContact(user);
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(paymentApi.createDepositSession).toHaveBeenCalledWith(
        'share-1', 'mgr-1', expect.objectContaining({userEmail: 'jane@example.com'}));
    expect(navigateAfterEvents).toHaveBeenCalledWith('https://checkout/cs_1');
});

// payment_page_viewed (ТЗ §8). The app has no payment page of its own — the
// share is paid on Stripe Checkout — so the handoff to the provider IS the
// funnel step. Without it nothing at all sits between checkout_viewed and
// payment_completed, and everyone lost on Stripe is invisible.
test('deposit handoff pushes payment_page_viewed before leaving for Stripe', async () => {
    const user = userEvent.setup();
    // resolveUserRole reads this: the manager token marks the trip's organizer.
    localStorage.setItem('myhive-manager-share-1', 'mgr-1');
    renderActions();

    await user.click(screen.getByRole('button', {name: /Book & pay 30% prepayment/i}));
    await fillContact(user);
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(pushEvent).toHaveBeenCalledWith('payment_page_viewed', {
        trip_id: 'share-1',
        user_role: 'organizer',
        currency: 'EUR',
    });
    // Ordering is the whole point of navigateAfterEvents: the push has to happen
    // while the document is still alive.
    expect(pushEvent.mock.invocationCallOrder[0])
        .toBeLessThan(navigateAfterEvents.mock.invocationCallOrder[0]);
});

test('a participant opening the deposit reports its own role', async () => {
    const user = userEvent.setup();
    // No manager/initiator token for this share -> not the organizer.
    renderActions();

    await user.click(screen.getByRole('button', {name: /Book & pay 30% prepayment/i}));
    await fillContact(user);
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(pushEvent).toHaveBeenCalledWith(
        'payment_page_viewed', expect.objectContaining({user_role: 'participant'}));
});

test('a failed checkout session pushes nothing — the payment page was never reached', async () => {
    const user = userEvent.setup();
    paymentApi.createDepositSession.mockRejectedValue(new Error('Trip total is too small to take a deposit'));
    renderActions();

    await user.click(screen.getByRole('button', {name: /Book & pay 30% prepayment/i}));
    await fillContact(user);
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(await screen.findByText(/too small to take a deposit/i)).toBeInTheDocument();
    expect(pushEvent).not.toHaveBeenCalled();
    expect(navigateAfterEvents).not.toHaveBeenCalled();
});

test('consultant flow creates a lead and shows confirmation', async () => {
    const user = userEvent.setup();
    renderActions();

    await user.click(screen.getByRole('button', {name: /Contact our consultant/i}));
    await fillContact(user);
    await user.click(screen.getByRole('button', {name: /Submit Booking/i}));

    expect(paymentApi.createConsultationLead).toHaveBeenCalledWith(
        'share-1', 'mgr-1', expect.objectContaining({userEmail: 'jane@example.com'}));
    expect(await screen.findByText(/consultant will contact you/i)).toBeInTheDocument();
    expect(mockAssign).not.toHaveBeenCalled();
});
