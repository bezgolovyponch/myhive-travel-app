import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import PaymentSuccessPage from './PaymentSuccessPage';
import PaymentCancelledPage from './PaymentCancelledPage';
import {TripProvider} from '../context/TripContext';
import {WHATSAPP_URL} from '../services/config';

jest.mock('../utils/analytics', () => ({pushEvent: jest.fn()}));
const {pushEvent} = require('../utils/analytics');

function renderSuccessPage(route = '/payment/success?booking=b1') {
    return render(
        <MemoryRouter initialEntries={[route]}>
            <TripProvider>
                <PaymentSuccessPage />
            </TripProvider>
        </MemoryRouter>
    );
}

afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
});

test('success page confirms the payment is being processed', () => {
    renderSuccessPage();
    expect(screen.getByRole('heading', {name: /thank you/i})).toBeInTheDocument();
});

test('success page clears the trip so paid activities are not booked twice', () => {
    localStorage.setItem('myhive-trip-items', JSON.stringify([{id: 'a1', name: 'Beer Tour', price: 25}]));
    localStorage.setItem('myhive-trip-setup', JSON.stringify({travelers: 4, startDate: '2026-08-01', endDate: '2026-08-03', budget: 500}));

    renderSuccessPage();

    expect(JSON.parse(localStorage.getItem('myhive-trip-items'))).toEqual([]);
    const expectedSetup = {travelers: 1, startDate: '', endDate: '', budget: null};
    expect(JSON.parse(localStorage.getItem('myhive-trip-setup'))).toEqual(expectedSetup);
});

test('success page without a booking reference leaves the trip intact', () => {
    // A bare /payment/success (opened from history or a shared link) is not proof of payment.
    localStorage.setItem('myhive-trip-items', JSON.stringify([{id: 'a1', name: 'Beer Tour', price: 25}]));
    localStorage.setItem('myhive-trip-vote-session', 'tok-123');

    render(
        <MemoryRouter initialEntries={['/payment/success']}>
            <TripProvider>
                <PaymentSuccessPage />
            </TripProvider>
        </MemoryRouter>
    );

    expect(JSON.parse(localStorage.getItem('myhive-trip-items'))).toHaveLength(1);
    expect(localStorage.getItem('myhive-trip-vote-session')).toBe('tok-123');
});

test('success page clears the stale group-vote session key so Trip Builder cannot re-annotate a paid trip', () => {
    localStorage.setItem('myhive-trip-vote-session', 'tok-123');

    renderSuccessPage();

    expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
});

test('success page offers the WhatsApp follow-up contact', () => {
    renderSuccessPage();
    const link = screen.getByRole('link', {name: /whatsapp/i});
    expect(link).toHaveAttribute('href', WHATSAPP_URL);
    expect(link).toHaveAttribute('target', '_blank');
});

test('success page fires payment_completed with revenue params from the return URL', () => {
    renderSuccessPage('/payment/success?booking=b1&value=30.00&currency=EUR&trip_id=TRV-ABC123');

    expect(pushEvent).toHaveBeenCalledWith('payment_completed', {
        transaction_id: 'b1',
        value: 30,
        currency: 'EUR',
        trip_id: 'TRV-ABC123',
        event_id: undefined,
    });
});

test('success page echoes the event_id URL param so browser Purchase dedups against server CAPI Purchase', () => {
    renderSuccessPage('/payment/success?booking=b1&value=30.00&currency=EUR&trip_id=TRV-ABC123&event_id=evt-xyz');

    const [, params] = pushEvent.mock.calls.find(([event]) => event === 'payment_completed');
    expect(params.event_id).toBe('evt-xyz');
});

test('payment_completed fires at most once per booking across re-mounts', () => {
    renderSuccessPage('/payment/success?booking=b1&value=30.00&currency=EUR');
    renderSuccessPage('/payment/success?booking=b1&value=30.00&currency=EUR');

    const paymentCalls = pushEvent.mock.calls.filter(([event]) => event === 'payment_completed');
    expect(paymentCalls).toHaveLength(1);
});

test('no payment_completed without a booking reference (bare success URL is not proof of payment)', () => {
    renderSuccessPage('/payment/success');

    expect(pushEvent).not.toHaveBeenCalledWith('payment_completed', expect.anything());
});

test('cancelled page shows a cancellation notice and keeps the trip intact', () => {
    localStorage.setItem('myhive-trip-items', JSON.stringify([{id: 'a1', name: 'Beer Tour', price: 25}]));

    render(
        <MemoryRouter initialEntries={['/payment/cancelled']}>
            <TripProvider>
                <PaymentCancelledPage />
            </TripProvider>
        </MemoryRouter>
    );

    expect(screen.getByRole('heading', {name: /payment cancelled/i})).toBeInTheDocument();
    // Cancelling on Stripe must not lose the trip — the user may retry the deposit.
    expect(JSON.parse(localStorage.getItem('myhive-trip-items'))).toHaveLength(1);
});
